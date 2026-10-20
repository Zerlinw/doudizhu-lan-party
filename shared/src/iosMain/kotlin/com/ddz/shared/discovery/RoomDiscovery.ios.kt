package com.ddz.shared.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * iOS discovery placeholder (M5 compile-only). M6 will wrap NetService/Bonjour;
 * until then the iOS shell is limited to the lobby mock.
 */
actual class RoomDiscovery actual constructor() {
    actual suspend fun advertise(room: RoomInfo) = Unit

    actual fun discover(): Flow<DiscoveredRoom> = emptyFlow()

    actual fun stop() = Unit
}

actual fun localIpAddresses(): List<String> = emptyList()

actual fun deviceName(): String = "iOS"

actual fun currentTimeMillis(): Long = 0L
