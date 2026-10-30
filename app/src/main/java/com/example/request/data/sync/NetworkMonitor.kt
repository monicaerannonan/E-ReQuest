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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class NetworkMonitor(
    context: Context,
    private val onReconnected: () -> Unit
) {

    private val appContext = context.applicationContext

    private val connectivityManager =
        appContext.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

    private var wasOffline = false

    private var isRegistered = false

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                // Trigger reconciliation if the device just transitioned from offline -> online
                // OR if a pending FCM/offline sync was queued while disconnected.
                if (wasOffline || FirestoreSyncManager.hasPendingSync(appContext)) {
                    wasOffline = false
                    onReconnected()
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)

                if (!isInternetAvailable()) {
                    wasOffline = true
                }
            }
        }

    fun start() {
        if (isRegistered) {
            return
        }

        wasOffline = !isInternetAvailable()

        connectivityManager.registerDefaultNetworkCallback(
            networkCallback
        )

        isRegistered = true
    }

    fun stop() {
        if (!isRegistered) {
            return
        }

        try {
            connectivityManager.unregisterNetworkCallback(
                networkCallback
            )
        } catch (_: Exception) {
            // Ignore if callback is already unregistered.
        }

        isRegistered = false
    }

    private fun isInternetAvailable(): Boolean {
        val network =
            connectivityManager.activeNetwork
                ?: return false

        val capabilities =
            connectivityManager.getNetworkCapabilities(network)
                ?: return false

        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) && capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        )
    }
}
