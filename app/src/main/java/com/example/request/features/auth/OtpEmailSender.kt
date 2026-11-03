package com.example.request.features.auth

import android.util.Base64
import android.util.Log
import com.example.request.data.sync.CatalogCryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

sealed class OtpDeliveryResult {
    data object SentViaEmail : OtpDeliveryResult()
    data class DevPreviewMode(val otpCode: String) : OtpDeliveryResult()
    data class Failed(val reason: String) : OtpDeliveryResult()
}

object OtpEmailSender {

    private const val TAG = "OtpEmailSender"

    suspend fun sendOtpEmail(
        recipientEmail: String,
        studentNumber: String,
        otpCode: String,
        purposeLabel: String,
        expiresAtMillis: Long
    ): OtpDeliveryResult = withContext(Dispatchers.IO) {
        val cleanRecipient = CatalogCryptoUtils.decrypt(recipientEmail).trim()

        // 1. Primary: Live EmailJS HTTP API delivery
        if (EmailOtpConfig.isEmailJsConfigured()) {
            try {
                sendViaEmailJs(
                    recipientEmail = cleanRecipient,
                    studentNumber = studentNumber,
                    otpCode = otpCode,
                    purposeLabel = purposeLabel,
                    expiresAtMillis = expiresAtMillis
                )
                return@withContext OtpDeliveryResult.SentViaEmail
            } catch (e: Exception) {
                Log.e(TAG, "EmailJS OTP delivery failed", e)
                if (!EmailOtpConfig.isSmtpConfigured()) {
                    return@withContext OtpDeliveryResult.Failed(
                        "Could not send OTP to $cleanRecipient (${e.localizedMessage ?: "Email service error"})."
                    )
                }
            }
        }

        // 2. Secondary: Direct Gmail SMTPS (Port 465 SSL) if configured
        if (EmailOtpConfig.isSmtpConfigured()) {
            return@withContext try {
                val subject = "E-ReQuest Verification Code: $otpCode"
                val htmlBody = buildHtmlTemplate(studentNumber, otpCode, purposeLabel)
                sendViaSmtps(
                    recipientEmail = cleanRecipient,
                    subject = subject,
                    htmlBody = htmlBody
                )
                OtpDeliveryResult.SentViaEmail
            } catch (e: Exception) {
                Log.e(TAG, "SMTP OTP delivery failed", e)
                OtpDeliveryResult.Failed(
                    "Could not deliver OTP email (${e.localizedMessage ?: "SMTP error"})."
                )
            }
        }

        Log.i(TAG, "[DEV OTP PREVIEW] Student=$studentNumber | Email=$cleanRecipient | OTP=$otpCode")
        OtpDeliveryResult.DevPreviewMode(otpCode)
    }

    private fun sendViaEmailJs(
        recipientEmail: String,
        studentNumber: String,
        otpCode: String,
        purposeLabel: String,
        expiresAtMillis: Long
    ) {
        val timeFormatter = SimpleDateFormat("h:mm a", Locale.US)
        val expirationTime = timeFormatter.format(Date(expiresAtMillis))

        val templateParams = JSONObject().apply {
            put("passcode", otpCode)
            put("otp", otpCode)
            put("otp_code", otpCode)
            put("code", otpCode)
            put("time", expirationTime)
            put("expirationTime", expirationTime)
            put("expiration_time", expirationTime)
            put("expiry_time", expirationTime)
            put("expires_in", "5 minutes")
            put("to_email", recipientEmail)
            put("email", recipientEmail)
            put("recipient", recipientEmail)
            put("student_number", studentNumber)
            put("studentNumber", studentNumber)
            put(
                "message",
                "Your E-ReQuest Student verification OTP ($purposeLabel) for $studentNumber is $otpCode. Valid until $expirationTime (5 minutes)."
            )
            put("app_name", "E-ReQuest Student Portal")
        }

        val payload = JSONObject().apply {
            put("service_id", EmailOtpConfig.EMAILJS_SERVICE_ID)
            put("template_id", EmailOtpConfig.EMAILJS_TEMPLATE_ID)
            put("user_id", EmailOtpConfig.EMAILJS_PUBLIC_KEY)
            put("template_params", templateParams)
        }

        val connection = (URL(EmailOtpConfig.EMAILJS_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Origin", EmailOtpConfig.EMAILJS_ORIGIN)
            setRequestProperty("Referer", "${EmailOtpConfig.EMAILJS_ORIGIN}/")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
        }

        try {
            connection.outputStream.use { os ->
                os.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                os.flush()
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                val errBody = try {
                    connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
                } catch (_: Exception) {
                    null
                }
                throw IllegalStateException(errBody?.takeIf { it.isNotBlank() } ?: "HTTP $statusCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sendViaSmtps(
        recipientEmail: String,
        subject: String,
        htmlBody: String
    ) {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val socket = factory.createSocket(
            EmailOtpConfig.SMTP_HOST,
            EmailOtpConfig.SMTP_PORT
        ) as SSLSocket
        socket.soTimeout = 15_000

        socket.use { sslSocket ->
            sslSocket.startHandshake()
            val reader = BufferedReader(
                InputStreamReader(sslSocket.inputStream, StandardCharsets.UTF_8)
            )
            val writer = BufferedWriter(
                OutputStreamWriter(sslSocket.outputStream, StandardCharsets.UTF_8)
            )

            expectSmtpCode(reader, 220)
            sendSmtpLine(writer, "EHLO erequest.local")
            expectSmtpCode(reader, 250)

            sendSmtpLine(writer, "AUTH LOGIN")
            expectSmtpCode(reader, 334)

            val encodedUser = Base64.encodeToString(
                EmailOtpConfig.SMTP_SENDER_EMAIL.trim().toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )
            sendSmtpLine(writer, encodedUser)
            expectSmtpCode(reader, 334)

            val cleanPassword = EmailOtpConfig.SMTP_APP_PASSWORD.replace(" ", "")
            val encodedPass = Base64.encodeToString(
                cleanPassword.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )
            sendSmtpLine(writer, encodedPass)
            expectSmtpCode(reader, 235)

            val sender = EmailOtpConfig.SMTP_SENDER_EMAIL.trim()
            sendSmtpLine(writer, "MAIL FROM:<$sender>")
            expectSmtpCode(reader, 250)

            sendSmtpLine(writer, "RCPT TO:<$recipientEmail>")
            expectSmtpCode(reader, 250)

            sendSmtpLine(writer, "DATA")
            expectSmtpCode(reader, 354)

            val messagePayload = buildString {
                append("From: \"${EmailOtpConfig.SENDER_DISPLAY_NAME}\" <$sender>\r\n")
                append("To: <$recipientEmail>\r\n")
                append("Subject: $subject\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: text/html; charset=UTF-8\r\n")
                append("\r\n")
                append(htmlBody)
                append("\r\n.\r\n")
            }
            writer.write(messagePayload)
            writer.flush()
            expectSmtpCode(reader, 250)

            sendSmtpLine(writer, "QUIT")
        }
    }

    private fun sendSmtpLine(writer: BufferedWriter, command: String) {
        writer.write("$command\r\n")
        writer.flush()
    }

    private fun expectSmtpCode(reader: BufferedReader, expectedCode: Int) {
        var line: String?
        var lastLine = ""
        while (true) {
            line = reader.readLine() ?: break
            lastLine = line
            if (line.length < 4 || line[3] != '-') {
                break
            }
        }
        val statusCode = lastLine.take(3).toIntOrNull() ?: -1
        if (statusCode != expectedCode) {
            throw IllegalStateException("SMTP unexpected response: $lastLine (expected $expectedCode)")
        }
    }

    private fun buildHtmlTemplate(
        studentNumber: String,
        otpCode: String,
        purposeLabel: String
    ): String {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 24px; border: 1px solid #E4E7EC; border-radius: 12px; background-color: #FFFFFF;">
                <h2 style="color: #173B57; margin-top: 0;">E-ReQuest Verification</h2>
                <p style="color: #344054; font-size: 14px;">Here is your 6-digit One-Time Password (OTP) for <strong>$purposeLabel</strong> for Student Number <strong>$studentNumber</strong>:</p>
                <div style="background-color: #EEF2F6; border-radius: 10px; padding: 18px; text-align: center; margin: 20px 0;">
                    <span style="font-size: 30px; font-weight: bold; letter-spacing: 8px; color: #102A3E;">$otpCode</span>
                </div>
                <p style="color: #667085; font-size: 12px;">This OTP is valid for <strong>5 minutes</strong>. Do not share this code with anyone.</p>
            </div>
        """.trimIndent()
    }
}
