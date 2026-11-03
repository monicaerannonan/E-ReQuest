package com.example.request

import com.example.request.data.sync.CatalogCryptoUtils
import com.example.request.features.auth.DailyDeviceSwitchPolicy
import com.example.request.features.auth.EmailOtpConfig
import com.example.request.features.auth.PinSecurityUtils
import com.example.request.features.auth.StudentNumberValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentAuthUnitTest {

    @Test
    fun `student number validator accepts all 8 campus codes and 5-digit numbers`() {
        val expectedCampuses = listOf("AU", "BI", "CA", "CO", "SA", "SU", "TA", "XX")
        assertEquals(expectedCampuses, StudentNumberValidator.CAMPUS_CODES)

        for (campus in expectedCampuses) {
            val res = StudentNumberValidator.validateStudentNumberParts(
                campusCode = campus,
                year = "2024",
                digits = "01234",
                currentCalendarYear = 2026
            )
            assertTrue(res is StudentNumberValidator.ValidationResult.Valid)
            assertEquals(
                "$campus-2024-01234",
                (res as StudentNumberValidator.ValidationResult.Valid).formattedStudentNumber
            )
        }
    }

    @Test
    fun `student number validator covers years 2000 through 2040 and rejects invalid digits`() {
        val years = StudentNumberValidator.getAvailableYears(currentCalendarYear = 2026)
        assertEquals("2000", years.first())
        assertEquals("2040", years.last())

        val valid2000 = StudentNumberValidator.validateStudentNumberParts("AU", "2000", "00001", 2026)
        val valid2040 = StudentNumberValidator.validateStudentNumberParts("XX", "2040", "99999", 2026)
        assertTrue(valid2000 is StudentNumberValidator.ValidationResult.Valid)
        assertTrue(valid2040 is StudentNumberValidator.ValidationResult.Valid)

        val tooShort = StudentNumberValidator.validateStudentNumberParts("CA", "2024", "1234", 2026)
        val tooLong = StudentNumberValidator.validateStudentNumberParts("CA", "2024", "123456", 2026)
        val nonNumeric = StudentNumberValidator.validateStudentNumberParts("CA", "2024", "12A45", 2026)
        val invalidCampus = StudentNumberValidator.validateStudentNumberParts("ZZ", "2024", "12345", 2026)
        val invalidYear = StudentNumberValidator.validateStudentNumberParts("CA", "1999", "12345", 2026)

        assertTrue(tooShort is StudentNumberValidator.ValidationResult.Invalid)
        assertTrue(tooLong is StudentNumberValidator.ValidationResult.Invalid)
        assertTrue(nonNumeric is StudentNumberValidator.ValidationResult.Invalid)
        assertTrue(invalidCampus is StudentNumberValidator.ValidationResult.Invalid)
        assertTrue(invalidYear is StudentNumberValidator.ValidationResult.Invalid)
    }

    @Test
    fun `pin security utils hashes and verifies 4-digit PIN and 6-digit OTP`() {
        val pin = "4829"
        assertTrue(PinSecurityUtils.isValidFourDigitPin(pin))
        assertFalse(PinSecurityUtils.isValidFourDigitPin("482"))
        assertFalse(PinSecurityUtils.isValidFourDigitPin("48291"))
        assertFalse(PinSecurityUtils.isValidFourDigitPin("48a9"))

        val salt1 = PinSecurityUtils.generateSalt()
        val salt2 = PinSecurityUtils.generateSalt()
        val hash1 = PinSecurityUtils.hashSecret(pin, salt1)
        val hash2 = PinSecurityUtils.hashSecret(pin, salt2)

        assertNotEquals(hash1, hash2)
        assertTrue(PinSecurityUtils.verifySecret("4829", salt1, hash1))
        assertFalse(PinSecurityUtils.verifySecret("0000", salt1, hash1))

        val otp = PinSecurityUtils.generateSixDigitOtp()
        assertTrue(PinSecurityUtils.isValidSixDigitOtp(otp))
        val otpSalt = PinSecurityUtils.generateSalt()
        val otpHash = PinSecurityUtils.hashSecret(otp, otpSalt)
        assertTrue(PinSecurityUtils.verifySecret(otp, otpSalt, otpHash))
    }

    @Test
    fun `Option B allows up to 3 distinct accounts per device and blocks 4th without daily reset`() {
        val today = "2026-11-02"
        var used = emptySet<String>()

        // Account 1
        val d1 = DailyDeviceSwitchPolicy.evaluateAccountAccess("AU-2024-00001", today, today, used)
        assertTrue(d1 is DailyDeviceSwitchPolicy.SwitchDecision.Allowed)
        used = (d1 as DailyDeviceSwitchPolicy.SwitchDecision.Allowed).updatedUsedStudents
        assertEquals(2, d1.remainingNewAccountSlots)

        // Account 2
        val d2 = DailyDeviceSwitchPolicy.evaluateAccountAccess("BI-2024-00002", today, today, used)
        assertTrue(d2 is DailyDeviceSwitchPolicy.SwitchDecision.Allowed)
        used = (d2 as DailyDeviceSwitchPolicy.SwitchDecision.Allowed).updatedUsedStudents
        assertEquals(1, d2.remainingNewAccountSlots)

        // Account 3
        val d3 = DailyDeviceSwitchPolicy.evaluateAccountAccess("CA-2024-00003", today, today, used)
        assertTrue(d3 is DailyDeviceSwitchPolicy.SwitchDecision.Allowed)
        used = (d3 as DailyDeviceSwitchPolicy.SwitchDecision.Allowed).updatedUsedStudents
        assertEquals(0, d3.remainingNewAccountSlots)

        // Switching back to Account 1 is still ALLOWED
        val switchBack = DailyDeviceSwitchPolicy.evaluateAccountAccess("AU-2024-00001", today, today, used)
        assertTrue(switchBack is DailyDeviceSwitchPolicy.SwitchDecision.Allowed)

        // Account 4 is BLOCKED
        val d4 = DailyDeviceSwitchPolicy.evaluateAccountAccess("CO-2024-00004", today, today, used)
        assertTrue(d4 is DailyDeviceSwitchPolicy.SwitchDecision.DeniedDailyLimit)

        // The limit is per device: the next day does NOT reset it
        val tomorrow = "2026-11-03"
        val nextDayDecision = DailyDeviceSwitchPolicy.evaluateAccountAccess("CO-2024-00004", today, tomorrow, used)
        assertTrue(nextDayDecision is DailyDeviceSwitchPolicy.SwitchDecision.DeniedDailyLimit)

        // Existing accounts can still log in on later days
        val nextDaySwitchBack = DailyDeviceSwitchPolicy.evaluateAccountAccess("BI-2024-00002", today, tomorrow, used)
        assertTrue(nextDaySwitchBack is DailyDeviceSwitchPolicy.SwitchDecision.Allowed)
    }

    @Test
    fun `Option B detects backward system clock tampering`() {
        val lastObserved = 1_800_000_000_000L
        val rolledBack = lastObserved - (30 * 60 * 1000L) // 30 minutes backward

        val decision = DailyDeviceSwitchPolicy.evaluateAccountAccess(
            candidateStudentNumber = "AU-2024-00001",
            recordedDate = "2026-11-02",
            todayDate = "2026-11-02",
            recordedStudents = emptySet(),
            nowMillis = rolledBack,
            lastObservedMillis = lastObserved
        )
        assertTrue(decision is DailyDeviceSwitchPolicy.SwitchDecision.DeniedClockTamper)
    }

    @Test
    fun `pin hash and admin switch counters match firestore security rules constraints`() {
        val salt = PinSecurityUtils.generateSalt()
        val hash = PinSecurityUtils.hashSecret("1234", salt)
        assertEquals(32, salt.length)
        assertEquals(64, hash.length)
        assertTrue(salt.matches(Regex("^[0-9a-fA-F]{32}$")))
        assertTrue(hash.matches(Regex("^[0-9a-fA-F]{64}$")))

        // 0 = before login today, 1 = unang login (1/3), 2 = unang switch (2/3), 3 = limit (3/3)
        assertEquals(0, DailyDeviceSwitchPolicy.toAdminDailySwitchCount(0))
        assertEquals(1, DailyDeviceSwitchPolicy.toAdminDailySwitchCount(1))
        assertEquals(2, DailyDeviceSwitchPolicy.toAdminDailySwitchCount(2))
        assertEquals(3, DailyDeviceSwitchPolicy.toAdminDailySwitchCount(3))
        assertEquals("normal", DailyDeviceSwitchPolicy.toAdminSwitchStatus(0))
        assertEquals("normal", DailyDeviceSwitchPolicy.toAdminSwitchStatus(1))
        assertEquals("switched", DailyDeviceSwitchPolicy.toAdminSwitchStatus(2))
        assertEquals("limit_reached", DailyDeviceSwitchPolicy.toAdminSwitchStatus(3))
    }

    @Test
    fun `getActiveStudentsForToday keeps per-device set regardless of date change`() {
        val yesterday = "2026-11-02"
        val today = "2026-11-03"
        val deviceAccounts = setOf("CA-2023-06131", "CA-2023-02858", "CA-2022-05346")

        val sameDay = DailyDeviceSwitchPolicy.getActiveStudentsForToday(yesterday, yesterday, deviceAccounts)
        assertEquals(3, sameDay.size)
        assertFalse(DailyDeviceSwitchPolicy.canAddNewDistinctAccountToday(yesterday, yesterday, deviceAccounts))
        assertEquals(0, DailyDeviceSwitchPolicy.getRemainingSlotsToday(yesterday, yesterday, deviceAccounts))

        // New day does NOT reset: the 3-account limit is per device
        val newDay = DailyDeviceSwitchPolicy.getActiveStudentsForToday(yesterday, today, deviceAccounts)
        assertEquals(3, newDay.size)
        assertFalse(DailyDeviceSwitchPolicy.canAddNewDistinctAccountToday(yesterday, today, deviceAccounts))
        assertEquals(0, DailyDeviceSwitchPolicy.getRemainingSlotsToday(yesterday, today, deviceAccounts))
    }

    @Test
    fun `CatalogCryptoUtils encrypt and decrypt roundtrip matches plaintext and bypasses non-encrypted text`() {
        val originalText = "Official ICCT Transcript of Records & Diploma"
        val encrypted = CatalogCryptoUtils.encrypt(originalText)
        assertTrue(encrypted.startsWith("enc:v1:"))
        assertNotEquals(originalText, encrypted)

        val decrypted = CatalogCryptoUtils.decrypt(encrypted)
        assertEquals(originalText, decrypted)

        // Non-encrypted plaintext bypass
        assertEquals("Plaintext string", CatalogCryptoUtils.decrypt("Plaintext string"))
        assertEquals("", CatalogCryptoUtils.decrypt(""))
        assertEquals("", CatalogCryptoUtils.decrypt(null))
    }

    @Test
    fun `encrypted student email in database decrypts cleanly for OTP validation and display`() {
        val rawEmail = "student.portal@gmail.com"
        val encryptedInDb = CatalogCryptoUtils.encrypt(rawEmail)

        assertTrue(encryptedInDb.startsWith("enc:v1:"))
        assertTrue(encryptedInDb.matches(Regex("^enc:v1:[A-Za-z0-9+/=]+:[A-Za-z0-9+/=]+$")))

        // Decrypting from Firestore restores exact email for OTP sending and UI display
        val decryptedForStudent = CatalogCryptoUtils.decrypt(encryptedInDb)
        assertEquals(rawEmail, decryptedForStudent)
        assertTrue(StudentNumberValidator.isValidEmail(encryptedInDb))
        assertTrue(StudentNumberValidator.isValidEmail(decryptedForStudent))
        assertEquals(
            StudentNumberValidator.maskEmail(rawEmail),
            StudentNumberValidator.maskEmail(encryptedInDb)
        )
    }

    @Test
    fun `CatalogCryptoUtils does not declare plaintext catalog seed constant in class fields`() {
        val fields = CatalogCryptoUtils::class.java.declaredFields
        for (field in fields) {
            field.isAccessible = true
            val name = field.name
            assertFalse("Seed field should not be named CATALOG_ENCRYPTION_SEED", name.contains("CATALOG_ENCRYPTION_SEED"))
            if (field.type == String::class.java) {
                val value = field.get(CatalogCryptoUtils) as? String
                assertFalse(
                    "Plaintext seed must not be stored in String constant",
                    value?.contains("erequest_production_catalog_aes_gcm_seed_2026") == true
                )
            }
        }
    }

    @Test
    fun `PIN and OTP rate limiting configurations match security requirements`() {
        assertEquals(5, DailyDeviceSwitchPolicy.MAX_FAILED_PIN_ATTEMPTS)
        assertEquals(5 * 60 * 1000L, DailyDeviceSwitchPolicy.PIN_LOCKOUT_DURATION_MS)
        assertEquals(5, EmailOtpConfig.MAX_OTP_VERIFY_ATTEMPTS)
        assertEquals(5 * 60 * 1000L, EmailOtpConfig.OTP_LOCKOUT_DURATION_MS)

        // Escalating lockout durations: 1st = 5m, 2nd = 15m, 3rd = 30m
        val baseMs = DailyDeviceSwitchPolicy.PIN_LOCKOUT_DURATION_MS
        val lockout1 = baseMs * 1L
        val lockout2 = baseMs * 3L
        val lockout3 = baseMs * 6L
        assertEquals(300_000L, lockout1)  // 5 mins
        assertEquals(900_000L, lockout2)  // 15 mins
        assertEquals(1_800_000L, lockout3) // 30 mins
    }
}

