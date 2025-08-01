package com.awty.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.*
import java.time.ZonedDateTime
import java.util.*
import java.io.File

class AwtyStepService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "awty_foreground_channel"
        private const val MILESTONE_CHANNEL_ID = "awty_milestone_channel"
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
    private var timer: Timer? = null
    private var hasStartedPolling: Boolean = false
    private var pollingWakeLock: android.os.PowerManager.WakeLock? = null
    private lateinit var healthConnectClient: HealthConnectClient
    private lateinit var powerManager: PowerManager
    private lateinit var packageManager: android.content.pm.PackageManager
    private lateinit var packageName: String
    private lateinit var prefs: android.content.SharedPreferences
    private var segmentStartDate: String = ""
    private var stepsAccumulated: Int = 0

    override fun onCreate() {
        super.onCreate()
        Log.d("AWTY_SERVICE", "onCreate called")
        writeToLogFile(this, "SERVICE_CREATED")
        
        healthConnectClient = HealthConnectClient.getOrCreate(this)
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
        
        deltaSteps = intent?.getIntExtra("deltaSteps", 1000) ?: 1000
        goalId = intent?.getStringExtra("goalId") ?: UUID.randomUUID().toString()
        appName = intent?.getStringExtra("appName") ?: "Unknown App"
        
        Log.d("AWTY_SERVICE", "onStartCommand: deltaSteps=$deltaSteps, goalId=$goalId, appName=$appName")
        writeToLogFile(this, "SERVICE_CONFIG: deltaSteps=$deltaSteps, goalId=$goalId, appName=$appName")
        
        // Clear any existing "Goal reached" notifications when starting a new goal
        clearGoalReachedNotifications()
        
        // Persist goalId as active
        goalId?.let { prefs.edit().putBoolean("goalActive_$it", true).apply() }
        
        // Foreground service notification (should be silent)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$appName is tracking your steps")
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
        
        // STEP 1: Clear old baseline and get initial step count
        CoroutineScope(Dispatchers.IO).launch {
            // Clear old baseline to prevent negative step counts on new segments
            prefs.edit().remove("baseline").remove("current").remove("segmentStartDate").remove("stepsAccumulated").apply()
            Log.d("AWTY_SERVICE", "OLD_BASELINE_CLEARED: Starting fresh segment")
            writeToLogFile(this@AwtyStepService, "OLD_BASELINE_CLEARED: Starting fresh segment")
            
            val initialStepCount = getCurrentStepCount()
            baseline = initialStepCount
            current = initialStepCount
            // Store the segment start date and reset accumulated steps
            segmentStartDate = java.time.LocalDate.now().toString()
            stepsAccumulated = 0
            prefs.edit().putString("segmentStartDate", segmentStartDate).putInt("stepsAccumulated", stepsAccumulated).apply()
            Log.d("AWTY_SERVICE", "INIT: baseline=$baseline, deltaSteps=$deltaSteps, current=$current, segmentStartDate=$segmentStartDate")
            writeToLogFile(this@AwtyStepService, "INIT: baseline=$baseline, deltaSteps=$deltaSteps, current=$current, segmentStartDate=$segmentStartDate")
            
            // Save initial state
            saveState(baseline, deltaSteps, current, true)
            Log.d("AWTY_SERVICE", "STATE_SAVED: baseline=$baseline, deltaSteps=$deltaSteps, current=$current, running=true")
            writeToLogFile(this@AwtyStepService, "STATE_SAVED: baseline=$baseline, deltaSteps=$deltaSteps, current=$current, running=true")
            
            // STEP 2: Start simple polling timer
            startSimplePolling()
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
        writeToLogFile(this, "SERVICE_DESTROYED: hasStartedPolling=$hasStartedPolling, deltaSteps=$deltaSteps, baseline=$baseline, current=$current")
        
        // Release polling wake lock
        pollingWakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d("AWTY_SERVICE", "POLLING_WAKELOCK_RELEASED: Service destroyed")
                writeToLogFile(this, "POLLING_WAKELOCK_RELEASED: Service destroyed")
            }
        }
        
        timer?.cancel()
        saveState(baseline, deltaSteps, current, false)
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

    private fun createMilestoneNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            // Force recreation of the channel to ensure correct vibration settings
            manager?.deleteNotificationChannel(MILESTONE_CHANNEL_ID)
            Log.d("AWTY_SERVICE", "Milestone notification channel deleted for recreation")
            writeToLogFile(this, "MILESTONE_CHANNEL_DELETED: forcing recreation with correct settings")
            
            val channel = NotificationChannel(
                MILESTONE_CHANNEL_ID,
                "AWTY Milestones",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                // Set the aggressive vibration pattern directly on the channel
                vibrationPattern = getGoalVibrationPattern()
                // Use gentler notification sound instead of alarm sound
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
                description = "Milestone notifications with aggressive vibration and gentle notification sound."
            }
            manager?.createNotificationChannel(channel)
            Log.d("AWTY_SERVICE", "Milestone notification channel created with IMPORTANCE_HIGH, aggressive vibration and gentle sound.")
            writeToLogFile(this, "MILESTONE_CHANNEL_CREATED: high priority with aggressive vibration and gentle sound")
            
            // Log the channel settings to verify
            val createdChannel = manager?.getNotificationChannel(MILESTONE_CHANNEL_ID)
            if (createdChannel != null) {
                Log.d("AWTY_SERVICE", "CHANNEL_VERIFICATION: vibrationEnabled=${createdChannel.shouldVibrate()}, vibrationPattern=${createdChannel.vibrationPattern?.contentToString()}")
                writeToLogFile(this, "CHANNEL_VERIFICATION: vibrationEnabled=${createdChannel.shouldVibrate()}, vibrationPattern=${createdChannel.vibrationPattern?.contentToString()}")
            }
        }
    }

    // STEP 2: Simple polling with clear intervals
    private fun startSimplePolling() {
        if (deltaSteps <= 0) {
            Log.d("AWTY_SERVICE", "POLLING_SKIPPED: No active goal (deltaSteps=$deltaSteps)")
            writeToLogFile(this, "POLLING_SKIPPED: No active goal (deltaSteps=$deltaSteps)")
            return
        }
        
        Log.d("AWTY_SERVICE", "POLLING_STARTED: Starting timer for deltaSteps=$deltaSteps")
        writeToLogFile(this, "POLLING_STARTED: Starting timer for deltaSteps=$deltaSteps")
        
        hasStartedPolling = true
        
        // Acquire a partial wake lock to ensure timer fires reliably
        pollingWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AWTY::PollingWakeLock"
        )
        pollingWakeLock?.acquire()
        Log.d("AWTY_SERVICE", "WAKELOCK_ACQUIRED: For reliable polling")
        writeToLogFile(this, "WAKELOCK_ACQUIRED: For reliable polling")
        
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    val today = java.time.LocalDate.now().toString()
                    val lastDate = segmentStartDate
                    var stepsToday = getCurrentStepCount()
                    // If the date has changed, accumulate yesterday's steps and reset baseline
                    if (today != lastDate && lastDate.isNotEmpty()) {
                        val stepsAtMidnight = baseline
                        val stepsYesterday = stepsAtMidnight - baseline
                        // Actually, steps at midnight is baseline, so steps taken yesterday = (steps at 23:59) - baseline
                        val stepsToMidnight = stepsAtMidnight - baseline // This will be 0, so we need to use current - baseline before midnight
                        val stepsYesterdayTaken = current - baseline
                        stepsAccumulated += stepsYesterdayTaken
                        baseline = 0
                        segmentStartDate = today
                        prefs.edit().putString("segmentStartDate", segmentStartDate).putInt("stepsAccumulated", stepsAccumulated).apply()
                        Log.d("AWTY_SERVICE", "MIDNIGHT_ROLLOVER: stepsAccumulated=$stepsAccumulated, newBaseline=$baseline, newDate=$segmentStartDate")
                        writeToLogFile(this@AwtyStepService, "MIDNIGHT_ROLLOVER: stepsAccumulated=$stepsAccumulated, newBaseline=$baseline, newDate=$segmentStartDate")
                    }
                    // Always update current
                    current = stepsToday
                    // Calculate total steps taken since segment start (across days)
                    val stepsTaken = stepsAccumulated + (current - baseline)
                    val stepsRemaining = deltaSteps - stepsTaken
                    // Save state and update status file
                    saveState(baseline, deltaSteps, current, true)
                    writeStatusFile(stepsTaken, stepsRemaining)
                    updateForegroundNotification(stepsRemaining)
                    // Check for goal completion
                    if (stepsRemaining <= 0) {
                        fireGoalNotification()
                        stopSelf()
                    }
                }
            }
        }, 0, 30000) // Poll every 30 seconds
        
        // Wake lock will be released when service is destroyed or goal is reached
        // No auto-release timer - keep it active until completion
    }

    // STEP 3: Simple poll function
    private suspend fun performPoll() {
        val pollTime = ZonedDateTime.now().toString()
        Log.d("AWTY_SERVICE", "PERFORM_POLL_CALLED: at $pollTime")
        writeToLogFile(this, "PERFORM_POLL_CALLED: at $pollTime")
        
        // Don't poll if there's no active goal
        if (deltaSteps <= 0) {
            Log.d("AWTY_SERVICE", "POLL_SKIPPED: No active goal (deltaSteps=$deltaSteps)")
            writeToLogFile(this, "POLL_SKIPPED: No active goal (deltaSteps=$deltaSteps)")
            return
        }
        
        Log.d("AWTY_SERVICE", "POLL_STARTED: hasStartedPolling=$hasStartedPolling")
        writeToLogFile(this, "POLL_STARTED: hasStartedPolling=$hasStartedPolling")
        
        // Get current step count
        val previousCurrent = current
        current = getCurrentStepCount()
        
        // Check for step count reset (e.g., daily Health Connect reset)
        if (current < baseline) {
            Log.d("AWTY_SERVICE", "STEP_COUNT_RESET: current=$current < baseline=$baseline")
            writeToLogFile(this, "STEP_COUNT_RESET: current=$current < baseline=$baseline")
            
            // Adjust baseline to new current step count
            baseline = current
            Log.d("AWTY_SERVICE", "BASELINE_ADJUSTED: newBaseline=$baseline")
            writeToLogFile(this, "BASELINE_ADJUSTED: newBaseline=$baseline")
        }
        
        // Calculate progress
        val stepsTaken = current - baseline
        val stepsRemaining = deltaSteps - stepsTaken
        
        Log.d("AWTY_SERVICE", "POLL_RESULT: baseline=$baseline, deltaSteps=$deltaSteps, current=$current, stepsTaken=$stepsTaken, stepsRemaining=$stepsRemaining")
        writeToLogFile(this, "POLL_RESULT: goalId=$goalId, baseline=$baseline, deltaSteps=$deltaSteps, current=$current, stepsTaken=$stepsTaken, stepsRemaining=$stepsRemaining")
        saveState(baseline, deltaSteps, current, true)
        
        // Write status file for host application to read
        writeStatusFile(stepsTaken, stepsRemaining)
        
        // Update foreground notification with current progress
        updateForegroundNotification(stepsRemaining)
        
        // STEP 4: Check if goal reached (only after polling has started)
        if (stepsTaken >= deltaSteps && deltaSteps > 0 && stepsTaken > 0 && hasStartedPolling) {
            Log.d("AWTY_SERVICE", "GOAL_REACHED: baseline=$baseline, deltaSteps=$deltaSteps, current=$current, stepsTaken=$stepsTaken")
            writeToLogFile(this, "GOAL_REACHED: goalId=$goalId, stepsTaken=$stepsTaken, deltaSteps=$deltaSteps, baseline=$baseline, current=$current")
            
            withContext(Dispatchers.Main) {
                fireGoalNotification()
                
                // Clear the delta to prevent further polling
                deltaSteps = 0
                saveState(baseline, deltaSteps, current, false)
                
                // Release polling wake lock
                pollingWakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                        Log.d("AWTY_SERVICE", "POLLING_WAKELOCK_RELEASED: Goal reached")
                        writeToLogFile(this@AwtyStepService, "POLLING_WAKELOCK_RELEASED: Goal reached")
                    }
                }
                
                // Stop polling timer first
                timer?.cancel()
                timer = null
                writeToLogFile(this@AwtyStepService, "TIMER_CANCELLED: service stopping")
                
                // Wait 4 seconds for vibration to complete, then notify app and stop service
                Handler(Looper.getMainLooper()).postDelayed({
                    // Send callback to app
                    platformChannel?.notifyMilestoneReached()
                    Log.d("AWTY_SERVICE", "PLATFORM_CALLBACK_DELAYED: Sent to app after 4 seconds")
                    writeToLogFile(this@AwtyStepService, "PLATFORM_CALLBACK_DELAYED: Sent to app after 4 seconds")
                    
                    // Then stop the service
                    stopSelf()
                    Log.d("AWTY_SERVICE", "SERVICE_STOPPED_DELAYED: After 4 second delay")
                    writeToLogFile(this@AwtyStepService, "SERVICE_STOPPED_DELAYED: After 4 second delay")
                }, 4000L) // 4 seconds delay
            }
            
            // Update goal status
            goalId?.let {
                prefs.edit().putBoolean("goalActive_$it", false)
                    .putBoolean("goalReached_$it", true)
                    .putString("goalReachedTime_$it", ZonedDateTime.now().toString())
                    .apply()
            }
        } else {
            // Log why goal wasn't reached
            val reason = when {
                !hasStartedPolling -> "polling not started yet"
                stepsTaken < deltaSteps -> "stepsTaken ($stepsTaken) < deltaSteps ($deltaSteps)"
                deltaSteps <= 0 -> "deltaSteps ($deltaSteps) <= 0"
                stepsTaken <= 0 -> "stepsTaken ($stepsTaken) <= 0"
                else -> "unknown"
            }
            Log.d("AWTY_SERVICE", "GOAL_NOT_REACHED: $reason")
            writeToLogFile(this, "GOAL_NOT_REACHED: $reason")
        }
    }

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
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("$appName is tracking your steps")
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

    private fun fireGoalNotification() {
        Log.d("AWTY_SERVICE", "NOTIFICATION_FIRED: Goal notification triggered")
        writeToLogFile(this, "NOTIFICATION_FIRED: Goal notification triggered")
        
        // Acquire wake lock to ensure device wakes up
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "AWTY::MilestoneWakeLock"
        )
        wakeLock.acquire(10000) // Hold for 10 seconds
        
        // Create intent to launch the app normally (method channel will handle the rest)
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create simple, generic milestone notification
        val manager = getSystemService(NotificationManager::class.java)
        // Don't delete the channel - preserve custom settings
        createMilestoneNotificationChannel()
        
        // Use gentler notification sound instead of alarm sound
        val alertSound = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
        
        val vibrationPattern = getGoalVibrationPattern()
        Log.d("AWTY_SERVICE", "VIBRATION_PATTERN: ${vibrationPattern.contentToString()}")
        writeToLogFile(this, "VIBRATION_PATTERN: ${vibrationPattern.contentToString()}")
        
        val notification = NotificationCompat.Builder(this, MILESTONE_CHANNEL_ID)
            .setContentTitle("Goal reached!")
            .setContentText("Tap to continue.")
            .setSmallIcon(R.drawable.ic_walking_man)
            .setColor(android.graphics.Color.WHITE) // Force white color
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setVibrate(vibrationPattern) // Aggressive vibration for pocket detection
            .setSound(alertSound)
            .build()
        manager?.notify(NOTIFICATION_ID + 1, notification)
        Log.d("AWTY_SERVICE", "NOTIFICATION_SENT: Generic goal notification")
        writeToLogFile(this, "NOTIFICATION_SENT: Generic goal notification")
        
        // Release wake lock after a delay (longer than notification delay to ensure device stays awake)
        Handler(Looper.getMainLooper()).postDelayed({
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d("AWTY_SERVICE", "WAKELOCK_RELEASED")
                writeToLogFile(this@AwtyStepService, "WAKELOCK_RELEASED")
            }
        }, 6000L) // 6 seconds to ensure it covers the 4-second notification delay
    }

    private suspend fun getCurrentStepCount(): Int {
        val now = java.time.ZonedDateTime.now()
        Log.d("AWTY_SERVICE", "STEP_COUNT_REQUESTED: at $now")
        writeToLogFile(this, "STEP_COUNT_REQUESTED: at $now")
        
        val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        if (!granted.containsAll(permissions)) {
            saveState(baseline, deltaSteps, current, false)
            withContext(Dispatchers.Main) {
                firePermissionNotification()
                stopSelf()
            }
            Log.e("AWTY_SERVICE", "PERMISSION_ERROR: Missing Health Connect permissions!")
            writeToLogFile(this, "PERMISSION_ERROR: Missing Health Connect permissions!")
            return prefs.getInt("current", 0)
        }
        return try {
            val now = ZonedDateTime.now()
            val midnight = now.toLocalDate().atStartOfDay(now.zone)
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    midnight.toInstant(),
                    now.toInstant()
                )
            )
            val response = healthConnectClient.readRecords(request)
            val steps = response.records.sumOf { it.count }.toInt()
            
            Log.d("AWTY_SERVICE", "STEP_COUNT_RETURNED: $steps at $now")
            Log.d("AWTY_SERVICE", "STEP_RECORDS_FOUND: ${response.records.size} records")
            writeToLogFile(this, "STEP_COUNT_RETURNED: $steps (${response.records.size} records) at $now")
            
            // Log the most recent record if available
            if (response.records.isNotEmpty()) {
                val latestRecord = response.records.maxByOrNull { it.startTime }
                Log.d("AWTY_SERVICE", "LATEST_RECORD: ${latestRecord?.count} steps at ${latestRecord?.startTime}")
                writeToLogFile(this, "LATEST_RECORD: ${latestRecord?.count} steps at ${latestRecord?.startTime}")
            }
            
            steps
        } catch (e: Exception) {
            Log.e("AWTY_SERVICE", "STEP_COUNT_ERROR: ${e.message}")
            writeToLogFile(this, "STEP_COUNT_ERROR: ${e.message}")
            prefs.getInt("current", 0)
        }
    }

    private fun firePermissionNotification() {
        // Create an intent to launch Health Connect permission screen
        val intent = Intent().apply {
            action = "androidx.health.ACTION_REQUEST_PERMISSIONS"
            setPackage("com.google.android.apps.healthdata")
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
            .setContentText("Tap to grant Health Connect access to track your steps.")
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

/**
 * Get the current vibration pattern for goal notifications
 * This can be easily modified to test different patterns
 */
private fun getGoalVibrationPattern(): LongArray {
    // Very distinctive pattern that should be noticeable even at low intensity
    // Pattern: 3 long pulses with longer pauses - more distinctive than gentle patterns
    return longArrayOf(0, 1200, 300, 1200, 300, 1200)
    
    // Alternative patterns to test:
    // Original 5-pulse: longArrayOf(0, 800, 150, 800, 150, 800, 150, 800, 150, 800)
    // 7 short pulses: longArrayOf(0, 400, 100, 400, 100, 400, 100, 400, 100, 400, 100, 400, 100, 400)
    // Morse code "SOS": longArrayOf(0, 200, 100, 200, 100, 200, 300, 500, 100, 500, 100, 500, 300, 200, 100, 200, 100, 200)
}
} 