package com.example.request.features.auth

import android.content.Context
import com.example.request.data.sync.CatalogCryptoUtils
import org.json.JSONArray
import org.json.JSONObject

data class SavedStudentAccount(
    val studentNumber: String,
    val email: String,
    val pinHash: String,
    val pinSalt: String,
    val enrolledAtMillis: Long,
    val lastLoginAtMillis: Long,
    val failedPinAttempts: Int = 0,
    val lockoutUntilMillis: Long = 0L,
    val lockoutCount: Int = 0
)

data class StoredOtpSession(
    val studentNumber: String,
    val email: String,
    val otpHash: String,
    val otpSalt: String,
    val purpose: String,
    val attempts: Int,
    val expiresAtMillis: Long,
    val resendAvailableAtMillis: Long
)

data class OfflineLoginAuditEvent(
    val id: String,
    val studentNumber: String,
    val eventType: String,
    val clientTimestampMillis: Long
)

sealed class LocalPinVerifyResult {
    data class Success(val account: SavedStudentAccount) : LocalPinVerifyResult()
    data class WrongPin(val remainingAttempts: Int) : LocalPinVerifyResult()
    data class LockedOut(val remainingSeconds: Long) : LocalPinVerifyResult()
    data class DailyLimitReached(val message: String) : LocalPinVerifyResult()
    data object AccountNotFound : LocalPinVerifyResult()
}

class StudentSessionManager(context: Context) {

    companion object {
        private const val KEY_ACTIVE_STUDENT = "active_student_number"
        private const val KEY_INITIAL_DEVICE_STUDENT = "initial_device_student_number"
        private const val KEY_DEVICE_OPEN_COUNT = "device_open_count"
        private const val KEY_SAVED_ACCOUNTS_JSON = "saved_accounts_json"
        private const val KEY_OTP_SESSION_JSON = "active_otp_session_json"
        private const val KEY_DAILY_DATE = "daily_usage_date"
        private const val KEY_DAILY_STUDENTS_JSON = "daily_students_json"
        private const val KEY_LAST_OBSERVED_MILLIS = "last_observed_millis"
        private const val KEY_PENDING_AUDIT_LOGS_JSON = "pending_offline_audit_logs_json"
        private const val KEY_DATA_PRIVACY_ACCEPTED = "data_privacy_act_accepted"
        private const val KEY_DATA_PRIVACY_TIMESTAMP = "data_privacy_act_accepted_timestamp"

        private const val LEGACY_CHECKLIST_PREFS = "RequirementChecklistProgress"
        private const val KEY_LEGACY_MIGRATED = "legacy_checklist_migrated"

        fun getChecklistPrefsName(context: Context): String {
            val activeStudent = StudentSessionManager(context).getActiveStudentNumber()
            if (activeStudent.isNullOrBlank()) {
                return LEGACY_CHECKLIST_PREFS
            }
            val safeSuffix = activeStudent.replace("[^A-Za-z0-9_-]".toRegex(), "_")
            val targetName = "${LEGACY_CHECKLIST_PREFS}_$safeSuffix"
            migrateLegacyChecklistOnceIfNeeded(context.applicationContext, targetName)
            return targetName
        }

        private fun migrateLegacyChecklistOnceIfNeeded(appContext: Context, targetPrefsName: String) {
            val metaPrefs = appContext.getSharedPreferences("erequest_checklist_meta", Context.MODE_PRIVATE)
            if (metaPrefs.getBoolean(KEY_LEGACY_MIGRATED, false)) {
                return
            }
            val legacyPrefs = appContext.getSharedPreferences(LEGACY_CHECKLIST_PREFS, Context.MODE_PRIVATE)
            val allEntries = legacyPrefs.all
            if (allEntries.isNotEmpty()) {
                val targetPrefs = appContext.getSharedPreferences(targetPrefsName, Context.MODE_PRIVATE)
                val editor = targetPrefs.edit()
                for ((k, v) in allEntries) {
                    if (v is Boolean) {
                        editor.putBoolean(k, v)
                    }
                }
                editor.apply()
            }
            metaPrefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
        }
    }

    private val storage = SecureAuthStorage(context.applicationContext)

    fun getActiveStudentNumber(): String? {
        return storage.getString(KEY_ACTIVE_STUDENT)?.takeIf { it.isNotBlank() }
    }

    fun setActiveStudentNumber(studentNumber: String?) {
        storage.putString(KEY_ACTIVE_STUDENT, studentNumber?.trim()?.uppercase())
    }

    fun getOrSetInitialDeviceStudentNumber(fallbackStudentNumber: String): String {
        val normalizedFallback = fallbackStudentNumber.trim().uppercase()
        val existing = storage.getString(KEY_INITIAL_DEVICE_STUDENT)?.takeIf { it.isNotBlank() }
        if (existing != null) {
            return existing
        }
        storage.putString(KEY_INITIAL_DEVICE_STUDENT, normalizedFallback)
        return normalizedFallback
    }

    fun incrementAndGetDeviceOpenCount(): Int {
        val current = storage.getString(KEY_DEVICE_OPEN_COUNT)?.toIntOrNull() ?: 0
        val next = (current + 1).coerceAtLeast(1)
        storage.putString(KEY_DEVICE_OPEN_COUNT, next.toString())
        return next
    }

    fun getDeviceOpenCount(): Int {
        return (storage.getString(KEY_DEVICE_OPEN_COUNT)?.toIntOrNull() ?: 1).coerceAtLeast(1)
    }

    fun syncDeviceOpenCount(remoteCount: Int): Int {
        val current = storage.getString(KEY_DEVICE_OPEN_COUNT)?.toIntOrNull() ?: 0
        val target = maxOf(current, remoteCount)
        storage.putString(KEY_DEVICE_OPEN_COUNT, target.toString())
        return target
    }

    fun isDataPrivacyAccepted(): Boolean {
        return storage.getString(KEY_DATA_PRIVACY_ACCEPTED) == "true"
    }

    fun setDataPrivacyAccepted(accepted: Boolean) {
        storage.putString(KEY_DATA_PRIVACY_ACCEPTED, if (accepted) "true" else "false")
        if (accepted) {
            storage.putString(KEY_DATA_PRIVACY_TIMESTAMP, System.currentTimeMillis().toString())
        }
    }

    fun getActiveAccount(): SavedStudentAccount? {
        val activeNumber = getActiveStudentNumber() ?: return null
        return getSavedAccounts().firstOrNull {
            it.studentNumber.equals(activeNumber, ignoreCase = true)
        }
    }

    fun getSavedAccounts(): List<SavedStudentAccount> {
        val raw = storage.getString(KEY_SAVED_ACCOUNTS_JSON) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<SavedStudentAccount>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SavedStudentAccount(
                        studentNumber = obj.optString("studentNumber"),
                        email = CatalogCryptoUtils.decrypt(obj.optString("email")).trim(),
                        pinHash = obj.optString("pinHash"),
                        pinSalt = obj.optString("pinSalt"),
                        enrolledAtMillis = obj.optLong("enrolledAtMillis", 0L),
                        lastLoginAtMillis = obj.optLong("lastLoginAtMillis", 0L),
                        failedPinAttempts = obj.optInt("failedPinAttempts", 0),
                        lockoutUntilMillis = obj.optLong("lockoutUntilMillis", 0L),
                        lockoutCount = obj.optInt("lockoutCount", 0)
                    )
                )
            }
            list.sortedByDescending { it.lastLoginAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAccountsList(accounts: List<SavedStudentAccount>) {
        val array = JSONArray()
        for (acc in accounts) {
            val obj = JSONObject().apply {
                put("studentNumber", acc.studentNumber)
                put("email", CatalogCryptoUtils.decrypt(acc.email).trim())
                put("pinHash", acc.pinHash)
                put("pinSalt", acc.pinSalt)
                put("enrolledAtMillis", acc.enrolledAtMillis)
                put("lastLoginAtMillis", acc.lastLoginAtMillis)
                put("failedPinAttempts", acc.failedPinAttempts)
                put("lockoutUntilMillis", acc.lockoutUntilMillis)
                put("lockoutCount", acc.lockoutCount)
            }
            array.put(obj)
        }
        storage.putString(KEY_SAVED_ACCOUNTS_JSON, array.toString())
    }

    fun upsertAccountAndActivate(
        studentNumber: String,
        email: String,
        pinHash: String,
        pinSalt: String,
        nowMillis: Long = System.currentTimeMillis()
    ): SavedStudentAccount {
        val normalizedId = studentNumber.trim().uppercase()
        val cleanEmail = CatalogCryptoUtils.decrypt(email).trim()
        getOrSetInitialDeviceStudentNumber(normalizedId)
        val current = getSavedAccounts().toMutableList()
        val existingIndex = current.indexOfFirst {
            it.studentNumber.equals(normalizedId, ignoreCase = true)
        }

        val updated = if (existingIndex >= 0) {
            val prev = current[existingIndex]
            prev.copy(
                email = cleanEmail,
                pinHash = pinHash,
                pinSalt = pinSalt,
                lastLoginAtMillis = nowMillis,
                failedPinAttempts = 0,
                lockoutUntilMillis = 0L,
                lockoutCount = 0
            ).also { current[existingIndex] = it }
        } else {
            SavedStudentAccount(
                studentNumber = normalizedId,
                email = cleanEmail,
                pinHash = pinHash,
                pinSalt = pinSalt,
                enrolledAtMillis = nowMillis,
                lastLoginAtMillis = nowMillis,
                failedPinAttempts = 0,
                lockoutUntilMillis = 0L,
                lockoutCount = 0
            ).also { current.add(it) }
        }

        saveAccountsList(current)
        setActiveStudentNumber(normalizedId)
        recordAccountUsedToday(normalizedId, nowMillis)
        return updated
    }

    // =====================================================
    // ENCRYPTED OTP SESSION MANAGEMENT
    // =====================================================

    fun saveOtpSession(session: StoredOtpSession) {
        val cleanEmail = CatalogCryptoUtils.decrypt(session.email).trim()
        val obj = JSONObject().apply {
            put("studentNumber", session.studentNumber)
            put("email", cleanEmail)
            put("otpHash", session.otpHash)
            put("otpSalt", session.otpSalt)
            put("purpose", session.purpose)
            put("attempts", session.attempts)
            put("expiresAtMillis", session.expiresAtMillis)
            put("resendAvailableAtMillis", session.resendAvailableAtMillis)
        }
        storage.putString(KEY_OTP_SESSION_JSON, obj.toString())
    }

    fun getOtpSession(studentNumber: String): StoredOtpSession? {
        val raw = storage.getString(KEY_OTP_SESSION_JSON) ?: return null
        return try {
            val obj = JSONObject(raw)
            val storedStudent = obj.optString("studentNumber")
            if (!storedStudent.equals(studentNumber.trim(), ignoreCase = true)) {
                return null
            }
            StoredOtpSession(
                studentNumber = storedStudent,
                email = CatalogCryptoUtils.decrypt(obj.optString("email")).trim(),
                otpHash = obj.optString("otpHash"),
                otpSalt = obj.optString("otpSalt"),
                purpose = obj.optString("purpose"),
                attempts = obj.optInt("attempts", 0),
                expiresAtMillis = obj.optLong("expiresAtMillis", 0L),
                resendAvailableAtMillis = obj.optLong("resendAvailableAtMillis", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearOtpSession() {
        storage.remove(KEY_OTP_SESSION_JSON)
    }

    fun recordOtpLockout(
        studentNumber: String,
        durationMs: Long = EmailOtpConfig.OTP_LOCKOUT_DURATION_MS,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val normalized = studentNumber.trim().uppercase()
        val lockoutUntil = nowMillis + durationMs
        storage.putString("otp_lockout_${normalized}", lockoutUntil.toString())
    }

    fun getOtpLockoutRemainingSeconds(
        studentNumber: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val normalized = studentNumber.trim().uppercase()
        val raw = storage.getString("otp_lockout_${normalized}")?.toLongOrNull() ?: 0L
        if (raw > nowMillis) {
            return ((raw - nowMillis) + 999L) / 1000L
        }
        return 0L
    }

    fun clearOtpLockout(studentNumber: String) {
        val normalized = studentNumber.trim().uppercase()
        storage.remove("otp_lockout_${normalized}")
    }

    // =====================================================
    // OPTION B: DAILY DEVICE ACCOUNT SWITCH TRACKING
    // =====================================================

    /**
     * Distinct student accounts registered on this device (limit is per device, no daily reset).
     * Name kept for backward compatibility.
     */
    fun getDailyUsedStudentsToday(nowMillis: Long = System.currentTimeMillis()): Set<String> {
        return getDeviceAccountSet()
    }

    /**
     * Returns all saved student numbers on this phone ordered with [mostRecentStudentNumber]
     * strictly at index 0, followed by remaining accounts sorted by lastLoginAtMillis descending (max 10).
     */
    fun getOrderedLoggedStudentNumbers(mostRecentStudentNumber: String): List<String> {
        val primary = mostRecentStudentNumber.trim().uppercase()
        val ordered = mutableListOf<String>()
        if (primary.isNotEmpty()) {
            ordered.add(primary)
        }
        for (acc in getSavedAccounts()) {
            val id = acc.studentNumber.trim().uppercase()
            if (id.isNotEmpty() && ordered.none { it.equals(id, ignoreCase = true) }) {
                ordered.add(id)
            }
            if (ordered.size >= 10) break
        }
        return ordered
    }

    /**
     * Returns today's used student numbers (max 3) ordered with [mostRecentStudentNumber] first.
     */
    fun getOrderedDailyStudentNumbers(
        mostRecentStudentNumber: String,
        nowMillis: Long = System.currentTimeMillis()
    ): List<String> {
        val todaySet = getDailyUsedStudentsToday(nowMillis)
        val primary = mostRecentStudentNumber.trim().uppercase()
        val recencyOrder = getOrderedLoggedStudentNumbers(primary)
        val orderedToday = mutableListOf<String>()
        for (id in recencyOrder) {
            if (todaySet.any { it.equals(id, ignoreCase = true) } &&
                orderedToday.none { it.equals(id, ignoreCase = true) }
            ) {
                orderedToday.add(id)
            }
            if (orderedToday.size >= DailyDeviceSwitchPolicy.MAX_DISTINCT_ACCOUNTS_PER_DAY) break
        }
        for (id in todaySet) {
            val clean = id.trim().uppercase()
            if (clean.isNotEmpty() && orderedToday.none { it.equals(clean, ignoreCase = true) }) {
                orderedToday.add(clean)
            }
            if (orderedToday.size >= DailyDeviceSwitchPolicy.MAX_DISTINCT_ACCOUNTS_PER_DAY) break
        }
        if (orderedToday.isEmpty() && primary.isNotEmpty()) {
            orderedToday.add(primary)
        }
        return orderedToday.take(DailyDeviceSwitchPolicy.MAX_DISTINCT_ACCOUNTS_PER_DAY)
    }

    fun evaluateDailySwitchPolicy(
        candidateStudentNumber: String,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyDeviceSwitchPolicy.SwitchDecision {
        val today = DailyDeviceSwitchPolicy.getCurrentDateKey(nowMillis)
        val recordedDate = storage.getString(KEY_DAILY_DATE)
        val lastObserved = storage.getString(KEY_LAST_OBSERVED_MILLIS)?.toLongOrNull() ?: 0L

        return DailyDeviceSwitchPolicy.evaluateAccountAccess(
            candidateStudentNumber = candidateStudentNumber,
            recordedDate = recordedDate,
            todayDate = today,
            recordedStudents = getDeviceAccountSet(),
            nowMillis = nowMillis,
            lastObservedMillis = lastObserved
        )
    }

    fun recordAccountUsedToday(
        studentNumber: String,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyDeviceSwitchPolicy.SwitchDecision {
        val decision = evaluateDailySwitchPolicy(studentNumber, nowMillis)
        if (decision is DailyDeviceSwitchPolicy.SwitchDecision.Allowed) {
            storage.putString(KEY_DAILY_DATE, decision.updatedDate)
            storage.putString(KEY_DAILY_STUDENTS_JSON, toJsonStringArray(decision.updatedUsedStudents))
            updateObservedTimestamp(nowMillis)
        }
        return decision
    }

    /**
     * The 3-account limit is PER DEVICE (no daily reset). The device account set is the union of
     * the locally stored set and every account saved on this phone.
     */
    private fun getDeviceAccountSet(): Set<String> {
        val stored = parseJsonStringSet(storage.getString(KEY_DAILY_STUDENTS_JSON))
        val result = LinkedHashSet<String>()
        for (id in stored) {
            val clean = id.trim().uppercase()
            if (clean.isNotEmpty()) result.add(clean)
        }
        for (acc in getSavedAccounts()) {
            val clean = acc.studentNumber.trim().uppercase()
            if (clean.isNotEmpty()) result.add(clean)
        }
        return result
    }

    /**
     * Merges the distinct student numbers registered on this device according to the server
     * (Firestore `student_activity_logs`) into the local per-device counter so the UI label and
     * the local limit policy stay accurate after reinstall / clear data. Never reset by date.
     */
    fun mergeRemoteDailyStudents(
        remoteSwitchDate: String?,
        remoteStudents: Collection<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): Set<String> {
        val local = getDeviceAccountSet()

        val merged = LinkedHashSet<String>()
        merged.addAll(local)
        for (raw in remoteStudents) {
            val clean = raw.trim().uppercase()
            if (clean.isNotEmpty()) merged.add(clean)
        }

        if (merged != local) {
            storage.putString(KEY_DAILY_DATE, DailyDeviceSwitchPolicy.getCurrentDateKey(nowMillis))
            storage.putString(KEY_DAILY_STUDENTS_JSON, toJsonStringArray(merged))
        }
        return merged
    }

    private fun updateObservedTimestamp(nowMillis: Long) {
        val prev = storage.getString(KEY_LAST_OBSERVED_MILLIS)?.toLongOrNull() ?: 0L
        if (nowMillis > prev) {
            storage.putString(KEY_LAST_OBSERVED_MILLIS, nowMillis.toString())
        }
    }

    // =====================================================
    // OFFLINE & ONLINE 4-DIGIT PIN VERIFICATION + LOCKOUT
    // =====================================================

    fun getLockoutRemainingSeconds(
        studentNumber: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val account = getSavedAccounts().firstOrNull {
            it.studentNumber.equals(studentNumber.trim(), ignoreCase = true)
        } ?: return 0L

        if (account.lockoutUntilMillis > nowMillis) {
            return ((account.lockoutUntilMillis - nowMillis) + 999L) / 1000L
        }
        return 0L
    }

    fun verifyPinLocally(
        studentNumber: String,
        enteredPin: String,
        nowMillis: Long = System.currentTimeMillis()
    ): LocalPinVerifyResult {
        val normalizedId = studentNumber.trim().uppercase()
        val accounts = getSavedAccounts().toMutableList()
        val index = accounts.indexOfFirst {
            it.studentNumber.equals(normalizedId, ignoreCase = true)
        }
        if (index < 0) {
            return LocalPinVerifyResult.AccountNotFound
        }

        val account = accounts[index]

        if (account.lockoutUntilMillis > nowMillis) {
            val remainingSec = ((account.lockoutUntilMillis - nowMillis) + 999L) / 1000L
            return LocalPinVerifyResult.LockedOut(remainingSec)
        }

        when (val switchCheck = evaluateDailySwitchPolicy(normalizedId, nowMillis)) {
            is DailyDeviceSwitchPolicy.SwitchDecision.DeniedDailyLimit -> {
                return LocalPinVerifyResult.DailyLimitReached(switchCheck.message)
            }
            is DailyDeviceSwitchPolicy.SwitchDecision.DeniedClockTamper -> {
                return LocalPinVerifyResult.DailyLimitReached(switchCheck.message)
            }
            is DailyDeviceSwitchPolicy.SwitchDecision.Allowed -> Unit
        }

        val isMatch = PinSecurityUtils.verifySecret(
            candidateSecret = enteredPin,
            saltHex = account.pinSalt,
            expectedHashHex = account.pinHash
        )

        if (isMatch) {
            val updated = account.copy(
                lastLoginAtMillis = nowMillis,
                failedPinAttempts = 0,
                lockoutUntilMillis = 0L,
                lockoutCount = 0
            )
            accounts[index] = updated
            saveAccountsList(accounts)
            setActiveStudentNumber(normalizedId)
            recordAccountUsedToday(normalizedId, nowMillis)
            return LocalPinVerifyResult.Success(updated)
        } else {
            val baseAttempts = if (account.lockoutUntilMillis in 1..nowMillis) 0 else account.failedPinAttempts
            val newAttempts = baseAttempts + 1
            return if (newAttempts >= DailyDeviceSwitchPolicy.MAX_FAILED_PIN_ATTEMPTS) {
                val nextLockoutCount = account.lockoutCount + 1
                val multiplier = when (nextLockoutCount) {
                    1 -> 1L      // 1st lockout: 5 minutes
                    2 -> 3L      // 2nd lockout: 15 minutes
                    else -> 6L   // 3rd+ lockout: 30 minutes
                }
                val lockoutDurationMs = DailyDeviceSwitchPolicy.PIN_LOCKOUT_DURATION_MS * multiplier
                val lockoutUntil = nowMillis + lockoutDurationMs
                accounts[index] = account.copy(
                    failedPinAttempts = 0,
                    lockoutUntilMillis = lockoutUntil,
                    lockoutCount = nextLockoutCount
                )
                saveAccountsList(accounts)
                LocalPinVerifyResult.LockedOut(
                    lockoutDurationMs / 1000L
                )
            } else {
                accounts[index] = account.copy(
                    failedPinAttempts = newAttempts,
                    lockoutUntilMillis = 0L
                )
                saveAccountsList(accounts)
                LocalPinVerifyResult.WrongPin(
                    DailyDeviceSwitchPolicy.MAX_FAILED_PIN_ATTEMPTS - newAttempts
                )
            }
        }
    }

    // =====================================================
    // OFFLINE AUDIT LOG QUEUE (AUTO-SYNCED WHEN ONLINE)
    // =====================================================

    fun enqueueOfflineAuditEvent(
        studentNumber: String,
        eventType: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val current = getPendingOfflineAuditEvents().toMutableList()
        current.add(
            OfflineLoginAuditEvent(
                id = "${studentNumber}_${nowMillis}_${current.size}",
                studentNumber = studentNumber.trim().uppercase(),
                eventType = eventType,
                clientTimestampMillis = nowMillis
            )
        )
        val bounded = if (current.size > 50) current.takeLast(50) else current
        savePendingOfflineAuditEvents(bounded)
    }

    fun getPendingOfflineAuditEvents(): List<OfflineLoginAuditEvent> {
        val raw = storage.getString(KEY_PENDING_AUDIT_LOGS_JSON) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<OfflineLoginAuditEvent>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    OfflineLoginAuditEvent(
                        id = obj.optString("id"),
                        studentNumber = obj.optString("studentNumber"),
                        eventType = obj.optString("eventType"),
                        clientTimestampMillis = obj.optLong("clientTimestampMillis", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun removeOfflineAuditEventsById(syncedIds: Set<String>) {
        if (syncedIds.isEmpty()) return
        val remaining = getPendingOfflineAuditEvents().filterNot { it.id in syncedIds }
        savePendingOfflineAuditEvents(remaining)
    }

    private fun savePendingOfflineAuditEvents(events: List<OfflineLoginAuditEvent>) {
        val array = JSONArray()
        for (ev in events) {
            array.put(
                JSONObject().apply {
                    put("id", ev.id)
                    put("studentNumber", ev.studentNumber)
                    put("eventType", ev.eventType)
                    put("clientTimestampMillis", ev.clientTimestampMillis)
                }
            )
        }
        storage.putString(KEY_PENDING_AUDIT_LOGS_JSON, array.toString())
    }

    private fun parseJsonStringSet(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return try {
            val arr = JSONArray(raw)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val value = arr.optString(i).trim()
                if (value.isNotEmpty()) set.add(value)
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun toJsonStringArray(values: Set<String>): String {
        val arr = JSONArray()
        values.forEach { arr.put(it) }
        return arr.toString()
    }
}
