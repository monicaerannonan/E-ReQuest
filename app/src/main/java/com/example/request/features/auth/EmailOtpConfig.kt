package com.example.request.features.auth

/**
 * Configuration for Student Email OTP delivery.
 *
 * Uses the project's live EmailJS service so 6-digit OTP verification codes
 * are sent directly to the student's email inbox, with optional Gmail SMTPS support.
 */
object EmailOtpConfig {

    // Live EmailJS credentials shared with E-ReQuest Portal
    const val EMAILJS_ENDPOINT = "https://api.emailjs.com/api/v1.0/email/send"
    const val EMAILJS_SERVICE_ID = "service_4rwr6df"
    const val EMAILJS_TEMPLATE_ID = "template_065t767"
    const val EMAILJS_PUBLIC_KEY = "iC0ESDFLUm8ZrV8q2"
    const val EMAILJS_ORIGIN = "http://localhost:5173"

    // Optional Direct Gmail SMTPS fallback
    const val SMTP_HOST = "smtp.gmail.com"
    const val SMTP_PORT = 465
    const val SMTP_SENDER_EMAIL = ""
    const val SMTP_APP_PASSWORD = ""
    const val SENDER_DISPLAY_NAME = "E-ReQuest Campus Guide"

    const val OTP_VALIDITY_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    const val OTP_RESEND_COOLDOWN_MS = 60 * 1000L       // 60 seconds
    const val MAX_OTP_VERIFY_ATTEMPTS = 5
    const val OTP_LOCKOUT_DURATION_MS = 5 * 60 * 1000L  // 5 minutes lockout after 5 failed attempts

    fun isEmailJsConfigured(): Boolean {
        return EMAILJS_SERVICE_ID.isNotBlank() &&
                EMAILJS_TEMPLATE_ID.isNotBlank() &&
                EMAILJS_PUBLIC_KEY.isNotBlank()
    }

    fun isSmtpConfigured(): Boolean {
        return SMTP_SENDER_EMAIL.isNotBlank() && SMTP_APP_PASSWORD.isNotBlank()
    }
}
