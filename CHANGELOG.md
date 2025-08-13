## 0.2.0
* Major rewrite of the plugin for stability and simplicity.
* Rebuilt from a clean Flutter plugin template to resolve critical build issues.
* Implemented a robust Android Foreground Service for reliable background step tracking.
* Integrated real-time step counting by connecting the `pedometer` package.
* Added a `testMode` for rapid development, allowing goals to be reached in 30 seconds.
* Introduced a new `updateStepCount` method to feed live pedometer data to the service.
* Added the ability to specify a custom monochrome icon for the persistent notification.
* Simplified the Dart API, removing unnecessary methods and focusing on core functionality.
* Cleaned up and removed all old, unused code.

## 0.0.1

* Initial release.
