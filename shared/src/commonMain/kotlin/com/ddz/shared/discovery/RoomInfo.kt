package com.ddz.shared.discovery

import kotlinx.serialization.Serializable

/**
 * Broadcast metadata of a table. Serialised into the NSD TXT record (Android)
 * and into the UDP beacon (desktop), so every field must stay small.
 */
@Serializable
data class RoomInfo(
    val name: String,
    val hostIp: String,
    val port: Int,
    val playerCount: Int = 0,
    val needsPassword: Boolean = false,
    val protocol: Int = 1,
    val state: String = "waiting",
    val updatedAt: Long = 0L,
)

/** A room found by the platform's service discovery, with local receive time. */
@Serializable
data class DiscoveredRoom(
    val room: RoomInfo,
    val lastSeen: Long,
)

/** The NSD/mDNS service type used by the game. */
const val SERVICE_TYPE: String = "_doudizhu._tcp"

/** UDP broadcast fallback used by the JVM/desktop discovery implementation. */
const val DISCOVERY_PORT: Int = 9528
