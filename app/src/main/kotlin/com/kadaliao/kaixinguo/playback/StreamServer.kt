package com.kadaliao.kaixinguo.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.kadaliao.kaixinguo.model.debugLog
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * StreamServer serves episodes, decrypted, over plain HTTP on the local
 * network, for a television that fetches the video itself, once DLNA has
 * told it where. Each file is one the [StreamRegistry] holds, at the path
 * of its own random ID, and takes ranges as a CDN would; [CencDataSource]
 * downloads and decrypts them as they go out, from memory as far as the
 * file's start was read ahead.
 *
 * It listens only while something is cast, on a port the system picks.
 */
class StreamServer(private val registry: StreamRegistry, private val http: DataSource.Factory) {
    private var socket: ServerSocket? = null
    private val workers = Executors.newCachedThreadPool { r -> Thread(r, "kx-serve").apply { isDaemon = true } }

    /**
     * The URL a television on the network plays an episode registered at
     * `uri` from, at this device's address there; the server starts
     * listening if it wasn't.
     */
    @Synchronized
    fun url(uri: String): String {
        val host = Dlna.localAddress()?.second?.hostAddress ?: throw IOException("没有连接 Wi-Fi，无法投屏")
        val s = socket ?: ServerSocket().also { s ->
            s.reuseAddress = true
            s.bind(InetSocketAddress(0))
            socket = s
            Thread({ accept(s) }, "kx-server").apply { isDaemon = true }.start()
        }
        return "http://$host:${s.localPort}/episode/${idOf(uri)}.mp4"
    }

    /** Stops listening, as casting ends; what is on its way out stops too. */
    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun accept(s: ServerSocket) {
        while (!s.isClosed) {
            val c = try {
                s.accept()
            } catch (_: IOException) {
                break
            }
            workers.execute { c.use { answer(it, s) } }
        }
    }

    private fun answer(c: Socket, server: ServerSocket) {
        c.soTimeout = 30_000
        val input = c.getInputStream()
        val out = c.getOutputStream().buffered(64 shl 10)
        val lines = try {
            readHead(input)
        } catch (_: IOException) {
            return
        } ?: return
        val parts = lines.first().split(' ')
        if (parts.size < 2) return reply(out, "400 Bad Request")
        val method = parts[0]
        if (method != "GET" && method != "HEAD") return reply(out, "405 Method Not Allowed")
        val id = Regex("^/episode/([0-9a-f-]{36})\\.mp4$").find(parts[1])?.groupValues?.get(1)
        val uri = id?.let { "${StreamRegistry.SCHEME}://episode/$it.mp4" }
        val stream = uri?.let { registry[it] } ?: return reply(out, "404 Not Found")
        val size = try {
            stream.prepare().size
        } catch (e: IOException) {
            debugLog { "server: $e" }
            return reply(out, "502 Bad Gateway")
        }
        val headers = mutableListOf(
            "Content-Type: video/mp4",
            "Accept-Ranges: bytes",
            "transferMode.dlna.org: Streaming",
            "contentFeatures.dlna.org: ${Dlna.DLNA_FEATURES}",
        )
        val range = headerIn(lines, "range")
        val (a, b) = if (range != null) {
            byteRange(range, size) ?: return reply(out, "416 Range Not Satisfiable", listOf("Content-Range: bytes */$size"))
        } else {
            0L to size - 1
        }
        if (range != null) headers += "Content-Range: bytes $a-$b/$size"
        headers += "Content-Length: ${b - a + 1}"
        out.write(head(if (range != null) "206 Partial Content" else "200 OK", headers))
        if (method == "HEAD") return out.flush()
        val ds = CencDataSource(registry, http)
        try {
            ds.open(DataSpec.Builder().setUri(Uri.parse(uri)).setPosition(a).setLength(b - a + 1).build())
            val buf = ByteArray(64 shl 10)
            while (!server.isClosed) {
                val n = ds.read(buf, 0, buf.size)
                if (n == C.RESULT_END_OF_INPUT) break
                out.write(buf, 0, n)
            }
            out.flush()
        } catch (e: IOException) {
            // The television hung up, as it does when it seeks, or the CDN failed.
            debugLog { "server: $a-$b: $e" }
        } finally {
            runCatching { ds.close() }
        }
    }

    private fun reply(out: OutputStream, status: String, headers: List<String> = emptyList()) {
        runCatching {
            out.write(head(status, headers + "Content-Length: 0"))
            out.flush()
        }
    }

    private fun head(status: String, headers: List<String>): ByteArray =
        (listOf("HTTP/1.1 $status") + headers + listOf("Connection: close", "", "")).joinToString("\r\n").toByteArray()

    /** The request's lines up to the blank one after its headers; null if the client hung up first. */
    private fun readHead(input: InputStream): List<String>? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = input.read()
            if (c < 0) return null
            b.write(c)
            if (b.size() > 64 shl 10) throw IOException("request too long")
            val n = b.size()
            if (n >= 4) {
                val a = b.toByteArray()
                if (a[n - 4] == '\r'.code.toByte() && a[n - 3] == '\n'.code.toByte() && a[n - 2] == '\r'.code.toByte() && a[n - 1] == '\n'.code.toByte()) {
                    return String(a, 0, n - 4, Charsets.ISO_8859_1).split("\r\n")
                }
            }
        }
    }

    companion object {
        /** The random ID in an episode's URI: kaixinguo-stream://episode/<ID>.mp4. */
        fun idOf(uri: String): String = uri.substringAfterLast('/').removeSuffix(".mp4")
    }
}

/**
 * The bytes a Range header asks for of a file `size` long, first and last
 * included: "bytes=a-b", "bytes=a-" or the last n, "bytes=-n"; only the
 * first range of several. Null when none of it is in the file.
 */
fun byteRange(header: String, size: Long): Pair<Long, Long>? {
    if (!header.lowercase().startsWith("bytes=") || size <= 0) return null
    val spec = header.substring(6).split(',').first().trim()
    val dash = spec.indexOf('-')
    if (dash < 0) return null
    val first = spec.substring(0, dash).trim().toLongOrNull()
    val last = spec.substring(dash + 1).trim().toLongOrNull()
    return when {
        first != null && last != null -> if (first <= last && first < size) first to minOf(last, size - 1) else null
        first != null -> if (first < size) first to size - 1 else null
        last != null -> if (last > 0) maxOf(0, size - last) to size - 1 else null
        else -> null
    }
}
