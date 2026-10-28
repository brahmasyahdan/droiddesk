import 'package:droiddesk/services/update_service.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('UpdateService.isNewer', () {
    test('compares versions numerically, not as text', () {
      expect(UpdateService.isNewer('v1.10.0', '1.9.0'), isTrue);
      expect(UpdateService.isNewer('v1.3.1', '1.3.1'), isFalse);
      expect(UpdateService.isNewer('v1.3.0', '1.3.1'), isFalse);
    });

    test('ignores the v prefix, pre-release suffix and build number', () {
      expect(UpdateService.isNewer('V1.4.0-beta', '1.3.1+6'), isTrue);
      expect(UpdateService.isNewer('1.3.1+7', '1.3.1+6'), isFalse);
    });

    test('treats missing parts as zero', () {
      expect(UpdateService.isNewer('2', '1.9.9'), isTrue);
      expect(UpdateService.isNewer('1.3', '1.3.0'), isFalse);
    });
  });
}
