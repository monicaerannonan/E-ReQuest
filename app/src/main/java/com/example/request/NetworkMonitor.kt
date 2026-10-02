package com.example.request

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class NetworkMonitor(
    context: Context,
    private val onReconnected: () -> Unit
) {

    private val connectivityManager =
        context.applicationContext.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

    private var wasOffline = false

    private var isRegistered = false


    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                // Trigger ONLY if the app previously detected
                // that the device was offline.
                if (wasOffline) {

                    wasOffline = false

                    onReconnected()
                }
            }


            override fun onLost(network: Network) {
                super.onLost(network)

                // Check whether there is really no usable
                // internet connection remaining.
                if (!isInternetAvailable()) {
                    wasOffline = true
                }
            }
        }


    fun start() {

        if (isRegistered) {
            return
        }

        // IMPORTANT:
        // If the app starts while already online,
        // DO NOT trigger automatic sync.
        //
        // Automatic sync should only happen after:
        //
        // ONLINE -> OFFLINE -> ONLINE

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
            connectivityManager.getNetworkCapabilities(
                network
            ) ?: return false

        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) &&
                capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
    }
}