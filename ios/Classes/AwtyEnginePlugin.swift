import Flutter
import UIKit
import HealthKit
import UserNotifications
import CoreMotion

public class AwtyEnginePlugin: NSObject, FlutterPlugin {
  private static var instanceCount = 0
  private var instanceId: Int = 0
  private var testTimer: Timer?
  private var testStartTime: Date?
  private var goalSteps: Int = 0
  private var goalReachedCallback: (() -> Void)?
  private var healthStore: HKHealthStore?
  private var channel: FlutterMethodChannel?
  private var baselineSteps: Int = 0
  private var currentSteps: Int = 0
  // iOS follows Android pattern - no native pedometer, uses Flutter's pedometer package
  private var appName: String = "Unknown App"
  
  deinit {
    print("AWTY iOS: Plugin deinit - Instance #\(instanceId)")
    // Ensure proper cleanup
    testTimer?.invalidate()
  }

  public static func register(with registrar: FlutterPluginRegistrar) {
    print("🚨 AWTY iOS: REGISTER METHOD CALLED - PEDOMETER VERSION!")
    AwtyEnginePlugin.instanceCount += 1
    let channel = FlutterMethodChannel(name: "awty_engine_v2", binaryMessenger: registrar.messenger())
    let instance = AwtyEnginePlugin()
    instance.instanceId = AwtyEnginePlugin.instanceCount
    instance.channel = channel
    registrar.addMethodCallDelegate(instance, channel: channel)
    
    print("🚨 AWTY iOS: Plugin registered - Instance #\(instance.instanceId)")
    print("🚨 AWTY iOS: Method channel created: awty_engine_v2")
    print("🚨 AWTY iOS: Delegate added to registrar")
    
    // Request notification permissions
    instance.requestNotificationPermissions()
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    // Only log method calls for debugging, not every getStepsRemaining call
    if call.method != "getStepsRemaining" {
      print("AWTY iOS: Method called: \(call.method) - Instance #\(instanceId)")
    }
    switch call.method {
    case "getPlatformVersion":
      print("AWTY iOS: getPlatformVersion called")
      result("iOS \(UIDevice.current.systemVersion)")
    case "testMethod":
      print("AWTY iOS: testMethod called - Swift code is working!")
      result("Swift test method successful")
    case "calculateHandicap":
      guard let args = call.arguments as? [String: Any],
            let existingHandicap = args["existingHandicap"] as? Double else {
        result(FlutterError(code: "INVALID_ARGUMENTS", message: "Missing existingHandicap parameter", details: nil))
        return
      }
      
      print("AWTY iOS: calculateHandicap called with existing handicap: \(existingHandicap)")
      
      // Get 30-day step average and calculate handicap
      get30DayStepAverage { [weak self] dailyAverage in
        let newHandicap = self?.calculateHandicap(dailyAverage: dailyAverage, existingHandicap: existingHandicap) ?? existingHandicap
        print("AWTY iOS: Handicap calculation complete: \(newHandicap)")
        result(newHandicap)
      }
    case "startGoal":
      guard let args = call.arguments as? [String: Any],
            let steps = args["goalSteps"] as? Int,
            let testMode = args["testMode"] as? Bool,
            let appName = args["appName"] as? String else {
        result(FlutterError(code: "INVALID_ARGUMENTS", message: "Missing parameters (goalSteps, testMode, appName)", details: nil))
        return
      }
      goalSteps = steps
      self.appName = appName
      
      // Set up the goal reached callback to notify Flutter
      goalReachedCallback = { [weak self] in
        print("AWTY iOS: Goal reached - notifying Flutter")
        // Send goal reached event to Flutter
        DispatchQueue.main.async {
          self?.channel?.invokeMethod("goalReached", arguments: nil)
        }
      }
      
      if testMode {
        startTestModeTimer()
        result(true)
      } else {
        // For real mode, just start tracking (Flutter handles pedometer)
        print("AWTY iOS: Started tracking - Flutter will provide step updates")
        result(true)
      }
    case "getStepsRemaining":
      // Minimal logging for getStepsRemaining - only log significant changes
      
      if let startTime = testStartTime {
        print("AWTY iOS: 🧪 TEST MODE - Using simulated steps")
        let elapsed = Date().timeIntervalSince(startTime)
        let progress = min(elapsed / 30.0, 1.0)
        let simulatedSteps = Int(Double(goalSteps) * progress)
        let remaining = goalSteps - simulatedSteps
        print("AWTY iOS: Test mode calculation - elapsed: \(elapsed), progress: \(progress), simulatedSteps: \(simulatedSteps), remaining: \(remaining)")
        result(remaining)
      } else {
        // Real mode: Use current step count (from Flutter pedometer)
        let stepsTaken = max(0, currentSteps - baselineSteps)
        let remaining = max(0, goalSteps - stepsTaken)
        result(remaining)
      }
    case "updateNotification":
      guard let args = call.arguments as? [String: Any],
            let stepsRemaining = args["stepsRemaining"] as? Int else {
        result(FlutterError(code: "INVALID_ARGUMENTS", message: "Missing stepsRemaining parameter", details: nil))
        return
      }
      updatePersistentNotification(stepsRemaining: stepsRemaining)
      result(true)
    case "stopGoal":
      testTimer?.invalidate()
      testTimer = nil
      testStartTime = nil
      goalSteps = 0
      baselineSteps = 0
      currentSteps = 0
      result(true)
    case "updateStepCount":
      guard let args = call.arguments as? [String: Any],
            let steps = args["steps"] as? Int else {
        result(FlutterError(code: "INVALID_ARGUMENTS", message: "Missing steps parameter", details: nil))
        return
      }
      updateStepCount(steps)
      result(true)
    default:
      result(FlutterMethodNotImplemented)
    }
  }
  
  /// Update step count (called by Flutter pedometer package - matches Android pattern)
  private func updateStepCount(_ steps: Int) {
    if goalSteps == 0 { return }
    
    // Always update currentSteps with the latest value from Flutter
    currentSteps = steps
    
    if baselineSteps == 0 {
      // First step update - establish baseline
      baselineSteps = steps
      print("AWTY iOS: Established baseline steps: \(baselineSteps)")
      return
    }
    
    let stepsTaken = max(0, steps - baselineSteps)
    let stepsRemaining = max(0, goalSteps - stepsTaken)
    
    print("AWTY iOS: Steps taken: \(stepsTaken)/\(goalSteps), remaining: \(stepsRemaining)")
    
    if stepsTaken >= goalSteps {
      print("AWTY iOS: Goal reached! \(stepsTaken) steps taken")
      sendGoalReachedNotification()
      // Reset tracking
      goalSteps = 0
      baselineSteps = 0
      currentSteps = 0
    }
  }
  
  /// Request notification permissions
  private func requestNotificationPermissions() {
    print("AWTY iOS: requestNotificationPermissions called")
    
    UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
      if granted {
        print("AWTY iOS: Notification permissions granted")
      } else {
        print("AWTY iOS: Notification permissions denied: \(error?.localizedDescription ?? "Unknown error")")
      }
    }
  }
  
  private func startTestModeTimer() {
    testStartTime = Date()
    testTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
      guard let self = self else { return }
      let elapsed = Date().timeIntervalSince(self.testStartTime!)
      if elapsed >= 30.0 {
        self.testTimer?.invalidate()
        self.testTimer = nil
        // Trigger goal reached (simulate callback if needed)
        print("AWTY iOS: Test mode goal reached")
        self.goalReachedCallback?()
      }
    }
  }
  
  // Native pedometer code removed - iOS now uses Flutter's pedometer package like Android
  
  /// Start persistent notification for tracking
  private func startPersistentNotification() {
    print("AWTY iOS: startPersistentNotification called")
    
    let content = UNMutableNotificationContent()
    content.title = "\(appName) is tracking your steps"
    content.body = "Step goal: \(goalSteps) steps • Tap to return to \(appName)"
    content.sound = .none // Silent notification
    content.categoryIdentifier = "awty-tracking"
    
    // Create a notification that repeats every 60 seconds to keep it persistent
    let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 60, repeats: true)
    let request = UNNotificationRequest(
      identifier: "awty-persistent-tracking",
      content: content,
      trigger: trigger
    )
    
    UNUserNotificationCenter.current().add(request) { error in
      if let error = error {
        print("AWTY iOS: Error creating persistent notification: \(error.localizedDescription)")
      } else {
        print("AWTY iOS: Persistent notification started successfully")
      }
    }
  }
  
  /// Update persistent notification with current progress
  private func updatePersistentNotification(stepsRemaining: Int) {
    // Silent notification update - no logging
    
    let content = UNMutableNotificationContent()
    content.title = "\(appName) is tracking your steps"
    content.body = "Steps remaining: \(stepsRemaining) • Tap to return to \(appName)"
    content.sound = .none // Silent notification
    content.categoryIdentifier = "awty-tracking"
    
    // Update the notification immediately (no trigger = immediate)
    let request = UNNotificationRequest(
      identifier: "awty-persistent-tracking",
      content: content,
      trigger: nil
    )
    
    UNUserNotificationCenter.current().add(request) { error in
      if let error = error {
        print("AWTY iOS: Error updating persistent notification: \(error.localizedDescription)")
      }
      // Silent success - no logging
    }
  }
  
  /// Send local notification when goal is reached
  private func sendGoalReachedNotification() {
    print("AWTY iOS: sendGoalReachedNotification called")
    
    let content = UNMutableNotificationContent()
    content.title = "🎉 Goal Reached!"
    content.body = "You've completed your walking goal. Tap to continue your investigation."
    content.sound = .default
    content.badge = 1
    
    let request = UNNotificationRequest(
      identifier: "awty-goal-reached",
      content: content,
      trigger: nil // Send immediately
    )
    
    UNUserNotificationCenter.current().add(request) { error in
      if let error = error {
        print("AWTY iOS: Error sending notification: \(error.localizedDescription)")
      } else {
        print("AWTY iOS: Goal reached notification sent successfully")
      }
    }
    
    // Also notify Flutter via method channel AND callback
    DispatchQueue.main.async {
      print("AWTY iOS: Notifying Flutter via method channel...")
      self.channel?.invokeMethod("goalReached", arguments: nil)
      print("AWTY iOS: Notifying Flutter via callback...")
      self.goalReachedCallback?()
    }
  }
  
  /// Get 30-day step average for handicap calculation (HealthKit only for this)
  private func get30DayStepAverage(completion: @escaping (Double) -> Void) {
    print("AWTY iOS: get30DayStepAverage called")
    guard let healthStore = healthStore else {
      print("AWTY iOS: ERROR - healthStore is nil in get30DayStepAverage")
      completion(0.0)
      return
    }
    
    let stepType = HKQuantityType.quantityType(forIdentifier: .stepCount)!
    let calendar = Calendar.current
    let now = Date()
    let thirtyDaysAgo = calendar.date(byAdding: .day, value: -30, to: now)!
    let predicate = HKQuery.predicateForSamples(withStart: thirtyDaysAgo, end: now, options: .strictStartDate)
    
    print("AWTY iOS: Querying 30-day step average from \(thirtyDaysAgo) to \(now)")
    
    let query = HKSampleQuery(sampleType: stepType, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { query, samples, error in
      if let error = error {
        print("AWTY iOS: ERROR in 30-day average query: \(error.localizedDescription)")
        completion(0.0)
        return
      }
      
      guard let samples = samples as? [HKQuantitySample] else {
        print("AWTY iOS: ERROR - samples is nil or wrong type in 30-day average")
        completion(0.0)
        return
      }
      
      let totalSteps = samples.reduce(0) { total, sample in
        total + Int(sample.quantity.doubleValue(for: HKUnit.count()))
      }
      
      let dailyAverage = Double(totalSteps) / 30.0
      print("AWTY iOS: 30-day step average: \(dailyAverage) (total: \(totalSteps) over 30 days)")
      completion(dailyAverage)
    }
    
    healthStore.execute(query)
  }
  
  /// Calculate handicap using HealthKit data with fallback to existing handicap
  /// Uses the LARGER of: player-entered average OR system 30-day average
  private func calculateHandicap(dailyAverage: Double, existingHandicap: Double) -> Double {
    let minHandicap = 0.5
    let divisor = 10000.0
    
    // Calculate handicap from system data
    let systemHandicap = dailyAverage / divisor
    
    // Use the larger of system or existing (player-entered) handicap
    let calculatedHandicap = max(systemHandicap, existingHandicap)
    
    // Ensure minimum handicap
    let finalHandicap = max(calculatedHandicap, minHandicap)
    
    print("AWTY iOS: System handicap: \(systemHandicap), Existing: \(existingHandicap), Final: \(finalHandicap)")
    return finalHandicap
  }
}