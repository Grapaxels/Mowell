package com.grapaxels.mowell.transport

enum class Route { INTERNET, BLUETOOTH, LOCAL_ONLY }

data class DeliveryResult(val delivered: Boolean, val route: Route, val detail: String)

interface MessageTransport {
    suspend fun send(peer: String?, payload: String): Boolean
}
