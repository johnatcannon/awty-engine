/// AWTY Engine (Are We There Yet?)
///
/// Simple, focused step tracking that notifies your app when goals are reached.
/// See docs/__AWTY-Spec.md for details.
library awty_engine;

import 'package:flutter/services.dart';

/// Simple step tracking engine
///
/// AWTY does one thing: count steps and notify when goal reached.
/// Everything else (UI, game logic, rewards) is handled by your app.
class AwtyEngine {
  static const MethodChannel _channel = MethodChannel('awty_engine_v2');

  /// Callback function called when a goal is reached
  static VoidCallback? onGoalReached;

  /// Start tracking steps toward a goal
  ///
  /// [steps] - Number of additional steps needed to reach the goal
  /// [notificationText] - Text to show in the status bar notification
  /// [testMode] - If true, goal reached in 60 seconds instead of actual steps
  static Future<void> startTracking({
    required int steps,
    required String notificationText,
    String notificationIconName = 'barefoot', // Default icon name
    bool testMode = false,
  }) async {
    try {
      await _channel.invokeMethod('startTracking', {
        'deltaSteps': steps,
        'goalId': DateTime.now().millisecondsSinceEpoch.toString(),
        'appName': 'Agatha', // Hardcoding for now, can be parameterized
        'notificationText': notificationText,
        'notificationIconName': notificationIconName,
        'testMode': testMode,
      });
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

  /// Update the service with the latest step count from the pedometer
  static Future<void> updateStepCount(int steps) async {
    try {
      await _channel.invokeMethod('updateStepCount', {'steps': steps});
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
          print('Unknown method call: ${call.method}');
      }
    });
  }
}
