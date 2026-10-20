package com.examcamp.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tells the rest of the app whether the phone has a network. It only reflects "a network that
 * claims internet access exists". A request can still fail on a dead Wi-Fi, so every network call
 * keeps its own try/catch. Used to show the offline banner, skip pointless waits and to retry
 * unsynced work the moment the connection comes back.
 */
class NetworkMonitor(context: Context) {

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online

    private fun hasInternet(caps: NetworkCapabilities?): Boolean =
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    private fun currentlyOnline(): Boolean = try {
        val net = cm.activeNetwork
        net != null && hasInternet(cm.getNetworkCapabilities(net))
    } catch (e: Exception) {
        true // unknown: assume online and let the requests decide
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _online.value = true
        }

        override fun onLost(network: Network) {
            // Default-network callback: a switch (Wi-Fi to mobile data) reports onAvailable for the new
            // network instead, so onLost means there is no default network left.
            _online.value = false
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _online.value = hasInternet(caps)
        }
    }

    init {
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            // Some devices refuse the registration. The flow then stays at its initial value.
        }
    }
}
