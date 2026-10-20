package com.ddz.shared.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.ddz.shared.net.Protocol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Holds the application context used by platform services (set in Application.onCreate). */
object AndroidPlatform {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun requireContext(): Context =
        appContext ?: error("AndroidPlatform.init(context) 未调用：请在 Application 中初始化")

    fun contextOrNull(): Context? = appContext
}

/** Android NSD implementation of [_doudizhu._tcp] advertising + discovery. */
actual class RoomDiscovery actual constructor() {

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val nsdManager: NsdManager
        get() = AndroidPlatform.requireContext()
            .getSystemService(Context.NSD_SERVICE) as NsdManager

    actual suspend fun advertise(room: RoomInfo) {
        stopAdvertisement()
        val manager = nsdManager
        val serviceInfo = NsdServiceInfo().apply {
            // Keep the DNS-SD instance label ASCII and unique per host/port; the
            // human-readable room name travels in the TXT record below.
            serviceName = "DDZ-${room.port}-${room.name.hashCode().and(0xFFFF).toString(16)}"
            serviceType = SERVICE_TYPE
            port = room.port
            setAttribute("room", room.name)
            setAttribute("ip", room.hostIp)
            setAttribute("players", room.playerCount.toString())
            setAttribute("needsPassword", room.needsPassword.toString())
            setAttribute("protocol", Protocol.VERSION.toString())
            setAttribute("state", room.state)
        }

        suspendCancellableCoroutine { cont ->
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    if (cont.isActive) {
                        cont.resumeWithException(IOException("NSD 注册失败（错误码 $errorCode）"))
                    }
                }

                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            }
            registrationListener = listener
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            cont.invokeOnCancellation {
                runCatching { manager.unregisterService(listener) }
            }
        }
    }

    actual fun discover(): Flow<DiscoveredRoom> = callbackFlow {
        val manager = nsdManager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val type = serviceInfo.serviceType ?: return
                if (!type.startsWith(SERVICE_TYPE) && type != SERVICE_TYPE) return
                runCatching {
                    manager.resolveService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit

                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val room = info.toRoomInfo() ?: return
                                trySend(DiscoveredRoom(room, currentTimeMillis()))
                            }
                        },
                    )
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) {
                close()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IOException("NSD 发现启动失败（错误码 $errorCode）"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discoveryListener = listener
        manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { manager.stopServiceDiscovery(listener) }
            discoveryListener = null
        }
    }

    actual fun stop() {
        stopAdvertisement()
        discoveryListener?.let { listener ->
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
    }

    private fun stopAdvertisement() {
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching { nsdManager.unregisterService(listener) }
    }

    private fun NsdServiceInfo.toRoomInfo(): RoomInfo? {
        val attrs = attributes ?: emptyMap()
        fun text(key: String): String? = attrs[key]?.decodeToString()
        val host = host?.hostAddress ?: text("ip") ?: return null
        val name = text("room")?.takeIf { it.isNotBlank() } ?: serviceName ?: return null
        return RoomInfo(
            name = name,
            hostIp = host,
            port = port,
            playerCount = text("players")?.toIntOrNull() ?: 0,
            needsPassword = text("needsPassword")?.toBooleanStrictOrNull() ?: false,
            protocol = text("protocol")?.toIntOrNull() ?: Protocol.VERSION,
            state = text("state") ?: "waiting",
            updatedAt = currentTimeMillis(),
        )
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
    val manufacturer = Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: ""
    val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
    return if (manufacturer.isNotEmpty() && !model.startsWith(manufacturer, ignoreCase = true)) {
        "$manufacturer $model"
    } else {
        model
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
