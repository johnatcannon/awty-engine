/// AWTY Engine (Are We There Yet?)
///
/// Simple, focused step tracking that notifies your app when goals are reached.
/// Supports both Android (foreground service) and iOS (HealthKit background delivery).
/// See docs/__AWTY-Spec.md for details.
library awty_engine;

import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

/// Simple step tracking engine
///
/// AWTY does one thing: count steps and notify when goal reached.
/// Everything else (UI, game logic, rewards) is handled by your app.
/// 
/// **Platform Support:**
/// - **Android**: Uses foreground service for reliable background tracking
/// - **iOS**: Uses HealthKit background delivery for battery-efficient tracking
class AwtyEngine {
  static const MethodChannel _channel = MethodChannel('awty_engine_v2');

  /// Callback function called when a goal is reached
  static VoidCallback? onGoalReached;

  /// Get the platform version information
  static Future<String?> getPlatformVersion() async {
    try {
      final result = await _channel.invokeMethod('getPlatformVersion');
      return result as String?;
    } on PlatformException catch (e) {
      throw Exception('Failed to get platform version: ${e.message}');
    }
  }

  /// Start tracking steps toward a goal
  ///
  /// [steps] - Number of additional steps needed to reach the goal
  /// [notificationText] - Text to show in the status bar notification (Android only)
  /// [notificationIconName] - Icon name for notification (Android only)
  /// [testMode] - If true, goal reached in 60 seconds instead of actual steps
  static Future<void> startTracking({
    required int steps,
    String notificationText = "Walking to destination...",
    String notificationIconName = 'barefoot',
    bool testMode = false,
  }) async {
    try {
      if (defaultTargetPlatform == TargetPlatform.iOS) {
        // iOS: Use HealthKit background delivery
        await _startTrackingIOS(steps, testMode);
      } else {
        // Android: Use foreground service
        await _startTrackingAndroid(steps, notificationText, notificationIconName, testMode);
      }
    } on PlatformException catch (e) {
      throw Exception('Failed to start tracking: ${e.message}');
    }
  }

  /// Stop step tracking
  static Future<void> stopTracking() async {
    try {
      await _channel.invokeMethod('stopTracking');
    } on PlatformException catch (e) {
      throw Exception('Failed to stop tracking: ${e.message}');
    }
  }

  /// Get current progress
  static Future<Map<String, dynamic>?> getProgress() async {
    try {
      final result = await _channel.invokeMethod('getCurrentProgress');
      return result != null ? Map<String, dynamic>.from(result) : null;
    } on PlatformException catch (e) {
      throw Exception('Failed to get progress: ${e.message}');
    }
  }

  /// Clears all stored state from the AWTY engine.
  /// Call this before starting a new game to prevent issues with stale data.
  static Future<void> clearState() async {
    try {
      await _channel.invokeMethod('clearState');
    } on PlatformException catch (e) {
      throw Exception('Failed to clear state: ${e.message}');
    }
  }

  /// Update the service with the latest step count from the pedometer
  /// 
  /// **Note**: On both platforms, this method forwards step count updates
  /// to the native engine for processing and goal checking.
  static Future<void> updateStepCount(int steps) async {
    try {
      // Both platforms: Forward steps to native engine
      await _channel.invokeMethod('updateStepCount', {'stepCount': steps});
    } on PlatformException catch (e) {
      throw Exception('Failed to update step count: ${e.message}');
    }
  }

  /// Initialize AWTY and set up goal reached callback
  static Future<void> initialize() async {
    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'goalReached':
          onGoalReached?.call();
          break;
        default:
          // Unknown method call - ignore silently
      }
    });
  }

  /// Platform-specific tracking implementation for Android
  static Future<void> _startTrackingAndroid(
    int steps,
    String notificationText,
    String notificationIconName,
    bool testMode,
  ) async {
    await _channel.invokeMethod('startTracking', {
      'deltaSteps': steps,
      'goalId': DateTime.now().millisecondsSinceEpoch.toString(),
      'appName': 'Agatha',
      'notificationText': notificationText,
      'notificationIconName': notificationIconName,
      'testMode': testMode,
    });
  }

  /// Platform-specific tracking implementation for iOS
  static Future<void> _startTrackingIOS(int steps, bool testMode) async {
    await _channel.invokeMethod('startTracking', {
      'deltaSteps': steps,
      'goalId': DateTime.now().millisecondsSinceEpoch.toString(),
      'appName': 'Agatha',
      'testMode': testMode,
    });
  }
}
