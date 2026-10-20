package com.ddz.shared.net

/**
 * A connected, framed peer. Implementations read exactly one length-prefixed
 * frame per [readFrame] call and must be safe for one reader + one writer.
 */
interface Connection {
    /** Human-readable peer label, for logs only. */
    val remote: String

    /** Reads the next frame, or null on a clean EOF. */
    suspend fun readFrame(): ByteArray?

    /** Writes one frame (4-byte prefix + body) and flushes it. */
    suspend fun writeFrame(frame: ByteArray)

    /** Idempotent close. */
    fun close()
}

/** Listening TCP endpoint (host side). */
expect class TcpServer(port: Int) {
    /** Actual bound port (useful when the preferred port was taken). */
    val boundPort: Int

    /** Suspends until a client connects. */
    suspend fun accept(): Connection

    fun close()
}

/** Outbound TCP dialer (client side). */
expect class TcpConnector() {
    /** Opens a connection or throws (timeout / refused / unreachable). */
    suspend fun connect(host: String, port: Int, timeoutMs: Long): Connection
}
