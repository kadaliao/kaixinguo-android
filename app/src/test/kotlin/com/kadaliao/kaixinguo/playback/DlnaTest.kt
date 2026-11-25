package com.kadaliao.kaixinguo.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import kotlin.concurrent.thread

class DlnaTest {
    private val description = """<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <device>
    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
    <friendlyName>客厅的电视</friendlyName>
    <serviceList>
      <service>
        <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
        <controlURL>/upnp/control/RenderingControl1</controlURL>
      </service>
      <service>
        <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
        <controlURL>upnp/control/AVTransport1</controlURL>
      </service>
    </serviceList>
  </device>
</root>"""

    /** A renderer's description gives its name and where its two services take commands, relative to where it was read. */
    @Test
    fun readsDescription() {
        val r = Dlna.parseDescription("http://192.168.1.20:49152/dev/desc.xml", description.toByteArray())!!
        assertEquals("客厅的电视", r.name)
        assertEquals("http://192.168.1.20:49152/dev/upnp/control/AVTransport1", r.transport)
        assertEquals("http://192.168.1.20:49152/upnp/control/RenderingControl1", r.rendering)
    }

    /** A URLBase, where there is one, is what the control URLs are relative to; a device without AVTransport is no renderer. */
    @Test
    fun urlBaseAndNonRenderers() {
        val rooted = description.replace("<specVersion>", "<URLBase>http://10.0.0.5:8080/</URLBase><specVersion>")
        assertEquals("http://10.0.0.5:8080/upnp/control/AVTransport1", Dlna.parseDescription("http://192.168.1.20:49152/desc.xml", rooted.toByteArray())?.transport)
        val router = description.replace("AVTransport", "WANIPConnection")
        assertNull(Dlna.parseDescription("http://192.168.1.1/desc.xml", router.toByteArray()))
    }

    @Test
    fun ssdpAnswers() {
        val answer = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLocation: http://192.168.1.20:49152/desc.xml\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
        assertEquals("http://192.168.1.20:49152/desc.xml", Dlna.ssdpLocation(answer))
        assertNull(Dlna.ssdpLocation("NOTIFY * HTTP/1.1\r\nLOCATION: http://x/\r\n\r\n"))
        assertTrue(Dlna.searchMessage(Dlna.AV_TRANSPORT).endsWith("ST: ${Dlna.AV_TRANSPORT}\r\n\r\n"))
    }

    @Test
    fun clocks() {
        assertEquals("0:01:05", Dlna.clock(65.7))
        assertEquals("1:02:03", Dlna.clock(3723.0))
        assertEquals(3723.5, Dlna.seconds("1:02:03.5"), 0.001)
        assertTrue(Dlna.seconds("NOT_IMPLEMENTED").isNaN())
        assertTrue(Dlna.seconds("").isNaN())
        assertTrue(Dlna.seconds(null).isNaN())
    }

    /** The title and URL go into the metadata escaped, and the metadata into the command escaped again. */
    @Test
    fun metadataIsEscaped() {
        val d = Dlna.didl("甲 & 乙 <第 1 集>", "http://10.0.0.2:4000/episode/x.mp4?a=1&b=2")
        assertTrue(d.contains("<dc:title>甲 &amp; 乙 &lt;第 1 集&gt;</dc:title>"))
        assertTrue(d.contains(">http://10.0.0.2:4000/episode/x.mp4?a=1&amp;b=2</res>"))
        assertTrue(d.contains("protocolInfo=\"http-get:*:video/mp4:DLNA.ORG_OP=01;"))
    }

    /** Runs a server that answers one request with `answer`, handing what it was asked to `asked`. */
    private fun renderer(answer: String, asked: (String) -> Unit = {}): Int {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            server.use { s ->
                s.accept().use { c ->
                    val input = c.getInputStream()
                    val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n")) head.append(input.read().toChar())
                    val length = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)?.toInt() ?: 0
                    val body = ByteArray(length)
                    var got = 0
                    while (got < length) got += input.read(body, got, length - got)
                    asked(head.toString() + String(body))
                    c.getOutputStream().write(answer.toByteArray())
                }
            }
        }
        return server.localPort
    }

    /** A command goes as a SOAP POST with its action; the answer's values come back by name, chunked or not. */
    @Test
    fun callsActions() {
        var asked = ""
        val xml = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>" +
            "<u:GetPositionInfoResponse xmlns:u=\"${Dlna.AV_TRANSPORT}\"><Track>1</Track><TrackDuration>0:02:10</TrackDuration>" +
            "<RelTime>0:00:42</RelTime></u:GetPositionInfoResponse></s:Body></s:Envelope>"
        val chunks = xml.chunked(50).joinToString("") { "%x\r\n%s\r\n".format(it.toByteArray().size, it) } + "0\r\n\r\n"
        val port = renderer("HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nTransfer-Encoding: chunked\r\n\r\n$chunks") { asked = it }
        val got = Dlna.call("http://127.0.0.1:$port/AVTransport/control", Dlna.AV_TRANSPORT, "GetPositionInfo", listOf("InstanceID" to "0"))
        assertEquals("0:00:42", got["RelTime"])
        assertEquals("0:02:10", got["TrackDuration"])
        assertTrue(asked.startsWith("POST /AVTransport/control HTTP/1.1\r\n"))
        assertTrue(asked.contains("SOAPACTION: \"${Dlna.AV_TRANSPORT}#GetPositionInfo\""))
        assertTrue(asked.contains("<u:GetPositionInfo xmlns:u=\"${Dlna.AV_TRANSPORT}\"><InstanceID>0</InstanceID></u:GetPositionInfo>"))
    }

    /** A renderer that refuses says why, in its fault. */
    @Test
    fun saysWhyItRefused() {
        val fault = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><s:Fault>" +
            "<detail><UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\"><errorCode>714</errorCode>" +
            "<errorDescription>Illegal MIME-type</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"
        val port = renderer("HTTP/1.1 500 Internal Server Error\r\nContent-Length: ${fault.toByteArray().size}\r\n\r\n$fault")
        try {
            Dlna.call("http://127.0.0.1:$port/c", Dlna.AV_TRANSPORT, "SetAVTransportURI", listOf("InstanceID" to "0"))
            fail("refused")
        } catch (e: IOException) {
            assertEquals("SetAVTransportURI 失败：714 Illegal MIME-type", e.message)
        }
    }

    @Test
    fun ranges() {
        assertEquals(0L to 999L, byteRange("bytes=0-", 1000))
        assertEquals(100L to 199L, byteRange("bytes=100-199", 1000))
        assertEquals(500L to 999L, byteRange("bytes=500-5000", 1000))
        assertEquals(900L to 999L, byteRange("bytes=-100", 1000))
        assertEquals(0L to 999L, byteRange("bytes=-5000", 1000))
        assertEquals(0L to 9L, byteRange("bytes=0-9, 20-29", 1000))
        assertNull(byteRange("bytes=1000-", 1000))
        assertNull(byteRange("bytes=5-1", 1000))
        assertNull(byteRange("bytes=-0", 1000))
        assertNull(byteRange("items=0-1", 1000))
        assertEquals("1f0b5c2e-0000-4000-8000-000000000000", StreamServer.idOf("kaixinguo-stream://episode/1f0b5c2e-0000-4000-8000-000000000000.mp4"))
    }
}
