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

/** AwtyEnginePlugin */
class AwtyEnginePlugin: FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var goalReachedReceiver: BroadcastReceiver

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
        Log.d(TAG, "onAttachedToEngine completed and broadcast receiver registered.")
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "startTracking" -> {
                val deltaSteps = call.argument<Int>("deltaSteps") ?: 1000
                val goalId = call.argument<String>("goalId") ?: "goal_${System.currentTimeMillis()}"
                val appName = call.argument<String>("appName") ?: "Unknown App"
                val notificationText = call.argument<String>("notificationText") ?: "Tracking steps..."
                val notificationIconName = call.argument<String>("notificationIconName") ?: "ic_launcher"
                val testMode = call.argument<Boolean>("testMode") ?: false

                try {
                    startTracking(deltaSteps, goalId, appName, notificationText, notificationIconName, testMode)
                    result.success(true)
                } catch (e: Exception) {
                    result.error("START_ERROR", "Failed to start tracking", e.message)
                }
            }
            "stopTracking" -> {
                try {
                    stopTracking()
                    result.success(true)
                } catch (e: Exception) {
                    result.error("STOP_ERROR", "Failed to stop tracking", e.message)
                }
            }
            "getCurrentProgress" -> {
                try {
                    val progress = getCurrentProgress()
                    result.success(progress)
                } catch (e: Exception) {
                    result.error("PROGRESS_ERROR", "Failed to get progress", e.message)
                }
            }
            "updateStepCount" -> {
                val steps = call.argument<Int>("steps")
                if (steps != null) {
                    updateStepCount(steps)
                    result.success(null)
                } else {
                    result.error("STEP_UPDATE_ERROR", "Missing 'steps' argument", null)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun startTracking(deltaSteps: Int, goalId: String, appName: String, notificationText: String, notificationIconName: String, testMode: Boolean) {
        Log.d(TAG, "startTracking: deltaSteps=$deltaSteps, goalId=$goalId, appName='$appName', notificationText='$notificationText', notificationIconName='$notificationIconName', testMode=$testMode")
        val intent = Intent(context, AwtyStepService::class.java).apply {
            putExtra("deltaSteps", deltaSteps)
            putExtra("goalId", goalId)
            putExtra("appName", appName)
            putExtra("notificationText", notificationText)
            putExtra("notificationIconName", notificationIconName)
            putExtra("testMode", testMode)
        }
        context.startService(intent)
        Log.d(TAG, "AWTY service started via intent.")
    }

    private fun updateStepCount(steps: Int) {
        Log.d(TAG, "Updating step count: steps=$steps")
        val intent = Intent(context, AwtyStepService::class.java).apply {
            action = "com.awty.engine.UPDATE_STEPS"
            putExtra("currentSteps", steps)
        }
        context.startService(intent)
    }

    private fun stopTracking() {
        Log.d(TAG, "stopTracking called")
        val intent = Intent(context, AwtyStepService::class.java)
        context.stopService(intent)
        Log.d(TAG, "AWTY service stopped via intent.")
    }

    private fun getCurrentProgress(): Map<String, Any> {
        try {
            val statusFile = File(context.getExternalFilesDir(null), "awty_status.json")
            if (statusFile.exists()) {
                val jsonContent = statusFile.readText()
                val jsonObject = org.json.JSONObject(jsonContent)
                
                return mapOf(
                    "isRunning" to (jsonObject.optBoolean("isRunning", false)),
                    "currentSteps" to (jsonObject.optInt("currentSteps", 0)),
                    "baselineSteps" to (jsonObject.optInt("baselineSteps", 0)),
                    "deltaSteps" to (jsonObject.optInt("deltaSteps", 0)),
                    "stepsTaken" to (jsonObject.optInt("stepsTaken", 0)),
                    "stepsRemaining" to (jsonObject.optInt("stepsRemaining", 0)),
                    "goalId" to (jsonObject.optString("goalId", "")),
                    "lastUpdated" to (jsonObject.optString("lastUpdated", ""))
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading status file: $e")
        }
        
        return mapOf(
            "isRunning" to false, "currentSteps" to 0, "baselineSteps" to 0,
            "deltaSteps" to 0, "stepsTaken" to 0, "stepsRemaining" to 0,
            "goalId" to "", "lastUpdated" to ""
        )
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
