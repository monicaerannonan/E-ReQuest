package com.example.request

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat


class NotificationHelper(
    private val context: Context
) {

    companion object {

        const val CHANNEL_ID =
            "transaction_updates_channel"

        const val CHANNEL_NAME =
            "Transaction Updates"

        const val CHANNEL_DESCRIPTION =
            "Notifications when transaction information is updated"
    }


    // =====================================================
    // CREATE NOTIFICATION CHANNEL
    // =====================================================

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {

                    description =
                        CHANNEL_DESCRIPTION
                }


            val notificationManager =
                context.getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager


            notificationManager.createNotificationChannel(
                channel
            )
        }
    }


    // =====================================================
    // SHOW TRANSACTION UPDATE NOTIFICATION
    // =====================================================

    fun showTransactionUpdateNotification(
        notificationId: Int,
        transactionId: String,
        transactionName: String,
        serviceName: String,
        updatedSection: String,
        notificationMessage: String
    ) {

        createNotificationChannel()


        // =================================================
        // OPEN TRANSACTION DETAILS WHEN TAPPED
        // =================================================

        val intent =
            Intent(
                context,
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

                // Tells TransactionDetailsActivity
                // which section should be opened.
                putExtra(
                    "TARGET_SECTION",
                    updatedSection
                )

                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
            }


        // =================================================
        // PENDING INTENT
        // =================================================

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )


        // =================================================
        // CREATE NOTIFICATION
        // =================================================

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                    transactionName
                )
                .setContentText(
                    notificationMessage
                )
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            "$serviceName\n$notificationMessage"
                        )
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setAutoCancel(
                    true
                )
                .setContentIntent(
                    pendingIntent
                )
                .build()


        // =================================================
        // CHECK PERMISSION
        // =================================================

        val hasPermission =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.checkSelfPermission(
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED


        // =================================================
        // SHOW NOTIFICATION
        // =================================================

        if (hasPermission) {

            NotificationManagerCompat
                .from(context)
                .notify(
                    notificationId,
                    notification
                )
        }
    }
}