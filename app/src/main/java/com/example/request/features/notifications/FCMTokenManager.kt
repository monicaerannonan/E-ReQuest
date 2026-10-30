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

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FCMTokenManager(
    context: Context
) {

    companion object {
        private const val TAG = "FCMTokenManager"
        private const val COLLECTION_NAME = "device_tokens"

        private const val PREFS_NAME =
            "erequest_device_preferences"

        private const val KEY_INSTALLATION_ID =
            "installation_id"
    }

    private val applicationContext =
        context.applicationContext

    private val firestore =
        FirebaseFirestore.getInstance()


    // =====================================================
    // GET OR CREATE INSTALLATION ID
    // =====================================================

    private fun getInstallationId(): String {

        val preferences =
            applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        var installationId =
            preferences.getString(
                KEY_INSTALLATION_ID,
                null
            )

        if (installationId.isNullOrBlank()) {

            installationId =
                UUID.randomUUID().toString()

            preferences
                .edit()
                .putString(
                    KEY_INSTALLATION_ID,
                    installationId
                )
                .apply()
        }

        return installationId
    }


    // =====================================================
    // GET CURRENT FCM TOKEN AND SAVE IT
    // =====================================================

    suspend fun registerCurrentToken() {

        try {

            val token =
                FirebaseMessaging
                    .getInstance()
                    .token
                    .await()

            if (token.isBlank()) {

                Log.w(
                    TAG,
                    "FCM token is empty."
                )

                return
            }

            saveToken(
                token
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Unable to register current FCM token.",
                e
            )
        }
    }


    // =====================================================
    // SAVE / UPDATE TOKEN IN FIRESTORE
    // =====================================================

    suspend fun saveToken(
        token: String
    ) {

        if (token.isBlank()) {
            return
        }

        try {

            val installationId =
                getInstallationId()

            val deviceData =
                hashMapOf<String, Any>(

                    "installationId" to
                            installationId,

                    "token" to
                            token,

                    "platform" to
                            "android",

                    "active" to
                            true,

                    "updatedAt" to
                            FieldValue.serverTimestamp()
                )

            firestore
                .collection(
                    COLLECTION_NAME
                )
                .document(
                    installationId
                )
                .set(
                    deviceData
                )
                .await()

            Log.d(
                TAG,
                "FCM device token registered successfully."
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Unable to save FCM token to Firestore.",
                e
            )
        }
    }
}