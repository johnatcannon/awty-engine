/// AWTY Engine (Are We There Yet?)
/// Minimal, decoupled step-delta notifier for use in Agatha, WalkWise, etc.
/// See docs/__AWTY-Spec.md for details.

import 'package:flutter/services.dart';

class AwtyEngine {
  static const MethodChannel _channel = MethodChannel('awty_engine');

  /// Starts step tracking with delta steps (simplified API).
  /// Notify when X additional steps are taken from current baseline.
  static Future<void> startStepTracking({
    required int deltaSteps, 
    required String goalId, 
    required String appName,  // Name of the calling app (e.g., "Agatha", "WalkWise")
  }) async {
    try {
      await _channel.invokeMethod('startStepTracking', {
        'deltaSteps': deltaSteps,
        'goalId': goalId,
        'appName': appName,
      });
      print('[AWTY] Started step tracking for $deltaSteps additional steps, goalId: $goalId, app: $appName');
    } catch (e) {
      print('[AWTY] Error starting step tracking: $e');
      rethrow;
    }
  }

  /// Stops step tracking.
  static Future<void> stopStepTracking() async {
    try {
      await _channel.invokeMethod('stopStepTracking');
      print('[AWTY] Stopped step tracking');
    } catch (e) {
      print('[AWTY] Error stopping step tracking: $e');
      rethrow;
    }
  }

  /// Fetches the last N lines of the AWTY log file from the platform channel.
  static Future<String> getAwtyLogTail({int lines = 50}) async {
    try {
      final result = await _channel.invokeMethod('getAwtyLogTail', {'lines': lines});
      return result as String;
    } catch (e) {
      print('[AWTY] Error getting log tail: $e');
      return 'Error reading log: $e';
    }
  }

  /// Requests DND override (notification policy access) permission. Returns true if already granted, false if user needs to grant it.
  static Future<bool> requestDndOverridePermission() async {
    try {
      final result = await _channel.invokeMethod('requestDndOverridePermission');
      return result == true;
    } catch (e) {
      print('[AWTY] Error requesting DND permission: $e');
      return false;
    }
  }

  /// Refresh step count from AWTY service (for debugging/testing).
  static Future<void> refreshStepCount() async {
    try {
      await _channel.invokeMethod('refreshStepCount');
    } catch (e) {
      print('[AWTY] Error refreshing step count: $e');
      rethrow;
    }
  }

  /// Get current step tracking state from AWTY service.
  static Future<Map<String, dynamic>> getStepTrackingState() async {
    try {
      final result = await _channel.invokeMethod('getStepTrackingState');
      return result as Map<String, dynamic>;
    } catch (e) {
      print('[AWTY] Error getting step tracking state: $e');
      return {
        'isRunning': false,
        'stepsTaken': 0,
        'deltaSteps': 0,
        'baselineSteps': 0,
        'currentSteps': 0,
      };
    }
  }

  /// Get goal status from AWTY service.
  static Future<Map<String, dynamic>> getGoalStatus(String goalId) async {
    try {
      final result = await _channel.invokeMethod('getGoalStatus', {'goalId': goalId});
      return Map<String, dynamic>.from(result as Map);
    } catch (e) {
      print('[AWTY] Error getting goal status: $e');
      return {'goalId': goalId, 'active': false, 'reached': false, 'reachedTime': null};
    }
  }

  /// Check if Health Connect permissions are granted.
  /// Returns true if permissions are available, false otherwise.
  static Future<bool> checkHealthConnectPermissions() async {
    try {
      final result = await _channel.invokeMethod('checkHealthConnectPermissions');
      return result == true;
    } catch (e) {
      print('[AWTY] Error checking Health Connect permissions: $e');
      return false;
    }
  }

  /// Request Health Connect permissions.
  /// Returns true if permissions were granted, false otherwise.
  static Future<bool> requestHealthConnectPermissions() async {
    try {
      final result = await _channel.invokeMethod('requestHealthConnectPermissions');
      return result == true;
    } catch (e) {
      print('[AWTY] Error requesting Health Connect permissions: $e');
      return false;
    }
  }

  /// Clears the AWTY log file via platform channel.
  static Future<void> clearAwtyLog() async {
    try {
      await _channel.invokeMethod('clearAwtyLog');
      print('[AWTY] Cleared AWTY log file');
    } catch (e) {
      print('[AWTY] Error clearing AWTY log file: $e');
    }
  }
} 