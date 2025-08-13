# AWTY Engine (Are We There Yet?)

A simple, reliable, and decoupled step tracking engine for Flutter applications. AWTY provides pure step counting and goal notification services, allowing you to focus on building your game's UI and logic.

## Features

*   **Reliable Background Tracking:** Uses an Android Foreground Service to ensure step counting continues even when the app is closed.
*   **Real Pedometer Integration:** Connects to the device's native step sensor for accurate, real-time data.
*   **Simple API:** A clean and focused API for starting, stopping, and updating step counts.
*   **Customizable Notifications:** Set a custom monochrome icon for the persistent status bar notification.
*   **Test Mode:** Includes a 30-second test mode for rapid development and testing of goal-arrival events.

## Getting Started

### 1. Add Dependency

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  awty_engine: ^0.2.0
```

### 2. Android Setup

Add the necessary permissions to your `android/app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
    <application ...>
        ...
    </application>
</manifest>
```

### 3. Place Notification Icon

Place your small, monochrome notification icon (e.g., `barefoot.png`) in the `android/app/src/main/res/drawable` directory.

## Usage

### 1. Initialize the Engine and Pedometer

In your main walking page's `initState`, initialize the `AwtyEngine` to listen for goal events and start your pedometer stream.

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

    // Listen to the pedometer stream
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

  @override
  void dispose() {
    _stepCountSubscription?.cancel();
    super.dispose();
  }
  
  // ... rest of your widget
}
```

### 2. Start a Walk

When you are ready to start a walk, call `AwtyEngine.startTracking`.

```dart
void startNewWalk() {
  AwtyEngine.startTracking(
    steps: 1000, // The number of steps for this goal
    notificationText: "Walking to the Eiffel Tower...",
    notificationIconName: 'barefoot', // The name of your icon file (without extension)
    testMode: false, // Set to true for 30-second test
  );
}
```

### 3. Stop a Walk (Optional)

You can manually stop tracking at any time. The service will also stop itself automatically when a goal is reached.

```dart
void stopTheWalk() {
  AwtyEngine.stopTracking();
}
```

