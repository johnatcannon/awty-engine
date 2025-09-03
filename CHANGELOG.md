# Changelog

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
