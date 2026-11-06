package com.kadaliao.kaixinguo.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * CoverStore downloads posters once, keeps them on disk as JPEG, scaled
 * down, and in memory as bitmaps while there is room. The CDN serves HEIC,
 * which Android reads from 9 on.
 */
class CoverStore(private val dir: File) {
    companion object {
        /** The longest side covers are kept at, in pixels: a grid's poster on a dense screen, or the series page's. */
        const val MAX_PIXELS = 600
    }

    private val memory = object : LruCache<String, Bitmap>(96 shl 20) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    // On the main thread, where the views ask.
    private val loading = HashMap<String, Deferred<Bitmap?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = Dispatchers.IO.limitedParallelism(6)

    init {
        dir.mkdirs()
    }

    /** The cover at `raw` if memory has it: views show it at once, without a fade. */
    fun cached(raw: String): Bitmap? = if (raw.isEmpty()) null else memory.get(key(raw))

    /** The cover at `raw`, from memory, the disk or the CDN. Call it on the main thread. */
    suspend fun image(raw: String): Bitmap? {
        if (raw.isEmpty()) return null
        val k = key(raw)
        memory.get(k)?.let { return it }
        val task = loading.getOrPut(k) { scope.async(io) { load(k, raw) } }
        val bmp = task.await()
        loading.remove(k)
        if (bmp != null) memory.put(k, bmp)
        return bmp
    }

    /** The cover's JPEG, if the disk has it. */
    fun jpeg(raw: String): ByteArray? =
        if (raw.isEmpty()) null else file(key(raw)).takeIf { it.exists() }?.let { runCatching { it.readBytes() }.getOrNull() }

    /** Names a cover by its URL without the query, whose signature changes from one response to the next. */
    private fun key(raw: String): String {
        val u = runCatching { Uri.parse(raw) }.getOrNull()
        val name = if (u?.host != null) u.host + u.path.orEmpty() else raw
        return MessageDigest.getInstance("SHA-1").digest(name.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun file(k: String) = File(dir, "$k.jpg")

    private fun load(k: String, raw: String): Bitmap? {
        val path = file(k)
        if (path.exists()) {
            BitmapFactory.decodeFile(path.path)?.let { return it }
        }
        val bytes = download(raw) ?: return null
        val img = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = MAX_PIXELS.toDouble() / max(w, h)
                if (scale < 1) decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) {
            return null
        }
        save(img, path)
        return img
    }

    private fun download(raw: String): ByteArray? {
        val conn = runCatching { URL(raw).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        return try {
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            if (conn.responseCode != 200) null else conn.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun save(img: Bitmap, path: File) {
        val tmp = File(path.path + ".tmp")
        try {
            FileOutputStream(tmp).use { img.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            tmp.renameTo(path)
        } catch (e: Exception) {
            tmp.delete()
        }
    }
}
