package com.example.request.features.auth

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DailyDeviceSwitchPolicy {

    const val MAX_DISTINCT_ACCOUNTS_PER_DAY = 3
    const val MAX_FAILED_PIN_ATTEMPTS = 5
    const val PIN_LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    private const val CLOCK_ROLLBACK_TOLERANCE_MS = 5 * 60 * 1000L

    sealed class SwitchDecision {
        data class Allowed(
            val updatedDate: String,
            val updatedUsedStudents: Set<String>,
            val remainingNewAccountSlots: Int
        ) : SwitchDecision()

        data class DeniedDailyLimit(
            val usedStudentsToday: Set<String>,
            val message: String
        ) : SwitchDecision()

        data class DeniedClockTamper(
            val message: String
        ) : SwitchDecision()
    }

    fun getCurrentDateKey(nowMillis: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Manila")
        }
        return formatter.format(Date(nowMillis))
    }

    fun isClockRolledBack(
        nowMillis: Long,
        lastObservedMillis: Long
    ): Boolean {
        return lastObservedMillis > 0L &&
                nowMillis < (lastObservedMillis - CLOCK_ROLLBACK_TOLERANCE_MS)
    }

    /**
     * Resolves the set of distinct student accounts registered on this device.
     * The limit is PER DEVICE (maximum 3 accounts, never reset daily), so the date
     * parameters are intentionally ignored and only kept for API compatibility.
     */
    fun getActiveStudentsForToday(
        recordedDate: String?,
        todayDate: String,
        recordedStudents: Set<String>
    ): Set<String> {
        return recordedStudents.map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun canAddNewDistinctAccountToday(
        recordedDate: String?,
        todayDate: String,
        recordedStudents: Set<String>
    ): Boolean {
        val activeToday = getActiveStudentsForToday(recordedDate, todayDate, recordedStudents)
        return activeToday.size < MAX_DISTINCT_ACCOUNTS_PER_DAY
    }

    fun getRemainingSlotsToday(
        recordedDate: String?,
        todayDate: String,
        recordedStudents: Set<String>
    ): Int {
        val activeToday = getActiveStudentsForToday(recordedDate, todayDate, recordedStudents)
        return (MAX_DISTINCT_ACCOUNTS_PER_DAY - activeToday.size).coerceAtLeast(0)
    }

    /**
     * Maps Option B distinct daily student count (0..3) to daily counter (0..3):
     * - 0 distinct students today -> 0 ('normal')
     * - 1st login today (1 distinct student) -> 1 ('normal' — 1/3)
     * - 1st switch today (2 distinct students) -> 2 ('switched' — 2/3)
     * - 2nd switch today (3 distinct students) -> 3 ('limit_reached' — 3/3)
     */
    fun toAdminDailySwitchCount(distinctStudentsCountToday: Int): Int {
        return distinctStudentsCountToday.coerceIn(0, MAX_DISTINCT_ACCOUNTS_PER_DAY)
    }

    fun toAdminSwitchStatus(dailySwitchCount: Int): String {
        return when {
            dailySwitchCount >= MAX_DISTINCT_ACCOUNTS_PER_DAY -> "limit_reached"
            dailySwitchCount >= 2 -> "switched"
            else -> "normal"
        }
    }

    /**
     * Evaluates Option B: Maximum of 3 distinct student accounts on a single device per day.
     */
    fun evaluateAccountAccess(
        candidateStudentNumber: String,
        recordedDate: String?,
        todayDate: String,
        recordedStudents: Set<String>,
        nowMillis: Long = System.currentTimeMillis(),
        lastObservedMillis: Long = 0L
    ): SwitchDecision {
        if (isClockRolledBack(nowMillis, lastObservedMillis)) {
            return SwitchDecision.DeniedClockTamper(
                "Device date/time is out of sync. Please enable automatic date & time in your device settings."
            )
        }

        val normalizedCandidate = candidateStudentNumber.trim().uppercase()
        val activeToday = getActiveStudentsForToday(recordedDate, todayDate, recordedStudents)

        if (normalizedCandidate in activeToday) {
            return SwitchDecision.Allowed(
                updatedDate = todayDate,
                updatedUsedStudents = activeToday,
                remainingNewAccountSlots = (MAX_DISTINCT_ACCOUNTS_PER_DAY - activeToday.size).coerceAtLeast(0)
            )
        }

        if (activeToday.size >= MAX_DISTINCT_ACCOUNTS_PER_DAY) {
            return SwitchDecision.DeniedDailyLimit(
                usedStudentsToday = activeToday,
                message = "This device has reached the limit of $MAX_DISTINCT_ACCOUNTS_PER_DAY student accounts."
            )
        }

        val updatedSet = activeToday + normalizedCandidate
        return SwitchDecision.Allowed(
            updatedDate = todayDate,
            updatedUsedStudents = updatedSet,
            remainingNewAccountSlots = (MAX_DISTINCT_ACCOUNTS_PER_DAY - updatedSet.size).coerceAtLeast(0)
        )
    }
}
