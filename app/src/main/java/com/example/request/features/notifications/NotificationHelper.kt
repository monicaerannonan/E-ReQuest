package com.example.request.features.notifications

import com.example.request.ERequestApplication
import com.example.request.R
import com.example.request.data.local.*
import com.example.request.data.sync.*
import com.example.request.features.checklist.*
import com.example.request.features.home.*
import com.example.request.features.notifications.*
import com.example.request.features.progress.*
import com.example.request.features.services.*
import com.example.request.features.splash.*
import com.example.request.features.transactions.*
import com.example.request.ui.custom.*

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class NotificationHelper(
    context: Context
) {

    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "NotificationHelper"

        const val CHANNEL_ID = ERequestApplication.UPDATES_CHANNEL_ID
        const val CHANNEL_NAME = "E-Request Updates"
        const val CHANNEL_DESCRIPTION =
            "Notifications for updated school transactions and campus announcements."
    }

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
            }

            val notificationManager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        }
    }

    suspend fun dispatchUnnotifiedUpdates(dao: ERequestDao) {
        if (!canPostNotifications()) {
            return
        }

        val pendingNotifications = dao.getUnnotifiedUpdateNotifications()
        for (entity in pendingNotifications) {
            val dispatched = showTransactionUpdateNotification(
                notificationKey = entity.transactionId.ifBlank { entity.id },
                transactionId = entity.transactionId,
                transactionName = entity.transactionName,
                serviceName = entity.serviceName,
                updatedSection = entity.updatedSection,
                notificationMessage = entity.message,
                timestampMillis = entity.createdAt
            )
            if (dispatched) {
                dao.markUpdateNotificationAsNotified(entity.id)
            }
        }
    }

    fun showTransactionUpdateNotification(
        notificationKey: String,
        transactionId: String,
        transactionName: String,
        serviceName: String,
        updatedSection: String,
        notificationMessage: String,
        timestampMillis: Long = System.currentTimeMillis()
    ): Boolean {
        ensureNotificationChannel()

        if (!canPostNotifications()) {
            return false
        }

        val intent = if (transactionId.isNotBlank()) {
            Intent(appContext, TransactionDetailsActivity::class.java).apply {
                putExtra("TRANSACTION_ID", transactionId)
                putExtra("TRANSACTION_NAME", transactionName)
                putExtra("SERVICE_NAME", serviceName)
                putExtra("TARGET_SECTION", updatedSection)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        } else {
            Intent(appContext, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }

        val notificationId = notificationKey.hashCode()

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = transactionName.ifBlank { "E-ReQuest Update" }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(notificationMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setWhen(timestampMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(appContext).notify(notificationId, notification)
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Unable to post notification due to missing permission.", e)
            false
        }
    }
}
