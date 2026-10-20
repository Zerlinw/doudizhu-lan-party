package com.ddz.shared.discovery

import kotlinx.coroutines.flow.Flow

/**
 * Platform service advertisement / discovery.
 *
 * Android: [android.net.nsd.NsdManager] (`_doudizhu._tcp`).
 * Desktop: UDP broadcast beacon on [DISCOVERY_PORT] (no external dependency).
 * iOS: compile-only stub in the M5 preview.
 *
 * Discovery is best-effort; the UI always offers manual `IP:port` entry.
 */
expect class RoomDiscovery() {

    /** Register (or re-register) this device as a host for [room]. */
    suspend fun advertise(room: RoomInfo)

    /** Cold flow of rooms seen while collecting. Never throws on transient NSD errors. */
    fun discover(): Flow<DiscoveredRoom>

    /** Unregister the advertisement and stop discovery. */
    fun stop()
}

/** All non-loopback IPv4 addresses of this device, for the manual-connect hint. */
expect fun localIpAddresses(): List<String>

/** Human-readable device name used as the default room/player name. */
expect fun deviceName(): String

/** Wall-clock milliseconds; used for discovery freshness timestamps. */
expect fun currentTimeMillis(): Long
