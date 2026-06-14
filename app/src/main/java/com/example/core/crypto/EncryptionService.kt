package com.example.core.crypto

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionService {

    // AES-256-CBC Encrypts plain text JSON and returns Base64 (including IV)
    fun encrypt(plainText: String, secretKey: SecretKeySpec): String {
        // Generate secure random IV (16 bytes for AES)
        val iv = ByteArray(16)
        java.security.SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encryptedData = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Combine IV and Encrypted Data: [IV (16 bytes)] + [Encrypted payload]
        val combined = ByteArray(iv.size + encryptedData.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    // AES-256-CBC Decrypts Base64 payload containing IV
    fun decrypt(encryptedBase64: String, secretKey: SecretKeySpec): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (combined.size < 16) {
            throw IllegalArgumentException("Data too short to contain IV")
        }

        // Extract IV (first 16 bytes)
        val iv = ByteArray(16)
        System.arraycopy(combined, 0, iv, 0, 16)

        // Extract encrypted payload
        val encryptedPayload = ByteArray(combined.size - 16)
        System.arraycopy(combined, 16, encryptedPayload, 0, encryptedPayload.size)

        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        val decryptedBytes = cipher.doFinal(encryptedPayload)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    // SHA-256 for user PIN hashing
    fun hashSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
