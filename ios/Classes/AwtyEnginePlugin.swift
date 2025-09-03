import Flutter
import UIKit
import HealthKit

public class AwtyEnginePlugin: NSObject, FlutterPlugin {
    private let healthStore = HKHealthStore()
    private var methodChannel: FlutterMethodChannel?
    private var goalSteps: Int = 0
    private var isTracking = false
    private var baselineSteps: Int = 0
    private var lastStepCount: Int = 0
    private var testModeStartTime: Date?
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "awty_engine_v2", binaryMessenger: registrar.messenger())
        let instance = AwtyEnginePlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
        instance.methodChannel = channel
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "getPlatformVersion":
            handleGetPlatformVersion(result: result)
        case "startTracking":
            handleStartTracking(call, result: result)
        case "stopTracking":
            handleStopTracking(result: result)
        case "getCurrentProgress":
            handleGetProgress(result: result)
        case "updateStepCount":
            handleUpdateStepCount(call, result: result)
        case "clearState":
            // For iOS, stopping tracking effectively clears the in-memory state.
            handleStopTracking(result: result)
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    private func handleGetPlatformVersion(result: @escaping FlutterResult) {
        let version = UIDevice.current.systemVersion
        result("iOS \(version)")
    }
    
    private func handleStartTracking(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let steps = args["deltaSteps"] as? Int else {
            result(FlutterError(code: "INVALID_ARGUMENTS", message: "Missing deltaSteps", details: nil))
            return
        }
        
        let testMode = args["testMode"] as? Bool ?? false
        goalSteps = steps
        isTracking = true
        
        // Get current step count as baseline
        getCurrentStepCount { [weak self] currentSteps in
            self?.baselineSteps = currentSteps
            self?.lastStepCount = currentSteps
            
            if testMode {
                // Test mode: use 30-second timer instead of actual steps
                self?.startTestModeTimer()
                print("AWTY iOS: Started test mode tracking \(steps) steps (30-second timer)")
                result(true)
            } else {
                // Normal mode: enable HealthKit background delivery
                self?.enableBackgroundDelivery { success in
                    if success {
                        print("AWTY iOS: Started tracking \(steps) steps from baseline \(currentSteps)")
                        result(true)
                    } else {
                        print("AWTY iOS: Failed to enable background delivery")
                        result(FlutterError(code: "BACKGROUND_DELIVERY_FAILED", message: "Could not enable HealthKit background delivery", details: nil))
                    }
                }
            }
        }
    }
    
    private func handleStopTracking(result: @escaping FlutterResult) {
        isTracking = false
        goalSteps = 0
        baselineSteps = 0
        lastStepCount = 0
        testModeStartTime = nil
        
        // Disable background delivery and test mode timer
        disableBackgroundDelivery()
        stopTestModeTimer()
        
        print("AWTY iOS: Stopped tracking")
        result(true)
    }
    
    private func startTestModeTimer() {
        // Set start time for progress calculation
        testModeStartTime = Date()
        
        // Test mode: goal reached in 30 seconds
        DispatchQueue.main.asyncAfter(deadline: .now() + 30.0) { [weak self] in
            guard let self = self, self.isTracking else { return }
            
            print("AWTY iOS: Test mode goal reached after 30 seconds")
            self.methodChannel?.invokeMethod("goalReached", arguments: nil)
            
            // Reset tracking state
            self.isTracking = false
            self.goalSteps = 0
            self.baselineSteps = 0
            self.lastStepCount = 0
            self.testModeStartTime = nil
        }
    }
    
    private func stopTestModeTimer() {
        // Test mode timer is automatically cancelled when isTracking becomes false
        // No additional cleanup needed for the simple timer approach
    }
    
    private func handleGetProgress(result: @escaping FlutterResult) {
        let stepsTaken: Int
        let stepsRemaining: Int
        
        if isTracking && goalSteps > 0 {
            // For test mode, simulate progress over 30 seconds
            if let startTime = testModeStartTime {
                let elapsed = Date().timeIntervalSince(startTime)
                let progress = min(elapsed / 30.0, 1.0) // 30 seconds total
                stepsTaken = Int(progress * Double(goalSteps))
                stepsRemaining = max(0, goalSteps - stepsTaken)
            } else {
                // Normal mode: calculate from actual step difference using lastStepCount
                stepsTaken = max(0, lastStepCount - baselineSteps)
                stepsRemaining = max(0, goalSteps - stepsTaken)
            }
        } else {
            stepsTaken = 0
            stepsRemaining = 0
        }
        
        let isRunning = isTracking
        
        let progress: [String: Any] = [
            "isRunning": isRunning,
            "currentSteps": lastStepCount,
            "baselineSteps": baselineSteps,
            "deltaSteps": goalSteps,
            "stepsTaken": stepsTaken,
            "stepsRemaining": stepsRemaining,
            "goalId": "ios_goal_\(Date().timeIntervalSince1970)",
            "lastUpdated": ISO8601DateFormatter().string(from: Date())
        ]
        
        result(progress)
    }
    
    private func handleUpdateStepCount(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let stepCount = args["stepCount"] as? Int else {
            result(FlutterError(code: "INVALID_ARGUMENTS", message: "Missing stepCount", details: nil))
            return
        }
        
        // Update the current step count and check if goal is reached
        lastStepCount = stepCount
        
        if isTracking && goalSteps > 0 {
            let stepsTaken = max(0, stepCount - baselineSteps)
            
            print("AWTY iOS: Step update - total: \(stepCount), baseline: \(baselineSteps), taken: \(stepsTaken), goal: \(goalSteps)")
            
            if stepsTaken >= goalSteps {
                // Goal reached!
                DispatchQueue.main.async {
                    self.methodChannel?.invokeMethod("goalReached", arguments: nil)
                }
                
                // Stop tracking
                isTracking = false
                goalSteps = 0
                baselineSteps = 0
                lastStepCount = 0
                
                print("AWTY iOS: Goal reached! \(stepsTaken) steps taken")
            }
        }
        
        result(true)
    }
    
    private func getCurrentStepCount(completion: @escaping (Int) -> Void) {
        guard HKHealthStore.isHealthDataAvailable() else {
            print("AWTY iOS: HealthKit not available")
            completion(0)
            return
        }
        
        let stepType = HKQuantityType.quantityType(forIdentifier: .stepCount)!
        let now = Date()
        let startOfDay = Calendar.current.startOfDay(for: now)
        let predicate = HKQuery.predicateForSamples(withStart: startOfDay, end: now, options: .strictStartDate)
        
        let query = HKStatisticsQuery(quantityType: stepType, quantitySamplePredicate: predicate, options: .cumulativeSum) { _, result, error in
            if let error = error {
                print("AWTY iOS: Error getting step count: \(error)")
                completion(0)
                return
            }
            
            let steps = result?.sumQuantity()?.doubleValue(for: HKUnit.count()) ?? 0
            completion(Int(steps))
        }
        
        healthStore.execute(query)
    }
    
    private func enableBackgroundDelivery(completion: @escaping (Bool) -> Void) {
        guard HKHealthStore.isHealthDataAvailable() else {
            completion(false)
            return
        }
        
        let stepType = HKQuantityType.quantityType(forIdentifier: .stepCount)!
        
        healthStore.enableBackgroundDelivery(for: stepType, frequency: .immediate) { success, error in
            if let error = error {
                print("AWTY iOS: Background delivery error: \(error)")
            }
            
            // Set up observer for step count changes
            self.setupStepObserver()
            completion(success)
        }
    }
    
    private func disableBackgroundDelivery() {
        guard HKHealthStore.isHealthDataAvailable() else { return }
        
        let stepType = HKQuantityType.quantityType(forIdentifier: .stepCount)!
        healthStore.disableBackgroundDelivery(for: stepType) { _, _ in }
    }
    
    private func setupStepObserver() {
        let stepType = HKQuantityType.quantityType(forIdentifier: .stepCount)!
        let query = HKObserverQuery(sampleType: stepType, predicate: nil) { [weak self] _, _, error in
            if let error = error {
                print("AWTY iOS: Observer query error: \(error)")
                return
            }
            
            // Check if goal reached
            self?.checkGoalReached()
        }
        
        healthStore.execute(query)
    }
    
    private func checkGoalReached() {
        guard isTracking else { return }
        
        getCurrentStepCount { [weak self] currentSteps in
            guard let self = self else { return }
            
            let stepsTaken = max(0, currentSteps - self.baselineSteps)
            
            if stepsTaken >= self.goalSteps {
                // Goal reached!
                DispatchQueue.main.async {
                    self.methodChannel?.invokeMethod("goalReached", arguments: nil)
                }
                
                // Stop tracking
                self.isTracking = false
                self.goalSteps = 0
                self.baselineSteps = 0
                self.lastStepCount = 0
                
                print("AWTY iOS: Goal reached! \(stepsTaken) steps taken")
            }
        }
    }
}
