package com.kadaliao.kaixinguo.playback

import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Renderer is a television, or a box or a projector, that plays a video
 * from a URL it is given over DLNA (UPnP AV): where it describes itself,
 * its name, and where it takes the commands that play the video and set
 * its volume.
 */
data class Renderer(
    val location: String,
    val name: String,
    /** Where AVTransport's commands go: SetAVTransportURI, Play, Pause, Seek… */
    val transport: String,
    /** Where RenderingControl's go, to set the volume; null without one. */
    val rendering: String?,
)

/**
 * Dlna finds renderers on the local network and works them. Everything
 * here blocks, on a thread of its own; the HTTP is spoken over plain
 * sockets, since Android lets the app's HTTP clients reach nothing but
 * HTTPS, and renderers speak only HTTP.
 */
object Dlna {
    const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"
    private const val MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"
    private val group = InetSocketAddress("239.255.255.250", 1900)

    /**
     * Asks the network for renderers, over SSDP, and hands each one's
     * description URL to `found` as it answers, once each, for `ms`
     * milliseconds. The search goes out on `via`, the Wi-Fi, when given.
     */
    fun search(via: NetworkInterface?, ms: Long, found: (String) -> Unit) {
        MulticastSocket(null as InetSocketAddress?).use { s ->
            s.reuseAddress = true
            s.bind(InetSocketAddress(0))
            if (via != null) s.networkInterface = via
            s.timeToLive = 4
            s.soTimeout = 300
            val seen = HashSet<String>()
            val end = System.currentTimeMillis() + ms
            var next = 0L
            var sent = 0
            val buf = ByteArray(8 shl 10)
            while (System.currentTimeMillis() < end) {
                // Asked a few times: a lost packet is common over Wi-Fi.
                if (sent < 3 && System.currentTimeMillis() >= next) {
                    for (st in listOf(MEDIA_RENDERER, AV_TRANSPORT)) {
                        val m = searchMessage(st).toByteArray()
                        s.send(DatagramPacket(m, m.size, group))
                    }
                    sent++
                    next = System.currentTimeMillis() + 800
                }
                val p = DatagramPacket(buf, buf.size)
                try {
                    s.receive(p)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val loc = ssdpLocation(String(p.data, 0, p.length, Charsets.UTF_8)) ?: continue
                if (seen.add(loc)) found(loc)
            }
        }
    }

    fun searchMessage(st: String) =
        "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $st\r\n\r\n"

    /** The description URL in an answer to a search, if it is one. */
    fun ssdpLocation(answer: String): String? {
        val lines = answer.split("\r\n", "\n")
        if (lines.firstOrNull()?.startsWith("HTTP/1.1 200") != true) return null
        return headerIn(lines, "location")?.takeIf { it.startsWith("http://") }
    }

    /** Reads the renderer's description; null when it doesn't play videos from a URL. */
    fun describe(location: String): Renderer? {
        val r = Http.request(location, "GET")
        if (r.status != 200) throw IOException("设备描述读取失败：${r.status}")
        return parseDescription(location, r.body)
    }

    fun parseDescription(location: String, xml: ByteArray): Renderer? {
        val doc = parseXml(xml)
        val base = doc.documentElement.childText("URLBase")?.takeIf { it.isNotBlank() } ?: location
        var transport: String? = null
        var rendering: String? = null
        val services = doc.getElementsByTagNameNS("*", "service")
        for (i in 0 until services.length) {
            val s = services.item(i) as Element
            val type = s.childText("serviceType").orEmpty()
            val control = s.childText("controlURL")?.takeIf { it.isNotBlank() } ?: continue
            when {
                type.startsWith("urn:schemas-upnp-org:service:AVTransport:") && transport == null -> transport = resolve(base, control)
                type.startsWith("urn:schemas-upnp-org:service:RenderingControl:") && rendering == null -> rendering = resolve(base, control)
            }
        }
        transport ?: return null
        val names = doc.getElementsByTagNameNS("*", "friendlyName")
        val name = (if (names.length > 0) names.item(0).textContent.trim() else "").ifEmpty { URI(location).host }
        return Renderer(location, name, transport, rendering)
    }

    private fun resolve(base: String, path: String): String = URI(base).resolve(path.trim()).toString()

    /** Calls an action of a renderer's service, and returns what it answers by name. */
    fun call(control: String, service: String, action: String, args: List<Pair<String, String>>): Map<String, String> {
        val body = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
            append("<s:Body><u:$action xmlns:u=\"$service\">")
            for ((k, v) in args) append("<$k>").append(escapeXml(v)).append("</$k>")
            append("</u:$action></s:Body></s:Envelope>")
        }.toByteArray()
        val r = Http.request(
            control, "POST",
            listOf("Content-Type" to "text/xml; charset=\"utf-8\"", "SOAPACTION" to "\"$service#$action\""),
            body,
        )
        if (r.status != 200) throw IOException("$action 失败：${soapFault(r.body) ?: r.status.toString()}")
        return soapResult(r.body, action)
    }

    /** The values in a SOAP answer to an action, by name. */
    fun soapResult(xml: ByteArray, action: String): Map<String, String> {
        val doc = parseXml(xml)
        val found = doc.getElementsByTagNameNS("*", action + "Response")
        if (found.length == 0) return emptyMap()
        val out = LinkedHashMap<String, String>()
        val kids = found.item(0).childNodes
        for (i in 0 until kids.length) {
            val k = kids.item(i) as? Element ?: continue
            out[k.localName ?: k.tagName] = k.textContent
        }
        return out
    }

    /** What a SOAP fault says went wrong, if the body is one. */
    fun soapFault(xml: ByteArray): String? = runCatching {
        val doc = parseXml(xml)
        val desc = doc.getElementsByTagNameNS("*", "errorDescription")
        val code = doc.getElementsByTagNameNS("*", "errorCode")
        listOfNotNull(
            code.takeIf { it.length > 0 }?.item(0)?.textContent?.trim(),
            desc.takeIf { it.length > 0 }?.item(0)?.textContent?.trim(),
        ).joinToString(" ").ifEmpty { null }
    }.getOrNull()

    /**
     * The DIDL-Lite a renderer is told the video is: its title, and a
     * resource it fetches over HTTP as an MP4, by ranges.
     */
    fun didl(title: String, url: String): String =
        "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
            "<dc:title>${escapeXml(title)}</dc:title>" +
            "<upnp:class>object.item.videoItem</upnp:class>" +
            "<res protocolInfo=\"http-get:*:video/mp4:$DLNA_FEATURES\">${escapeXml(url)}</res>" +
            "</item></DIDL-Lite>"

    /** What the file allows a renderer, said the DLNA way: ranges, streaming. */
    const val DLNA_FEATURES = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

    fun escapeXml(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    /** Seconds as DLNA writes a time: 1:02:03. */
    fun clock(seconds: Double): String {
        val n = seconds.coerceAtLeast(0.0).toLong()
        return "%d:%02d:%02d".format(n / 3600, n / 60 % 60, n % 60)
    }

    /** A time as DLNA writes it, in seconds; NaN for none ("NOT_IMPLEMENTED", empty). */
    fun seconds(clock: String?): Double {
        val parts = clock?.trim()?.split(':') ?: return Double.NaN
        if (parts.size != 3) return Double.NaN
        val h = parts[0].toLongOrNull() ?: return Double.NaN
        val m = parts[1].toLongOrNull() ?: return Double.NaN
        val s = parts[2].toDoubleOrNull() ?: return Double.NaN
        return h * 3600.0 + m * 60 + s
    }

    private fun parseXml(xml: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        // A renderer's XML has no business pulling in anything else.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        isExpandEntityReferences = false
    }.newDocumentBuilder().parse(xml.inputStream())

    private fun Element.childText(name: String): String? {
        val kids = childNodes
        for (i in 0 until kids.length) {
            val k = kids.item(i) as? Element ?: continue
            if ((k.localName ?: k.tagName) == name) return k.textContent.trim()
        }
        return null
    }

    /**
     * This device's IPv4 address on the local network, and its interface:
     * the Wi-Fi's, or else any other private one, as an emulator's or over
     * Ethernet; null off any.
     */
    fun localAddress(): Pair<NetworkInterface, Inet4Address>? {
        val all = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        fun v4(i: NetworkInterface) = i.inetAddresses.toList().filterIsInstance<Inet4Address>().firstOrNull { it.isSiteLocalAddress }
        val ranked = all.sortedBy { if (it.name.startsWith("wlan")) 0 else if (it.name.startsWith("eth")) 1 else 2 }
        for (i in ranked) {
            if (i.name.startsWith("rmnet") || i.name.startsWith("ccmni") || i.name.startsWith("tun")) continue // mobile data, VPNs
            v4(i)?.let { return i to it }
        }
        return null
    }
}

internal fun headerIn(lines: List<String>, name: String): String? {
    for (line in lines.drop(1)) {
        val colon = line.indexOf(':')
        if (colon > 0 && line.substring(0, colon).trim().equals(name, ignoreCase = true)) return line.substring(colon + 1).trim()
    }
    return null
}

/** Http is just enough HTTP/1.1 over a socket for a renderer: one request a connection, plain or chunked answers. */
object Http {
    class Response(val status: Int, val headers: List<String>, val body: ByteArray)

    fun request(url: String, method: String, headers: List<Pair<String, String>> = emptyList(), body: ByteArray? = null): Response {
        val u = URI(url)
        if (u.scheme != "http") throw IOException("只支持 http：$url")
        val port = if (u.port > 0) u.port else 80
        val path = (u.rawPath?.ifEmpty { "/" } ?: "/") + (u.rawQuery?.let { "?$it" } ?: "")
        Socket().use { s ->
            s.connect(InetSocketAddress(InetAddress.getByName(u.host), port), 3000)
            s.soTimeout = 6000
            val head = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: ${u.host}:$port\r\n")
                append("User-Agent: Kaixinguo DLNA/1.0\r\n")
                append("Connection: close\r\n")
                for ((k, v) in headers) append("$k: $v\r\n")
                if (body != null) append("Content-Length: ${body.size}\r\n")
                append("\r\n")
            }
            val out = s.getOutputStream()
            out.write(head.toByteArray(Charsets.UTF_8))
            if (body != null) out.write(body)
            out.flush()
            return read(s.getInputStream())
        }
    }

    fun read(input: InputStream): Response {
        val lines = ArrayList<String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            lines += line
        }
        val status = lines.firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: throw IOException("设备的回应看不懂")
        val chunked = headerIn(lines, "transfer-encoding")?.contains("chunked", ignoreCase = true) == true
        val length = headerIn(lines, "content-length")?.toIntOrNull()
        val body = ByteArrayOutputStream()
        when {
            chunked -> while (true) {
                val size = readLine(input)?.substringBefore(';')?.trim()?.toIntOrNull(16) ?: break
                if (size == 0) break
                body.write(readFully(input, size))
                readLine(input)
            }
            length != null -> body.write(readFully(input, length))
            else -> input.copyTo(body)
        }
        return Response(status, lines, body.toByteArray())
    }

    private fun readLine(input: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = input.read()
            if (c < 0) return if (b.size() == 0) null else b.toString(Charsets.UTF_8.name())
            if (c == '\n'.code) return b.toString(Charsets.UTF_8.name()).trimEnd('\r')
            b.write(c)
            if (b.size() > 16 shl 10) throw IOException("设备的回应太长")
        }
    }

    private fun readFully(input: InputStream, n: Int): ByteArray {
        if (n > 4 shl 20) throw IOException("设备的回应太长")
        val buf = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = input.read(buf, got, n - got)
            if (r < 0) break
            got += r
        }
        return if (got == n) buf else buf.copyOf(got)
    }
}
