package com.example.request

import android.app.Application
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
            val database = ERequestDatabase.getDatabase(this@ERequestApplication)
            DatabaseSeeder.seedIfEmpty(database)
        }
    }
}