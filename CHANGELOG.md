# Changelog

## [2.1.0] - 2025-12-06

### Added
- **Step Update Callback API**: New `onStepUpdate()` callback for real-time step count updates
  - Apps can subscribe to step count changes without polling
  - Eliminates need for apps to poll `getStepsRemaining()` every 2 seconds
  - Callback fires whenever pedometer stream updates (cross-platform)
- **Callback Management**: New `clearStepUpdateCallback()` method for cleanup
- **Enhanced Logging**: Detailed Android notification icon lookup debugging

### Fixed
- **Duplicate Callback Prevention**: Improved protection against duplicate goal reached callbacks
  - Added `_goalReachedCallbackFired` flag to prevent multiple triggers
  - Prevents race conditions between pedometer stream and native callbacks
- **iOS Handicap Calculation**: Improved handicap calculation logic
  - Now uses the larger of: system 30-day average OR player-entered handicap
  - Ensures players aren't penalized if system data is incomplete
  - Minimum handicap increased from 0.3 to 0.5

### Changed
- **Android Icon Lookup**: Enhanced icon resource lookup with better error messages
  - Uses `applicationContext.packageName` instead of plugin package name
  - Added detailed logging to debug icon loading issues

### Technical Details
- **Cross-Platform**: `onStepUpdate()` works on both Android and iOS via pedometer stream
- **Performance**: Eliminates polling overhead - apps receive updates only when steps change
- **Backward Compatible**: Existing apps continue to work without changes
- **Notification Permission**: Apps must request `POST_NOTIFICATIONS` on Android 13+ for notification to display

## [2.0.2] - 2025-01-15

### Changed
- **Version Bump**: Updated version for pub.dev publication

## [2.0.1] - 2025-10-14

### Fixed
- **Android Step Tracking**: Fixed `getStepsRemaining()` to return actual remaining steps instead of raw step count
- **Notification Performance**: Simplified notifications to use static message, eliminating step count update overhead
- **Code Cleanup**: Removed unused code (`_currentStepCount` and `_startBackupPolling`)

## [2.0.0] - 2025-10-01

### Major Changes
- **Complete Pedometer Rewrite**: Replaced HealthKit real-time tracking with pedometer package for iOS
- **Cross-Platform Consistency**: Both Android and iOS now use identical pedometer-based step tracking
- **Improved Reliability**: Eliminated HealthKit synchronization issues and delays
- **Real-Time Performance**: Much faster and more responsive step tracking

### Fixed
- **iOS Step Tracking**: Fixed critical bug where `currentSteps` wasn't being updated in `getStepsRemaining`
- **Step Count Synchronization**: iOS now returns accurate remaining steps instead of always returning goal amount
- **Notification Spam**: Fixed excessive notification updates - now only updates when steps actually change
- **Baseline Management**: Improved baseline step establishment and management

### Changed
- **iOS Architecture**: Complete rewrite of iOS implementation to match Android pedometer pattern
- **Step Update Flow**: Flutter pedometer package now provides step updates to both platforms
- **HealthKit Usage**: HealthKit now only used for 30-day average handicap calculation, not real-time tracking
- **Logging**: Reduced verbose logging to essential information only

### Technical Details
- **iOS**: `updateStepCount` now properly updates `currentSteps` variable
- **Cross-Platform**: Both platforms use `Pedometer.stepCountStream` for real-time updates
- **Baseline Logic**: Consistent baseline establishment across platforms
- **Goal Completion**: Improved goal reached detection and notification handling

## [0.3.0] - 2025-09-03

### Fixed
- **iOS Step Tracking**: Fixed iOS AWTY engine to properly process step count updates from Flutter
- **Step Count Updates**: iOS now correctly handles `updateStepCount` calls instead of ignoring them
- **Steps Remaining Display**: Fixed steps remaining calculation to work consistently across platforms
- **Goal Detection**: Improved iOS goal reached detection and notification handling

### Changed
- **iOS Implementation**: Updated iOS AWTY engine to use step count updates from Flutter app
- **Cross-Platform Consistency**: Both Android and iOS now use the same step tracking logic
- **API Compatibility**: Ensured `updateStepCount` works on both platforms

### Technical Details
- **iOS**: Fixed `handleUpdateStepCount` to process step count updates and calculate `stepsTaken`
- **Progress Tracking**: iOS now properly updates internal step counters for accurate progress reporting
- **State Management**: Improved iOS state management for consistent behavior with Android

## [0.2.0] - 2025-01-XX

### Added
- **iOS Support**: Full iOS implementation using HealthKit background delivery
- **Cross-Platform API**: Unified API that works on both Android and iOS
- **Platform Detection**: Automatic platform detection and optimization
- **HealthKit Integration**: iOS step tracking using Apple's Health app
- **Background Delivery**: iOS background app wake-up when step goals reached

### Changed
- **API Simplification**: Made notificationText and notificationIconName optional (iOS doesn't use them)
- **Platform Optimization**: Android uses foreground service, iOS uses HealthKit
- **Documentation**: Comprehensive documentation for both platforms

### Technical Details
- **Android**: Maintains existing foreground service approach for reliability
- **iOS**: New HealthKit-based implementation for battery efficiency
- **Unified Interface**: Same API works on both platforms with platform-specific optimizations

## [0.1.0] - 2024-XX-XX

### Added
- Initial Android implementation
- Foreground service for background step tracking
- Real-time step counting and goal notifications
- Test mode for development
- Customizable notification support
