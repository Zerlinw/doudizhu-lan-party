package com.ddz.shared.discovery

import com.ddz.shared.net.NetworkJson
import com.ddz.shared.net.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import kotlin.concurrent.thread

/**
 * Desktop discovery over UDP broadcast on [DISCOVERY_PORT]. NSD/mDNS is the
 * Android path; a plain beacon keeps the desktop build dependency-free.
 */
actual class RoomDiscovery actual constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var advertiseJob: Job? = null
    private var discoverySocket: DatagramSocket? = null

    actual suspend fun advertise(room: RoomInfo) {
        stopAdvertisement()
        advertiseJob = scope.launch {
            val socket = runCatching { DatagramSocket().apply { broadcast = true } }.getOrNull()
                ?: return@launch
            try {
                while (isActive) {
                    val payload = NetworkJson.encodeToString(
                        RoomInfo.serializer(),
                        room.copy(updatedAt = currentTimeMillis()),
                    ).encodeToByteArray()
                    val packet = DatagramPacket(
                        payload,
                        payload.size,
                        InetAddress.getByName("255.255.255.255"),
                        DISCOVERY_PORT,
                    )
                    runCatching { socket.send(packet) }
                    delay(ADVERTISE_INTERVAL_MS)
                }
            } finally {
                socket.close()
            }
        }
    }

    actual fun discover(): Flow<DiscoveredRoom> = callbackFlow {
        val socket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(DISCOVERY_PORT))
            }
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        discoverySocket = socket
        thread(name = "ddz-discovery", isDaemon = true) {
            try {
                val buffer = ByteArray(8 * 1024)
                while (!socket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    val room = runCatching {
                        NetworkJson.decodeFromString(RoomInfo.serializer(), text)
                    }.getOrNull() ?: continue
                    trySend(DiscoveredRoom(room, currentTimeMillis()))
                }
            } catch (e: Exception) {
                // Closed socket ends the receive loop.
            }
        }
        awaitClose {
            socket.close()
            discoverySocket = null
        }
    }

    actual fun stop() {
        stopAdvertisement()
        discoverySocket?.close()
        discoverySocket = null
    }

    private fun stopAdvertisement() {
        advertiseJob?.cancel()
        advertiseJob = null
    }

    private companion object {
        const val ADVERTISE_INTERVAL_MS = 2_000L
    }
}

actual fun localIpAddresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
        .mapNotNull { it.hostAddress }
        .distinct()
}.getOrElse { emptyList() }

actual fun deviceName(): String {
    val os = System.getProperty("os.name") ?: "Desktop"
    val user = System.getProperty("user.name") ?: ""
    return if (user.isBlank()) os else "$os · $user"
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
