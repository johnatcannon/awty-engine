package com.awty.engine

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.os.Handler
import android.os.Looper
import kotlin.math.max
import kotlin.math.min

/** AwtyEnginePlugin */
class AwtyEnginePlugin: FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var goalReachedReceiver: BroadcastReceiver

    // Simple in-memory state management - AWTY owns all step tracking
    private var goalSteps: Int = 0
    private var baselineSteps: Int = 0
    private var currentStepsRemaining: Int = 0
    private var isTracking = false
    private var testModeStartTime: Long = 0L
    
    // Android uses pedometer package directly - no native step sensor needed

    companion object {
        private const val TAG = "AWTY_PLUGIN"
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "awty_engine_v2")
        channel.setMethodCallHandler(this)

        goalReachedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.awty.engine.GOAL_REACHED") {
                    Log.d(TAG, "Goal reached broadcast received, invoking callback to Flutter.")
                    channel.invokeMethod("goalReached", null)
                }
            }
        }
        val filter = IntentFilter("com.awty.engine.GOAL_REACHED")
        context.registerReceiver(goalReachedReceiver, filter)
        
        Log.d(TAG, "onAttachedToEngine completed - Android uses pedometer package directly")
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            "startGoal" -> {
                val goalSteps = call.argument<Int>("goalSteps")
                val appName = call.argument<String>("appName") 
                val goalId = call.argument<String>("goalId")
                val iconName = call.argument<String>("iconName") ?: "barefoot"
                val testMode = call.argument<Boolean>("testMode") ?: false

                if (goalSteps != null && appName != null && goalId != null) {
                    try {
                        startGoal(goalSteps, appName, goalId, iconName, testMode)
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("START_GOAL_ERROR", "Failed to start goal", e.message)
                    }
                } else {
                    result.error("INVALID_ARGUMENTS", "Missing required parameters", null)
                }
            }
            "getStepsRemaining" -> {
                try {
                    val remaining = getStepsRemaining()
                    result.success(remaining)
                } catch (e: Exception) {
                    result.error("GET_STEPS_ERROR", "Failed to get steps remaining", e.message)
                }
            }
            "stopGoal" -> {
                try {
                    stopGoal()
                    result.success(true)
                } catch (e: Exception) {
                    result.error("STOP_GOAL_ERROR", "Failed to stop goal", e.message)
                }
            }
            "updateStepCount" -> {
                val steps = call.argument<Int>("steps")
                if (steps != null) {
                    updateStepCount(steps)
                    result.success(true)
                } else {
                    result.error("INVALID_ARGUMENTS", "Missing steps parameter", null)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun startGoal(goalSteps: Int, appName: String, goalId: String, iconName: String, testMode: Boolean) {
        Log.d(TAG, "startGoal: goalSteps=$goalSteps, appName='$appName', goalId='$goalId', testMode=$testMode")
        
        this.goalSteps = goalSteps
        this.currentStepsRemaining = goalSteps
        this.isTracking = true
        
        if (testMode) {
            // Test mode: goal reached in 30 seconds
            testModeStartTime = System.currentTimeMillis()
            Log.d(TAG, "Android Test Mode: Goal will be reached in 30 seconds")
            
            // Schedule test mode completion
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isTracking) {
                    Log.d(TAG, "Android Test Mode: Goal reached!")
                    channel.invokeMethod("goalReached", null)
                    stopGoal()
                }
            }, 30000) // 30 seconds
        } else {
            // Normal mode: Start foreground service for background step tracking
            startForegroundService(goalSteps, appName, goalId, iconName)
            Log.d(TAG, "Android: Started foreground service for background step tracking")
        }
    }

    private fun startForegroundService(goalSteps: Int, appName: String, goalId: String, iconName: String) {
        try {
            val intent = Intent(context, AwtyStepService::class.java).apply {
                putExtra("deltaSteps", goalSteps)
                putExtra("goalId", goalId)
                putExtra("appName", appName)
                putExtra("notificationIconName", iconName)
                putExtra("testMode", false)
            }
            
            context.startForegroundService(intent)
            Log.d(TAG, "Started AWTY foreground service with $goalSteps steps")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: $e")
        }
    }

    private fun getStepsRemaining(): Int {
        if (!isTracking || goalSteps == 0) {
            return 0
        }
        
        if (testModeStartTime > 0) {
            // Test mode: simulate progress over 30 seconds
            val elapsed = System.currentTimeMillis() - testModeStartTime
            val progress = min(elapsed / 30000.0, 1.0)
            val stepsTaken = (progress * goalSteps).toInt()
            return max(0, goalSteps - stepsTaken)
        } else {
            // Normal mode: Return actual steps remaining from pedometer updates
            return currentStepsRemaining
        }
    }

    private fun stopGoal() {
        Log.d(TAG, "stopGoal called")
        isTracking = false
        goalSteps = 0
        baselineSteps = 0
        currentStepsRemaining = 0
        testModeStartTime = 0L
        
        // Stop foreground service
        try {
            val intent = Intent(context, AwtyStepService::class.java)
            context.stopService(intent)
            Log.d(TAG, "Stopped AWTY foreground service")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground service: $e")
        }
        
        Log.d(TAG, "Android: Goal tracking stopped")
    }
    
    fun updateStepCount(currentSteps: Int) {
        if (!isTracking) return
        
        if (baselineSteps == 0) {
            // First step update - establish baseline
            baselineSteps = currentSteps
            currentStepsRemaining = goalSteps
            Log.d(TAG, "Android: Established baseline steps: $baselineSteps")
            return
        }
        
        val stepsTaken = max(0, currentSteps - baselineSteps)
        val stepsRemaining = max(0, goalSteps - stepsTaken)
        
        // Update the current steps remaining
        currentStepsRemaining = stepsRemaining
        
        Log.d(TAG, "Android: Steps taken: $stepsTaken/$goalSteps, remaining: $stepsRemaining")
        
        if (stepsTaken >= goalSteps) {
            Log.d(TAG, "Android: Goal reached! $stepsTaken steps taken")
            channel.invokeMethod("goalReached", null)
            stopGoal()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        try {
            context.unregisterReceiver(goalReachedReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering broadcast receiver: $e")
        }
        Log.d(TAG, "onDetachedFromEngine completed and broadcast receiver unregistered.")
    }
}

