/// AWTY Engine (Are We There Yet?)
///
/// Minimal, decoupled step-delta notifier for use in Agatha, WalkWise, etc.
/// See docs/__AWTY-Spec.md for details.
///
/// **BREAKING CHANGES in v0.1.0:**
/// - Simplified notification approach - AWTY focuses solely on step tracking and goal detection
/// - Removed custom notifications, sounds, and vibrations (handled by calling application)
/// - Moved from Health Connect to `pedometer` package for step counting
/// - Updated API to use `AwtyIntegration` class for better integration
library awty_engine;

import 'package:flutter/services.dart';

/// Service for integrating with the AWTY Engine
///
/// This class provides a high-level interface for integrating with the AWTY Engine.
/// It handles platform channel communication and provides callback mechanisms
/// for goal completion, errors, and permission issues.
class AwtyIntegration {
  static const MethodChannel _channel = MethodChannel('awty_engine');

  /// Callback function called when a goal is reached
  static VoidCallback? onGoalReached;

  /// Callback function called when an error occurs
  static Function(String)? onError;

  /// Callback function called when permissions are denied
  static Function(String)? onPermissionDenied;

  /// Initialize AWTY integration and set up platform channel handlers
  static Future<void> initialize() async {
    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'goalReached':
          onGoalReached?.call();
          break;
        case 'permissionDenied':
          onPermissionDenied?.call(call.arguments ?? 'Permission denied');
          break;
        case 'serviceError':
          onError?.call(call.arguments ?? 'Service error');
          break;
        default:
          print('Unknown method call: ${call.method}');
      }
    });
  }

  /// Start step tracking with AWTY
  static Future<void> startStepTracking({
    required int deltaSteps,
    required String goalId,
    required String appName,
    bool testMode = false,
  }) async {
    try {
      await _channel.invokeMethod('startStepTracking', {
        'deltaSteps': deltaSteps,
        'goalId': goalId,
        'appName': appName,
        'testMode': testMode,
      });
    } on PlatformException catch (e) {
      throw Exception('Failed to start tracking: ${e.message}');
    }
  }

  /// Stop step tracking
  static Future<void> stopStepTracking() async {
    try {
      await _channel.invokeMethod('stopStepTracking');
    } on PlatformException catch (e) {
      throw Exception('Failed to stop tracking: ${e.message}');
    }
  }

  /// Get current progress from AWTY
  static Future<Map<String, dynamic>?> getCurrentProgress() async {
    try {
      final result = await _channel.invokeMethod('getCurrentProgress');
      return result != null ? Map<String, dynamic>.from(result) : null;
    } on PlatformException catch (e) {
      throw Exception('Failed to get progress: ${e.message}');
    }
  }

  /// Get AWTY service status
  static Future<Map<String, dynamic>> getServiceStatus() async {
    try {
      final result = await _channel.invokeMethod('getServiceStatus');
      return Map<String, dynamic>.from(result ?? {});
    } on PlatformException catch (e) {
      throw Exception('Failed to get service status: ${e.message}');
    }
  }

  /// Check if AWTY service is running
  static Future<bool> isServiceRunning() async {
    try {
      final result = await _channel.invokeMethod('isServiceRunning');
      return result ?? false;
    } on PlatformException catch (e) {
      print('Error checking service status: ${e.message}');
      return false;
    }
  }

  /// Get AWTY service logs
  static Future<String> getServiceLogs() async {
    try {
      final result = await _channel.invokeMethod('getServiceLogs');
      return result ?? 'No logs available';
    } on PlatformException catch (e) {
      return 'Error getting logs: ${e.message}';
    }
  }

  /// Clear AWTY state (for testing)
  static Future<void> clearState() async {
    try {
      await _channel.invokeMethod('clearState');
    } on PlatformException catch (e) {
      throw Exception('Failed to clear state: ${e.message}');
    }
  }
}

/// Legacy AwtyEngine class for backward compatibility
///
/// This class provides backward compatibility for existing code that uses
/// the old AwtyEngine API. All methods are deprecated and should be replaced
/// with AwtyIntegration equivalents.
///
/// @deprecated Use AwtyIntegration instead
@Deprecated('Use AwtyIntegration instead')
class AwtyEngine {
  /// Starts step tracking with delta steps (with test mode support)
  @Deprecated('Use AwtyIntegration.startStepTracking instead')
  static Future<void> startStepTracking({
    required int deltaSteps,
    required String goalId,
    required String appName,
    bool testMode = false,
  }) async {
    return AwtyIntegration.startStepTracking(
      deltaSteps: deltaSteps,
      goalId: goalId,
      appName: appName,
      testMode: testMode,
    );
  }

  /// Stops step tracking.
  @Deprecated('Use AwtyIntegration.stopStepTracking instead')
  static Future<void> stopStepTracking() async {
    return AwtyIntegration.stopStepTracking();
  }

  /// Get current step tracking state from AWTY service.
  @Deprecated('Use AwtyIntegration.getCurrentProgress instead')
  static Future<Map<String, dynamic>> getStepTrackingState() async {
    final result = await AwtyIntegration.getCurrentProgress();
    return result ??
        {
          'isRunning': false,
          'stepsTaken': 0,
          'deltaSteps': 0,
          'baselineSteps': 0,
          'currentSteps': 0,
        };
  }

  /// Get goal status from AWTY service.
  @Deprecated('Use AwtyIntegration.getServiceStatus instead')
  static Future<Map<String, dynamic>> getGoalStatus(String goalId) async {
    final result = await AwtyIntegration.getServiceStatus();
    return {
      'goalId': goalId,
      'active': result['isRunning'] ?? false,
      'reached': false,
      'reachedTime': null,
    };
  }
}
