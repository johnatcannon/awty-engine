// This file is a copy of AwtyPlatformChannel.kt from java/ to kotlin/ for Flutter plugin compatibility.
package com.awty.engine

import android.content.Context
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.UUID

class AwtyPlatformChannel(private val context: Context) : MethodCallHandler {
    companion object {
        private const val TAG = "AWTY_CHANNEL"
        private const val prefsName = "awty_prefs"
        
        fun writeToLogFile(context: android.content.Context, message: String) {
            try {
                val timestamp = java.time.ZonedDateTime.now().toString()
                val logEntry = "[$timestamp] $message\n"
                val logFile = java.io.File(context.getExternalFilesDir(null), "awty_service.txt")
                logFile.appendText(logEntry)
                Log.d(TAG, "LOG: $message")
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to log file: $e")
            }
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startStepTracking" -> {
                val deltaSteps = call.argument<Int>("deltaSteps") ?: 1000
                val goalId = call.argument<String>("goalId") ?: UUID.randomUUID().toString()
                val appName = call.argument<String>("appName") ?: "Unknown App"
                Log.d(TAG, "startStepTracking called with goalId: $goalId, deltaSteps: $deltaSteps, appName: $appName")
                writeToLogFile(context, "startStepTracking called with goalId: $goalId, deltaSteps: $deltaSteps, appName: $appName")

                // Persist the current active goalId
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                prefs.edit().putString("currentGoalId", goalId).putBoolean("goalActive_$goalId", true).apply()

                // Set the static reference so service can call back
                AwtyStepService.setPlatformChannel(this)

                val intent = android.content.Intent(context, AwtyStepService::class.java).apply {
                    putExtra("deltaSteps", deltaSteps)
                    putExtra("goalId", goalId)
                    putExtra("appName", appName)
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }

                result.success(null)
            }
            "stopStepTracking" -> {
                val intent = android.content.Intent(context, AwtyStepService::class.java)
                context.stopService(intent)
                
                // Clear any lingering notifications when stopping
                clearAllNotifications()
                
                // Clear all persisted state to prevent negative step counts on next start
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                
                result.success(true)
            }
            "getStepTrackingState" -> {
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                val baseline = prefs.getInt("baseline", 0)
                val deltaSteps = prefs.getInt("deltaSteps", 0)
                val current = prefs.getInt("current", 0)
                val running = prefs.getBoolean("running", false)
                val stepsTaken = current - baseline
                
                val state = mapOf(
                    "isRunning" to running,
                    "stepsTaken" to stepsTaken,
                    "deltaSteps" to deltaSteps,
                    "baselineSteps" to baseline,
                    "currentSteps" to current
                )
                
                Log.d(TAG, "getStepTrackingState: $state")
                result.success(state)
            }
            "getGoalStatus" -> {
                val goalId = call.argument<String>("goalId") ?: ""
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                val active = prefs.getBoolean("goalActive_$goalId", false)
                val reached = prefs.getBoolean("goalReached_$goalId", false)
                val reachedTime = prefs.getString("goalReachedTime_$goalId", null)
                
                val status = mapOf(
                    "goalId" to goalId,
                    "active" to active,
                    "reached" to reached,
                    "reachedTime" to reachedTime
                )
                
                Log.d(TAG, "getGoalStatus for $goalId: $status")
                result.success(status)
            }
            "getAwtyLogTail" -> {
                val lines = call.argument<Int>("lines") ?: 50
                try {
                    val logFile = java.io.File(context.getExternalFilesDir(null), "awty_service.txt")
                    if (logFile.exists()) {
                        val allLines = logFile.readLines()
                        val lastLines = allLines.takeLast(lines)
                        val logContent = "AWTY log tail (last $lines lines)\n${lastLines.joinToString("\n")}"
                        result.success(logContent)
                    } else {
                        result.success("AWTY log file not found")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading AWTY log file", e)
                    result.error("READ_LOG_ERROR", "Failed to read AWTY log file", e.localizedMessage)
                }
            }
            "clearAwtyLog" -> {
                // Implementation for clearing the AWTY log file
                try {
                    val logFile = java.io.File(context.getExternalFilesDir(null), "awty_service.txt")
                    if (logFile.exists()) {
                        logFile.writeText("")
                    }
                    result.success(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing AWTY log file", e)
                    result.error("CLEAR_LOG_ERROR", "Failed to clear AWTY log file", e.localizedMessage)
                }
            }
            "requestDndOverridePermission" -> {
                // Implementation for DND permission request
                result.success(false)
            }
            "refreshStepCount" -> {
                // Implementation for refreshing step count
                result.success(null)
            }
            "checkHealthConnectPermissions" -> {
                // Implementation for checking Health Connect permissions
                result.success(true)
            }
            "requestHealthConnectPermissions" -> {
                // Implementation for requesting Health Connect permissions
                result.success(true)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    fun notifyMilestoneReached() {
        Log.d(TAG, "notifyMilestoneReached called")
        writeToLogFile(context, "Platform channel milestone notification sent")
        // This would trigger a callback to the Flutter app
    }
    
    /**
     * Clear all AWTY-related notifications when stopping the service
     */
    private fun clearAllNotifications() {
        try {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            
            // Cancel all AWTY notifications (using the same IDs as in AwtyStepService)
            manager?.cancel(1001) // NOTIFICATION_ID
            manager?.cancel(1002) // NOTIFICATION_ID + 1 (milestone)
            manager?.cancel(1003) // NOTIFICATION_ID + 2 (permission)
            manager?.cancel(1004) // NOTIFICATION_ID + 3 (any other)
            
            Log.d(TAG, "ALL_NOTIFICATIONS_CLEARED: Cleared all AWTY notifications")
            writeToLogFile(context, "ALL_NOTIFICATIONS_CLEARED: Cleared all AWTY notifications")
        } catch (e: Exception) {
            Log.e(TAG, "CLEAR_NOTIFICATIONS_ERROR: ${e.message}")
            writeToLogFile(context, "CLEAR_NOTIFICATIONS_ERROR: ${e.message}")
        }
    }
} 