package com.example.request

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ERequestFirebaseMessagingService :
    FirebaseMessagingService() {

    companion object {
        private const val TAG = "ERequestFCM"
    }

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )


    // =====================================================
    // NEW / REFRESHED FCM TOKEN
    // =====================================================

    override fun onNewToken(
        token: String
    ) {

        super.onNewToken(
            token
        )

        Log.d(
            TAG,
            "New FCM token generated."
        )

        serviceScope.launch {

            try {

                FCMTokenManager(
                    applicationContext
                ).saveToken(
                    token
                )

                Log.d(
                    TAG,
                    "New FCM token saved to Firestore."
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Unable to save new FCM token.",
                    e
                )
            }
        }
    }


    // =====================================================
    // RECEIVE FCM MESSAGE
    // =====================================================

    override fun onMessageReceived(
        remoteMessage: RemoteMessage
    ) {

        super.onMessageReceived(
            remoteMessage
        )

        Log.d(
            TAG,
            "FCM message received."
        )

        val data =
            remoteMessage.data

        val title =
            remoteMessage
                .notification
                ?.title
                ?: data["title"]
                ?: "E-Request"

        val message =
            remoteMessage
                .notification
                ?.body
                ?: data["message"]
                ?: "School transaction information has been updated."

        val transactionId =
            data["transactionId"]
                ?: ""

        val transactionName =
            data["transactionName"]
                ?: title

        val serviceName =
            data["serviceName"]
                ?: ""

        val targetSection =
            data["targetSection"]
                ?: ""

        showNotification(
            title = title,
            message = message,
            transactionId = transactionId,
            transactionName = transactionName,
            serviceName = serviceName,
            targetSection = targetSection
        )
    }


    // =====================================================
    // SHOW DEVICE NOTIFICATION
    // =====================================================

    private fun showNotification(
        title: String,
        message: String,
        transactionId: String,
        transactionName: String,
        serviceName: String,
        targetSection: String
    ) {

        val intent =
            if (transactionId.isNotBlank()) {

                Intent(
                    this,
                    TransactionDetailsActivity::class.java
                ).apply {

                    putExtra(
                        "TRANSACTION_ID",
                        transactionId
                    )

                    putExtra(
                        "TRANSACTION_NAME",
                        transactionName
                    )

                    putExtra(
                        "SERVICE_NAME",
                        serviceName
                    )

                    putExtra(
                        "TARGET_SECTION",
                        targetSection
                    )

                    flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                }

            } else {

                Intent(
                    this,
                    HomeActivity::class.java
                ).apply {

                    flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            }


        // Different transaction = different notification.
        // Same transaction can update its existing notification.

        val notificationId =
            if (transactionId.isNotBlank()) {

                transactionId.hashCode()

            } else {

                System
                    .currentTimeMillis()
                    .toInt()
            }


        val pendingIntent =
            PendingIntent.getActivity(
                this,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )


        val notification =
            NotificationCompat.Builder(
                this,
                ERequestApplication.UPDATES_CHANNEL_ID
            )
                .setSmallIcon(
                    R.mipmap.ic_launcher
                )
                .setContentTitle(
                    title
                )
                .setContentText(
                    message
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(
                            message
                        )
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setCategory(
                    NotificationCompat.CATEGORY_STATUS
                )
                .setAutoCancel(
                    true
                )
                .setContentIntent(
                    pendingIntent
                )
                .build()


        try {

            NotificationManagerCompat
                .from(this)
                .notify(
                    notificationId,
                    notification
                )

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "Notification permission was not granted.",
                e
            )
        }
    }
}