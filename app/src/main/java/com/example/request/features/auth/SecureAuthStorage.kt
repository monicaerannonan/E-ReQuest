package com.example.request.features.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed AndroidKeyStore AES-256-GCM encrypted key-value storage
 * for local offline student credentials and Option B daily switch tracking.
 */
class SecureAuthStorage(context: Context) {

    companion object {
        private const val TAG = "SecureAuthStorage"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "erequest_student_auth_aes_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREFS_NAME = "erequest_student_secure_auth"
    }

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun putString(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val encrypted = encrypt(value)
        if (encrypted != null) {
            prefs.edit().putString(key, encrypted).apply()
        }
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        val stored = prefs.getString(key, null) ?: return defaultValue
        return decrypt(stored) ?: defaultValue
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val ivEncoded = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataEncoded = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "$ivEncoded:$dataEncoded"
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            null
        }
    }

    private fun decrypt(payload: String): String? {
        return try {
            val separatorIndex = payload.indexOf(':')
            if (separatorIndex <= 0) return null
            val iv = Base64.decode(payload.substring(0, separatorIndex), Base64.NO_WRAP)
            val cipherBytes = Base64.decode(payload.substring(separatorIndex + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            null
        }
    }
}
