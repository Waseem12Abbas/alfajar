package com.example.core.storage

import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.SecretKeySpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class SecureStorageService(private val context: Context) {
    private val prefs = context.getSharedPreferences("pakpass_secure_prefs", Context.MODE_PRIVATE)
    private val keyStoreAlias = "PakPassHardwareMasterKey"

    init {
        initMasterKey()
    }

    private fun initMasterKey() {
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!ks.containsAlias(keyStoreAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
                )
                keyGenerator.init(
                    KeyGenParameterSpec.Builder(
                        keyStoreAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                     .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                     .build()
                )
                keyGenerator.generateKey()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun getMasterKey(): SecretKey {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val entry = ks.getEntry(keyStoreAlias, null)
            if (entry is KeyStore.SecretKeyEntry) {
                entry.secretKey
            } else {
                // Key corrupted or has different type, re-generate it
                try { ks.deleteEntry(keyStoreAlias) } catch (t: Throwable) {}
                initMasterKey()
                val newEntry = ks.getEntry(keyStoreAlias, null) as KeyStore.SecretKeyEntry
                newEntry.secretKey
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            // Try to force recreate and load
            try {
                val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                try { ks.deleteEntry(keyStoreAlias) } catch (t2: Throwable) {}
                initMasterKey()
                val entry = ks.getEntry(keyStoreAlias, null) as KeyStore.SecretKeyEntry
                entry.secretKey
            } catch (t3: Throwable) {
                throw RuntimeException("Master hardware key inaccessible", t3)
            }
        }
    }

    // Derive AES-256 key using PBKDF2 from user PIN
    fun deriveAndStoreKey(pin: String): Boolean {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "PakPassDefaultSaltDevice"
            val salt = androidId.toByteArray(Charsets.UTF_8)
            val iterations = 100000
            val keyLength = 256

            val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, keyLength)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val derivedBytes = skf.generateSecret(spec).encoded

            // Encrypt derived bytes using our Android KeyStore Hardware Master Key (AES-GCM)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(derivedBytes)

            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

            prefs.edit()
                .putString("encrypted_aes_key", encryptedBase64)
                .putString("aes_key_iv", ivBase64)
                .apply()
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    // Retrieve the decrypted PBKDF2 derived key
    fun getStoredKey(): SecretKeySpec? {
        return try {
            val encryptedBase64 = prefs.getString("encrypted_aes_key", null) ?: return null
            val ivBase64 = prefs.getString("aes_key_iv", null) ?: return null

            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), spec)

            val rawKey = cipher.doFinal(encryptedBytes)
            SecretKeySpec(rawKey, "AES")
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun hasKeyStored(): Boolean {
        return prefs.contains("encrypted_aes_key") && prefs.contains("aes_key_iv")
    }

    fun clearKey() {
        prefs.edit().clear().apply()
    }

    fun getSupabaseUrl(): String? {
        return prefs.getString("supabase_url", null)
    }

    fun setSupabaseUrl(url: String) {
        prefs.edit().putString("supabase_url", url.trim()).apply()
    }

    fun getSupabaseAnonKey(): String? {
        return prefs.getString("supabase_anon_key", null)
    }

    fun setSupabaseAnonKey(key: String) {
        prefs.edit().putString("supabase_anon_key", key.trim()).apply()
    }

    fun getLastSyncTime(): String {
        return prefs.getString("last_sync_time", "2020-01-01T00:00:00Z") ?: "2020-01-01T00:00:00Z"
    }

    fun setLastSyncTime(time: String) {
        prefs.edit().putString("last_sync_time", time).apply()
    }
}
