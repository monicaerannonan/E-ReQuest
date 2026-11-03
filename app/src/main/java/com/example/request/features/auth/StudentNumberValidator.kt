package com.example.request.features.auth

import com.example.request.data.sync.CatalogCryptoUtils
import java.util.Calendar
import java.util.regex.Pattern

object StudentNumberValidator {

    val CAMPUS_CODES: List<String> = listOf(
        "AU",
        "BI",
        "CA",
        "CO",
        "SA",
        "SU",
        "TA",
        "XX"
    )

    const val MIN_YEAR = 2000
    const val DEFAULT_MAX_YEAR = 2040
    const val REQUIRED_DIGITS_LENGTH = 5

    private val FIVE_DIGITS_PATTERN: Pattern = Pattern.compile("^\\d{5}$")
    private val EMAIL_PATTERN: Pattern = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    sealed class ValidationResult {
        data class Valid(val formattedStudentNumber: String) : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
    }

    fun getAvailableYears(
        currentCalendarYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): List<String> {
        val maxYear = maxOf(DEFAULT_MAX_YEAR, currentCalendarYear)
        return (MIN_YEAR..maxYear).map { it.toString() }
    }

    fun formatStudentNumber(
        campusCode: String,
        year: String,
        digits: String
    ): String {
        return "${campusCode.trim().uppercase()}-${year.trim()}-${digits.trim()}"
    }

    fun validateStudentNumberParts(
        campusCode: String,
        year: String,
        digits: String,
        currentCalendarYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): ValidationResult {
        val normalizedCampus = campusCode.trim().uppercase()
        if (normalizedCampus !in CAMPUS_CODES) {
            return ValidationResult.Invalid("Please select a valid Campus code.")
        }

        val parsedYear = year.trim().toIntOrNull()
        val maxYear = maxOf(DEFAULT_MAX_YEAR, currentCalendarYear)
        if (parsedYear == null || parsedYear !in MIN_YEAR..maxYear) {
            return ValidationResult.Invalid("Please select a valid year ($MIN_YEAR - $maxYear).")
        }

        val normalizedDigits = digits.trim()
        if (!FIVE_DIGITS_PATTERN.matcher(normalizedDigits).matches()) {
            return ValidationResult.Invalid("Please enter an exact 5-digit student number (e.g. 01234).")
        }

        return ValidationResult.Valid(
            formatStudentNumber(normalizedCampus, parsedYear.toString(), normalizedDigits)
        )
    }

    fun isValidEmail(email: String): Boolean {
        val trimmed = CatalogCryptoUtils.decrypt(email).trim()
        return trimmed.isNotEmpty() && EMAIL_PATTERN.matcher(trimmed).matches()
    }

    fun maskEmail(email: String): String {
        val trimmed = CatalogCryptoUtils.decrypt(email).trim()
        val atIndex = trimmed.indexOf('@')
        if (atIndex <= 0 || atIndex == trimmed.lastIndex) {
            return trimmed
        }
        val localPart = trimmed.substring(0, atIndex)
        val domainPart = trimmed.substring(atIndex)
        val visibleStart = localPart.take(minOf(2, localPart.length))
        val visibleEnd = if (localPart.length > 3) localPart.takeLast(1) else ""
        return "$visibleStart***$visibleEnd$domainPart"
    }
}
