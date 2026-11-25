package com.kadaliao.kaixinguo.playback

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kadaliao.kaixinguo.model.TimeControl
import com.kadaliao.kaixinguo.model.Video
import com.kadaliao.kaixinguo.model.VideoItem
import com.kadaliao.kaixinguo.model.VideoListener
import com.kadaliao.kaixinguo.model.debugLog
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * CastVideo is the video the app plays: here, on [local], or cast to a
 * television over DLNA. Cast, the television fetches the episode from the
 * app's [StreamServer], decrypted, and plays it itself; this tells it what
 * to play and when to pause or seek, and asks it every second how far it
 * has got. The player above sees one video either way.
 *
 * Its state belongs to the main thread; the network goes through a thread
 * of its own, one command at a time, in order.
 */
class CastVideo(context: Context, val local: ExoVideo, private val server: StreamServer) : Video {
    /** The television the episode plays on; null for here. */
    var device by mutableStateOf<Renderer?>(null)
        private set

    /** The televisions the last search found, and whether one goes on. */
    var devices by mutableStateOf(emptyList<Renderer>())
        private set
    var searching by mutableStateOf(false)
        private set

    /** The television's volume, from 0 to 1; NaN until known, or where it can't be set. */
    var volume by mutableFloatStateOf(Float.NaN)
        private set

    /** Hears the casting change: started, stopped, playing, paused, moved on. The media session follows it. */
    var onChange: (() -> Unit)? = null

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "kx-cast").apply { isDaemon = true } }
    private val finder = Executors.newSingleThreadExecutor { r -> Thread(r, "kx-cast-search").apply { isDaemon = true } }

    // What plays there, on the main thread.
    private var item: VideoItem? = null // the episode loaded, here or there
    private var gen = 0 // tells this episode's answers from those of an episode before
    private var wants = false // whether the viewer asked for it to play
    private var there = TimeControl.PAUSED // what the television is doing
    private var pos = Double.NaN // as the television last said, at posAt
    private var posAt = 0L
    private var dur = Double.NaN
    private var seekTo: Double? = null // where to start, once the television has the episode
    private var sawPlaying = false
    private var lastPlaying = 0.0 // where it was as it last played: near the end, a stop is the end
    private var ended = false
    private var settleUntil = 0L // a command just sent: what the television says till then may be from before it
    private var failures = 0
    private var poll: ScheduledFuture<*>? = null
    private val volumeTarget = AtomicInteger(-1)
    private var locks: Pair<WifiManager.WifiLock?, PowerManager.WakeLock?>? = null

    override var listener: VideoListener? = null

    init {
        // What the local video does counts only while it plays here.
        local.listener = object : VideoListener {
            override fun statusChanged() {
                if (device == null) listener?.statusChanged()
            }

            override fun ended() {
                if (device == null) listener?.ended()
            }

            override fun failed(message: String) {
                if (device == null) listener?.failed(message)
            }

            override fun aspectChanged(aspect: Float) {
                if (device == null) listener?.aspectChanged(aspect)
            }
        }
    }

    // MARK: - Video

    override val remote get() = device != null

    /** The episode cast, for the media session; null when nothing plays there. */
    val castItem: VideoItem? get() = if (device != null) item else null

    override val hasItem get() = if (device != null) item != null else local.hasItem

    override val position: Double
        get() {
            if (device == null) return local.position
            if (item == null || !pos.isFinite()) return Double.NaN
            val t = if (there == TimeControl.PLAYING) pos + (SystemClock.elapsedRealtime() - posAt) / 1000.0 else pos
            return if (dur.isFinite()) minOf(t, dur) else t
        }

    override val duration get() = if (device != null) dur else local.duration

    override val wantsToPlay get() = if (device != null) wants else local.wantsToPlay

    override val status get() = if (device != null) there else local.status

    /** The speed here; a television plays at its own, which DLNA can't change. */
    override var speed: Float
        get() = local.speed
        set(value) {
            local.speed = value
        }

    override var boost: Float
        get() = local.boost
        set(value) {
            local.boost = value
        }

    override fun load(item: VideoItem, start: Double) {
        this.item = item
        if (device == null) local.load(item, start) else castLoad(item, start)
    }

    override fun clear() {
        item = null
        val d = device ?: return local.clear()
        gen++
        stopPolling()
        wants = false
        pos = Double.NaN
        dur = Double.NaN
        setRemote(TimeControl.PAUSED)
        send { Dlna.call(d.transport, Dlna.AV_TRANSPORT, "Stop", listOf("InstanceID" to "0")) }
    }

    override fun play() {
        val d = device ?: return local.play()
        if (item == null) return
        wants = true
        if (there == TimeControl.PAUSED) setRemote(TimeControl.WAITING)
        settle()
        send { Dlna.call(d.transport, Dlna.AV_TRANSPORT, "Play", listOf("InstanceID" to "0", "Speed" to "1")) }
    }

    override fun pause() {
        val d = device ?: return local.pause()
        if (item == null) return
        pos = position
        posAt = SystemClock.elapsedRealtime()
        wants = false
        setRemote(TimeControl.PAUSED)
        settle()
        send { Dlna.call(d.transport, Dlna.AV_TRANSPORT, "Pause", listOf("InstanceID" to "0")) }
    }

    override fun seek(seconds: Double) {
        val d = device ?: return local.seek(seconds)
        if (item == null) return
        pos = seconds
        posAt = SystemClock.elapsedRealtime()
        ended = false
        settle()
        send { Dlna.call(d.transport, Dlna.AV_TRANSPORT, "Seek", listOf("InstanceID" to "0", "Unit" to "REL_TIME", "Target" to Dlna.clock(seconds))) }
    }

    // MARK: - Finding televisions

    private val added = HashSet<String>() // televisions added by hand, which no search drops

    /**
     * Looks for televisions on the Wi-Fi for a few seconds, listing them as
     * they answer; those listed before that don't answer go once it ends,
     * but for the one cast to.
     */
    fun search() {
        if (searching) return
        searching = true
        finder.execute {
            val seen = HashSet<String>()
            val multicast = (app.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.createMulticastLock("kx-cast")
            try {
                multicast?.acquire()
                Dlna.search(Dlna.localAddress()?.first, 4000) { loc ->
                    seen += loc
                    describe(loc)
                }
            } catch (e: Exception) {
                debugLog { "cast: search: $e" }
            } finally {
                runCatching { multicast?.release() }
                main.post {
                    searching = false
                    devices = devices.filter { it.location in seen || it.location in added || it == device }
                }
            }
        }
    }

    /** Adds the television that describes itself at `location`, found some other way than a search. */
    fun add(location: String) {
        added += location
        finder.execute { describe(location) }
    }

    private fun describe(location: String) {
        val r = try {
            Dlna.describe(location)
        } catch (e: Exception) {
            debugLog { "cast: $location: $e" }
            null
        } ?: return
        main.post { devices = if (devices.any { it.location == r.location }) devices.map { if (it.location == r.location) r else it } else devices + r }
    }

    // MARK: - Casting

    /**
     * Plays the episode on `to` from where it is here, playing or paused as
     * it was; the video here lets it go.
     */
    fun cast(to: Renderer) {
        val was = device
        if (was == to) return
        val t: Double
        val playing: Boolean
        if (was == null) {
            t = local.position
            playing = local.wantsToPlay
            device = to
            local.pause()
            local.clear()
            hold(true)
        } else {
            t = position
            playing = wants
            gen++
            stopPolling()
            send { Dlna.call(was.transport, Dlna.AV_TRANSPORT, "Stop", listOf("InstanceID" to "0")) }
            device = to
        }
        volume = Float.NaN
        fetchVolume(to)
        val it = item
        if (it != null) {
            castLoad(it, if (t.isFinite()) t else 0.0)
            if (playing) play()
        } else {
            setRemote(TimeControl.PAUSED)
        }
        listener?.statusChanged()
        onChange?.invoke()
    }

    /**
     * Plays the episode here again, from where the television got to,
     * playing or paused as it was; the television stops. `why`, the
     * casting ended by itself, and the player says so.
     */
    fun stopCasting(why: String? = null) {
        val d = device ?: return
        val t = position
        val playing = wants
        gen++
        stopPolling()
        send { Dlna.call(d.transport, Dlna.AV_TRANSPORT, "Stop", listOf("InstanceID" to "0")) }
        worker.execute { server.stop() }
        device = null
        volume = Float.NaN
        hold(false)
        val it = item
        if (it != null && why == null) {
            local.load(it, if (t.isFinite()) t else 0.0)
            if (playing) local.play()
        }
        listener?.statusChanged()
        if (why != null) listener?.failed(why)
        onChange?.invoke()
    }

    /** Sets the television's volume, from 0 to 1; the last asked for wins, however many wait. */
    fun chooseVolume(v: Float) {
        val d = device ?: return
        val control = d.rendering ?: return
        volume = v.coerceIn(0f, 1f)
        if (volumeTarget.getAndSet((volume * 100).toInt()) >= 0) return // one on its way takes this too
        worker.execute {
            val n = volumeTarget.getAndSet(-1)
            if (n < 0) return@execute
            runCatching {
                Dlna.call(control, Dlna.RENDERING_CONTROL, "SetVolume", listOf("InstanceID" to "0", "Channel" to "Master", "DesiredVolume" to "$n"))
            }.onFailure { debugLog { "cast: volume: $it" } }
        }
    }

    private fun fetchVolume(d: Renderer) {
        val control = d.rendering ?: return
        worker.execute {
            val v = runCatching {
                Dlna.call(control, Dlna.RENDERING_CONTROL, "GetVolume", listOf("InstanceID" to "0", "Channel" to "Master"))["CurrentVolume"]?.toFloatOrNull()
            }.getOrNull() ?: return@execute
            main.post { if (device == d) volume = (v / 100f).coerceIn(0f, 1f) }
        }
    }

    /**
     * Tells the television to play the episode, over the app's server; it
     * starts paused, at `start` once the television has it.
     */
    private fun castLoad(item: VideoItem, start: Double) {
        val d = device ?: return
        gen++
        val g = gen
        wants = false
        pos = start
        posAt = SystemClock.elapsedRealtime()
        dur = Double.NaN
        seekTo = start.takeIf { it > 1 }
        sawPlaying = false
        lastPlaying = 0.0
        ended = false
        failures = 0
        setRemote(TimeControl.PAUSED)
        settle()
        val title = listOf(item.title, item.subtitle).filter { it.isNotEmpty() }.joinToString(" ")
        send(onError = { e -> if (g == gen) stopCasting("投屏失败：${e.message}") }) {
            val url = server.url(item.uri)
            debugLog { "cast: ${d.name}: $url" }
            Dlna.call(
                d.transport, Dlna.AV_TRANSPORT, "SetAVTransportURI",
                listOf("InstanceID" to "0", "CurrentURI" to url, "CurrentURIMetaData" to Dlna.didl(title, url)),
            )
        }
        startPolling(d, g)
    }

    private fun startPolling(d: Renderer, g: Int) {
        stopPolling()
        poll = worker.scheduleWithFixedDelay({ ask(d, g) }, 1, 1, TimeUnit.SECONDS)
    }

    private fun stopPolling() {
        poll?.cancel(false)
        poll = null
    }

    /** Asks the television what it is doing, and how far it has got. */
    private fun ask(d: Renderer, g: Int) {
        try {
            val state = Dlna.call(d.transport, Dlna.AV_TRANSPORT, "GetTransportInfo", listOf("InstanceID" to "0"))["CurrentTransportState"].orEmpty()
            val info = Dlna.call(d.transport, Dlna.AV_TRANSPORT, "GetPositionInfo", listOf("InstanceID" to "0"))
            val at = Dlna.seconds(info["RelTime"])
            val length = Dlna.seconds(info["TrackDuration"])
            main.post { heard(g, state, at, length) }
        } catch (e: Exception) {
            debugLog { "cast: ask: $e" }
            main.post {
                if (g != gen) return@post
                // A television that has gone quiet: back to the phone.
                if (++failures >= 5) stopCasting("和「${d.name}」的连接断了，投屏已停止")
            }
        }
    }

    private fun heard(g: Int, state: String, at: Double, length: Double) {
        if (g != gen || device == null) return
        failures = 0
        if (length.isFinite() && length > 0) dur = length
        // Where to start, once the television has the episode and can seek.
        val s = seekTo
        if (s != null && (state == "PLAYING" || state == "PAUSED_PLAYBACK") && dur.isFinite()) {
            seekTo = null
            seek(s)
        }
        // Till then, and for a moment after a command, where it says it is may be from before.
        val settled = SystemClock.elapsedRealtime() >= settleUntil
        if (at.isFinite() && settled && seekTo == null) {
            pos = at
            posAt = SystemClock.elapsedRealtime()
        }
        if (state == "PLAYING" && at.isFinite() && seekTo == null) {
            sawPlaying = true
            lastPlaying = at
        }
        if (!settled) return
        when (state) {
            "PLAYING" -> {
                wants = true // or the television's own remote played it
                setRemote(TimeControl.PLAYING)
            }
            "TRANSITIONING" -> setRemote(if (wants) TimeControl.WAITING else TimeControl.PAUSED)
            "PAUSED_PLAYBACK" -> {
                wants = false
                setRemote(TimeControl.PAUSED)
            }
            "STOPPED", "NO_MEDIA_PRESENT" -> when {
                // Stopped near the end, as it played: the end.
                sawPlaying && wants && dur.isFinite() && lastPlaying >= dur - 5 -> if (!ended) {
                    ended = true
                    wants = false
                    setRemote(TimeControl.PAUSED)
                    listener?.ended()
                }
                sawPlaying -> {
                    wants = false
                    setRemote(TimeControl.PAUSED)
                }
                wants -> setRemote(TimeControl.WAITING) // still on its way
            }
        }
        onChange?.invoke()
    }

    private fun setRemote(s: TimeControl) {
        if (s == there) return
        there = s
        if (device != null) listener?.statusChanged()
        onChange?.invoke()
    }

    /** What the television says for a moment after a command may be from before it. */
    private fun settle() {
        settleUntil = SystemClock.elapsedRealtime() + 2500
    }

    /** Sends a command to the television, after those before it. */
    private fun send(onError: ((Exception) -> Unit)? = null, command: () -> Unit) {
        worker.execute {
            try {
                command()
            } catch (e: Exception) {
                debugLog { "cast: $e" }
                if (onError != null) main.post { onError(e) }
            }
        }
    }

    /**
     * Keeps the Wi-Fi awake, and the phone enough to serve the television,
     * while casting, the screen off or the app in the background.
     */
    private fun hold(on: Boolean) {
        if (on) {
            if (locks != null) return
            val wifi = (app.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "kx-cast")
            val wake = app.getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kaixinguo:cast")
            runCatching { wifi?.acquire() }
            runCatching { wake?.acquire() }
            locks = wifi to wake
        } else {
            val (wifi, wake) = locks ?: return
            runCatching { if (wifi?.isHeld == true) wifi.release() }
            runCatching { if (wake?.isHeld == true) wake.release() }
            locks = null
        }
    }
}
