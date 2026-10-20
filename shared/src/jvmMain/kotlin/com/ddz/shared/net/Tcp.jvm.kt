package com.ddz.shared.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/** Desktop TCP server backed by java.net. */
actual class TcpServer actual constructor(port: Int) {
    private val server: ServerSocket = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(port))
    }

    actual val boundPort: Int get() = server.localPort

    actual suspend fun accept(): Connection = withContext(Dispatchers.IO) {
        val socket = server.accept()
        socket.tcpNoDelay = true
        socket.keepAlive = true
        DesktopSocketConnection(socket)
    }

    actual fun close() {
        runCatching { server.close() }
    }
}

/** Desktop TCP dialer. */
actual class TcpConnector actual constructor() {
    actual suspend fun connect(host: String, port: Int, timeoutMs: Long): Connection =
        withContext(Dispatchers.IO) {
            val socket = Socket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), timeoutMs.toInt())
            DesktopSocketConnection(socket)
        }
}

internal class DesktopSocketConnection(private val socket: Socket) : Connection {
    private val input = BufferedInputStream(socket.getInputStream())
    private val output = BufferedOutputStream(socket.getOutputStream())
    private val writeLock = Any()

    override val remote: String
        get() = socket.remoteSocketAddress?.toString() ?: "unknown"

    override suspend fun readFrame(): ByteArray? = withContext(Dispatchers.IO) {
        val header = ByteArray(4)
        var headerRead = 0
        while (headerRead < 4) {
            val n = input.read(header, headerRead, 4 - headerRead)
            if (n < 0) {
                if (headerRead == 0) return@withContext null
                throw EOFException("连接在帧头处中断")
            }
            headerRead += n
        }
        val length = NetworkCodec.decodeLength(header)
        if (length <= 0 || length > Protocol.MAX_FRAME_BYTES) {
            throw ProtocolException("BAD_FRAME", "非法帧长度 $length")
        }
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val n = input.read(body, offset, length - offset)
            if (n < 0) throw EOFException("连接在帧体处中断")
            offset += n
        }
        body
    }

    override suspend fun writeFrame(frame: ByteArray) = withContext(Dispatchers.IO) {
        synchronized(writeLock) {
            output.write(frame)
            output.flush()
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }
}
