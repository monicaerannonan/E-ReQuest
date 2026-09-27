package com.example.request

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ERequestApplication : Application() {

    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            try {
                val syncManager =
                    FirestoreSyncManager(this@ERequestApplication)

                syncManager.syncAllData()

                Log.d(
                    "ERequestSync",
                    "Firestore to Room synchronization successful."
                )

            } catch (e: Exception) {

                Log.e(
                    "ERequestSync",
                    "Firestore synchronization failed.",
                    e
                )
            }
        }
    }
}