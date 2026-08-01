package com.grapaxels.mowell.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

class BluetoothTransport(context: Context) : MessageTransport {
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val serviceId = UUID.fromString("24cc9a42-73a8-4f65-a22a-04dc51d15262")
    @Volatile private var server: BluetoothServerSocket? = null
    private val seenPackets = ConcurrentHashMap.newKeySet<String>()
    var onMessage: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun bondedPeers(): List<Pair<String, String>> = try {
        adapter?.bondedDevices?.map { it.name.orEmpty() to it.address }?.sortedBy { it.first } ?: emptyList()
    } catch (_: SecurityException) { emptyList() }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (server != null || adapter == null) return
        Thread {
            try {
                server = adapter.listenUsingRfcommWithServiceRecord("Mowell", serviceId)
                while (!Thread.currentThread().isInterrupted) {
                    val socket = server?.accept() ?: break
                    socket.use {
                        val senderAddress = runCatching { it.remoteDevice.address }.getOrNull()
                        val raw = it.inputStream.bufferedReader().readLine().orEmpty()
                        val packet = runCatching { JSONObject(raw) }.getOrNull()
                        val packetId = packet?.optString("id").orEmpty()
                        if (packet == null || packetId.isBlank() || !seenPackets.add(packetId)) return@use
                        onMessage?.invoke(packet.optString("body"))
                        val nextTtl = packet.optInt("ttl", 1) - 1
                        if (nextTtl > 0) {
                            packet.put("ttl", nextTtl)
                            bondedPeers().filter { it.second != senderAddress }.forEach { peer ->
                                Thread { sendRaw(peer.second, packet.toString()) }.apply { isDaemon = true; start() }
                            }
                        }
                        if (seenPackets.size > 4_000) seenPackets.clear()
                    }
                }
            } catch (_: IOException) {
                // Bluetooth was disabled or listener closed.
            } catch (_: SecurityException) {
                // Permission is requested by the activity before nearby mode is enabled.
            }
        }.apply { name = "mowell-bt-server"; isDaemon = true; start() }
    }

    @SuppressLint("MissingPermission")
    override suspend fun send(peer: String?, payload: String): Boolean = withContext(Dispatchers.IO) {
        if (peer.isNullOrBlank()) return@withContext false
        val id = UUID.randomUUID().toString()
        seenPackets.add(id)
        val packet = JSONObject().put("v", 1).put("id", id).put("ttl", 4).put("sentAt", System.currentTimeMillis()).put("body", payload)
        sendRaw(peer, packet.toString())
    }

    @SuppressLint("MissingPermission")
    private fun sendRaw(peer: String, payload: String): Boolean {
        if (adapter == null) return false
        var socket: BluetoothSocket? = null
        return try {
            adapter.cancelDiscovery()
            socket = adapter.getRemoteDevice(peer).createRfcommSocketToServiceRecord(serviceId)
            socket.connect()
            socket.outputStream.bufferedWriter().use { it.appendLine(payload) }
            true
        } catch (_: Exception) {
            false
        } finally {
            try { socket?.close() } catch (_: IOException) {}
        }
    }
}
