package com.example.request

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

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
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

    private var startedActivityCount = 0
    private var globalFirestoreListener: FirestoreUpdateListener? = null


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
        // REAL-TIME FIRESTORE LISTENER (FOREGROUND-AWARE)
        // =================================================

        setupForegroundFirestoreListener()


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
        // INITIAL FIRESTORE -> ROOM SYNC
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


    // =====================================================
    // FOREGROUND REAL-TIME FIRESTORE LISTENER
    // =====================================================

    private fun setupForegroundFirestoreListener() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                if (startedActivityCount == 1) {
                    startGlobalFirestoreListener()
                }
            }

            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount--
                if (startedActivityCount <= 0) {
                    startedActivityCount = 0
                    stopGlobalFirestoreListener()
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun startGlobalFirestoreListener() {
        if (globalFirestoreListener == null) {
            globalFirestoreListener = FirestoreUpdateListener {
                applicationScope.launch {
                    try {
                        FirestoreSyncManager(this@ERequestApplication).syncAllData()
                        Log.d("ERequestApp", "Global real-time Firestore sync completed.")
                    } catch (e: Exception) {
                        Log.w("ERequestApp", "Global real-time Firestore sync failed: ${e.message}")
                    }
                }
            }
        }
        globalFirestoreListener?.start()
        Log.d("ERequestApp", "Global Firestore real-time listener started (app in foreground).")
    }

    private fun stopGlobalFirestoreListener() {
        globalFirestoreListener?.stop()
        Log.d("ERequestApp", "Global Firestore real-time listener stopped (app in background).")
    }
}