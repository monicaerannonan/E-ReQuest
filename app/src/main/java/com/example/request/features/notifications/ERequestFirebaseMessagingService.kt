package com.example.request.features.notifications

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

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    override fun onNewToken(
        token: String
    ) {
        super.onNewToken(token)

        Log.d(TAG, "New FCM token generated.")

        serviceScope.launch {
            try {
                FCMTokenManager(applicationContext).saveToken(token)
                Log.d(TAG, "New FCM token saved to Firestore.")
            } catch (e: Exception) {
                Log.e(TAG, "Unable to save new FCM token.", e)
            }
        }
    }

    override fun onMessageReceived(
        remoteMessage: RemoteMessage
    ) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "FCM message received.")

        val data = remoteMessage.data

        val title =
            remoteMessage.notification?.title
                ?: data["title"]
                ?: "E-ReQuest"

        val message =
            remoteMessage.notification?.body
                ?: data["message"]
                ?: data["body"]
                ?: "School transaction information has been updated."

        val transactionId =
            data["transactionId"]
                ?.trim()
                ?: ""

        val transactionName =
            data["transactionName"]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: title

        val serviceName =
            data["serviceName"]
                ?.trim()
                ?: ""

        val targetSection =
            data["targetSection"]
                ?.trim()
                ?: "transaction"

        val notificationHelper = NotificationHelper(applicationContext)

        val notificationKey = if (transactionId.isNotBlank()) {
            transactionId
        } else {
            remoteMessage.messageId ?: "fcm_${System.currentTimeMillis()}"
        }

        // 1. Immediately display local NotificationCompat alert (works when foregrounded or backgrounded with data payload)
        val wasDispatched = notificationHelper.showTransactionUpdateNotification(
            notificationKey = notificationKey,
            transactionId = transactionId,
            transactionName = transactionName,
            serviceName = serviceName,
            updatedSection = targetSection,
            notificationMessage = message
        )

        // 2. Persist notification in Room with isNotified = wasDispatched and trigger background sync
        serviceScope.launch {
            val database = ERequestDatabase.getDatabase(applicationContext)
            val dao = database.eRequestDao()

            try {
                val now = System.currentTimeMillis()
                val formattedDate = SimpleDateFormat(
                    "MMM dd, yyyy â€¢ hh:mm a",
                    Locale.getDefault()
                ).format(Date(now))

                val entityId = if (transactionId.isNotBlank()) {
                    dao.getUnreadNotificationByTransaction(transactionId)?.id
                        ?: "tx_update_$transactionId"
                } else {
                    "fcm_notice_${remoteMessage.messageId ?: now}"
                }

                val entity = UpdateNotificationEntity(
                    id = entityId,
                    serviceName = serviceName,
                    transactionId = transactionId,
                    transactionName = transactionName,
                    updatedSection = targetSection,
                    message = message,
                    dateTime = formattedDate,
                    isRead = false,
                    isNotified = wasDispatched,
                    createdAt = now
                )

                dao.insertUpdateNotification(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist FCM notification in Room.", e)
            }

            // 3. Mark pending sync and attempt immediate background Firestore -> Room reconciliation
            FirestoreSyncManager.setPendingSync(applicationContext, true)
            try {
                FirestoreSyncManager(applicationContext).syncAllData()
                Log.d(TAG, "Background sync after FCM message completed.")
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Immediate sync after FCM deferred; enqueuing connected WorkManager sync.",
                    e
                )
                ERequestBackgroundSyncWorker.enqueueImmediateOnlineSync(applicationContext)
            }
        }
    }
}
