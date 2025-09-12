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
import java.io.File
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

/** AwtyEnginePlugin */
class AwtyEnginePlugin: FlutterPlugin, MethodCallHandler, SensorEventListener {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var goalReachedReceiver: BroadcastReceiver

    // Simple in-memory state management - AWTY owns all step tracking
    private var goalSteps: Int = 0
    private var baselineSteps: Int = 0
    private var isTracking = false
    private var testModeStartTime: Long = 0L
    
    // Step sensor integration - direct pedometer access
    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var stepMonitorHandler: Handler? = null
    private var stepMonitorRunnable: Runnable? = null

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
        
        // Initialize step sensor for direct pedometer access
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        
        if (stepCounterSensor == null) {
            Log.w(TAG, "Step counter sensor not available on this device")
        } else {
            Log.d(TAG, "Step counter sensor initialized successfully")
        }
        
        Log.d(TAG, "onAttachedToEngine completed and broadcast receiver registered.")
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
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun startGoal(goalSteps: Int, appName: String, goalId: String, iconName: String, testMode: Boolean) {
        Log.d(TAG, "startGoal: goalSteps=$goalSteps, appName='$appName', goalId='$goalId', testMode=$testMode")
        
        this.goalSteps = goalSteps
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
            // Normal mode: AWTY will manage pedometer internally
            // For now, we'll rely on external step updates via notification
            baselineSteps = 0 // Will be set when first step update arrives
            Log.d(TAG, "Android: Started goal tracking - waiting for step updates")
        }
    }

    fun updateStepCount(currentSteps: Int) {
        if (!isTracking) return
        
        if (baselineSteps == 0) {
            // First step update - establish baseline
            baselineSteps = currentSteps
            Log.d(TAG, "Android: Established baseline steps: $baselineSteps")
            return
        }
        
        val stepsTaken = maxOf(0, currentSteps - baselineSteps)
        val stepsRemaining = maxOf(0, goalSteps - stepsTaken)
        
        Log.d(TAG, "Android: Steps taken: $stepsTaken/$goalSteps, remaining: $stepsRemaining")
        
        if (stepsTaken >= goalSteps) {
            Log.d(TAG, "Android: Goal reached! $stepsTaken steps taken")
            channel.invokeMethod("goalReached", null)
            stopGoal()
        }
    }

    private fun getStepsRemaining(): Int {
        if (!isTracking || goalSteps == 0) {
            return 0
        }
        
        if (testModeStartTime > 0) {
            // Test mode: simulate progress over 30 seconds
            val elapsed = System.currentTimeMillis() - testModeStartTime
            val progress = minOf(elapsed / 30000.0, 1.0)
            val stepsTaken = (progress * goalSteps).toInt()
            return maxOf(0, goalSteps - stepsTaken)
        } else {
            // Normal mode: This method will be called after external step updates
            // For now, return the goal steps (since external updates handle completion)
            return goalSteps
        }
    }

    private fun stopGoal() {
        Log.d(TAG, "stopGoal called")
        isTracking = false
        goalSteps = 0
        baselineSteps = 0
        testModeStartTime = 0L
        
        // Stop step sensor monitoring
        stopStepSensorMonitoring()
        
        Log.d(TAG, "Android: Goal tracking stopped")
    }
    
    // Step sensor event handling - direct pedometer integration
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER && isTracking && testModeStartTime == 0L) {
            val currentStepCount = event.values[0].toInt()
            val stepsTaken = maxOf(0, currentStepCount - baselineSteps)
            val stepsRemaining = maxOf(0, goalSteps - stepsTaken)
            
            Log.d(TAG, "Step sensor update: current=$currentStepCount, baseline=$baselineSteps, taken=$stepsTaken, remaining=$stepsRemaining")
            
            if (stepsRemaining == 0 && goalSteps > 0) {
                Log.d(TAG, "Android: Real step goal reached!")
                // Goal reached - notify Flutter
                channel.invokeMethod("goalReached", null)
                // Auto-stop tracking
                stopGoal()
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Step counter accuracy changes - log for debugging
        Log.d(TAG, "Step sensor accuracy changed: $accuracy")
    }
    
    private fun startStepSensorMonitoring() {
        if (stepCounterSensor != null) {
            // Get current step count as baseline
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Started step sensor monitoring")
            
            // Get baseline step count (need to wait for first sensor reading)
            stepMonitorHandler = Handler(Looper.getMainLooper())
            stepMonitorRunnable = Runnable {
                // This will be updated by onSensorChanged callback
                Log.d(TAG, "Step sensor monitoring active")
            }
            stepMonitorHandler?.post(stepMonitorRunnable!!)
        } else {
            Log.e(TAG, "Cannot start step monitoring - no step sensor available")
        }
    }
    
    private fun stopStepSensorMonitoring() {
        if (stepCounterSensor != null) {
            sensorManager.unregisterListener(this)
            Log.d(TAG, "Stopped step sensor monitoring")
        }
        
        stepMonitorHandler?.removeCallbacks(stepMonitorRunnable ?: return)
        stepMonitorHandler = null
        stepMonitorRunnable = null
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
