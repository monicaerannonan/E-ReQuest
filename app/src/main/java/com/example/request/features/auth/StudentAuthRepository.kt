package com.example.request.features.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.request.data.sync.CatalogCryptoUtils
import com.example.request.data.sync.ERequestBackgroundSyncWorker
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class OtpPurpose(val label: String) {
    REGISTER("Account Registration"),
    EXISTING_ACCOUNT_LOGIN("Device Login Verification"),
    RESET_PIN("Forgot PIN Reset")
}

sealed class OtpRequestResult {
    data class Sent(
        val studentNumber: String,
        val email: String,
        val maskedEmail: String,
        val devPreviewOtp: String? = null,
        val cooldownSeconds: Int = 60
    ) : OtpRequestResult()

    data class CooldownActive(val remainingSeconds: Long) : OtpRequestResult()
    data class Error(val message: String) : OtpRequestResult()
}

sealed class OtpVerifyResult {
    data class VerifiedForRegistration(
        val studentNumber: String,
        val email: String
    ) : OtpVerifyResult()

    data class VerifiedExistingStudent(
        val studentNumber: String,
        val email: String,
        val pinHash: String,
        val pinSalt: String
    ) : OtpVerifyResult()

    data class VerifiedForPinReset(
        val studentNumber: String,
        val email: String
    ) : OtpVerifyResult()

    data class Error(val message: String) : OtpVerifyResult()
}

sealed class AuthActionResult {
    data class Success(val account: SavedStudentAccount) : AuthActionResult()
    data class Error(val message: String) : AuthActionResult()
}

class StudentAuthRepository(context: Context) {

    companion object {
        private const val TAG = "StudentAuthRepository"

        const val COLLECTION_STUDENTS = "students"
        const val COLLECTION_STUDENT_ACTIVITY_LOGS = "student_activity_logs"
    }

    private val appContext = context.applicationContext
    private val firestore = FirebaseFirestore.getInstance()
    val sessionManager = StudentSessionManager(appContext)

    fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // =====================================================
    // STEP 1A: REQUEST OTP FOR NEW REGISTRATION
    // =====================================================

    suspend fun requestRegistrationOtp(
        studentNumber: String,
        email: String
    ): OtpRequestResult = withContext(Dispatchers.IO) {
        val normalizedStudent = studentNumber.trim().uppercase()
        val normalizedEmail = CatalogCryptoUtils.decrypt(email).trim().lowercase()

        if (!StudentNumberValidator.isValidEmail(normalizedEmail)) {
            return@withContext OtpRequestResult.Error("Please enter a valid email address.")
        }

        if (!isOnline()) {
            return@withContext OtpRequestResult.Error(
                "An internet connection is required to register and receive an Email OTP."
            )
        }

        // Check Firestore student_activity_logs to enforce 3/3 PER DEVICE limit (survives reinstallation!)
        val deviceInfo = DeviceInfoProvider.getDeviceInfo(appContext)
        try {
            val deviceLogDoc = firestore
                .collection(COLLECTION_STUDENT_ACTIVITY_LOGS)
                .document(deviceInfo.deviceId)
                .get(Source.SERVER)
                .await()

            if (deviceLogDoc.exists()) {
                val deviceStudents = extractDeviceStudents(deviceLogDoc)
                sessionManager.mergeRemoteDailyStudents(null, deviceStudents)

                val isAlreadyOnDevice = deviceStudents.any { it.equals(normalizedStudent, ignoreCase = true) }
                if (!isAlreadyOnDevice && deviceStudents.size >= DailyDeviceSwitchPolicy.MAX_DISTINCT_ACCOUNTS_PER_DAY) {
                    return@withContext OtpRequestResult.Error(
                        "Device limit reached (3/3). This device already has 3 student accounts registered."
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Device activity log check warning: ${e.message}", e)
        }

        // Enforce Local Policy (Max 3 distinct student accounts on this device)
        when (val limitCheck = sessionManager.evaluateDailySwitchPolicy(normalizedStudent)) {
            is DailyDeviceSwitchPolicy.SwitchDecision.DeniedDailyLimit -> {
                return@withContext OtpRequestResult.Error(limitCheck.message)
            }
            is DailyDeviceSwitchPolicy.SwitchDecision.DeniedClockTamper -> {
                return@withContext OtpRequestResult.Error(limitCheck.message)
            }
            is DailyDeviceSwitchPolicy.SwitchDecision.Allowed -> Unit
        }

        // Check local saved accounts first
        val alreadyLocal = sessionManager.getSavedAccounts().firstOrNull {
            it.studentNumber.equals(normalizedStudent, ignoreCase = true)
        }
        if (alreadyLocal != null) {
            return@withContext OtpRequestResult.Error(
                "$normalizedStudent is already saved on this device. Please use PIN Login or Switch Account."
            )
        }

        // Check Firestore students/{studentNumber} if accessible
        try {
            val existingDoc = firestore
                .collection(COLLECTION_STUDENTS)
                .document(normalizedStudent)
                .get(Source.SERVER)
                .await()

            if (existingDoc.exists() && !existingDoc.getString("pinHash").isNullOrBlank()) {
                return@withContext OtpRequestResult.Error(
                    "Student Number $normalizedStudent is already registered. Please select the 'Existing Account' tab to log in."
                )
            }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.w(TAG, "Firestore students lookup skipped (rules pending); proceeding with Email OTP.", e)
            } else {
                Log.w(TAG, "Firestore students check warning: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore students lookup warning: ${e.message}", e)
        }

        generateSaveAndSendOtp(
            studentNumber = normalizedStudent,
            email = normalizedEmail,
            purpose = OtpPurpose.REGISTER
        )
    }

    // =====================================================
    // STEP 1B: REQUEST OTP FOR EXISTING REGISTERED ACCOUNT
    // =====================================================

    suspend fun requestExistingAccountOtp(
        studentNumber: String
    ): OtpRequestResult = withContext(Dispatchers.IO) {
        val normalizedStudent = studentNumber.trim().uppercase()

        if (!isOnline()) {
            return@withContext OtpRequestResult.Error(
                "An internet connection is required to add an account to this device."
            )
        }

        // Check local saved accounts first
        val alreadyLocal = sessionManager.getSavedAccounts().firstOrNull {
            it.studentNumber.equals(normalizedStudent, ignoreCase = true)
        }
        if (alreadyLocal != null) {
            return@withContext OtpRequestResult.Error(
                "$normalizedStudent is already saved on this device. Please use PIN Login or Switch Account."
            )
        }

        val deviceInfo = DeviceInfoProvider.getDeviceInfo(appContext)

        val deviceLogDoc = try {
            firestore
                .collection(COLLECTION_STUDENT_ACTIVITY_LOGS)
                .document(deviceInfo.deviceId)
                .get(Source.SERVER)
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Device activity log check warning: ${e.message}", e)
            null
        }

        val activityLoggedNumbers = if (deviceLogDoc != null && deviceLogDoc.exists()) {
            val rawLogged = (deviceLogDoc.get("loggedStudentNumbers") as? List<*>)
                ?.mapNotNull { it?.toString()?.trim()?.uppercase() }
                .orEmpty()
            val rawDaily = (deviceLogDoc.get("dailyStudentNumbers") as? List<*>)
                ?.mapNotNull { it?.toString()?.trim()?.uppercase() }
                .orEmpty()
            val primaryStudent = deviceLogDoc.getString("studentNumber")?.trim()?.uppercase().orEmpty()
            val currentStudent = deviceLogDoc.getString("currentStudentNumber")?.trim()?.uppercase().orEmpty()
            (rawLogged + rawDaily + listOf(primaryStudent, currentStudent))
                .filter { it.isNotBlank() }
                .distinct()
        } else {
            emptyList()
        }
        val isRecordedInActivityLog = activityLoggedNumbers.any {
            it.equals(normalizedStudent, ignoreCase = true)
        }

        // Verify that the account exists and is active in Firestore
        val studentDoc = try {
            firestore
                .collection(COLLECTION_STUDENTS)
                .document(normalizedStudent)
                .get(Source.SERVER)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting existing account OTP", e)
            return@withContext OtpRequestResult.Error(
                "Could not find student account on the server (${e.localizedMessage ?: "error"})."
            )
        }

        val hasStudentRecord = studentDoc.exists() && !studentDoc.getString("pinHash").isNullOrBlank()
        if (!hasStudentRecord && !isRecordedInActivityLog) {
            return@withContext OtpRequestResult.Error(
                "$normalizedStudent is not yet registered. Please select the 'Register' tab to create an account."
            )
        }

        if (studentDoc.exists() && studentDoc.getString("status") == "blocked") {
            return@withContext OtpRequestResult.Error(
                "This student account is blocked. Please contact the campus Administrator."
            )
        }

        val rawEmail = studentDoc.getString("email")?.trim().orEmpty()
        var registeredEmail = CatalogCryptoUtils.decrypt(rawEmail).trim().lowercase()

        if ((registeredEmail.isBlank() || !StudentNumberValidator.isValidEmail(registeredEmail)) && isRecordedInActivityLog) {
            // Recover email from local saved accounts on this device or sibling accounts in student_activity_logs
            val localFallbackEmail = sessionManager.getSavedAccounts()
                .map { CatalogCryptoUtils.decrypt(it.email).trim().lowercase() }
                .firstOrNull { StudentNumberValidator.isValidEmail(it) }
                .orEmpty()

            if (localFallbackEmail.isNotBlank()) {
                registeredEmail = localFallbackEmail
            } else {
                for (siblingId in activityLoggedNumbers) {
                    if (siblingId.equals(normalizedStudent, ignoreCase = true)) continue
                    val siblingEmail = try {
                        firestore.collection(COLLECTION_STUDENTS)
                            .document(siblingId)
                            .get(Source.SERVER)
                            .await()
                            .getString("email")
                            ?.let { CatalogCryptoUtils.decrypt(it).trim().lowercase() }
                            .orEmpty()
                    } catch (_: Exception) {
                        ""
                    }
                    if (StudentNumberValidator.isValidEmail(siblingEmail)) {
                        registeredEmail = siblingEmail
                        break
                    }
                }
            }
        }

        if (registeredEmail.isBlank() || !StudentNumberValidator.isValidEmail(registeredEmail)) {
            return@withContext OtpRequestResult.Error(
                "No valid email is linked to this student account."
            )
        }

        // Enforce the 3-account PER DEVICE limit from Firestore (survives reinstallation, never resets daily)
        if (deviceLogDoc != null && deviceLogDoc.exists()) {
            val deviceStudents = extractDeviceStudents(deviceLogDoc)
            sessionManager.mergeRemoteDailyStudents(null, deviceStudents)

            val isAlreadyOnDevice = deviceStudents.any { it.equals(normalizedStudent, ignoreCase = true) }
            if (!isAlreadyOnDevice && deviceStudents.size >= DailyDeviceSwitchPolicy.MAX_DISTINCT_ACCOUNTS_PER_DAY) {
                return@withContext OtpRequestResult.Error(
                    "Device limit reached (3/3). This device already has 3 student accounts registered."
                )
            }
        }

        // Enforce Local Daily Switch Policy
        when (val limitCheck = sessionManager.evaluateDailySwitchPolicy(normalizedStudent)) {
            is DailyDeviceSwitchPolicy.SwitchDecision.DeniedDailyLimit -> {
                return@withContext OtpRequestResult.Error(limitCheck.message)
            }
            is DailyDeviceSwitchPolicy.SwitchDecision.DeniedClockTamper -> {
                return@withContext OtpRequestResult.Error(limitCheck.message)
            }
            is DailyDeviceSwitchPolicy.SwitchDecision.Allowed -> Unit
        }

        return@withContext generateSaveAndSendOtp(
            studentNumber = normalizedStudent,
            email = registeredEmail,
            purpose = OtpPurpose.EXISTING_ACCOUNT_LOGIN
        )
    }

    // =====================================================
    // STEP 1C: REQUEST OTP FOR FORGOT 4-DIGIT PIN
    // =====================================================

    suspend fun requestPinResetOtp(
        studentNumber: String
    ): OtpRequestResult = withContext(Dispatchers.IO) {
        val normalizedStudent = studentNumber.trim().uppercase()

        if (!isOnline()) {
            return@withContext OtpRequestResult.Error(
                "An internet connection is required to reset your 4-digit PIN via Email OTP."
            )
        }

        val localEmail = sessionManager.getSavedAccounts()
            .firstOrNull { it.studentNumber.equals(normalizedStudent, ignoreCase = true) }
            ?.email
            ?.let { CatalogCryptoUtils.decrypt(it).trim().lowercase() }

        val serverEmail = try {
            firestore.collection(COLLECTION_STUDENTS)
                .document(normalizedStudent)
                .get(Source.SERVER)
                .await()
                .getString("email")
                ?.let { CatalogCryptoUtils.decrypt(it).trim().lowercase() }
        } catch (_: Exception) {
            null
        }

        val email = serverEmail?.takeIf { StudentNumberValidator.isValidEmail(it) }
            ?: localEmail?.takeIf { StudentNumberValidator.isValidEmail(it) }
        if (email.isNullOrBlank()) {
            return@withContext OtpRequestResult.Error(
                "Could not find registered email for $normalizedStudent."
            )
        }

        generateSaveAndSendOtp(
            studentNumber = normalizedStudent,
            email = email,
            purpose = OtpPurpose.RESET_PIN
        )
    }

    private suspend fun generateSaveAndSendOtp(
        studentNumber: String,
        email: String,
        purpose: OtpPurpose
    ): OtpRequestResult {
        val cleanEmail = CatalogCryptoUtils.decrypt(email).trim().lowercase()
        val nowMillis = System.currentTimeMillis()
        val lockoutRemainingSec = sessionManager.getOtpLockoutRemainingSeconds(studentNumber, nowMillis)
        if (lockoutRemainingSec > 0L) {
            return OtpRequestResult.CooldownActive(lockoutRemainingSec)
        }

        val existingSession = sessionManager.getOtpSession(studentNumber)
        if (existingSession != null &&
            existingSession.resendAvailableAtMillis > nowMillis &&
            StudentNumberValidator.isValidEmail(existingSession.email)
        ) {
            val waitSec = ((existingSession.resendAvailableAtMillis - nowMillis) + 999L) / 1000L
            return OtpRequestResult.CooldownActive(waitSec)
        }

        val otpCode = PinSecurityUtils.generateSixDigitOtp()
        val otpSalt = PinSecurityUtils.generateSalt()
        val otpHash = PinSecurityUtils.hashSecret(otpCode, otpSalt)
        val expiresAtMillis = nowMillis + EmailOtpConfig.OTP_VALIDITY_DURATION_MS

        return when (
            val delivery = OtpEmailSender.sendOtpEmail(
                recipientEmail = cleanEmail,
                studentNumber = studentNumber,
                otpCode = otpCode,
                purposeLabel = purpose.label,
                expiresAtMillis = expiresAtMillis
            )
        ) {
            is OtpDeliveryResult.SentViaEmail -> {
                sessionManager.saveOtpSession(
                    StoredOtpSession(
                        studentNumber = studentNumber,
                        email = cleanEmail,
                        otpHash = otpHash,
                        otpSalt = otpSalt,
                        purpose = purpose.name,
                        attempts = 0,
                        expiresAtMillis = expiresAtMillis,
                        resendAvailableAtMillis = nowMillis + EmailOtpConfig.OTP_RESEND_COOLDOWN_MS
                    )
                )
                OtpRequestResult.Sent(
                    studentNumber = studentNumber,
                    email = cleanEmail,
                    maskedEmail = StudentNumberValidator.maskEmail(cleanEmail),
                    devPreviewOtp = null
                )
            }
            is OtpDeliveryResult.DevPreviewMode -> {
                sessionManager.saveOtpSession(
                    StoredOtpSession(
                        studentNumber = studentNumber,
                        email = cleanEmail,
                        otpHash = otpHash,
                        otpSalt = otpSalt,
                        purpose = purpose.name,
                        attempts = 0,
                        expiresAtMillis = expiresAtMillis,
                        resendAvailableAtMillis = nowMillis + EmailOtpConfig.OTP_RESEND_COOLDOWN_MS
                    )
                )
                OtpRequestResult.Sent(
                    studentNumber = studentNumber,
                    email = cleanEmail,
                    maskedEmail = StudentNumberValidator.maskEmail(cleanEmail),
                    devPreviewOtp = delivery.otpCode
                )
            }
            is OtpDeliveryResult.Failed -> OtpRequestResult.Error(delivery.reason)
        }
    }

    // =====================================================
    // STEP 2: VERIFY 6-DIGIT EMAIL OTP
    // =====================================================

    suspend fun verifyOtp(
        studentNumber: String,
        enteredOtp: String,
        expectedPurpose: OtpPurpose
    ): OtpVerifyResult = withContext(Dispatchers.IO) {
        val normalizedStudent = studentNumber.trim().uppercase()
        val cleanOtp = enteredOtp.trim()

        if (!PinSecurityUtils.isValidSixDigitOtp(cleanOtp)) {
            return@withContext OtpVerifyResult.Error("Please enter the exact 6-digit OTP code.")
        }

        val session = sessionManager.getOtpSession(normalizedStudent)
            ?: return@withContext OtpVerifyResult.Error(
                "No active OTP session found. Please tap 'Resend OTP'."
            )

        if (session.purpose != expectedPurpose.name) {
            return@withContext OtpVerifyResult.Error(
                "OTP session mismatch. Please request a new OTP."
            )
        }

        val nowMillis = System.currentTimeMillis()
        val lockoutRemainingSec = sessionManager.getOtpLockoutRemainingSeconds(normalizedStudent, nowMillis)
        if (lockoutRemainingSec > 0L) {
            val mins = (lockoutRemainingSec + 59L) / 60L
            return@withContext OtpVerifyResult.Error(
                "Too many incorrect attempts. Account is temporarily locked. Please wait $mins minute(s) before trying again."
            )
        }

        if (nowMillis > session.expiresAtMillis) {
            sessionManager.clearOtpSession()
            return@withContext OtpVerifyResult.Error(
                "OTP code has expired (5 mins). Please tap 'Resend OTP'."
            )
        }

        if (session.attempts >= EmailOtpConfig.MAX_OTP_VERIFY_ATTEMPTS) {
            sessionManager.clearOtpSession()
            sessionManager.recordOtpLockout(normalizedStudent, nowMillis = nowMillis)
            return@withContext OtpVerifyResult.Error(
                "Too many incorrect attempts. Please wait 5 minutes before requesting a new OTP."
            )
        }

        val isValid = PinSecurityUtils.verifySecret(cleanOtp, session.otpSalt, session.otpHash)
        if (!isValid) {
            val nextAttempts = session.attempts + 1
            if (nextAttempts >= EmailOtpConfig.MAX_OTP_VERIFY_ATTEMPTS) {
                sessionManager.clearOtpSession()
                sessionManager.recordOtpLockout(normalizedStudent, nowMillis = nowMillis)
                return@withContext OtpVerifyResult.Error(
                    "Too many incorrect attempts (5/5). Account temporarily locked for 5 minutes for security."
                )
            }
            sessionManager.saveOtpSession(session.copy(attempts = nextAttempts))
            val remaining = EmailOtpConfig.MAX_OTP_VERIFY_ATTEMPTS - nextAttempts
            return@withContext OtpVerifyResult.Error(
                "Incorrect OTP code. You have $remaining attempt(s) remaining."
            )
        }

        // OTP matched — consume session and clear any lockout
        sessionManager.clearOtpSession()
        sessionManager.clearOtpLockout(normalizedStudent)
        val cleanSessionEmail = CatalogCryptoUtils.decrypt(session.email).trim().lowercase()

        when (expectedPurpose) {
            OtpPurpose.REGISTER -> OtpVerifyResult.VerifiedForRegistration(
                studentNumber = normalizedStudent,
                email = cleanSessionEmail
            )
            OtpPurpose.RESET_PIN -> OtpVerifyResult.VerifiedForPinReset(
                studentNumber = normalizedStudent,
                email = cleanSessionEmail
            )
            OtpPurpose.EXISTING_ACCOUNT_LOGIN -> {
                try {
                    val studentDoc = firestore
                        .collection(COLLECTION_STUDENTS)
                        .document(normalizedStudent)
                        .get(Source.SERVER)
                        .await()

                    val pinHash = studentDoc.getString("pinHash").orEmpty()
                    val pinSalt = studentDoc.getString("pinSalt").orEmpty()
                    val resolvedEmail = studentDoc.getString("email")
                        ?.let { CatalogCryptoUtils.decrypt(it).trim().lowercase() }
                        ?.takeIf { StudentNumberValidator.isValidEmail(it) }
                        ?: cleanSessionEmail
                    if (pinHash.isBlank() || pinSalt.isBlank()) {
                        OtpVerifyResult.VerifiedForRegistration(normalizedStudent, resolvedEmail)
                    } else {
                        completeExistingStudentDeviceEnrollment(
                            studentNumber = normalizedStudent,
                            email = resolvedEmail,
                            pinHash = pinHash,
                            pinSalt = pinSalt
                        )
                        OtpVerifyResult.VerifiedExistingStudent(
                            studentNumber = normalizedStudent,
                            email = resolvedEmail,
                            pinHash = pinHash,
                            pinSalt = pinSalt
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading existing student profile after OTP", e)
                    OtpVerifyResult.Error("Could not load your student profile from the server.")
                }
            }
        }
    }

    // =====================================================
    // STEP 3: SAVE 4-DIGIT PIN (REGISTRATION OR PIN RESET)
    // =====================================================

    suspend fun completePinSetupAndLogin(
        studentNumber: String,
        email: String,
        fourDigitPin: String,
        isPinReset: Boolean
    ): AuthActionResult = withContext(Dispatchers.IO) {
        val normalizedStudent = studentNumber.trim().uppercase()
        val normalizedEmail = CatalogCryptoUtils.decrypt(email).trim().lowercase()

        if (!PinSecurityUtils.isValidFourDigitPin(fourDigitPin)) {
            return@withContext AuthActionResult.Error("PIN must be an exact 4-digit number (0-9).")
        }

        when (val limitCheck = sessionManager.evaluateDailySwitchPolicy(normalizedStudent)) {
            is DailyDeviceSwitchPolicy.SwitchDecision.DeniedDailyLimit -> {
                return@withContext AuthActionResult.Error(limitCheck.message)
            }
            is DailyDeviceSwitchPolicy.SwitchDecision.DeniedClockTamper -> {
                return@withContext AuthActionResult.Error(limitCheck.message)
            }
            is DailyDeviceSwitchPolicy.SwitchDecision.Allowed -> Unit
        }

        val pinSalt = PinSecurityUtils.generateSalt()
        val pinHash = PinSecurityUtils.hashSecret(fourDigitPin, pinSalt)
        val deviceInfo = DeviceInfoProvider.getDeviceInfo(appContext)
        val nowMillis = System.currentTimeMillis()

        // 1. Save locally in AndroidKeyStore-encrypted storage for instant Offline 4-Digit PIN Login
        val localAccount = sessionManager.upsertAccountAndActivate(
            studentNumber = normalizedStudent,
            email = normalizedEmail,
            pinHash = pinHash,
            pinSalt = pinSalt,
            nowMillis = nowMillis
        )
        val openCount = sessionManager.incrementAndGetDeviceOpenCount()

        // 2. Sync Student Record & Device Activity Log to Firestore for Admin
        if (isOnline()) {
            var needsOfflineSyncRetry = false
            try {
                recordAdminStudentActivityLog(
                    activeStudentNumber = normalizedStudent,
                    deviceInfo = deviceInfo,
                    openCount = openCount,
                    nowMillis = nowMillis
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not sync student_activity_logs: ${e.message}", e)
                needsOfflineSyncRetry = true
            }

            try {
                upsertStudentDocumentInFirestore(
                    studentNumber = normalizedStudent,
                    email = normalizedEmail,
                    pinHash = pinHash,
                    pinSalt = pinSalt,
                    deviceInfo = deviceInfo,
                    isPinReset = isPinReset
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not sync student profile to Firestore: ${e.message}", e)
                needsOfflineSyncRetry = true
            }

            if (needsOfflineSyncRetry) {
                queueOfflineAuditAndScheduleSync(normalizedStudent, "PIN_SETUP", nowMillis)
            }
        } else {
            queueOfflineAuditAndScheduleSync(normalizedStudent, "PIN_SETUP", nowMillis)
        }

        AuthActionResult.Success(localAccount)
    }

    private suspend fun completeExistingStudentDeviceEnrollment(
        studentNumber: String,
        email: String,
        pinHash: String,
        pinSalt: String
    ): AuthActionResult {
        val nowMillis = System.currentTimeMillis()
        val deviceInfo = DeviceInfoProvider.getDeviceInfo(appContext)
        val cleanEmail = CatalogCryptoUtils.decrypt(email).trim().lowercase()

        val localAccount = sessionManager.upsertAccountAndActivate(
            studentNumber = studentNumber,
            email = cleanEmail,
            pinHash = pinHash,
            pinSalt = pinSalt,
            nowMillis = nowMillis
        )
        val openCount = sessionManager.incrementAndGetDeviceOpenCount()

        try {
            recordAdminStudentActivityLog(
                activeStudentNumber = studentNumber,
                deviceInfo = deviceInfo,
                openCount = openCount,
                nowMillis = nowMillis
            )
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal warning updating student_activity_logs: ${e.message}")
            queueOfflineAuditAndScheduleSync(studentNumber, "EXISTING_LOGIN", nowMillis)
        }

        try {
            firestore.collection(COLLECTION_STUDENTS)
                .document(studentNumber)
                .update(
                    mapOf(
                        "deviceId" to deviceInfo.deviceId,
                        "deviceName" to CatalogCryptoUtils.encrypt(deviceInfo.deviceName),
                        "osVersion" to CatalogCryptoUtils.encrypt(deviceInfo.osVersion),
                        "lastLoginAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        } catch (e: Exception) {
            try {
                upsertStudentDocumentInFirestore(
                    studentNumber = studentNumber,
                    email = cleanEmail,
                    pinHash = pinHash,
                    pinSalt = pinSalt,
                    deviceInfo = deviceInfo,
                    isPinReset = false
                )
            } catch (inner: Exception) {
                Log.w(TAG, "Non-fatal warning updating student device info: ${inner.message}")
            }
        }

        return AuthActionResult.Success(localAccount)
    }

    // =====================================================
    // QUICK 4-DIGIT PIN LOGIN (WORKS ONLINE & OFFLINE!)
    // =====================================================

    suspend fun loginWithFourDigitPin(
        studentNumber: String,
        enteredPin: String
    ): LocalPinVerifyResult = withContext(Dispatchers.IO) {
        val normalizedStudent = studentNumber.trim().uppercase()

        val verifyResult = sessionManager.verifyPinLocally(
            studentNumber = normalizedStudent,
            enteredPin = enteredPin
        )

        if (verifyResult is LocalPinVerifyResult.Success) {
            val nowMillis = System.currentTimeMillis()
            val deviceInfo = DeviceInfoProvider.getDeviceInfo(appContext)
            val openCount = sessionManager.incrementAndGetDeviceOpenCount()

            if (isOnline()) {
                try {
                    recordAdminStudentActivityLog(
                        activeStudentNumber = normalizedStudent,
                        deviceInfo = deviceInfo,
                        openCount = openCount,
                        nowMillis = nowMillis
                    )
                    syncPendingOfflineLogs()
                } catch (e: Exception) {
                    Log.w(TAG, "Deferred student_activity_logs update to background sync", e)
                    queueOfflineAuditAndScheduleSync(normalizedStudent, "PIN_LOGIN", nowMillis)
                }

                try {
                    firestore.collection(COLLECTION_STUDENTS)
                        .document(normalizedStudent)
                        .update(
                            mapOf(
                                "deviceId" to deviceInfo.deviceId,
                                "deviceName" to CatalogCryptoUtils.encrypt(deviceInfo.deviceName),
                                "osVersion" to CatalogCryptoUtils.encrypt(deviceInfo.osVersion),
                                "lastLoginAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .await()
                } catch (_: Exception) {
                    try {
                        upsertStudentDocumentInFirestore(
                            studentNumber = normalizedStudent,
                            email = verifyResult.account.email,
                            pinHash = verifyResult.account.pinHash,
                            pinSalt = verifyResult.account.pinSalt,
                            deviceInfo = deviceInfo,
                            isPinReset = false
                        )
                    } catch (_: Exception) {
                        // Non-blocking
                    }
                }
            } else {
                queueOfflineAuditAndScheduleSync(normalizedStudent, "OFFLINE_PIN_LOGIN", nowMillis)
            }
        }

        verifyResult
    }

    private fun queueOfflineAuditAndScheduleSync(
        studentNumber: String,
        eventType: String,
        nowMillis: Long
    ) {
        sessionManager.enqueueOfflineAuditEvent(
            studentNumber = studentNumber,
            eventType = eventType,
            nowMillis = nowMillis
        )
        ERequestBackgroundSyncWorker.enqueueImmediateOnlineSync(appContext)
    }

    // =====================================================
    // FIRESTORE WRITERS MATCHING ADMIN PORTAL & RULES SCHEMA
    // =====================================================

    private suspend fun upsertStudentDocumentInFirestore(
        studentNumber: String,
        email: String,
        pinHash: String,
        pinSalt: String,
        deviceInfo: StudentDeviceInfo,
        isPinReset: Boolean
    ) {
        val parts = studentNumber.split("-")
        val campusCode = parts.getOrNull(0).orEmpty()
        val enrollmentYear = parts.getOrNull(1).orEmpty()
        val studentDigits = parts.getOrNull(2).orEmpty()
        val cleanEmail = CatalogCryptoUtils.decrypt(email).trim().lowercase()
        val encryptedEmail = CatalogCryptoUtils.encrypt(cleanEmail)

        val docRef = firestore.collection(COLLECTION_STUDENTS).document(studentNumber)
        if (isPinReset) {
            val existing = try {
                docRef.get(Source.SERVER).await()
            } catch (_: Exception) {
                null
            }
            if (existing != null && existing.exists()) {
                docRef.update(
                    mapOf(
                        "pinHash" to pinHash,
                        "pinSalt" to pinSalt,
                        "deviceId" to deviceInfo.deviceId,
                        "deviceName" to CatalogCryptoUtils.encrypt(deviceInfo.deviceName),
                        "osVersion" to CatalogCryptoUtils.encrypt(deviceInfo.osVersion),
                        "lastLoginAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                return
            }
        }

        val payload = hashMapOf<String, Any>(
            "studentNumber" to studentNumber,
            "campusCode" to campusCode,
            "enrollmentYear" to enrollmentYear,
            "studentDigits" to studentDigits,
            "email" to encryptedEmail,
            "pinHash" to pinHash,
            "pinSalt" to pinSalt,
            "status" to "active",
            "deviceId" to deviceInfo.deviceId,
            "deviceName" to CatalogCryptoUtils.encrypt(deviceInfo.deviceName),
            "osVersion" to CatalogCryptoUtils.encrypt(deviceInfo.osVersion),
            "registeredAt" to FieldValue.serverTimestamp(),
            "lastLoginAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
    }

    /**
     * Writes directly to `student_activity_logs/{installationId}` matching the exact
     * Admin Portal (`StudentActivityLogsPage.jsx`) and `firestore.rules` schema!
     */
    private suspend fun recordAdminStudentActivityLog(
        activeStudentNumber: String,
        deviceInfo: StudentDeviceInfo,
        openCount: Int,
        nowMillis: Long
    ) {
        val normalizedActive = activeStudentNumber.trim().uppercase()
        val initialStudentNumber = sessionManager.getOrSetInitialDeviceStudentNumber(normalizedActive)
        val switchDate = DailyDeviceSwitchPolicy.getCurrentDateKey(nowMillis)
        val orderedLoggedStudents = sessionManager.getOrderedLoggedStudentNumbers(normalizedActive)

        // Fetch existing remote document to merge history if user reinstalled app
        val existingDoc = try {
            firestore.collection(COLLECTION_STUDENT_ACTIVITY_LOGS)
                .document(deviceInfo.deviceId)
                .get(Source.SERVER)
                .await()
        } catch (_: Exception) {
            null
        }

        val remoteLogged = (existingDoc?.get("loggedStudentNumbers") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim()?.uppercase() }
            .orEmpty()
        val remoteInitial = existingDoc?.getString("studentNumber")?.trim()?.uppercase()

        // The 3-account limit is PER DEVICE (no daily reset): keep every account known for this device
        val remoteDaily = extractDeviceStudents(existingDoc)

        val localDaily = sessionManager.getDailyUsedStudentsToday(nowMillis)

        // Merge daily students active today (max 3)
        val mergedDaily = (listOf(normalizedActive) + localDaily + remoteDaily)
            .filter { it.isNotBlank() }
            .distinct()
            .take(DailyDeviceSwitchPolicy.MAX_DISTINCT_ACCOUNTS_PER_DAY)

        // Merge all-time logged student numbers (up to 10)
        val mergedLogged = (listOf(normalizedActive) + orderedLoggedStudents + remoteLogged)
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)

        val persistentInitial = if (!remoteInitial.isNullOrBlank()) remoteInitial else initialStudentNumber
        val dailySwitchCount = DailyDeviceSwitchPolicy.toAdminDailySwitchCount(mergedDaily.size)
        val status = DailyDeviceSwitchPolicy.toAdminSwitchStatus(dailySwitchCount)

        val remoteOpenCount = existingDoc?.getLong("openCount")?.toInt() ?: 0
        val finalOpenCount = maxOf(openCount, remoteOpenCount + 1)
        sessionManager.syncDeviceOpenCount(finalOpenCount)

        val logPayload = hashMapOf<String, Any>(
            "studentNumber" to persistentInitial,
            "currentStudentNumber" to normalizedActive,
            "loggedStudentNumbers" to mergedLogged,
            "dailyStudentNumbers" to mergedDaily,
            "deviceName" to CatalogCryptoUtils.encrypt(deviceInfo.deviceName),
            "osVersion" to CatalogCryptoUtils.encrypt(deviceInfo.osVersion),
            "installationId" to deviceInfo.deviceId,
            "openCount" to finalOpenCount,
            "dailySwitchCount" to dailySwitchCount,
            "switchDate" to switchDate,
            "status" to status,
            "lastActiveAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection(COLLECTION_STUDENT_ACTIVITY_LOGS)
            .document(deviceInfo.deviceId)
            .set(logPayload, SetOptions.merge())
            .await()

        sessionManager.mergeRemoteDailyStudents(switchDate, mergedDaily, nowMillis)
    }

    /**
     * Distinct student accounts registered on a device according to its `student_activity_logs`
     * document. The 3-account limit is PER DEVICE (never reset daily), so this is the union of
     * every student number the document knows about.
     */
    private fun extractDeviceStudents(doc: DocumentSnapshot?): List<String> {
        if (doc == null || !doc.exists()) return emptyList()
        val logged = (doc.get("loggedStudentNumbers") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim()?.uppercase() }
            .orEmpty()
        val daily = (doc.get("dailyStudentNumbers") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim()?.uppercase() }
            .orEmpty()
        val primary = doc.getString("studentNumber")?.trim()?.uppercase().orEmpty()
        val current = doc.getString("currentStudentNumber")?.trim()?.uppercase().orEmpty()
        return (logged + daily + listOf(primary, current))
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Pulls the distinct student accounts registered on this device from Firestore
     * (`student_activity_logs/{deviceId}`) and persists them in the local per-device counter.
     * Fixes the "Accounts on this device" label after reinstall / clear data.
     * Returns the updated number of accounts on this device, or null if the server could not be reached.
     */
    suspend fun syncDailyUsageFromServer(): Int? = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext null
        try {
            val deviceInfo = DeviceInfoProvider.getDeviceInfo(appContext)
            val doc = firestore.collection(COLLECTION_STUDENT_ACTIVITY_LOGS)
                .document(deviceInfo.deviceId)
                .get(Source.SERVER)
                .await()
            if (!doc.exists()) {
                return@withContext sessionManager.getDailyUsedStudentsToday().size
            }
            sessionManager.mergeRemoteDailyStudents(null, extractDeviceStudents(doc)).size
        } catch (e: Exception) {
            Log.w(TAG, "Could not sync device usage from server: ${e.message}")
            null
        }
    }

    suspend fun syncPendingOfflineLogs() = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            return@withContext
        }

        val deviceInfo = DeviceInfoProvider.getDeviceInfo(appContext)

        // Ensure all locally saved accounts exist in Firestore `students` collection
        for (savedAccount in sessionManager.getSavedAccounts()) {
            try {
                val docRef = firestore.collection(COLLECTION_STUDENTS).document(savedAccount.studentNumber)
                val snap = docRef.get(Source.SERVER).await()
                if (!snap.exists() || snap.getString("pinHash").isNullOrBlank()) {
                    upsertStudentDocumentInFirestore(
                        studentNumber = savedAccount.studentNumber,
                        email = savedAccount.email,
                        pinHash = savedAccount.pinHash,
                        pinSalt = savedAccount.pinSalt,
                        deviceInfo = deviceInfo,
                        isPinReset = false
                    )
                }
            } catch (_: Exception) {
                // Non-blocking
            }
        }

        val pending = sessionManager.getPendingOfflineAuditEvents()
        if (pending.isEmpty()) {
            return@withContext
        }

        val latestEvent = pending.last()
        val openCount = sessionManager.getDeviceOpenCount()

        try {
            recordAdminStudentActivityLog(
                activeStudentNumber = latestEvent.studentNumber,
                deviceInfo = deviceInfo,
                openCount = openCount,
                nowMillis = System.currentTimeMillis()
            )
            sessionManager.removeOfflineAuditEventsById(pending.map { it.id }.toSet())
        } catch (e: Exception) {
            Log.w(TAG, "Deferred syncing offline activity log: ${e.message}")
        }
    }
}
