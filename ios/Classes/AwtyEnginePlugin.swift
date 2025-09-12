import Flutter
import UIKit

public class AwtyEnginePlugin: NSObject, FlutterPlugin {
  private var testTimer: Timer?
  private var testStartTime: Date?
  private var goalSteps: Int = 0
  private var goalReachedCallback: (() -> Void)?

  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "awty_engine_v2", binaryMessenger: registrar.messenger())
    let instance = AwtyEnginePlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "startGoal":
      guard let args = call.arguments as? [String: Any],
            let steps = args["goalSteps"] as? Int,
            let testMode = args["testMode"] as? Bool else {
        result(FlutterError(code: "INVALID_ARGUMENTS", message: "Missing parameters", details: nil))
        return
      }
      goalSteps = steps
      if testMode {
        startTestModeTimer()
      }
      result(true)
    case "getStepsRemaining":
      if let startTime = testStartTime {
        let elapsed = Date().timeIntervalSince(startTime)
        let progress = min(elapsed / 30.0, 1.0)
        let simulatedSteps = Int(Double(goalSteps) * progress)
        result(goalSteps - simulatedSteps)
      } else {
        result(goalSteps)
      }
    case "stopGoal":
      testTimer?.invalidate()
      testTimer = nil
      testStartTime = nil
      goalSteps = 0
      result(true)
    default:
      result(FlutterMethodNotImplemented)
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
}
