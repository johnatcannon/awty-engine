package com.awty.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.time.ZonedDateTime
import java.util.*
import java.io.File

class AwtyStepService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "awty_foreground_channel"
        private const val ACTION_UPDATE_STEPS = "com.awty.engine.UPDATE_STEPS"
    }

    private var deltaSteps: Int = 0
    private var baselineSteps: Int = 0
    private var currentSteps: Int = 0
    private var goalId: String = ""
    private var appName: String = "Unknown App"
    private var notificationIconName: String = "ic_launcher"
    private var isForegroundService: Boolean = false
    private var testModeTimer: CountDownTimer? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("AWTY_SERVICE", "onCreate called")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AWTY_SERVICE", "onStartCommand called with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_UPDATE_STEPS -> {
                val steps = intent.getIntExtra("currentSteps", -1)
                if (steps != -1) {
                    processStepUpdate(steps)
                }
            }
            else -> {
                // This is the initial startTracking call
                initializeTracking(intent)
            }
        }

        return START_STICKY
    }

    private fun initializeTracking(intent: Intent?) {
        deltaSteps = intent?.getIntExtra("deltaSteps", 1000) ?: 1000
        goalId = intent?.getStringExtra("goalId") ?: UUID.randomUUID().toString()
        appName = intent?.getStringExtra("appName") ?: "Unknown App"
        notificationIconName = intent?.getStringExtra("notificationIconName") ?: "ic_launcher"
        val testMode = intent?.getBooleanExtra("testMode", false) ?: false
        
        baselineSteps = 0
        currentSteps = 0

        Log.d("AWTY_SERVICE", "Initializing tracking: deltaSteps=$deltaSteps, goalId=$goalId, icon=$notificationIconName, testMode=$testMode")

        startForegroundService()
        
        if (testMode) {
            startTestMode()
        } else {
            updateForegroundNotification("Waiting for step data...")
            writeStatusFile(0, deltaSteps)
        }
    }

    private fun startTestMode() {
        Log.d("AWTY_SERVICE", "TEST MODE: Goal will be reached in 30 seconds.")
        updateForegroundNotification("Test Mode: Finishing in 30s")
        testModeTimer = object : CountDownTimer(30000, 1000) { // 30 seconds
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                updateForegroundNotification("Test Mode: ${secondsRemaining}s remaining")
            }

            override fun onFinish() {
                Log.d("AWTY_SERVICE", "TEST MODE: Goal reached.")
                markGoalAsReached()
                stopSelf()
            }
        }.start()
    }

    private fun processStepUpdate(steps: Int) {
        if (baselineSteps == 0) {
            baselineSteps = steps
            Log.d("AWTY_SERVICE", "Baseline steps established: $baselineSteps")
        }

        currentSteps = steps
        val stepsTaken = (currentSteps - baselineSteps).coerceAtLeast(0)
        val stepsRemaining = (deltaSteps - stepsTaken).coerceAtLeast(0)

        Log.d("AWTY_SERVICE", "Step update: current=$currentSteps, baseline=$baselineSteps, taken=$stepsTaken, remaining=$stepsRemaining")
        
        updateForegroundNotification("$stepsTaken / $deltaSteps steps")
        writeStatusFile(stepsTaken, stepsRemaining)

        if (stepsTaken >= deltaSteps) {
            Log.d("AWTY_SERVICE", "Goal of $deltaSteps steps reached!")
            markGoalAsReached()
            stopSelf()
        }
    }

    private fun markGoalAsReached() {
        Log.d("AWTY_SERVICE", "GOAL_REACHED: Notifying plugin.")
        updateForegroundNotification("Goal reached! Tap to continue.")
        
        val goalReachedIntent = Intent("com.awty.engine.GOAL_REACHED")
        sendBroadcast(goalReachedIntent)
        
        // Final status update to show completion
        writeStatusFile(deltaSteps, 0)
    }

    private fun startForegroundService() {
        if (!isForegroundService) {
            val notification = createNotification("Step tracking starting...")
            startForeground(NOTIFICATION_ID, notification)
            isForegroundService = true
            Log.d("AWTY_SERVICE", "Foreground service started")
        }
    }
    
    private fun updateForegroundNotification(statusText: String) {
        val notification = createNotification(statusText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
        Log.d("AWTY_SERVICE", "Notification updated: $statusText")
    }

    private fun createNotification(contentText: String): android.app.Notification {
        val iconId = resources.getIdentifier(notificationIconName, "drawable", packageName)
        val smallIcon = if (iconId != 0) iconId else applicationInfo.icon

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$appName is tracking your steps")
            .setContentText(contentText)
            .setSmallIcon(smallIcon)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AWTY Step Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows step tracking progress"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun writeStatusFile(stepsTaken: Int, stepsRemaining: Int) {
        try {
            val status = mapOf(
                "isRunning" to true,
                "currentSteps" to currentSteps,
                "baselineSteps" to baselineSteps,
                "deltaSteps" to deltaSteps,
                "stepsTaken" to stepsTaken,
                "stepsRemaining" to stepsRemaining,
                "goalId" to goalId,
                "lastUpdated" to ZonedDateTime.now().toString()
            )
            val json = JSONObject(status).toString()
            val file = File(getExternalFilesDir(null), "awty_status.json")
            file.writeText(json)
        } catch (e: Exception) {
            Log.e("AWTY_SERVICE", "Error writing status file: $e")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        testModeTimer?.cancel()
        Log.d("AWTY_SERVICE", "onDestroy called")
    }
}
