package com.example.request.data.sync

import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Universal AES-256-GCM decryptor for encrypted Firestore catalog content
 * (services, transactions, requirements, procedure steps, guidelines, announcements).
 *
 * Interoperable with the Web Crypto API standard:
 * Format: "enc:v1:<base64-iv>:<base64-ciphertext-and-tag>"
 */
object CatalogCryptoUtils {

    private const val TAG = "CatalogCryptoUtils"
    private const val CIPHER_PREFIX = "enc:v1:"
    private const val GCM_TAG_LENGTH_BITS = 128

    // Obfuscated key material: reconstructed at runtime via XOR bitwise unmasking
    // to prevent plaintext seed extraction via DEX/APK decompiler strings while
    // maintaining exact AES-256-GCM interoperability with the Web Admin portal.
    private val KEY_MASK = byteArrayOf(
        0x4A.toByte(), 0x93.toByte(), 0xC1.toByte(), 0x7E.toByte(),
        0x5D.toByte(), 0x2B.toByte(), 0x8F.toByte(), 0x60.toByte()
    )

    private val OBFUSCATED_KEY_BYTES = byteArrayOf(
        0xB1.toByte(), 0x1D.toByte(), 0x3D.toByte(), 0x79.toByte(),
        0xEE.toByte(), 0xDC.toByte(), 0x80.toByte(), 0xB5.toByte(),
        0xF8.toByte(), 0x17.toByte(), 0x63.toByte(), 0xB3.toByte(),
        0xB4.toByte(), 0xD1.toByte(), 0xF6.toByte(), 0xCA.toByte(),
        0x64.toByte(), 0xA4.toByte(), 0x18.toByte(), 0x6A.toByte(),
        0x10.toByte(), 0x5B.toByte(), 0x49.toByte(), 0xE1.toByte(),
        0xF1.toByte(), 0xFC.toByte(), 0xD4.toByte(), 0xC5.toByte(),
        0x5A.toByte(), 0x9A.toByte(), 0xE0.toByte(), 0x24.toByte()
    )

    private val secretKey: SecretKeySpec by lazy {
        val keyBytes = ByteArray(OBFUSCATED_KEY_BYTES.size) { i ->
            (OBFUSCATED_KEY_BYTES[i].toInt() xor KEY_MASK[i % KEY_MASK.size].toInt()).toByte()
        }
        SecretKeySpec(keyBytes, "AES")
    }

    internal fun decodeBase64Safe(input: String): ByteArray {
        return try {
            Base64.decode(input, Base64.DEFAULT)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(input)
        }
    }

    internal fun encodeBase64Safe(input: ByteArray): String {
        return try {
            Base64.encodeToString(input, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(input)
        }
    }

    /**
     * Decrypts an encrypted string if it has the "enc:v1:" prefix.
     * If the string is plain text or null/empty, returns it unchanged.
     */
    @JvmStatic
    fun decrypt(text: String?): String {
        if (text.isNullOrBlank()) {
            return text.orEmpty()
        }
        if (!text.startsWith(CIPHER_PREFIX)) {
            return text // Legacy or unencrypted text
        }

        return try {
            val payload = text.substring(CIPHER_PREFIX.length)
            val parts = payload.split(":")
            if (parts.size != 2) {
                return text
            }

            val ivBytes = decodeBase64Safe(parts[0])
            val cipherAndTagBytes = decodeBase64Safe(parts[1])

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(cipherAndTagBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decrypt catalog string, falling back to original: ${e.message}")
            text
        }
    }

    /**
     * Encrypts a plain text string into "enc:v1:<base64-iv>:<base64-ciphertext-and-tag>"
     * using the shared catalog encryption key.
     */
    @JvmStatic
    fun encrypt(plainText: String?): String {
        if (plainText.isNullOrBlank()) {
            return plainText.orEmpty()
        }
        if (plainText.startsWith(CIPHER_PREFIX)) {
            return plainText
        }
        return try {
            val iv = ByteArray(12)
            java.security.SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val ivBase64 = encodeBase64Safe(iv)
            val cipherBase64 = encodeBase64Safe(cipherBytes)
            "$CIPHER_PREFIX$ivBase64:$cipherBase64"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to encrypt catalog string: ${e.message}")
            plainText
        }
    }
}
