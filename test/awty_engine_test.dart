import 'package:flutter_test/flutter_test.dart';
import 'package:awty_engine/awty_engine.dart';

void main() {
  group('AwtyEngine', () {
    test('should have static methods', () {
      expect(AwtyEngine.startTracking, isA<Function>());
      expect(AwtyEngine.stopTracking, isA<Function>());
      expect(AwtyEngine.getProgress, isA<Function>());
      expect(AwtyEngine.updateStepCount, isA<Function>());
      expect(AwtyEngine.initialize, isA<Function>());
      expect(AwtyEngine.getPlatformVersion, isA<Function>());
    });

    test('should have onGoalReached callback property', () {
      expect(AwtyEngine.onGoalReached, isNull);
      
      // Test setting callback
      bool callbackCalled = false;
      AwtyEngine.onGoalReached = () {
        callbackCalled = true;
      };
      
      expect(AwtyEngine.onGoalReached, isA<Function>());
      
      // Test calling callback
      AwtyEngine.onGoalReached?.call();
      expect(callbackCalled, isTrue);
    });
  });
}
