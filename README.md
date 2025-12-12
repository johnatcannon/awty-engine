# AWTY Engine (Are We There Yet?)

A simple, reliable, and decoupled step tracking engine for Flutter applications. AWTY provides pure step counting and goal notification services, allowing you to focus on building your game's UI and logic.

## Features

* **Cross-Platform Support**: Works on both Android and iOS with platform-optimized implementations
* **Reliable Background Tracking**: 
  - **Android**: Uses foreground service for continuous background operation
  - **iOS**: Uses HealthKit background delivery for battery-efficient tracking
* **Real Pedometer Integration**: Connects to the device's native step sensors
* **Simple API**: Clean and focused API for starting, stopping, and monitoring step goals
* **Customizable Notifications**: Set custom notification text and icons (Android)
* **Test Mode**: Includes test mode for rapid development and testing

## Platform Differences

### Android
- **Background Operation**: Foreground service ensures reliable step counting
- **Real-time Updates**: Continuous step monitoring with persistent notification
- **Battery Impact**: Higher due to foreground service, but very reliable
- **Best For**: Users who want guaranteed step tracking reliability

### iOS
- **Background Operation**: HealthKit background delivery wakes app when needed
- **Update Frequency**: Updates every 30-60 seconds when walking
- **Battery Impact**: Minimal - app sleeps most of the time
- **Best For**: Users who prioritize battery life and don't need real-time updates

## Getting Started

### 1. Add Dependency

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  awty_engine: ^2.0.1
```

### 2. Platform Setup

#### Android Setup

Add the necessary permissions to your `android/app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Required: Foreground service for background step tracking -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
    
    <!-- Required: Activity recognition for step counting -->
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
    
    <!-- Required: Notification permission for Android 13+ (API 33+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    
    <application ...>
        ...
    </application>
</manifest>
```

**Important:** On Android 13+ (API 33+), `POST_NOTIFICATIONS` must be requested at runtime:

```dart
import 'package:permission_handler/permission_handler.dart';

// Request notification permission before starting tracking
final notificationStatus = await Permission.notification.request();
if (!notificationStatus.isGranted) {
  print('Notification permission denied - notification may not appear');
  // Foreground service will still work, but notification won't display
}
```

Place your small, monochrome notification icon (e.g., `barefoot.png`) in the `android/app/src/main/res/drawable` directory.

#### iOS Setup

Add HealthKit capability to your iOS project:

1. Open your iOS project in Xcode
2. Select your target → Signing & Capabilities
3. Add "HealthKit" capability
4. Add privacy descriptions to `Info.plist`:

```xml
<key>NSHealthShareUsageDescription</key>
<string>We need access to your health data to track your walking progress and notify you when goals are reached.</string>
<key>NSHealthUpdateUsageDescription</key>
<string>We need access to update your health data to track your walking progress.</string>
```

## Usage

### 1. Initialize the Engine

In your main walking page's `initState`, initialize the `AwtyEngine` to listen for goal events:

```dart
import 'package:awty_engine/awty_engine.dart';
import 'package:pedometer/pedometer.dart';
import 'dart:async';

class WalkingPage extends StatefulWidget {
  // ...
}

class _WalkingPageState extends State<WalkingPage> {
  StreamSubscription<StepCount>? _stepCountSubscription;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  void _initialize() {
    // Listen for goal reached events from AWTY
    AwtyEngine.initialize();
    AwtyEngine.onGoalReached = () {
      print("Goal Reached! Navigating to the arrival page.");
      // Navigate to your clue or arrival page here
    };

    // On Android: Listen to pedometer stream and forward to AWTY
    // On iOS: HealthKit handles this automatically
    if (defaultTargetPlatform == TargetPlatform.android) {
      _stepCountSubscription = Pedometer.stepCountStream.listen(
        (StepCount event) {
          // Send live step counts to the AWTY service
          AwtyEngine.updateStepCount(event.steps);
        },
        onError: (error) {
          print("Pedometer Error: $error");
        },
        cancelOnError: true,
      );
    }
  }

  @override
  void dispose() {
    _stepCountSubscription?.cancel();
    super.dispose();
  }
  
  // ... rest of your widget
}
```

### 2. Start a Walk

When you are ready to start a walk, call `AwtyEngine.startTracking`:

```dart
void startNewWalk() {
  AwtyEngine.startTracking(
    steps: 1000, // The number of steps for this goal
    notificationText: "Walking to the Eiffel Tower...", // Android only
    notificationIconName: 'barefoot', // Android only
    testMode: false, // Set to true for 30-second test
  );
}
```

### 3. Stop a Walk (Optional)

You can manually stop tracking at any time. The service will also stop itself automatically when a goal is reached:

```dart
void stopTheWalk() {
  AwtyEngine.stopTracking();
}
```

## Platform-Specific Behavior

### Android
- **Immediate Start**: Step tracking begins immediately
- **Real-time Progress**: Progress updates with every step
- **Persistent Notification**: Status bar notification shows progress
- **Background Reliability**: Works even when app is closed

### iOS
- **HealthKit Integration**: Uses Apple's Health app for step data
- **Background Efficiency**: App sleeps most of the time
- **Update Frequency**: Progress updates every 30-60 seconds when walking
- **Battery Friendly**: Minimal battery impact

## API Reference

### Methods

- `AwtyEngine.initialize()` - Set up goal reached callback
- `AwtyEngine.startTracking(steps, notificationText?, notificationIconName?, testMode?)` - Start tracking steps
- `AwtyEngine.stopTracking()` - Stop tracking
- `AwtyEngine.getProgress()` - Get current progress
- `AwtyEngine.updateStepCount(steps)` - Update step count (Android only)

### Properties

- `AwtyEngine.onGoalReached` - Callback function for goal completion

## Example Projects

Check out the `example/` directory for complete working examples of both Android and iOS implementations.

## Contributing

We welcome contributions! Please see our contributing guidelines for more information.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support and questions, please visit our [GitHub repository](https://github.com/johnatcannon/awty-engine) or contact us at info@gamesafoot.co.

