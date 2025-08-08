# Changelog

All notable changes to the AWTY Engine project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0+1] - 2025-01-15

### Changed
- **BREAKING**: Simplified notification approach - AWTY now focuses solely on step tracking and goal detection
- **BREAKING**: Removed custom notifications, sounds, and vibrations (handled by calling application)
- **BREAKING**: Moved from Health Connect to `pedometer` package for step counting
- **BREAKING**: Changed from midnight step count resets to system boot resets
- **BREAKING**: Updated API to use `AwtyIntegration` class instead of direct `AwtyEngine` calls

### Added
- `pedometer` package integration for reliable step counting
- Immediate `goalReached` callbacks via platform channel
- Test mode functionality (60-second countdown for development/testing)
- Service restart prevention logic to avoid duplicate goal completions
- Enhanced app name persistence across service restarts
- Comprehensive logging and debugging capabilities

### Fixed
- Service crashes after goal completion
- "Unknown App" notification issue
- App restart recovery and state persistence
- Permission handling and startup issues
- Race conditions in service lifecycle management

### Technical
- Native Android implementation using `pedometer` package
- Foreground service for reliable background operation
- SharedPreferences for state persistence
- Method channel communication between Flutter and native code
- Comprehensive error handling and logging

### Known Limitations
- iOS support not yet implemented (planned for future release)
- Android only (pedometer package)

## [Unreleased]

### Added
- Initial release preparation
- Comprehensive documentation
- MIT License

## [0.0.2+1] - 2025-01-15

### Added
- Android Health Connect integration
- Native Android foreground service for background step tracking
- Generic goal notification system with distinctive vibration patterns
- Goal state persistence across app restarts and device reboots
- Daily step count reset handling (midnight rollover)
- Baseline management to prevent negative step counts
- Comprehensive logging and status tracking
- Platform channel API for Flutter integration

### Features
- `AwtyEngine.startStepTracking()` - Start tracking additional steps
- `AwtyEngine.stopStepTracking()` - Stop step tracking
- `AwtyEngine.getGoalStatus()` - Check goal completion status
- `AwtyEngine.getStepTrackingState()` - Get current tracking state
- `AwtyEngine.getAwtyLogTail()` - Get service logs for debugging
- `AwtyEngine.requestDndOverridePermission()` - Request notification policy access

### Technical
- Native Android implementation using Health Connect API
- Foreground service for reliable background operation
- SharedPreferences for state persistence
- Method channel communication between Flutter and native code
- Comprehensive error handling and logging

### Known Limitations
- iOS support not yet implemented (planned for future release)
- Android only (Health Connect API)

## [0.0.1] - Initial Development

### Added
- Basic Flutter plugin structure
- Initial Android platform implementation
- Core step tracking concept and architecture 