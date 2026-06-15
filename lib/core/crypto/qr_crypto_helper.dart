import 'package:encrypt/encrypt.dart';
import 'package:flutter/foundation.dart';

/// Elite Cryptographic Helper for PakPass QR Code En/Decoding
/// Enforces a static 256-bit AES key and a static initialization vector (IV)
/// to ensure any peer device can decrypt a scanned passenger pass identically.
class QrCryptoHelper {
  static const String _tag = "QrCryptoHelper";

  // Symmetric Static standard elements (AES 256 Key & IV matrix)
  static const String _staticKey = "NextEraPakPassSecureKey2026_V12"; // Exactly 32 bytes (256-bit key)
  static const String _staticIV = "1234567890123456"; // Exactly 16 bytes (128-bit block size)

  /// Encrypts plain text string metadata payload into secure base64 ciphertext
  static String encryptData(String plainText) {
    try {
      final key = Key.fromUtf8(_staticKey);
      final iv = IV.fromUtf8(_staticIV);
      final encrypter = Encrypter(AES(key, mode: AESMode.cbc));

      final encrypted = encrypter.encrypt(plainText, iv: iv);
      return encrypted.base64;
    } catch (e) {
      debugPrint("$_tag: Critical Encryption Error: $e");
      rethrow;
    }
  }

  /// Decrypts base64 ciphertext QR payload back into raw JSON plain text.
  /// Seamlessly trims input before processing to prevent decryption format failures.
  static String? decryptData(String cipherText) {
    final sanitizedInput = cipherText.trim();
    if (sanitizedInput.isEmpty) {
      debugPrint("$_tag: Decryption skipped. Raw ciphertext payload is empty.");
      return null;
    }

    try {
      final key = Key.fromUtf8(_staticKey);
      final iv = IV.fromUtf8(_staticIV);
      final encrypter = Encrypter(AES(key, mode: AESMode.cbc));

      final decrypted = encrypter.decrypt64(sanitizedInput, iv: iv);
      return decrypted;
    } catch (e) {
      debugPrint("$_tag: Decryption Failed. Potential forgery, clock mismatch, or corrupted data. Code: $e");
      return null;
    }
  }
}
