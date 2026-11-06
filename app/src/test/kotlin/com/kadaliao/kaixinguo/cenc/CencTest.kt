package com.kadaliao.kaixinguo.cenc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The ways a file can keep its sample encryption info. */
enum class Info {
    SENC, // in a senc box inside stbl
    SENC_SUBSAMPLES, // in a senc box, with subsamples
    SAIO, // in mdat, which saiz sizes and saio places
}

/**
 * A small CENC file built from scratch: an encrypted video track whose
 * samples were encrypted with the JDK's own CTR mode, so the test does not
 * check the decryptor against itself.
 */
class TestFile(info: Info, moovLast: Boolean = false, count: Int = 12) {
    val key = ByteArray(16) { (it * 7 + 3).toByte() }
    var file = ByteArray(0) // encrypted
    var clear = ByteArray(0) // what decrypting it must give

    init {
        val rng = SplitMix(42)
        val sizes = List(count) { 3000 + (rng.next() % 9000u).toInt() }
        val plain = sizes.map { n -> ByteArray(n) { rng.next().toByte() } }
        val ivs = List(count) { ByteArray(8) { rng.next().toByte() } }
        val subs = sizes.map { n ->
            if (info == Info.SENC_SUBSAMPLES) listOf(100 to 1000, 37 to n - 100 - 1000 - 37 - 50, 50 to 0) else emptyList()
        }
        val encrypted = plain.indices.map { encrypt(plain[it], ivs[it], subs[it], key) }
        // Two passes: the second knows where mdat's payload starts.
        var payloadAt = 0
        for (pass in 0 until 2) {
            val aux = if (info == Info.SAIO) cat(ivs) else ByteArray(0)
            val auxAt = payloadAt
            val samplesAt = payloadAt + aux.size
            fun build(clear: Boolean): ByteArray {
                val ftyp = box("ftyp", ascii("isom") + u32(0) + ascii("isomiso2"))
                val moov = moov(clear, info, sizes, ivs, subs, samplesAt, auxAt)
                val mdat = box("mdat", aux + cat(if (clear) plain else encrypted))
                return if (moovLast) ftyp + mdat + moov else ftyp + moov + mdat
            }
            file = build(clear = false)
            clear = build(clear = true)
            if (pass == 0) {
                // The payload follows ftyp and moov, or ftyp alone.
                val ftypSize = 8 + 4 + 4 + 8
                val moovSize = file.size - ftypSize - 8 - aux.size - encrypted.sumOf { it.size }
                payloadAt = if (moovLast) ftypSize + 8 else ftypSize + moovSize + 8
            }
        }
    }

    private fun moov(
        clear: Boolean,
        info: Info,
        sizes: List<Int>,
        ivs: List<ByteArray>,
        subs: List<List<Pair<Int, Int>>>,
        chunkAt: Int,
        auxAt: Int,
    ): ByteArray {
        val n = sizes.size
        val tenc = fullBox("tenc", bytes(0, 0, 1, 8) + ByteArray(16) { 0xab.toByte() })
        val sinf = box(
            if (clear) "free" else "sinf",
            box("frma", ascii("hvc1")) + fullBox("schm", ascii("cenc") + u32(0x10000)) + box("schi", tenc),
        )
        val entry = box(if (clear) "hvc1" else "encv", ByteArray(78) + box("hvcC", bytes(1, 2, 3, 4)) + sinf)
        val stsd = fullBox("stsd", u32(1) + entry)
        val stsz = fullBox("stsz", u32(0) + u32(n) + cat(sizes.map { u32(it) }))
        // Two chunks, to walk stsc's runs: the first holds 5 samples.
        val first = 5
        val stsc = fullBox("stsc", u32(2) + u32(1) + u32(first) + u32(1) + u32(2) + u32(n - first) + u32(1))
        val second = chunkAt + sizes.take(first).sum()
        val stco = fullBox("stco", u32(2) + u32(chunkAt) + u32(second))
        val protection = when (info) {
            Info.SENC, Info.SENC_SUBSAMPLES -> {
                val body = ByteArrayOutputStream()
                body.write(u32(n))
                for (k in 0 until n) {
                    body.write(ivs[k])
                    if (info == Info.SENC_SUBSAMPLES) {
                        body.write(u16(subs[k].size))
                        for ((c, e) in subs[k]) body.write(u16(c) + u32(e))
                    }
                }
                fullBox(if (clear) "free" else "senc", flags = if (info == Info.SENC_SUBSAMPLES) 2 else 0, body = body.toByteArray())
            }
            Info.SAIO -> fullBox(if (clear) "free" else "saiz", bytes(8) + u32(n)) +
                fullBox(if (clear) "free" else "saio", u32(1) + u32(auxAt))
        }
        val stbl = box("stbl", stsd + stsz + stsc + stco + protection)
        val trak = box("trak", box("mdia", box("minf", stbl)))
        val pssh = fullBox(if (clear) "free" else "pssh", ByteArray(20) { 0x11 })
        return box("moov", trak + pssh)
    }

    val fetch = Fetch { off, n -> file.copyOfRange(off.toInt(), (off + n).toInt()) }
}

class CencTest {
    @Test
    fun decryptsWholeFile() {
        for (info in Info.entries) {
            val s = TestFile(info)
            val layout = Layout.open(s.file.size.toLong(), s.key, s.fetch)
            assertTrue(layout.isProtected)
            val out = s.file.copyOf()
            layout.decrypt(out, 0)
            assertArrayEquals("$info", s.clear, out)
        }
    }

    /** Any range decrypts to the same bytes as the whole, as a player seeking through the stream needs. */
    @Test
    fun decryptsInPieces() {
        for (info in Info.entries) {
            val s = TestFile(info)
            val layout = Layout.open(s.file.size.toLong(), s.key, s.fetch)
            val rng = SplitMix(7)
            repeat(10) {
                val out = s.file.copyOf()
                var off = 0
                while (off < out.size) {
                    val n = minOf(1 + (rng.next() % 9000u).toInt(), out.size - off)
                    layout.decrypt(out, off, n, off.toLong())
                    off += n
                }
                assertArrayEquals("$info", s.clear, out)
            }
        }
    }

    /** A file that keeps moov after mdat, past the first read, is found. */
    @Test
    fun findsMoovAfterMdat() {
        val s = TestFile(Info.SENC, moovLast = true)
        assertTrue(s.file.size > 64 shl 10)
        val layout = Layout.open(s.file.size.toLong(), s.key, s.fetch)
        val out = s.file.copyOf()
        layout.decrypt(out, 0)
        assertArrayEquals(s.clear, out)
    }

    @Test
    fun rejectsBadKey() {
        val s = TestFile(Info.SENC)
        assertThrows(CencException::class.java) { Layout.open(s.file.size.toLong(), bytes(1, 2, 3), s.fetch) }
    }

    /**
     * Decrypts a real episode when CENC_SAMPLE names one and CENC_KEY holds
     * its key. CENC_CLEAR, when set, names the clear file the macOS app's Go
     * decryptor made of it, which this one must match byte for byte.
     */
    @Test
    fun decryptsRealEpisode() {
        val sample = System.getenv("CENC_SAMPLE")
        assumeTrue(sample != null)
        val src = File(sample!!).readBytes()
        val key = com.kadaliao.kaixinguo.model.hexBytes(System.getenv("CENC_KEY") ?: "")
        val layout = Layout.open(src.size.toLong(), key) { off, n -> src.copyOfRange(off.toInt(), (off + n).toInt()) }
        assertTrue(layout.isProtected)
        val whole = src.copyOf()
        layout.decrypt(whole, 0)
        System.getenv("CENC_CLEAR")?.let { assertArrayEquals("differs from the Go decryptor's output", File(it).readBytes(), whole) }
        val rng = SplitMix(1)
        repeat(5) { round ->
            val pieces = src.copyOf()
            var off = 0
            while (off < pieces.size) {
                val n = minOf(1 + (rng.next() % 70000u).toInt(), pieces.size - off)
                layout.decrypt(pieces, off, n, off.toLong())
                off += n
            }
            assertArrayEquals("round $round: pieces differ from the whole", whole, pieces)
        }
    }
}

// MARK: - Helpers

private fun encrypt(plain: ByteArray, iv: ByteArray, subs: List<Pair<Int, Int>>, key: ByteArray): ByteArray {
    // The encrypted ranges, as one CTR stream.
    val ranges = if (subs.isEmpty()) {
        listOf(0 until plain.size)
    } else {
        var pos = 0
        subs.map { (c, e) ->
            pos += c
            (pos until pos + e).also { pos += e }
        }
    }
    val stream = cat(ranges.map { plain.copyOfRange(it.first, it.last + 1) })
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv + ByteArray(8)))
    val enc = cipher.doFinal(stream)
    val out = plain.copyOf()
    var at = 0
    for (r in ranges) {
        val len = r.last + 1 - r.first
        System.arraycopy(enc, at, out, r.first, len)
        at += len
    }
    return out
}

private fun cat(parts: List<ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    for (p in parts) out.write(p)
    return out.toByteArray()
}

private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

private fun box(type: String, body: ByteArray) = u32(8 + body.size) + ascii(type) + body

private fun fullBox(type: String, body: ByteArray, version: Int = 0, flags: Int = 0) =
    box(type, bytes(version, flags shr 16 and 0xff, flags shr 8 and 0xff, flags and 0xff) + body)

private fun u16(v: Int) = bytes(v shr 8 and 0xff, v and 0xff)

private fun u32(v: Int) = bytes(v ushr 24 and 0xff, v shr 16 and 0xff, v shr 8 and 0xff, v and 0xff)

/** SplitMix is a small seeded generator, so the samples are the same on every run. */
class SplitMix(seed: Long) {
    private var state = seed.toULong()

    fun next(): ULong {
        state += 0x9e3779b97f4a7c15uL
        var z = state
        z = (z xor (z shr 30)) * 0xbf58476d1ce4e5b9uL
        z = (z xor (z shr 27)) * 0x94d049bb133111ebuL
        return z xor (z shr 31)
    }
}
