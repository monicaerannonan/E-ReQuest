package com.example.request

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ERequestApplication :
    Application() {

    companion object {

        const val UPDATES_CHANNEL_ID =
            "erequest_transaction_updates"
    }


    private val applicationScope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )


    // =====================================================
    // APPLICATION START
    // =====================================================

    override fun onCreate() {

        super.onCreate()


        // =================================================
        // CREATE ANDROID NOTIFICATION CHANNEL
        // =================================================

        createNotificationChannel()


        // =================================================
        // SCHEDULE WORKMANAGER PERIODIC BACKGROUND SYNC
        // =================================================

        ERequestBackgroundSyncWorker.schedulePeriodicSync(this)


        // =================================================
        // REGISTER CURRENT DEVICE FOR FCM
        // =================================================

        applicationScope.launch {

            try {

                FCMTokenManager(
                    this@ERequestApplication
                ).registerCurrentToken()

                Log.d(
                    "ERequestFCM",
                    "Device FCM registration completed."
                )

            } catch (e: Exception) {

                Log.e(
                    "ERequestFCM",
                    "Device FCM registration failed.",
                    e
                )
            }
        }


        // =================================================
        // INITIAL FIRESTORE → ROOM SYNC
        // =================================================

        applicationScope.launch {

            try {

                val syncManager =
                    FirestoreSyncManager(
                        this@ERequestApplication
                    )

                syncManager.syncAllData()

                Log.d(
                    "ERequestSync",
                    "Firestore to Room synchronization successful."
                )

            } catch (e: Exception) {

                Log.e(
                    "ERequestSync",
                    "Firestore synchronization failed; enqueuing connected WorkManager sync.",
                    e
                )

                ERequestBackgroundSyncWorker.enqueueImmediateOnlineSync(
                    this@ERequestApplication
                )
            }
        }
    }


    // =====================================================
    // NOTIFICATION CHANNEL
    // =====================================================

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    UPDATES_CHANNEL_ID,
                    "E-Request Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {

                    description =
                        "Notifications for updated school transactions."
                }


            val notificationManager =
                getSystemService(
                    NotificationManager::class.java
                )


            notificationManager
                .createNotificationChannel(
                    channel
                )
        }
    }
}