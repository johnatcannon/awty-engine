# Changelog

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
