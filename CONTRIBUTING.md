# Contributing to AWTY Engine

Thank you for your interest in contributing to AWTY Engine! We welcome contributions from the Flutter community to help make this step tracking engine better for everyone.

## How Can I Contribute?

### 🐛 Report Bugs
- Use the [GitHub Issues](https://github.com/johnatcannon/awty-engine/issues) page
- Include detailed steps to reproduce the bug
- Specify your device, Android version, and Flutter version
- Include any error messages or logs

### 💡 Suggest Features
- Use the [GitHub Issues](https://github.com/johnatcannon/awty-engine/issues) page
- Describe the feature and its use case
- Explain how it would benefit the community

### 🔧 Submit Code Changes
- Fork the repository
- Create a feature branch (`git checkout -b feature/amazing-feature`)
- Make your changes
- Add tests if applicable
- Commit your changes (`git commit -m 'Add amazing feature'`)
- Push to the branch (`git push origin feature/amazing-feature`)
- Open a Pull Request

## Development Setup

### Prerequisites
- Flutter SDK (>=3.0.0)
- Android Studio / VS Code
- Android device or emulator for testing

### Local Development
1. Clone the repository:
   ```bash
   git clone https://github.com/johnatcannon/awty-engine.git
   cd awty-engine
   ```

2. Install dependencies:
   ```bash
   flutter pub get
   ```

3. Run tests:
   ```bash
   flutter test
   ```

4. Create a test app to verify your changes:
   ```bash
   flutter create test_app
   cd test_app
   # Add awty_engine dependency to pubspec.yaml
   flutter pub get
   # Test your changes
   ```

## Priority Areas for Contributions

### 🚀 High Priority
- **iOS Implementation**: HealthKit integration and background delivery
- **Device Testing**: Testing across different Android versions and manufacturers
- **Bug Fixes**: Any issues that affect core functionality

### 📈 Medium Priority
- **Documentation**: Examples, tutorials, and integration guides
- **Performance Optimization**: Improving battery usage and responsiveness
- **Error Handling**: Better error messages and recovery mechanisms

### 🎨 Low Priority
- **UI/UX Improvements**: Better notification designs or customization options
- **Additional Features**: New health data types, advanced goal management
- **Code Quality**: Refactoring, code style improvements

## Code Style Guidelines

- Follow the [Dart Style Guide](https://dart.dev/guides/language/effective-dart/style)
- Use meaningful variable and function names
- Add comments for complex logic
- Write tests for new functionality
- Keep functions small and focused

## Testing Guidelines

- Test on real Android devices when possible
- Test background operation scenarios
- Verify step counting accuracy
- Test notification delivery
- Test app restart scenarios

## Pull Request Guidelines

- Provide a clear description of the changes
- Include any relevant issue numbers
- Add tests if adding new functionality
- Update documentation if needed
- Ensure all tests pass

## Release Process

1. **Version Bumping**: Update version in `pubspec.yaml`
2. **Changelog**: Add entries to `CHANGELOG.md`
3. **Testing**: Verify functionality on multiple devices
4. **Documentation**: Update README if needed
5. **Tagging**: Create a git tag for the release
6. **Publishing**: Publish to pub.dev

## Community Guidelines

- Be respectful and inclusive
- Help others learn and grow
- Provide constructive feedback
- Follow the project's code of conduct

## Getting Help

- Check the [documentation](https://github.com/johnatcannon/awty-engine/blob/main/README.md)
- Search existing [issues](https://github.com/johnatcannon/awty-engine/issues)
- Ask questions in the [GitHub Discussions](https://github.com/johnatcannon/awty-engine/discussions)

## License

By contributing to AWTY Engine, you agree that your contributions will be licensed under the MIT License.

Thank you for contributing to AWTY Engine! 🚶‍♂️📱 