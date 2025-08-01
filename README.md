# AWTY Engine

[![pub.dev](https://img.shields.io/pub/v/awty_engine)](https://pub.dev/packages/awty_engine)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Are We There Yet?** - A reliable, decoupled step tracking engine for Flutter applications.

## What is AWTY?

AWTY Engine is a **community-driven, open source Flutter package** that provides **pure step counting and goal notification services** for any Flutter application. AWTY solves a common problem: reliably notifying users when they have taken a specific number of additional steps, even if the application is in the background, the device is asleep, or the app is killed.

### Core Philosophy: Decoupling and Reusability

The engine is designed to be completely decoupled from any specific application's logic, UI, or analytics. Its sole responsibility is to count steps and report when a goal is met, firing a generic notification. This makes it a highly reliable tool that can be easily integrated into various projects (fitness apps, walking games, health trackers, etc.) with minimal maintenance.

## Features

### ✅ What AWTY Does:
- Counts steps using Health Connect (Android) / HealthKit (iOS, future)
- Tracks progress toward step goals (e.g., "walk 1000 more steps")  
- Fires generic notifications when goals are reached
- Persists goal state across app restarts and device reboots
- Runs reliably in the background via foreground service
- Handles daily step count resets (midnight rollover)
- Prevents negative step counts through baseline management

### ❌ What AWTY Does NOT Do:
- Know anything about your app's purpose, UI, or business logic
- Handle app-specific navigation, rewards, or user messaging
- Store or process any data beyond step counts and goal status
- Customize notifications beyond the generic "Goal reached!" message
- Integrate with analytics, user accounts, or cloud services

**Host applications handle everything beyond step counting**: UI updates, game logic, user rewards, analytics, navigation, and app-specific messaging.

## Current Status

### ✅ Android Support
- **Fully implemented** with Health Connect API
- Native Android foreground service for reliable background operation
- Comprehensive step tracking with goal management
- Generic notifications with distinctive vibration patterns

### 📱 iOS Support - Coming Soon!
**Note**: As of this first release, AWTY Engine does **not yet support iOS devices**. 

I recently got my iPhone and plan to implement iOS support using HealthKit background delivery. This will be added in a future release through community contributions.

## Installation

Add AWTY Engine to your `pubspec.yaml`:

```yaml
dependencies:
  awty_engine: ^0.0.2
```

## Quick Start

### 1. Initialize Global Notification Service

```dart
// In main.dart or app initialization
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    AwtyNotificationService.initialize(context);
  }
}
```

### 2. Start Step Tracking

```dart
// Start tracking 1000 additional steps
await AwtyEngine.startStepTracking(
  deltaSteps: 1000,  // Notify when 1000 more steps are taken
  goalId: 'unique_goal_id',
  appName: 'MyApp',  // App name for generic notifications
);
```

### 3. Monitor Progress

```dart
// Monitor progress with timers
Timer.periodic(Duration(seconds: 5), (_) async {
  final progress = await stepTrackingService.getCurrentProgress();
  updateUI(progress['stepsTaken'], progress['stepsRemaining']);
});
```

### 4. Handle Goal Completion

```dart
// Global notification service handles this automatically
class AwtyNotificationService {
  static void initialize(BuildContext context) {
    const MethodChannel channel = MethodChannel('awty_engine');
    channel.setMethodCallHandler((call) async {
      if (call.method == 'goalReached') {
        await _handleGoalReached();
      }
    });
  }
  
  static Future<void> _handleGoalReached() async {
    // Handle goal completion from any page
    // Update game state, navigate to appropriate page, etc.
  }
}
```

## Use Cases

AWTY Engine works for any app that needs step-based goals:

### 🎮 Walking Games
```dart
// Track steps for a game level
await AwtyEngine.startStepTracking(
  deltaSteps: 1500,
  goalId: 'level_${currentLevel}_${playerId}',
  appName: 'WalkingGame',
);
```

### 🏃‍♂️ Fitness Apps
```dart
// Start a daily step goal
await AwtyEngine.startStepTracking(
  deltaSteps: 10000,
  goalId: 'daily_${DateFormat('yyyy-MM-dd').format(DateTime.now())}',
  appName: 'FitnessTracker',
);
```

### 🚚 Delivery/Rideshare Apps
```dart
// Track walking to pickup location
await AwtyEngine.startStepTracking(
  deltaSteps: 500,
  goalId: 'pickup_walk_${orderId}',
  appName: 'DeliveryApp',
);
```

### 📚 Educational Apps
```dart
// Science lesson while walking
await AwtyEngine.startStepTracking(
  deltaSteps: 2000,
  goalId: 'lesson_${lessonId}_segment_${segmentId}',
  appName: 'WalkingLessons',
);
```

## Android Setup

Add required permissions to `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.VIBRATE" />

<queries>
  <package android:name="com.google.android.apps.healthdata" />
</queries>
```

## About GamesAfoot.co

**GamesAfoot.co** is passionate about promoting and marketing walking-related fitness apps, especially games. We believe that combining physical activity with engaging gameplay creates a powerful incentive for people to stay active and healthy.

Our mission is to support the development of innovative walking-based applications that make fitness fun and accessible to everyone. AWTY Engine is our contribution to the Flutter community, designed to help developers create reliable, engaging walking experiences.

## Open Source Strategy

AWTY Engine is developed as an **open source contribution to the Flutter community** with the following goals:

- **Fill Market Gap**: No existing Flutter packages provide reliable background step tracking with goal management
- **Universal Tool**: One step counter that works for any app type—fitness, games, education, delivery, healthcare
- **Community Development**: Enable iOS support and broader device compatibility through community contributions  
- **Quality Through Usage**: More diverse apps using AWTY means better testing across devices and scenarios
- **Developer Adoption**: Open source removes barriers vs. paid packages, enabling widespread adoption

**License**: MIT (allows commercial use, modification, and distribution)

## Contributing

We welcome contributions from the Flutter community! Areas where help is especially needed:

- **iOS Implementation**: HealthKit integration and background delivery
- **Device Testing**: Testing across different Android versions and manufacturers  
- **Feature Enhancement**: Additional health data types, notification customization
- **Documentation**: Examples, tutorials, and integration guides

## Community Governance

- **Maintainers**: Core team provides oversight and release management
- **Contributors**: Community members can submit PRs for features and fixes
- **Issues**: GitHub issues used for bug reports and feature requests
- **Releases**: Semantic versioning with regular updates to pub.dev

## Battery Optimization

For reliable background operation, host applications should request users to exempt the app from battery optimizations (set to "Unrestricted" mode) via system settings. This is critical for ensuring the AWTY engine can deliver milestone notifications and track steps in the background, especially on devices with aggressive battery management.

## Documentation

For detailed technical specifications, architecture, and implementation details, see the [AWTY Specification](docs/__AWTY-Spec.md).

## Links

- **Package**: [pub.dev/packages/awty_engine](https://pub.dev/packages/awty_engine)
- **Repository**: [GitHub](https://github.com/johnatcannon/awty-engine)
- **Company**: [GamesAfoot.co](https://gamesafoot.co)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.