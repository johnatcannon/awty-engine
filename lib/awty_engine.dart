/// AWTY Engine (Are We There Yet?)
///
/// Clean, simple step tracking engine with platform-agnostic interface.
/// AWTY's ONLY job: "You have 387 steps remaining" → "Goal reached!"
library awty_engine;

import 'package:flutter/services.dart';
import 'package:pedometer/pedometer.dart';
import 'dart:async';
import 'dart:io';

/// Clean step tracking engine with single responsibility
///
/// AWTY handles ALL step tracking internally - no external step management needed.
/// Simple interface: startGoal() → poll getStepsRemaining() → stopGoal()
/// 
/// **Core Principle:**
/// - Agatha's job: "I need to walk 420 steps to unlock this clue"  
/// - AWTY's job: "You have 387 steps remaining" → "Goal reached!"
class AwtyEngine {
  static const MethodChannel _channel = MethodChannel('awty_engine_v2');

  /// Optional callback when goal reached (alternative to polling)
  static VoidCallback? _goalReachedCallback;
  
  /// Internal step tracking state - AWTY manages everything
  static int _goalSteps = 0;
  static int _baselineSteps = 0;
  static int _currentStepCount = 0;
  static bool _isTracking = false;
  static bool _testMode = false;
  static DateTime? _testModeStartTime;
  
  /// Pedometer stream subscription for cross-platform step counting
  static StreamSubscription<StepCount>? _stepCountSubscription;
  
  /// Backup polling timer for goal completion (in case stream fails)
  static Timer? _backupPollingTimer;

  /// Start a goal - AWTY handles ALL step tracking internally
  /// 
  /// [goalSteps] - Number of steps needed (e.g., 420)
  /// [appName] - Name of calling app (e.g., "Agatha")
  /// [goalId] - Unique identifier for this goal (e.g., "hagia_sophia_clue_1")
  /// [iconName] - Optional icon for Android notification (e.g., "barefoot")
  /// [testMode] - For testing: goal reached in 30 seconds instead of actual steps
  static Future<void> startGoal({
    required int goalSteps,
    required String appName,
    required String goalId,
    String? iconName,
    bool testMode = false,
  }) async {
    try {
      // Stop any existing tracking first
      await stopGoal();
      
      // Set up goal parameters
      _goalSteps = goalSteps;
      _isTracking = true;
      _testMode = testMode;
      
      if (testMode) {
        // Test mode: goal reached in 30 seconds
        _testModeStartTime = DateTime.now();
        print('[AWTY] Test mode: Goal will complete in 30 seconds');
        
        // Schedule test completion
        Timer(const Duration(seconds: 30), () {
          if (_isTracking) {
            print('[AWTY] Test mode: Goal reached!');
            _goalReachedCallback?.call();
            stopGoal();
          }
        });
      } else {
        // Real step mode: Use platform-specific implementations
        _testModeStartTime = null;
        await _startNativeTracking(goalSteps, appName, goalId, iconName);
      }
      
      print('[AWTY] Started goal: $goalSteps steps (testMode: $testMode)');
    } catch (e) {
      throw Exception('Failed to start goal: $e');
    }
  }

  /// Update the background notification with current steps remaining
  static Future<void> updateNotification(int stepsRemaining) async {
    try {
      await _channel.invokeMethod('updateNotification', {
        'stepsRemaining': stepsRemaining,
      });
    } catch (e) {
      print('[AWTY] Error updating notification: $e');
    }
  }

  /// Simple question: "Are we there yet?"
  /// Returns number of steps remaining to reach goal
  /// Returns 0 when goal is reached
  static Future<int> getStepsRemaining() async {
    if (!_isTracking) return 0;
    
    if (_testMode && _testModeStartTime != null) {
      // Test mode: simulate progress over 30 seconds
      final elapsed = DateTime.now().difference(_testModeStartTime!);
      final progress = (elapsed.inMilliseconds / 30000.0).clamp(0.0, 1.0);
      final stepsTaken = (progress * _goalSteps).round();
      return (_goalSteps - stepsTaken).clamp(0, _goalSteps);
    } else {
      // Real step mode: use native platform method
      try {
        print('[AWTY] Calling native getStepsRemaining...');
        final result = await _channel.invokeMethod('getStepsRemaining');
        print('[AWTY] Native getStepsRemaining returned: $result');
        return result ?? 0;
      } catch (e) {
        print('[AWTY] Error getting steps remaining from native: $e');
        return _goalSteps;
      }
    }
  }

  /// Optional callback when goal reached (alternative to polling)
  static void onGoalReached(VoidCallback callback) {
    _goalReachedCallback = callback;
    
    // Set up native goal reached callback
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onGoalReached') {
        print('[AWTY] Native goal reached callback triggered');
        _goalReachedCallback?.call();
        await stopGoal();
      }
    });
  }

  /// Clean up for next goal
  static Future<void> stopGoal() async {
    _isTracking = false;
    _goalSteps = 0;
    _baselineSteps = 0;
    _testMode = false;
    _testModeStartTime = null;
    
    // Stop native tracking
    try {
      await _channel.invokeMethod('stopGoal');
    } catch (e) {
      print('[AWTY] Error stopping native goal: $e');
    }
    
    // Cancel pedometer subscription (Android + fallback)
    await _stepCountSubscription?.cancel();
    _stepCountSubscription = null;
    
    // Cancel backup polling timer
    _backupPollingTimer?.cancel();
    _backupPollingTimer = null;
    
    print('[AWTY] Goal stopped and cleaned up');
  }

  /// Start platform-specific tracking (iOS HealthKit, Android pedometer)
  static Future<void> _startNativeTracking(int goalSteps, String appName, String goalId, String? iconName) async {
    try {
      print('[AWTY] Starting platform-specific tracking...');
      
      if (Platform.isIOS) {
        // iOS: Use pedometer package (same as Android)
        print('[AWTY] iOS: Using pedometer package for step tracking');
        final result = await _channel.invokeMethod('startGoal', {
          'goalSteps': goalSteps,
          'appName': appName,
          'goalId': goalId,
          'iconName': iconName,
          'testMode': false,
        });
        print('[AWTY] iOS pedometer started successfully, result: $result');
        // Also start pedometer tracking for step updates
        await _startPedometerTracking();
      } else {
        // Android: Use foreground service + pedometer package
        print('[AWTY] Android: Using foreground service + pedometer package');
        final result = await _channel.invokeMethod('startGoal', {
          'goalSteps': goalSteps,
          'appName': appName,
          'goalId': goalId,
          'iconName': iconName,
          'testMode': false,
        });
        print('[AWTY] Android foreground service started, result: $result');
        // Also start pedometer tracking for step updates
        await _startPedometerTracking();
      }
      
    } catch (e) {
      print('[AWTY] Failed to start platform-specific tracking: $e');
      // Fallback to pedometer if platform-specific implementation fails
      await _startPedometerTracking();
    }
  }

  /// Start pedometer tracking and set baseline step count (Android + fallback)
  static Future<void> _startPedometerTracking() async {
    try {
      // Get initial step count as baseline
      final stepCount = await _getCurrentStepCount();
      _baselineSteps = stepCount;
      
      print('[AWTY] Setting up pedometer stream listener...');
      
      // Start monitoring step changes
      _stepCountSubscription = Pedometer.stepCountStream.listen(
        (StepCount event) {
          print('[AWTY] 🚶 Pedometer stream event: steps=${event.steps}, isTracking=$_isTracking, testMode=$_testMode');
          
          // Always update current step count
          _currentStepCount = event.steps;
          
          if (_isTracking && !_testMode) {
            final currentSteps = event.steps;
            final stepsTaken = (currentSteps - _baselineSteps).clamp(0, _goalSteps);
            final stepsRemaining = (_goalSteps - stepsTaken).clamp(0, _goalSteps);
            
            print('[AWTY] Step update: current=$currentSteps, baseline=$_baselineSteps, taken=$stepsTaken, remaining=$stepsRemaining');
            
            // Send step count to native plugin for both platforms
            if (Platform.isAndroid) {
              _channel.invokeMethod('updateStepCount', {'steps': currentSteps}).catchError((e) {
                print('[AWTY] Error sending step count to Android: $e');
              });
            } else if (Platform.isIOS) {
              _channel.invokeMethod('updateStepCount', {'steps': currentSteps}).catchError((e) {
                print('[AWTY] Error sending step count to iOS: $e');
              });
            }
            
            // Check if goal reached
            if (stepsRemaining == 0 && _goalSteps > 0) {
              print('[AWTY] 🎉 Goal reached via pedometer stream!');
              _goalReachedCallback?.call();
              stopGoal();
            }
          } else {
            print('[AWTY] Ignoring pedometer event - tracking:$_isTracking, testMode:$_testMode');
          }
        },
        onError: (error) {
          print('[AWTY] ❌ Pedometer stream error: $error');
        },
      );
      
      print('[AWTY] Pedometer tracking started. Baseline: $_baselineSteps steps, stream listener active');
    } catch (e) {
      print('[AWTY] Failed to start pedometer tracking: $e');
      throw Exception('Failed to start step tracking: $e');
    }
  }
  
  /// Get current step count from pedometer
  static Future<int> _getCurrentStepCount() async {
    try {
      // Get current step count - this is a one-time reading
      final stepCount = await Pedometer.stepCountStream.first;
      print('[AWTY] 📊 Polling pedometer: current=${stepCount.steps} steps');
      return stepCount.steps;
    } catch (e) {
      print('[AWTY] ❌ Failed to get current step count: $e');
      return 0;
    }
  }
  
  /// Backup polling timer for goal completion (fallback if stream fails)
  static void _startBackupPolling() {
    _backupPollingTimer = Timer.periodic(const Duration(seconds: 5), (timer) async {
      if (!_isTracking || _testMode) {
        timer.cancel();
        return;
      }
      
      try {
        final currentSteps = await _getCurrentStepCount();
        final stepsTaken = (currentSteps - _baselineSteps).clamp(0, _goalSteps);
        final stepsRemaining = (_goalSteps - stepsTaken).clamp(0, _goalSteps);
        
        print('[AWTY] 🔄 Backup polling check: current=$currentSteps, taken=$stepsTaken, remaining=$stepsRemaining');
        
        if (stepsRemaining == 0 && _goalSteps > 0) {
          print('[AWTY] 🎉 Goal reached via backup polling!');
          _goalReachedCallback?.call();
          await stopGoal();
        }
      } catch (e) {
        print('[AWTY] ❌ Backup polling error: $e');
      }
    });
    
    print('[AWTY] 🔄 Backup polling started (5-second intervals)');
  }
  
  /// Initialize AWTY - no longer needed but kept for compatibility
  static Future<void> initialize() async {
    // Test if native platform is available
    try {
      print('[AWTY] Testing iOS plugin communication...');
      final result = await _channel.invokeMethod('getPlatformVersion');
      print('[AWTY] Native platform test successful: $result');
      
      // Test our custom method
      print('[AWTY] Testing custom method...');
      final testResult = await _channel.invokeMethod('testMethod');
      print('[AWTY] Custom method test result: $testResult');
    } catch (e) {
      print('[AWTY] Native platform test failed: $e');
    }
    
    // Initialization now happens automatically on first startGoal() call
    print('[AWTY] Initialize called - no setup needed with pedometer implementation');
  }
}
