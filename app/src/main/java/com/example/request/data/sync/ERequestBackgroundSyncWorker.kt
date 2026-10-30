package com.example.request.data.sync

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

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ERequestBackgroundSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ERequestBgSyncWorker"

        const val PERIODIC_WORK_NAME = "erequest_periodic_firestore_sync"
        const val ONE_TIME_WORK_NAME = "erequest_onetime_firestore_sync"

        private const val REPEAT_INTERVAL_MINUTES = 15L
        private const val MAX_RETRY_ATTEMPTS = 3

        private fun buildConnectedConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        }

        /**
         * Safely initializes periodic background synchronization (every 15 minutes)
         * when the device has an active network connection.
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = buildConnectedConstraints()

            val periodicWorkRequest =
                PeriodicWorkRequestBuilder<ERequestBackgroundSyncWorker>(
                    REPEAT_INTERVAL_MINUTES,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS
                    )
                    .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWorkRequest
                )

            Log.d(TAG, "Periodic background Firestore sync scheduled (interval=${REPEAT_INTERVAL_MINUTES}m, requires=CONNECTED).")
        }

        /**
         * Enqueues a one-time background sync constrained to NetworkType.CONNECTED.
         * If the student is currently offline or the app is terminated before sync completes,
         * WorkManager will automatically execute this worker as soon as connectivity returns.
         */
        fun enqueueImmediateOnlineSync(context: Context) {
            val constraints = buildConnectedConstraints()

            val oneTimeWorkRequest =
                OneTimeWorkRequestBuilder<ERequestBackgroundSyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS
                    )
                    .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    ONE_TIME_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    oneTimeWorkRequest
                )

            Log.d(TAG, "One-time connected Firestore sync enqueued.")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting background Firestore -> Room sync (attempt=${runAttemptCount + 1}).")

        val database = ERequestDatabase.getDatabase(applicationContext)
        val dao = database.eRequestDao()

        return@withContext try {
            // 1. Synchronize Firestore collections with Room and generate unread update records
            //    for any modified transactions, requirements, procedures, or guidelines.
            val syncManager = FirestoreSyncManager(applicationContext)
            syncManager.syncAllData()

            // 2. Ensure any unnotified updates in Room (isRead = 0, isNotified = 0)
            //    are dispatched once to the system status bar via NotificationCompat.
            NotificationHelper(applicationContext).dispatchUnnotifiedUpdates(dao)

            Log.d(TAG, "Background Firestore sync and notification dispatch completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background Firestore sync failed: ${e.message}", e)
            FirestoreSyncManager.setPendingSync(applicationContext, true)

            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
