package com.awty.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.ZonedDateTime
import java.util.*
import java.io.File

class AwtyStepService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "awty_foreground_channel"
        private const val prefsName = "awty_prefs"
        
        private var platformChannel: AwtyPlatformChannel? = null
        
        fun setPlatformChannel(channel: AwtyPlatformChannel) {
            platformChannel = channel
        }
        
        fun writeToLogFile(context: android.content.Context, message: String) {
            try {
                val timestamp = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"))
                val logEntry = "$message [$timestamp]\n"
                val logFile = java.io.File(context.getExternalFilesDir(null), "awty_service.txt")
                logFile.appendText(logEntry)
                Log.d("AWTY_SERVICE", "LOG: $message")
            } catch (e: Exception) {
                Log.e("AWTY_SERVICE", "Error writing to log file: $e")
            }
        }
    }

    private var deltaSteps: Int = 0
    private var baseline: Int = 0
    private var current: Int = 0
    private var goalId: String? = null
    private var appName: String = "Unknown App"
    private var testMode: Boolean = false  // New field
    private var testModeTimer: CountDownTimer? = null  // Timer for test mode
    private var timer: Timer? = null
    // hasStartedPolling variable - REMOVED (no longer used with platform channel approach)
    private var pollingWakeLock: android.os.PowerManager.WakeLock? = null
    private lateinit var powerManager: PowerManager
    private lateinit var packageManager: android.content.pm.PackageManager
    private lateinit var packageName: String
    private lateinit var prefs: android.content.SharedPreferences
    private var segmentStartDate: String = ""
    private var stepsAccumulated: Int = 0
    private var isForegroundService: Boolean = false

    override fun onCreate() {
        super.onCreate()
        Log.d("AWTY_SERVICE", "onCreate called")
        writeToLogFile(this, "SERVICE_CREATED")
        
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        packageManager = getPackageManager()
        packageName = getPackageName()
        prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        
        // Load persisted segment start date and accumulated steps
        segmentStartDate = prefs.getString("segmentStartDate", "") ?: ""
        stepsAccumulated = prefs.getInt("stepsAccumulated", 0)
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AWTY_SERVICE", "onStartCommand called")
        writeToLogFile(this, "SERVICE_STARTED")
        
        // Check if this is a service restart after goal completion
        val intentGoalId = intent?.getStringExtra("goalId")
        val goalId = if (intentGoalId != null) {
            intentGoalId
        } else {
            // Try to get the last active goalId from SharedPreferences
            prefs.getString("lastGoalId", null) ?: UUID.randomUUID().toString()
        }
        
        // IMMEDIATE CHECK: Always check for recently completed goals regardless of intent
        Log.d("AWTY_SERVICE", "CHECKING_FOR_RECENT_GOALS: intentGoalId=$intentGoalId, action=${intent?.action}")
        writeToLogFile(this, "CHECKING_FOR_RECENT_GOALS: intentGoalId=$intentGoalId, action=${intent?.action}")
        
        // Check if there's any recently completed goal (within the last 5 minutes) regardless of goalId
        val allKeys = prefs.all.keys.filter { it.startsWith("goalReachedTime_") }
        for (key in allKeys) {
            val timeStr = prefs.getString(key, null)
            if (timeStr != null) {
                try {
                    val reachedTime = ZonedDateTime.parse(timeStr)
                    val now = ZonedDateTime.now()
                    val minutesSinceReached = java.time.Duration.between(reachedTime, now).toMinutes()
                    
                    if (minutesSinceReached < 5) { // Within the last 5 minutes
                        val recentGoalId = key.removePrefix("goalReachedTime_")
                        Log.d("AWTY_SERVICE", "SERVICE_RESTART_PREVENTED: Recent goal completed for goalId=$recentGoalId (${minutesSinceReached}m ago)")
                        writeToLogFile(this, "SERVICE_RESTART_PREVENTED: Recent goal completed for goalId=$recentGoalId (${minutesSinceReached}m ago)")
                        stopSelf()
                        return START_NOT_STICKY
                    }
                } catch (e: Exception) {
                    Log.e("AWTY_SERVICE", "Error parsing recent goal time: $e")
                }
            }
        }
        
        // Check if the specific goalId was reached recently
        val goalReached = prefs.getBoolean("goalReached_$goalId", false)
        val goalReachedTime = prefs.getString("goalReachedTime_$goalId", null)
        
        if (goalReached && goalReachedTime != null) {
            try {
                val reachedTime = ZonedDateTime.parse(goalReachedTime)
                val now = ZonedDateTime.now()
                val hoursSinceReached = java.time.Duration.between(reachedTime, now).toHours()
                
                if (hoursSinceReached < 1) { // Within the last hour
                    Log.d("AWTY_SERVICE", "SERVICE_RESTART_PREVENTED: Goal already reached for goalId=$goalId (${hoursSinceReached}h ago)")
                    writeToLogFile(this, "SERVICE_RESTART_PREVENTED: Goal already reached for goalId=$goalId (${hoursSinceReached}h ago)")
                    stopSelf()
                    return START_NOT_STICKY
                }
            } catch (e: Exception) {
                Log.e("AWTY_SERVICE", "Error parsing goal reached time: $e")
            }
        }
        
        // Handle step count updates from platform channel
        if (intent?.action == "UPDATE_STEP_COUNT") {
            val stepCount = intent.getIntExtra("stepCount", 0)
            val updateGoalId = intent.getStringExtra("goalId") ?: ""
            
            Log.d("AWTY_SERVICE", "UPDATE_STEP_COUNT: stepCount=$stepCount, goalId=$updateGoalId")
            writeToLogFile(this, "UPDATE_STEP_COUNT: stepCount=$stepCount, goalId=$updateGoalId")
            
            // IMMEDIATELY start foreground service to prevent crash
            if (!isForegroundService) {
                startForegroundService()
            }
            
            // Set baseline on first step count update if not set
            if (baseline == 0) {
                baseline = stepCount
                Log.d("AWTY_SERVICE", "BASELINE_SET: baseline=$baseline")
                writeToLogFile(this, "BASELINE_SET: baseline=$baseline")
            }
            
            // Update current step count
            current = stepCount
            
            // Check if goal is reached
            val stepsTaken = current - baseline
            Log.d("AWTY_SERVICE", "STEP_CHECK: current=$current, baseline=$baseline, stepsTaken=$stepsTaken, deltaSteps=$deltaSteps")
            writeToLogFile(this, "STEP_CHECK: current=$current, baseline=$baseline, stepsTaken=$stepsTaken, deltaSteps=$deltaSteps")
            
            if (stepsTaken >= deltaSteps && goalId == updateGoalId) {
                Log.d("AWTY_SERVICE", "GOAL_REACHED: stepsTaken=$stepsTaken, deltaSteps=$deltaSteps")
                writeToLogFile(this, "GOAL_REACHED: stepsTaken=$stepsTaken, deltaSteps=$deltaSteps")
                markGoalAsReached()
                stopSelf()
                return START_NOT_STICKY  // Don't restart service after goal completion
            }
            
            // Calculate steps remaining and update notification
            val stepsRemaining = deltaSteps - stepsTaken
            writeStatusFile(stepsTaken, stepsRemaining)
            updateForegroundNotification(stepsRemaining)
            
            // Save updated state
            saveState(baseline, deltaSteps, current, true)
            
            return START_STICKY
        }
        
        deltaSteps = intent?.getIntExtra("deltaSteps", 1000) ?: 1000
        // goalId is already declared above
        
        // Persist the goalId for future service restarts
        prefs.edit().putString("lastGoalId", goalId).apply()
        
        // Get app name from intent or fall back to persisted value
        val intentAppName = intent?.getStringExtra("appName")
        appName = if (intentAppName != null && intentAppName != "Unknown App") {
            // Save the app name for future service restarts
            prefs.edit().putString("appName", intentAppName).apply()
            Log.d("AWTY_SERVICE", "APP_NAME_SAVED: '$intentAppName' to SharedPreferences")
            writeToLogFile(this, "APP_NAME_SAVED: '$intentAppName' to SharedPreferences")
            intentAppName
        } else {
            // Use persisted app name or default
            val persistedAppName = prefs.getString("appName", "Unknown App") ?: "Unknown App"
            Log.d("AWTY_SERVICE", "APP_NAME_LOADED: '$persistedAppName' from SharedPreferences")
            writeToLogFile(this, "APP_NAME_LOADED: '$persistedAppName' from SharedPreferences")
            
            // If we're loading from SharedPreferences, make sure it's not "Unknown App"
            if (persistedAppName == "Unknown App") {
                Log.w("AWTY_SERVICE", "WARNING: App name is 'Unknown App' from SharedPreferences")
                writeToLogFile(this, "WARNING: App name is 'Unknown App' from SharedPreferences")
            }
            
            persistedAppName
        }
        
        testMode = intent?.getBooleanExtra("testMode", false) ?: false  // Extract test mode
        
        // Debug intent extras
        Log.d("AWTY_SERVICE", "Intent extras: ${intent?.extras?.keySet()?.joinToString(", ") ?: "null"}")
        writeToLogFile(this, "Intent extras: ${intent?.extras?.keySet()?.joinToString(", ") ?: "null"}")
        
        Log.d("AWTY_SERVICE", "onStartCommand: deltaSteps=$deltaSteps, goalId=$goalId, appName='$appName', testMode=$testMode")
        writeToLogFile(this, "SERVICE_CONFIG: deltaSteps=$deltaSteps, goalId=$goalId, appName='$appName', testMode=$testMode")
        
        // Clear any existing "Goal reached" notifications when starting a new goal
        clearGoalReachedNotifications()
        
        // Persist goalId as active
        goalId?.let { prefs.edit().putBoolean("goalActive_$it", true).apply() }
        
        // Foreground service notification (should be silent)
        startForegroundService()
        
        if (testMode) {
            startTestMode()
        } else {
            // For platform channel approach, we don't need polling
            // Step data comes from Flutter app via platform channel
            Log.d("AWTY_SERVICE", "PLATFORM_CHANNEL_MODE: No polling needed, waiting for step updates from Flutter")
            writeToLogFile(this, "PLATFORM_CHANNEL_MODE: No polling needed, waiting for step updates from Flutter")
            
            // Initialize baseline and current to 0
            baseline = 0
            current = 0
            
            // Save initial state
            saveState(baseline, deltaSteps, current, true)
            Log.d("AWTY_SERVICE", "STATE_SAVED: baseline=$baseline, deltaSteps=$deltaSteps, current=$current, running=true")
            writeToLogFile(this, "STATE_SAVED: baseline=$baseline, deltaSteps=$deltaSteps, current=$current, running=true")
        }
        
        Log.d("AWTY_SERVICE", "onStartCommand completed")
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AWTY_SERVICE", "onDestroy called")
        writeToLogFile(this, "SERVICE_DESTROYED: deltaSteps=$deltaSteps, baseline=$baseline, current=$current")
        
        // Release polling wake lock
        pollingWakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d("AWTY_SERVICE", "POLLING_WAKELOCK_RELEASED: Service destroyed")
                writeToLogFile(this, "POLLING_WAKELOCK_RELEASED: Service destroyed")
            }
        }
        
        timer?.cancel()
        testModeTimer?.cancel()  // Cancel test mode timer if running
        saveState(baseline, deltaSteps, current, false)
    }

    private fun startTestMode() {
        Log.d("AWTY_SERVICE", "Starting TEST MODE - goal will be reached in 60 seconds")
        writeToLogFile(this, "TEST MODE: Goal will be reached in 60 seconds regardless of steps")
        
        // Create a 60-second countdown timer
        testModeTimer = object : CountDownTimer(60000, 1000) {  // 60 seconds, 1 second intervals
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                Log.d("AWTY_SERVICE", "Test mode countdown: $secondsRemaining seconds remaining")
                
                // Update status file to show test mode progress
                updateStatusFile(
                    currentSteps = 0,
                    deltaSteps = deltaSteps,
                    baselineSteps = 0,
                    stepsTaken = 0,
                    stepsRemaining = deltaSteps,
                    isRunning = true,
                    testMode = true,
                    testModeSecondsRemaining = secondsRemaining.toInt()
                )
            }

            override fun onFinish() {
                Log.d("AWTY_SERVICE", "TEST MODE: Goal reached after 60 seconds")
                writeToLogFile(this@AwtyStepService, "TEST MODE: Goal reached after 60 seconds")
                
                // Mark goal as reached
                markGoalAsReached()
                
                // Stop the service
                stopSelf()
            }
        }.start()
    }

    private fun startNormalTracking() {
        // Existing normal step tracking logic
        // This is the original logic that was moved to the else block
    }

    private fun markGoalAsReached() {
        Log.d("AWTY_SERVICE", "GOAL_REACHED: Marking goal as reached, appName='$appName'")
        writeToLogFile(this, "GOAL_REACHED: Marking goal as reached, appName='$appName'")
        
        // Update goal status
        goalId?.let {
            prefs.edit().putBoolean("goalActive_$it", false)
                .putBoolean("goalReached_$it", true)
                .putString("goalReachedTime_$it", ZonedDateTime.now().toString())
                .apply()
        }
        
        // Update foreground notification first
        updateForegroundNotification(0)
        
        // Notify Flutter app via platform channel
        platformChannel?.notifyMilestoneReached()
        
        // Add a delay before stopping the service to ensure the callback is processed
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("AWTY_SERVICE", "GOAL_REACHED: Stopping service after delay")
            writeToLogFile(this@AwtyStepService, "GOAL_REACHED: Stopping service after delay")
            stopSelf()
        }, 1000) // 1 second delay
        
        Log.d("AWTY_SERVICE", "GOAL_REACHED: Goal reached, service will stop in 1 second")
        writeToLogFile(this, "GOAL_REACHED: Goal reached, service will stop in 1 second")
    }

    private fun updateStatusFile(
        currentSteps: Int,
        deltaSteps: Int,
        baselineSteps: Int,
        stepsTaken: Int,
        stepsRemaining: Int,
        isRunning: Boolean,
        testMode: Boolean = false,
        testModeSecondsRemaining: Int? = null
    ) {
        val status = JSONObject().apply {
            put("currentSteps", currentSteps)
            put("deltaSteps", deltaSteps)
            put("baselineSteps", baselineSteps)
            put("stepsTaken", stepsTaken)
            put("stepsRemaining", stepsRemaining)
            put("isRunning", isRunning)
            put("goalId", goalId)
            put("lastUpdated", System.currentTimeMillis())
            put("testMode", testMode)
            if (testModeSecondsRemaining != null) {
                put("testModeSecondsRemaining", testModeSecondsRemaining)
            }
        }
        
        // Write to status file
        try {
            val statusFile = File(filesDir, "awty_status.json")
            statusFile.writeText(status.toString())
        } catch (e: Exception) {
            Log.e("AWTY_SERVICE", "Error writing status file: $e")
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AWTY Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableVibration(false)
                setSound(null, null)
                vibrationPattern = null
                description = "Foreground service channel for silent step tracking."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.deleteNotificationChannel(CHANNEL_ID)
            manager?.createNotificationChannel(channel)
            Log.d("AWTY_SERVICE", "Notification channel created with vibration disabled and no sound.")
            writeToLogFile(this, "NOTIFICATION_CHANNEL_CREATED: silent tracking")
        }
    }



    // STEP 2: Simple polling - REMOVED
    // This method is no longer used since we get step data via platform channel
    // from the pedometer package in the Flutter app

    // STEP 3: Simple poll function - REMOVED
    // This method is no longer used since we get step data via platform channel
    // from the pedometer package in the Flutter app

    private fun saveState(baseline: Int, deltaSteps: Int, current: Int, running: Boolean) {
        prefs.edit()
            .putInt("baseline", baseline)
            .putInt("deltaSteps", deltaSteps)
            .putInt("current", current)
            .putBoolean("running", running)
            .apply()
    }
    
    private fun writeStatusFile(stepsTaken: Int, stepsRemaining: Int) {
        try {
            val status = mapOf(
                "isRunning" to (deltaSteps > 0),
                "currentSteps" to current,
                "baselineSteps" to baseline,
                "deltaSteps" to deltaSteps,
                "stepsTaken" to stepsTaken,
                "stepsRemaining" to stepsRemaining,
                "goalId" to (goalId ?: ""),
                "lastUpdated" to ZonedDateTime.now().toString()
            )
            
            val json = org.json.JSONObject(status).toString()
            val file = File(getExternalFilesDir(null), "awty_status.json")
            file.writeText(json)
            
            Log.d("AWTY_SERVICE", "STATUS_FILE_WRITTEN: $json")
            writeToLogFile(this, "STATUS_FILE_WRITTEN: $json")
            // Log the full file contents with a timestamp
            writeToLogFile(this, "AWTY_STATUS_JSON_WRITE: $json")
        } catch (e: Exception) {
            Log.e("AWTY_SERVICE", "STATUS_FILE_ERROR: ${e.message}")
            writeToLogFile(this, "STATUS_FILE_ERROR: ${e.message}")
        }
    }
    
    private fun updateForegroundNotification(stepsRemaining: Int) {
        try {
            val notificationText = if (stepsRemaining > 0) {
                "$stepsRemaining steps to goal"
            } else {
                "Goal reached!"
            }
            
            val notificationTitle = "$appName is tracking your steps"
            Log.d("AWTY_SERVICE", "UPDATE_NOTIFICATION: title='$notificationTitle', text='$notificationText', appName='$appName'")
            writeToLogFile(this, "UPDATE_NOTIFICATION: title='$notificationTitle', text='$notificationText', appName='$appName'")
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setSmallIcon(R.drawable.ic_walking_man)
                .setColor(android.graphics.Color.WHITE) // Force white color
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .setVibrate(null)
                .build()
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.notify(NOTIFICATION_ID, notification)
            
            Log.d("AWTY_SERVICE", "FOREGROUND_NOTIFICATION_UPDATED: $notificationText")
            writeToLogFile(this, "FOREGROUND_NOTIFICATION_UPDATED: $notificationText")
        } catch (e: Exception) {
            Log.e("AWTY_SERVICE", "FOREGROUND_NOTIFICATION_ERROR: ${e.message}")
            writeToLogFile(this, "FOREGROUND_NOTIFICATION_ERROR: ${e.message}")
        }
    }



    // getCurrentStepCount() method - REMOVED
    // This method is no longer used since we get step data via platform channel
    // from the pedometer package in the Flutter app

    private fun firePermissionNotification() {
        // Create an intent to launch app settings for Activity Recognition permission
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$appName needs step tracking permission")
            .setContentText("Tap to grant Activity Recognition permission for step tracking.")
            .setSmallIcon(R.drawable.ic_walking_man)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID + 2, notification)
        
        Log.d("AWTY_SERVICE", "PERMISSION_NOTIFICATION_SENT: for $appName")
        writeToLogFile(this, "PERMISSION_NOTIFICATION_SENT: for $appName")
    }
    
    /**
     * Clear any existing "Goal reached" notifications when starting a new goal
     * This prevents the old "Goal reached" message from appearing when a new goal starts
     */
    private fun startForegroundService() {
        if (!isForegroundService) {
            val notificationTitle = "$appName is tracking your steps"
            Log.d("AWTY_SERVICE", "Creating notification with title: '$notificationTitle'")
            writeToLogFile(this, "NOTIFICATION_CREATED: title='$notificationTitle'")
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(notificationTitle)
                .setContentText("Step tracking in progress")
                .setSmallIcon(R.drawable.ic_walking_man)
                .setColor(android.graphics.Color.WHITE) // Force white color
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .setVibrate(null)
                .build()
            Log.d("AWTY_SERVICE", "Calling startForeground immediately (silent notification)")
            startForeground(NOTIFICATION_ID, notification)
            isForegroundService = true
        }
    }

    private fun clearGoalReachedNotifications() {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            
            // Cancel the milestone notification (NOTIFICATION_ID + 1)
            manager?.cancel(NOTIFICATION_ID + 1)
            
            // Also cancel any other potential goal-related notifications
            manager?.cancel(NOTIFICATION_ID + 2) // Permission notification
            manager?.cancel(NOTIFICATION_ID + 3) // Any other potential notifications
            
                    Log.d("AWTY_SERVICE", "GOAL_REACHED_NOTIFICATIONS_CLEARED: Cleared old notifications for new goal")
        writeToLogFile(this, "GOAL_REACHED_NOTIFICATIONS_CLEARED: Cleared old notifications for new goal")
    } catch (e: Exception) {
        Log.e("AWTY_SERVICE", "CLEAR_NOTIFICATIONS_ERROR: ${e.message}")
        writeToLogFile(this, "CLEAR_NOTIFICATIONS_ERROR: ${e.message}")
    }
}


} 