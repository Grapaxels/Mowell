package com.grapaxels.mowell.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class TransportRouter(context: Context, private val bluetooth: BluetoothTransport) {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val internet = InternetTransport(context)

    fun hasInternet(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun send(conversationId: String, bluetoothPeer: String?, payload: String): DeliveryResult {
        if (hasInternet() && internet.send(conversationId, payload)) {
            return DeliveryResult(true, Route.INTERNET, "Delivered over internet")
        }
        if (bluetooth.send(bluetoothPeer, payload)) {
            return DeliveryResult(true, Route.BLUETOOTH, "Delivered to paired nearby device")
        }
        return DeliveryResult(false, Route.LOCAL_ONLY, "Saved on this phone; connect a backend or paired device")
    }
}
