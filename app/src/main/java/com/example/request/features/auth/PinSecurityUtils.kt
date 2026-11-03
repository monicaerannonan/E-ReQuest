package com.example.request.features.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinSecurityUtils {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 65_536
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    private val secureRandom = SecureRandom()

    fun isValidFourDigitPin(pin: String): Boolean {
        return pin.length == 4 && pin.all { it.isDigit() }
    }

    fun isValidSixDigitOtp(otp: String): Boolean {
        return otp.length == 6 && otp.all { it.isDigit() }
    }

    fun generateSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        return bytesToHex(salt)
    }

    fun generateSixDigitOtp(): String {
        val value = secureRandom.nextInt(1_000_000)
        return String.format(Locale.US, "%06d", value)
    }

    fun hashSecret(secret: String, saltHex: String): String {
        val saltBytes = hexToBytes(saltHex)
        val spec = PBEKeySpec(
            secret.toCharArray(),
            saltBytes,
            PBKDF2_ITERATIONS,
            KEY_LENGTH_BITS
        )
        return try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val hashBytes = factory.generateSecret(spec).encoded
            bytesToHex(hashBytes)
        } finally {
            spec.clearPassword()
        }
    }

    fun verifySecret(
        candidateSecret: String,
        saltHex: String,
        expectedHashHex: String
    ): Boolean {
        if (candidateSecret.isBlank() || saltHex.isBlank() || expectedHashHex.isBlank()) {
            return false
        }
        return try {
            val candidateHash = hexToBytes(hashSecret(candidateSecret, saltHex))
            val expectedHash = hexToBytes(expectedHashHex)
            MessageDigest.isEqual(candidateHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    internal fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            result[i * 2] = hexChars[v ushr 4]
            result[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return String(result)
    }

    internal fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Invalid hex string length" }
        return ByteArray(clean.length / 2) { i ->
            val high = Character.digit(clean[i * 2], 16)
            val low = Character.digit(clean[i * 2 + 1], 16)
            require(high >= 0 && low >= 0) { "Invalid hex character" }
            ((high shl 4) or low).toByte()
        }
    }
}
