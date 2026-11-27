@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kadaliao.kaixinguo.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kadaliao.kaixinguo.model.Card
import com.kadaliao.kaixinguo.model.DoubleSpeed
import com.kadaliao.kaixinguo.model.Fav
import com.kadaliao.kaixinguo.model.Lanes
import com.kadaliao.kaixinguo.model.Player
import com.kadaliao.kaixinguo.model.PlayerState
import com.kadaliao.kaixinguo.model.Sleep
import com.kadaliao.kaixinguo.model.favLabel
import com.kadaliao.kaixinguo.playback.CastVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * PlayerScreen is the episode playing, its video filling the screen: what
 * plays and how fast above it, the episodes below it on phones and beside
 * it on tablets. Swiping up in the middle goes on to the next episode, and
 * down back to the one before; up and down on the left sets the brightness,
 * and on the right the volume. A while after the last touch, as the video
 * plays, all but the video fades away, the system bars too; a tap brings it
 * back. In picture in picture only the video shows.
 */
@Composable
fun PlayerScreen(inPip: Boolean) {
    val model = LocalModel.current
    val player = model.player
    val video = player.video
    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE && config.screenHeightDp < 500 // a phone on its side: the video alone
    val panel = !landscape && config.screenWidthDp >= 600
    // On a tablet or a TV the episodes beside the video can be put away, for the video to have the whole screen.
    val tv = isTv()
    var fullScreen by remember { mutableStateOf(false) }
    val sidePanel = panel && !fullScreen
    var showEpisodes by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var resumeAfterSearch by remember { mutableStateOf(false) }
    val hud = remember { PlayerHud() }
    // Touches can be locked out while the video fills the screen: a phone on
    // its side, or a tablet with the episodes put away. The lock lifts as
    // that ends, in picture in picture, and as the episode fails, for its
    // buttons to work; a remote or a keyboard is no hand on the screen.
    val lockable = !tv && !inPip && (landscape || (panel && fullScreen)) && player.state !is PlayerState.Failed
    LaunchedEffect(lockable) { if (!lockable) hud.locked = false }
    val rotation = rememberRotation()
    val brightness = rememberBrightness()
    // The little window shows at the screen's own brightness.
    LaunchedEffect(inPip) { if (inPip) brightness?.restore() }
    BoostEnds(player)

    // Pauses the episode for a search, to go on with it after.
    fun openSearch() {
        resumeAfterSearch = video.wantsToPlay
        video.pause()
        searching = true
    }

    // Leaves the search: to play the series picked, or the episode as it was.
    fun closeSearch(picked: Card?) {
        searching = false
        if (picked != null) model.playSeason(picked.id) else if (resumeAfterSearch) video.play()
    }

    // The video itself takes the keys; on a TV the remote also visits the bar over it and the episodes beside it.
    val keys = remember { FocusRequester() }
    val episodes = remember { FocusRequester() }
    var videoFocused by remember { mutableStateOf(false) }

    // Down from the video goes to the episode playing. With the episodes put
    // away, it brings them back first; their grid lays that episode out a
    // frame or two later, so the focus goes there once it can.
    var toEpisodes by remember { mutableStateOf(false) }
    fun downToEpisodes() {
        if (sidePanel) {
            runCatching { episodes.requestFocus() }
        } else if (panel) {
            fullScreen = false
            toEpisodes = true
        }
    }
    LaunchedEffect(toEpisodes, sidePanel) {
        if (!toEpisodes || !sidePanel) return@LaunchedEffect
        for (i in 0 until 10) {
            if (runCatching { episodes.requestFocus() }.getOrDefault(false)) break
            withFrameNanos {}
        }
        toEpisodes = false
    }

    // Back closes the search, then on a TV brings the remote back to the video, then leaves.
    fun back() {
        if (searching) return closeSearch(null)
        if (tv && !videoFocused && runCatching { keys.requestFocus() }.getOrDefault(false)) return
        player.leave()
    }

    BackHandler(enabled = !inPip, onBack = ::back)
    PlayerWindow(
        // The status bar comes with the controls even on a phone on its side, for the time and the battery; the navigation bar there stays away.
        hideStatusBar = inPip || !hud.controls,
        hideNavigationBar = landscape || inPip || !hud.controls,
        // Cast, the phone is only a remote, and may sleep.
        keepOn = LocalVideo.current.device == null && !player.paused && (player.state == PlayerState.Playing || player.state == PlayerState.Buffering),
    )
    if (!inPip) ChromeTimer(hud, busy = showEpisodes || searching)
    PlayerTheme {
        LaunchedEffect(searching) { if (!searching) runCatching { keys.requestFocus() } }
        // Upright, both bars come and go with the controls, on its side only the status bar: laid out as if they showed, nothing moves.
        CompositionLocalProvider(LocalSteadyBars provides !landscape) {
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    .focusRequester(keys).onFocusChanged { videoFocused = it.isFocused }.focusable()
                    .onPreviewKeyEvent { e ->
                        !searching && e.type == KeyEventType.KeyDown &&
                            (if (tv) videoFocused && remoteKey(player, hud, e, ::downToEpisodes) else playerKey(player, e.key)).also { if (it) hud.poke() }
                    },
            ) {
                // One layout for all, so the video keeps its place, and its
                // surface, as the device turns or picture in picture starts.
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        VideoArea(
                            hud, brightness, edge = landscape || panel, chrome = !inPip, rotation = rotation.takeUnless { tv }, landscape = landscape,
                            fullScreen = fullScreen.takeIf { panel }, toggleFullScreen = { fullScreen = !fullScreen }, touchLock = lockable,
                            search = ::openSearch, video = keys, modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        if (!inPip && !landscape && !panel) {
                            Chrome(hud, tapToShow = true) { EpisodeBar { showEpisodes = true } }
                        }
                    }
                    if (!inPip && sidePanel) {
                        EpisodePanel(
                            Modifier.width(320.dp).fillMaxHeight().background(Color(0xFF171717))
                                .windowInsetsPadding(systemBars().only(WindowInsetsSides.Vertical + WindowInsetsSides.End)),
                            steps = true,
                            focus = episodes,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = searching && !inPip,
                    enter = slideInHorizontally(tween(250)) { it },
                    exit = slideOutHorizontally(tween(250)) { it },
                ) {
                    PlayerSearch(::closeSearch)
                }
            }
        }
        if (showEpisodes && !inPip) {
            ModalBottomSheet(onDismissRequest = { showEpisodes = false }, containerColor = Color(0xFF171717)) {
                EpisodePanel(Modifier.fillMaxHeight(0.85f)) { showEpisodes = false }
            }
        }
    }
}

/** The keys of a keyboard that work the video: space, the arrows and Esc. */
private fun playerKey(player: Player, key: Key): Boolean {
    when (key) {
        Key.Spacebar -> player.togglePause()
        Key.DirectionLeft -> player.skip(-10.0)
        Key.DirectionRight -> player.skip(10.0)
        Key.DirectionUp -> player.step(-1)
        Key.DirectionDown -> player.step(1)
        Key.Escape -> player.leave()
        else -> return false
    }
    return true
}

/**
 * The keys of a TV's remote while the video has the focus: OK pauses, left
 * and right go back and on 10 seconds, up goes to the bar over the video,
 * and down to the episodes beside it, where the arrows move between them;
 * put away, `down` brings them back.
 */
private fun remoteKey(player: Player, hud: PlayerHud, e: KeyEvent, down: () -> Unit): Boolean {
    when (e.key) {
        // Once a press: held, the remote repeats it.
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> if (e.nativeKeyEvent.repeatCount == 0) player.ok()
        Key.DirectionLeft -> player.skip(-10.0)
        Key.DirectionRight -> player.skip(10.0)
        // The bar may have faded away: the key brings it back, and it takes the focus as it shows.
        Key.DirectionUp -> hud.toBar = true
        Key.DirectionDown -> down()
        else -> return false
    }
    return true
}

/** PlayerWindow darkens the system bars around the video, hides them when it fills the screen, and keeps the screen on while it plays. */
@Composable
private fun PlayerWindow(hideStatusBar: Boolean, hideNavigationBar: Boolean, keepOn: Boolean) {
    val view = LocalView.current
    val window = LocalActivity.current?.window ?: return
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(controller) {
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNav
        }
    }
    DisposableEffect(hideStatusBar, hideNavigationBar) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val status = WindowInsetsCompat.Type.statusBars()
        val others = WindowInsetsCompat.Type.systemBars() and status.inv() // the navigation bar, and a free window's caption
        if (hideStatusBar) controller.hide(status) else controller.show(status)
        if (hideNavigationBar) controller.hide(others) else controller.show(others)
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Whether the player lays itself out as if the system bars showed, so that
 * they come and go with the controls, moving nothing. The status bar always
 * does; this is for the navigation bar, which a phone on its side keeps away.
 */
private val LocalSteadyBars = compositionLocalOf { false }

/** The status bar's insets, as the player lays itself out: always as if it showed, as it comes and goes with the controls however the phone is held. */
@Composable
private fun statusBars(): WindowInsets = WindowInsets.statusBarsIgnoringVisibility

/** The navigation bar's insets, as the player lays itself out. */
@Composable
private fun navigationBars(): WindowInsets = if (LocalSteadyBars.current) WindowInsets.navigationBarsIgnoringVisibility else WindowInsets.navigationBars

/** The system bars' insets, as the player lays itself out. */
@Composable
private fun systemBars(): WindowInsets = if (LocalSteadyBars.current) WindowInsets.systemBarsIgnoringVisibility else WindowInsets.systemBars

/**
 * The player screen's [Brightness], which puts the screen's own back as the
 * player closes.
 */
@Composable
private fun rememberBrightness(): Brightness? {
    val window = LocalActivity.current?.window ?: return null
    val context = LocalContext.current
    val brightness = remember(window) { Brightness(window, context) }
    DisposableEffect(brightness) { onDispose { brightness.restore() } }
    return brightness
}

/**
 * Brightness sets how bright the screen is while the player shows: the
 * window's own brightness, which needs no permission and holds only while
 * the app is on screen, so the system's is left as it was.
 */
private class Brightness(private val window: Window, private val context: Context) {
    /** How bright the screen is now, from 0 to 1: the player's, or the system's until a swipe sets it. */
    fun level(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0f) return b
        val system = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull() ?: return 0.5f
        return (system / 255f).coerceIn(0f, 1f)
    }

    fun set(v: Float) {
        // Not quite 0, which some screens take as off.
        window.attributes = window.attributes.apply { screenBrightness = v.coerceIn(0.01f, 1f) }
    }

    /** Lets the screen follow the system's brightness again. */
    fun restore() {
        if (window.attributes.screenBrightness < 0f) return
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
    }
}

/**
 * Volume sets the system's media volume from a swipe, the one its buttons
 * set, without the system's panel; past the system's loudest, the player
 * turns the episode up itself ([Player.chooseBoost]). Cast, it sets the
 * television's, up to its loudest.
 */
private class Volume(private val audio: AudioManager, private val player: Player, private val cast: CastVideo) {
    private val stream = AudioManager.STREAM_MUSIC
    private val min = audio.getStreamMinVolume(stream)
    private val max = audio.getStreamMaxVolume(stream)
    private val casting get() = cast.device != null

    /** Why the volume can't be set from here, if it can't: a fixed one, or a television that won't say its own. */
    val fixed: String?
        get() = when {
            casting -> if (cast.volume.isFinite()) null else "这台电视的音量不能在手机上调"
            max <= min || audio.isVolumeFixed -> "这台设备的音量不能调"
            else -> null
        }

    /** The loudest a swipe goes: past the system's only here. */
    val most: Float get() = if (casting) 1f else Player.BOOST_LIMIT

    private val system: Float get() = (audio.getStreamVolume(stream) - min).toFloat() / (max - min)

    /** The volume now, from 0 to [most]. */
    fun level(): Float {
        if (casting) return cast.volume
        if (player.boost > 1f && system < 1f) player.chooseBoost(1f) // turned down meanwhile
        return if (player.boost > 1f) player.boost else system
    }

    fun set(v: Float) {
        if (casting) return cast.chooseVolume(v)
        val to = (min + v.coerceAtMost(1f) * (max - min)).roundToInt().coerceIn(min, max)
        if (to != audio.getStreamVolume(stream)) runCatching { audio.setStreamVolume(stream, to, 0) } // no system panel: the player's says it
        player.chooseBoost(v.coerceAtLeast(1f))
    }
}

/** BoostEnds turns the sound back to the system's loudest as its buttons, or anything else, turn the volume down. */
@Composable
private fun BoostEnds(player: Player) {
    val context = LocalContext.current
    DisposableEffect(context, player) {
        val audio = context.getSystemService(AudioManager::class.java)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val s = AudioManager.STREAM_MUSIC
                if (audio != null && player.boost > 1f && audio.getStreamVolume(s) < audio.getStreamMaxVolume(s)) player.chooseBoost(1f)
            }
        }
        // Not a public broadcast, but every Android sends it; without it the turning down waits for the next swipe.
        ContextCompat.registerReceiver(context, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
}

/**
 * The player screen's [Rotation], which hears the phone turn while a press
 * holds the screen, and lets go as the player closes.
 */
@Composable
private fun rememberRotation(): Rotation? {
    val activity = LocalActivity.current ?: return null
    val rotation = remember(activity) { Rotation(activity) }
    val holding = rotation.held != null
    DisposableEffect(rotation, holding) {
        val listener = object : OrientationEventListener(activity) {
            override fun onOrientationChanged(degrees: Int) = rotation.phoneTurned(degrees)
        }
        if (holding) listener.enable()
        onDispose { listener.disable() }
    }
    DisposableEffect(rotation) { onDispose { rotation.release() } }
    return rotation
}

/**
 * Rotation turns the screen at a press: on its side, and upright again.
 * The way pressed holds until the phone is turned that way too, when the
 * screen goes back to following the phone, if auto-rotate is on; and as
 * the player closes.
 */
private class Rotation(private val activity: Activity) {
    /** The way a press turned the screen, while it holds: true on its side, false upright. */
    var held by mutableStateOf<Boolean?>(null)
        private set

    fun turn(landscape: Boolean) {
        held = landscape
        activity.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    /** Lets the screen follow the phone again, and the auto-rotate setting. */
    fun release() {
        if (held == null) return
        held = null
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    /**
     * Hears the phone turn, in degrees from upright. With auto-rotate off
     * the screen wouldn't follow the phone, so the press holds.
     */
    fun phoneTurned(degrees: Int) {
        val held = held ?: return
        if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) return // lying flat
        val side = degrees in 60..120 || degrees in 240..300
        val upright = degrees <= 30 || degrees >= 330
        if ((if (held) side else upright) && autoRotate()) release()
    }

    private fun autoRotate() =
        Settings.System.getInt(activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
}

/**
 * PlayerHud is what the fingers on the video are doing, for what shows over
 * it: whether the controls show, a press and hold as it goes, the video
 * following a swipe, the brightness or volume one sets, and a word on what
 * the last one did.
 */
private class PlayerHud {
    /**
     * Whether the controls over the video show: they fade a while after the
     * last touch as the video plays, and a tap brings them back. With the
     * touch lock on, it is whether the button to unlock shows, alone.
     */
    var chrome by mutableStateOf(true)

    /** Whether the touch lock is on: the video plays on, and a touch on it only brings back the button to unlock. */
    var locked by mutableStateOf(false)

    /** Whether the controls show, the system bars with them: not while the touch lock is on. */
    val controls: Boolean get() = chrome && !locked

    /**
     * How many touches on the video and its controls have ended, each
     * keeping the controls a while longer; and what keeps them while it
     * goes on: a finger along the progress bar, a menu open.
     */
    var touches by mutableIntStateOf(0)
    var scrubbing by mutableStateOf(false)
    var menus by mutableIntStateOf(0)

    /** Whether a TV remote's focus is in the top bar, which keeps the controls; and whether it is to go there as the bar shows. */
    var inBar by mutableStateOf(false)
    var toBar by mutableStateOf(false)

    /** The brightness or volume a swipe at the side sets, while it shows; and whether the swipe goes on. */
    var level by mutableStateOf<Level?>(null)
    var adjusting = false
    var levelJob: Job? = null // hides the level a moment after the swipe

    var holding by mutableStateOf(false)

    /** Whether the held finger has swiped far enough that letting go flips the lock. */
    var swiped by mutableStateOf(false)

    /** How far the video has followed a swipe up or down, in pixels. */
    var offset by mutableFloatStateOf(0f)

    /** What the last gesture did, or couldn't, said for a moment. */
    var toast by mutableStateOf<Toast?>(null)

    fun say(text: String) {
        toast = Toast(text)
    }

    fun showChrome() {
        chrome = true
    }

    /** Keeps the controls a while longer, after a touch on them; or brings them back. */
    fun poke() {
        touches++
        showChrome()
    }
}

/** A level a swipe at the side of the video sets: the brightness from 0 to 1, or the volume from 0 to `most`, [Player.BOOST_LIMIT] here. */
private data class Level(val volume: Boolean, val value: Float, val most: Float = 1f)

/** A word for a moment; each one shows anew, the same words or not. */
private class Toast(val text: String)

private val shadowed = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.4f), blurRadius = 6f))

/**
 * VideoArea is the video and what lies over it: what plays and how fast at
 * the top, how far it has got at the bottom, and what a press, a pause or a
 * failure shows. The video goes up under the status bar; `edge` says it
 * goes down to the bottom of the screen too, with no episode bar below.
 * `video` takes a remote's focus back down to the video from the top bar.
 * `fullScreen` is whether the episodes beside the video are put away, on
 * a tablet or a TV; null where there are none beside it to put away.
 * `touchLock` is whether touches can be locked out, with the video filling
 * the screen.
 */
@Composable
private fun VideoArea(
    hud: PlayerHud,
    brightness: Brightness?,
    edge: Boolean,
    chrome: Boolean,
    rotation: Rotation?,
    landscape: Boolean,
    fullScreen: Boolean?,
    toggleFullScreen: () -> Unit,
    touchLock: Boolean,
    search: () -> Unit,
    video: FocusRequester,
    modifier: Modifier,
) {
    Box(modifier.clipToBounds()) {
        VideoSurface(Modifier.fillMaxSize().offset { IntOffset(0, hud.offset.roundToInt()) })
        if (!chrome) return@Box
        CastMark()
        // Under the controls and below the top bar's row; it goes with the video as a swipe moves it.
        DanmakuLayer(
            Modifier.fillMaxSize().offset { IntOffset(0, hud.offset.roundToInt()) }
                .windowInsetsPadding(statusBars().union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
                .padding(top = 52.dp),
        )
        Gestures(hud, brightness, Modifier.fillMaxSize())
        PauseMark(Modifier.align(Alignment.Center))
        PlayerOverlay()
        Column(Modifier.fillMaxSize()) {
            Chrome(hud) { TopBar(hud, rotation, landscape, fullScreen, toggleFullScreen, search, video) }
            SpeedHint(hud)
            Spacer(Modifier.weight(1f))
            // With nothing under the video, the bar keeps clear of the system's.
            Chrome(hud) {
                ProgressBar(
                    hud,
                    if (edge) {
                        Modifier.windowInsetsPadding(navigationBars().union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    } else {
                        Modifier
                    },
                )
            }
        }
        if (touchLock) {
            TouchLock(
                hud,
                Modifier.align(Alignment.CenterEnd)
                    .windowInsetsPadding(navigationBars().union(WindowInsets.displayCutout).only(WindowInsetsSides.End))
                    .padding(end = 12.dp),
            )
        }
    }
}

/**
 * TouchLock is the button halfway down the right of the video that locks
 * out touches, so that a hand on the screen neither pauses the video nor
 * moves it on; and, locked, the one way back, which a tap on the video
 * brings. It comes and goes with the controls.
 */
@Composable
private fun TouchLock(hud: PlayerHud, modifier: Modifier) {
    AnimatedVisibility(hud.chrome, modifier, enter = fadeIn(tween(200)), exit = fadeOut(tween(300))) {
        val locked = hud.locked
        IconButton(
            onClick = {
                hud.locked = !locked
                hud.poke()
            },
            Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
        ) {
            Icon(if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen, contentDescription = if (locked) "解锁触摸" else "锁定触摸", tint = Color.White)
        }
    }
}

/**
 * Chrome shows the controls in it, or fades them, as [PlayerHud.controls]
 * says, keeping their place so that nothing else moves. Faded, they are
 * gone, and touches where they were go to the video; `tapToShow`, off the
 * video, a tap there brings them back.
 */
@Composable
private fun Chrome(hud: PlayerHud, tapToShow: Boolean = false, content: @Composable () -> Unit) {
    val shown = hud.controls
    val alpha = animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 200 else 300), label = "chrome")
    val gone by remember { derivedStateOf { !hud.controls && alpha.value == 0f } }
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxWidth()
            .then(if (gone) Modifier.height(with(density) { height.toDp() }) else Modifier.onSizeChanged { height = it.height })
            .graphicsLayer { this.alpha = alpha.value },
    ) {
        if (!gone) content()
        // While they fade, a tap on them brings them back rather than doing what they do.
        if (!shown && (tapToShow || !gone)) {
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        hud.poke()
                    }
                },
            )
        }
    }
}

/** How long the controls stay after the last touch, as the video plays. */
private const val CHROME_FOR = 4000L

/**
 * ChromeTimer fades the controls once the video has played on a while
 * untouched, and brings them back as it pauses or fails. It is a
 * composable of its own so that each touch, which starts it again,
 * recomposes nothing else. With TalkBack on they stay.
 */
@Composable
private fun ChromeTimer(hud: PlayerHud, busy: Boolean) {
    val player = LocalModel.current.player
    val context = LocalContext.current
    val talkBack = remember(context) { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
    val playing = player.state == PlayerState.Playing && !player.paused
    val swiping by remember { derivedStateOf { hud.offset != 0f } }
    val held = busy || talkBack || hud.holding || hud.level != null || hud.scrubbing || hud.menus > 0 || hud.inBar || swiping
    LaunchedEffect(hud.touches, hud.chrome, playing, held) {
        if (!hud.chrome || !playing || held) return@LaunchedEffect
        delay(CHROME_FOR)
        hud.chrome = false
    }
    val stopped = player.state is PlayerState.Failed ||
        (player.paused && (player.state == PlayerState.Playing || player.state == PlayerState.Buffering))
    LaunchedEffect(stopped) { if (stopped) hud.showChrome() }
}

/** VideoSurface shows the video, fitted to its frame, black around it. */
@Composable
private fun VideoSurface(modifier: Modifier) {
    val video = LocalVideo.current
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setBackgroundColor(android.graphics.Color.BLACK)
                player = video.local.exo
            }
        },
        modifier = modifier,
        onRelease = { it.player = null },
    )
}

/** What a finger down on the video turned out to be, before a long press would. */
private sealed interface Touch {
    data object Up : Touch
    data class Moved(val by: Offset) : Touch
}

/**
 * Gestures reads the fingers on the video: a tap brings back the controls,
 * or with them showing pauses; a swipe up or down moves between episodes in
 * the middle third, and sets the brightness on the left third and the
 * volume on the right; a press and hold plays at double speed. With the
 * touch lock on, any of them only brings back the button to unlock.
 */
@Composable
private fun Gestures(hud: PlayerHud, brightness: Brightness?, modifier: Modifier) {
    val player = LocalModel.current.player
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val cast = LocalVideo.current
    val volume = remember(context, player, cast) { context.getSystemService(AudioManager::class.java)?.let { Volume(it, player, cast) } }
    val scope = rememberCoroutineScope()
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop
                val touch = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var t: Touch? = null
                    while (t == null) {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        t = when {
                            c == null || !c.pressed -> Touch.Up
                            (c.position - down.position).getDistance() > slop -> Touch.Moved(c.position - down.position)
                            else -> null
                        }
                    }
                    t
                }
                val x = down.position.x
                when {
                    hud.locked -> hud.showChrome() // the gesture's other events go unheard until it ends
                    touch == null -> hold(down, player, hud, haptic)
                    touch == Touch.Up -> if (hud.chrome) {
                        player.togglePause()
                        hud.poke()
                    } else {
                        hud.showChrome()
                    }
                    touch is Touch.Moved && abs(touch.by.y) <= abs(touch.by.x) -> untilUp(down) // sideways: nothing
                    x < size.width / 3 -> if (brightness != null) adjust(down, hud, haptic, scope, false, 1f, brightness::level, brightness::set) else untilUp(down)
                    x > size.width * 2 / 3 -> when {
                        volume == null -> untilUp(down)
                        volume.fixed != null -> {
                            hud.say(volume.fixed ?: "")
                            untilUp(down)
                        }
                        else -> adjust(down, hud, haptic, scope, true, volume.most, volume::level, volume::set)
                    }
                    else -> swipe(down, player, hud, scope)
                }
                hud.touches++
            }
        },
    )
}

private suspend fun AwaitPointerEventScope.untilUp(down: PointerInputChange) {
    while (true) {
        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return
        if (!c.pressed) return
    }
}

/**
 * Plays at double speed while a finger holds the video; swiping down locks
 * it on, and swiping up in a later hold turns it off.
 */
private suspend fun AwaitPointerEventScope.hold(down: PointerInputChange, player: Player, hud: PlayerHud, haptic: HapticFeedback) {
    val far = 40.dp.toPx()
    player.beginHold()
    hud.toast = null
    hud.holding = true
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    var lifted = false
    try {
        while (true) {
            val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) {
                lifted = true
                break
            }
            c.consume()
            val dy = c.position.y - down.position.y
            val swiped = if (player.doubleSpeed == DoubleSpeed.LOCKED) dy < -far else dy > far
            if (swiped != hud.swiped) {
                hud.swiped = swiped
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    } finally {
        val was = player.doubleSpeed
        player.endHold(swiped = lifted && hud.swiped)
        hud.holding = false
        hud.swiped = false
        if (was == DoubleSpeed.HELD && player.doubleSpeed == DoubleSpeed.LOCKED) {
            hud.say("已锁定 2 倍速，长按上滑可取消")
        } else if (was == DoubleSpeed.LOCKED && player.doubleSpeed == DoubleSpeed.OFF) {
            hud.say("已" + restoring(player.baseRate))
        }
    }
}

/**
 * Swiping up plays the next episode, and down the one before: the video
 * follows the finger, and goes on out if it went far or fast enough.
 */
private suspend fun AwaitPointerEventScope.swipe(down: PointerInputChange, player: Player, hud: PlayerHud, scope: CoroutineScope) {
    val tracker = VelocityTracker()
    tracker.addPosition(down.uptimeMillis, down.position)
    var dy = 0f
    var lifted = false
    while (true) {
        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
        tracker.addPosition(c.uptimeMillis, c.position)
        if (!c.pressed) {
            lifted = true
            break
        }
        c.consume()
        dy = c.position.y - down.position.y
        val can = if (dy < 0) player.hasNext else player.hasPrevious
        hud.offset = if (can) dy else dy / 4 // nowhere to go: it gives a little
    }
    val d = if (dy < 0) 1 else -1
    val can = if (d > 0) player.hasNext else player.hasPrevious
    val h = size.height.toFloat()
    val vy = tracker.calculateVelocity().y
    val far = abs(dy) > h / 6 || (abs(vy) > 700.dp.toPx() && vy * dy > 0)
    if (!lifted || !can || !far) {
        scope.launch { animate(hud.offset, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)) { v, _ -> hud.offset = v } }
        if (lifted && !can && abs(dy) > 50.dp.toPx()) hud.say(if (d > 0) "已经是最后一集了" else "已经是第一集了")
        return
    }
    scope.launch {
        animate(hud.offset, if (dy < 0) -h else h, animationSpec = tween(200, easing = FastOutLinearInEasing)) { v, _ -> hud.offset = v }
        player.step(d)
        hud.offset = 0f // the screen black, until the next episode
    }
}

/**
 * Sets the brightness, or the volume, as the finger goes up or down: up is
 * more, and a swipe most of the way up the video goes from none to full.
 * The volume goes on past the system's loudest, to [Player.BOOST_LIMIT],
 * with a tick as it passes it.
 */
private suspend fun AwaitPointerEventScope.adjust(
    down: PointerInputChange,
    hud: PlayerHud,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    volume: Boolean,
    most: Float,
    get: () -> Float,
    set: (Float) -> Unit,
) {
    val span = maxOf(size.height * 0.7f, 240.dp.toPx())
    val from = get()
    hud.levelJob?.cancel()
    hud.toast = null
    hud.adjusting = true
    hud.level = Level(volume, from, most)
    try {
        while (true) {
            val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) break
            c.consume()
            val v = (from - (c.position.y - down.position.y) / span).coerceIn(0f, most)
            val was = hud.level?.value ?: from
            if (volume && (was > 1.001f) != (v > 1.001f)) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            set(v)
            hud.level = Level(volume, v, most)
        }
    } finally {
        hud.adjusting = false
        hud.levelJob = scope.launch {
            delay(1500)
            if (!hud.adjusting) hud.level = null
        }
    }
}

/**
 * The comments' text: a modest size, light and see-through, so they sit
 * over the video without hiding it. Light rather than thinner, and no more
 * see-through than this, so they still read over snow or a white wall.
 */
private val danmakuStyle = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Light)

/**
 * DanmakuLayer flies the episode's comments right to left across the top
 * of the video, white with a soft dark edge, as [Lanes] places them; they
 * fade in and out at the edges and none is drawn fully opaque, so they
 * stay in the back of the eye. Where they are follows the video's time:
 * read every frame while it plays, and now and then otherwise, which a drag
 * along the progress bar may move. Nothing here takes a touch, so the
 * video's gestures work through it.
 */
@Composable
private fun DanmakuLayer(modifier: Modifier) {
    val player = LocalModel.current.player
    val comments = player.danmaku
    if (comments.isEmpty() || !(player.state == PlayerState.Playing || player.state == PlayerState.Buffering) || LocalVideo.current.device != null) return
    val video = player.video
    val moving = player.state == PlayerState.Playing && !player.paused
    var now by remember { mutableLongStateOf(-1L) } // ms into the episode; -1 without one
    LaunchedEffect(moving) {
        while (true) {
            val t = video.position
            now = if (t.isFinite()) (t * 1000).toLong() else -1L
            if (moving) withFrameMillis {} else delay(250)
        }
    }
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier.drawWithCache {
            val em = danmakuStyle.fontSize.toPx()
            val lane = em * 1.6f
            val lanes = Lanes(comments, size.width / em)
            val texts = arrayOfNulls<TextLayoutResult>(lanes.flights.size) // each measured as it first shows
            val edge = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round)
            val edgeColor = Color.Black.copy(alpha = 0.3f)
            val glow = Shadow(color = Color.Black.copy(alpha = 0.28f), blurRadius = 2f * density)
            onDrawBehind {
                val ms = now
                if (ms < 0) return@onDrawBehind
                lanes.at(ms) { i, x ->
                    val f = lanes.flights[i]
                    val alpha = lanes.alpha(f, x)
                    if (alpha <= 0f) return@at // just in at the right edge, or off the left
                    val text = texts[i] ?: measurer.measure(f.comment.text, danmakuStyle, softWrap = false, maxLines = 1).also { texts[i] = it }
                    val at = Offset(x * em, f.lane * lane + (lane - text.size.height) / 2)
                    drawText(text, edgeColor, at, alpha = alpha, drawStyle = edge)
                    drawText(text, topLeft = at, alpha = alpha, shadow = glow, drawStyle = Fill) // left out, the text keeps the edge's stroke and comes out hollow
                }
            }
        },
    )
}

/**
 * CastMark covers the video while it plays on a television: what it plays
 * on. Taps and swipes go through it, as on the video.
 */
@Composable
private fun CastMark() {
    val d = LocalVideo.current.device ?: return
    val player = LocalModel.current.player
    Column(
        Modifier.fillMaxSize().background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(if (player.paused) "投屏已暂停" else "正在投屏", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(d.name, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
    }
}

/**
 * CastButton picks where the episode plays: here, or on a television on
 * the same Wi-Fi that takes DLNA, which fetches it from the app itself.
 * Opening it looks for televisions.
 */
@Composable
private fun CastButton(hud: PlayerHud) {
    val cast = LocalVideo.current
    var open by remember { mutableStateOf(false) }
    HoldsChrome(hud, open)
    val casting = cast.device
    Box {
        IconButton(onClick = {
            open = true
            cast.search()
        }) {
            Icon(
                if (casting != null) Icons.Filled.CastConnected else Icons.Filled.Cast,
                contentDescription = if (casting != null) "正在投屏" else "投屏",
                tint = if (casting != null) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
        // Rows only ever come in below the ones there, so none moves from under a finger.
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 240.dp, max = 320.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("投屏到电视", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (cast.searching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            if (casting != null) {
                DropdownMenuItem(
                    text = { Text("停止投屏，在手机上看") },
                    leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                    onClick = {
                        open = false
                        cast.stopCasting()
                        hud.poke()
                    },
                )
                HorizontalDivider()
            }
            for (d in cast.devices) {
                DropdownMenuItem(
                    text = { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Filled.Tv, contentDescription = null) },
                    trailingIcon = { if (d == casting) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        cast.cast(d)
                        hud.poke()
                    },
                )
            }
            if (cast.devices.isEmpty()) {
                Text(
                    if (cast.searching) {
                        "正在查找同一个 Wi-Fi 里的电视…"
                    } else {
                        "没有找到电视。手机和电视要连同一个 Wi-Fi，电视要支持 DLNA 投屏（多数智能电视、盒子和投影仪都支持）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (!cast.searching) DropdownMenuItem(text = { Text("再找一次") }, onClick = { cast.search() })
            }
        }
    }
}

/** PauseMark says the video is paused; a tap anywhere on it plays on. */
@Composable
private fun PauseMark(modifier: Modifier) {
    val player = LocalModel.current.player
    if (LocalVideo.current.device != null) return // the cast mark says so
    if (player.paused && (player.state == PlayerState.Playing || player.state == PlayerState.Buffering)) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "已暂停", tint = Color.White.copy(alpha = 0.85f), modifier = modifier.size(76.dp))
    }
}

/**
 * PlayerOverlay is what covers the video while it isn't playing: a spinner
 * while it loads or waits for more, or what went wrong. Swipes go through
 * it, to the episodes either side.
 */
@Composable
private fun PlayerOverlay() {
    val player = LocalModel.current.player
    when (val s = player.state) {
        PlayerState.Resolving -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        PlayerState.Buffering -> if (!player.paused) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        }
        is PlayerState.Failed -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(s.message, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = player::retry) { Text("重试") }
                    if (player.hasNext) OutlinedButton(onClick = { player.step(1) }) { Text("下一集") }
                }
            }
        }
        else -> {}
    }
}

// MARK: - Over the video

/**
 * TopBar is what plays, and how fast, over the top of the video, with the
 * way out, to turn the screen, to put away the episodes beside it and get
 * them back, to search and to more. A remote comes into
 * it at the speed, as [PlayerHud.toBar] asks, and goes down from it to the
 * video; while it is in the bar, the bar stays.
 */
@Composable
private fun TopBar(hud: PlayerHud, rotation: Rotation?, landscape: Boolean, fullScreen: Boolean?, toggleFullScreen: () -> Unit, search: () -> Unit, video: FocusRequester) {
    val player = LocalModel.current.player
    val speed = remember { FocusRequester() }
    // Where the episode's number would break onto a second line, search
    // goes into the menu to give it room. It stays there while the bar
    // shows, so no button moves from under a finger; the bar measures again
    // as it comes back, or as the screen turns.
    var crowded by remember(LocalConfiguration.current.screenWidthDp) { mutableStateOf(false) }
    // Here, not where the key is pressed: the bar may only now be back.
    LaunchedEffect(hud.toBar) {
        if (!hud.toBar) return@LaunchedEffect
        hud.toBar = false
        runCatching { speed.requestFocus() }
    }
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))) {
        Row(
            Modifier.fillMaxWidth()
                .focusProperties { down = video }
                .onFocusChanged { hud.inBar = it.hasFocus }
                .windowInsetsPadding(statusBars().union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 4.dp, end = 4.dp, bottom = 20.dp).height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = player::leave, Modifier.focusRing(Color.White, CircleShape)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "退出播放", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "第 ${player.number} 集", color = Color.White, style = MaterialTheme.typography.titleMedium.merge(shadowed), fontWeight = FontWeight.SemiBold,
                    onTextLayout = { if (it.lineCount > 1) crowded = true },
                )
                player.page?.series?.title?.let {
                    Text(it, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall.merge(shadowed), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SpeedMenu(hud, Modifier.focusRequester(speed))
            // On a TV itself there is nothing to cast to.
            if (!isTv()) CastButton(hud)
            if (rotation != null) RotateButton(hud, rotation, landscape)
            if (fullScreen != null) {
                IconButton(onClick = {
                    toggleFullScreen()
                    hud.poke()
                }, Modifier.focusRing(Color.White, CircleShape)) {
                    Icon(
                        if (fullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (fullScreen) "显示选集" else "全屏观看",
                        tint = Color.White,
                    )
                }
            }
            if (!crowded) IconButton(onClick = search, Modifier.focusRing(Color.White, CircleShape)) { Icon(Icons.Filled.Search, contentDescription = "搜索", tint = Color.White) }
            MoreMenu(hud, search.takeIf { crowded })
        }
    }
}

/**
 * RotateButton turns the screen on its side for a video wider than tall,
 * and upright again: on phones, where on its side the screen shows the
 * video alone. It stays while the screen is held on its side, so there is
 * always a way back, even to a series shot upright.
 */
@Composable
private fun RotateButton(hud: PlayerHud, rotation: Rotation, landscape: Boolean) {
    val player = LocalModel.current.player
    if (LocalConfiguration.current.smallestScreenWidthDp >= 600 || !(player.wide || rotation.held == true)) return
    IconButton(onClick = {
        rotation.turn(landscape = !landscape)
        hud.poke()
    }) {
        Icon(Icons.Filled.ScreenRotation, contentDescription = if (landscape) "竖屏观看" else "横屏观看", tint = Color.White)
    }
}

/** A speed as the player writes it: 1.25, 2. */
private fun rateText(r: Float): String = if (r == r.toInt().toFloat()) "${r.toInt()}" else "$r"

/** What going back from double speed says: to the speed the viewer chose. */
private fun restoring(rate: Float): String = if (rate == 1f) "恢复正常速度" else "恢复 ${rateText(rate)} 倍速"

/** Keeps the controls while a menu of theirs is open. */
@Composable
private fun HoldsChrome(hud: PlayerHud, open: Boolean) {
    DisposableEffect(open) {
        if (open) hud.menus++
        onDispose { if (open) hud.menus-- }
    }
}

/** SpeedMenu shows the speed the video plays at, and changes it. */
@Composable
private fun SpeedMenu(hud: PlayerHud, modifier: Modifier = Modifier) {
    val player = LocalModel.current.player
    var open by remember { mutableStateOf(false) }
    HoldsChrome(hud, open)
    Box {
        Row(
            modifier.focusRing(Color.White, CircleShape).clip(CircleShape).clickable { open = true }.height(44.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (isTv()) 24.dp else 20.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (player.speed == 1f) "倍速" else "${rateText(player.speed)}x", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SpeedItems(player) { open = false }
        }
    }
}

@Composable
private fun SpeedItems(player: Player, done: () -> Unit) {
    for (s in Player.speeds.reversed()) {
        Choice(if (s == 1f) "1x 正常" else "${rateText(s)}x", player.speed == s) {
            player.chooseSpeed(s)
            done()
        }
    }
}

@Composable
private fun Choice(label: String, on: Boolean, pick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { if (on) Icon(Icons.Filled.Check, contentDescription = null) },
        onClick = pick,
    )
}

private enum class More { MAIN, SPEED, QUALITY, SLEEP }

/**
 * MoreMenu sets how the episode plays: its speed, its definition, when to
 * stop and whether comments fly over it; and saves the series, or opens its
 * page. `search` is there when the bar has no room for its own button.
 */
@Composable
private fun MoreMenu(hud: PlayerHud, search: (() -> Unit)?) {
    val model = LocalModel.current
    val router = LocalRouter.current
    val player = model.player
    var open by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(More.MAIN) }
    HoldsChrome(hud, open)
    Box {
        IconButton(onClick = {
            page = More.MAIN
            open = true
        }, Modifier.focusRing(Color.White, CircleShape)) { Icon(Icons.Filled.MoreHoriz, contentDescription = "更多", tint = Color.White) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            when (page) {
                More.MAIN -> {
                    if (search != null) {
                        DropdownMenuItem(
                            text = { Text("搜索") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            onClick = {
                                open = false
                                search()
                            },
                        )
                        HorizontalDivider()
                    }
                    Entry("倍速", Icons.Filled.Speed, if (player.speed == 1f) "正常" else "${rateText(player.speed)}x") { page = More.SPEED }
                    if (player.qualities.isNotEmpty()) {
                        Entry("清晰度", Icons.Filled.HighQuality, "${player.quality}P") { page = More.QUALITY }
                    }
                    Entry("定时关闭", Icons.Filled.Bedtime, sleepLine(player)) { page = More.SLEEP }
                    DropdownMenuItem(
                        text = { Text("弹幕") },
                        leadingIcon = { Icon(Icons.Filled.Subtitles, contentDescription = null) },
                        trailingIcon = { Switch(checked = player.showDanmaku, onCheckedChange = null) },
                        onClick = { player.chooseDanmaku(!player.showDanmaku) },
                    )
                    val sp = player.page
                    val series = sp?.series
                    if (sp != null && series != null) {
                        HorizontalDivider()
                        val saved = model.favorites.has(sp.id)
                        DropdownMenuItem(
                            text = { Text(favLabel(saved) + "这部剧") },
                            leadingIcon = { Icon(if (saved) Icons.Outlined.StarOutline else Icons.Filled.Star, contentDescription = null) },
                            onClick = {
                                model.favorites.toggle(Fav.of(Card.of(series)))
                                open = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("剧集详情") },
                            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            onClick = {
                                open = false
                                player.leave()
                                if (router.top != Route.Series(sp.id)) router.open(Route.Series(sp.id))
                            },
                        )
                    }
                }
                More.SPEED -> {
                    Heading("倍速") { page = More.MAIN }
                    SpeedItems(player) { open = false }
                }
                More.QUALITY -> {
                    Heading("清晰度") { page = More.MAIN }
                    for (q in player.qualities) {
                        Choice("${q}P", q == player.quality) {
                            player.chooseQuality(q)
                            open = false
                        }
                    }
                }
                More.SLEEP -> {
                    Heading("定时关闭") { page = More.MAIN }
                    val choices = listOf(Sleep.Off to "不开启", Sleep.EndOfEpisode to "播完本集") +
                        listOf(15, 30, 60, 90).map { Sleep.Minutes(it) to "$it 分钟" }
                    for ((s, label) in choices) {
                        Choice(label, player.sleep == s) {
                            player.chooseSleep(s)
                            open = false
                        }
                    }
                }
            }
        }
    }
}

/** A line of the menu that opens a list of choices, the one made beside it. */
@Composable
private fun Entry(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, open: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) },
        onClick = open,
    )
}

/** The head of a list of choices, which goes back to the menu. */
@Composable
private fun Heading(label: String, back: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = FontWeight.SemiBold) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "返回", modifier = Modifier.size(16.dp)) },
        onClick = back,
    )
    HorizontalDivider()
}

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

/** When the sleep timer stops the video: "播完本集", "22:30 停止". */
private fun sleepLine(player: Player): String = when (player.sleep) {
    Sleep.Off -> "未开启"
    Sleep.EndOfEpisode -> "播完本集"
    is Sleep.Minutes -> (player.sleepAt?.atZone(ZoneId.systemDefault())?.format(clockFormat) ?: "") + " 停止"
}

/** Seconds as minutes and seconds: 1:05. */
private fun clock(s: Double): String {
    if (!s.isFinite() || s <= 0) return "0:00"
    val n = s.toInt()
    return "%d:%02d".format(n / 60, n % 60)
}

/** ProgressBar is how far the episode has got, along the bottom of the video; dragging along it moves there. */
@Composable
private fun ProgressBar(hud: PlayerHud, modifier: Modifier) {
    val video = LocalModel.current.player.video
    var dragged by remember { mutableStateOf<Float?>(null) } // where a drag is, as a fraction
    var at by remember { mutableDoubleStateOf(0.0) }
    var total by remember { mutableDoubleStateOf(0.0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            val d = video.duration
            total = if (d.isFinite() && d > 0) d else 0.0
            val t = video.position
            at = if (total > 0 && t.isFinite()) (t / total).coerceIn(0.0, 1.0) else 0.0
            delay(250)
        }
    }
    val shown = dragged ?: at.toFloat()
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        dragged?.let { f ->
            Text(
                "${clock(f * total)} / ${clock(total)}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        Canvas(
            Modifier.fillMaxWidth().height(28.dp).pointerInput(total) {
                if (total <= 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    dragged = (down.position.x / size.width).coerceIn(0f, 1f)
                    hud.scrubbing = true
                    try {
                        while (true) {
                            val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            c.consume()
                            dragged = (c.position.x / size.width).coerceIn(0f, 1f)
                        }
                    } finally {
                        hud.scrubbing = false
                        hud.poke()
                    }
                    val f = dragged ?: return@awaitEachGesture
                    video.seek(f * total)
                    // Keeps the bar there until the video has moved.
                    scope.launch {
                        delay(400)
                        dragged = null
                    }
                }
            },
        ) {
            if (total <= 0) return@Canvas
            val active = dragged != null
            val h = (if (active) 4.dp else 2.dp).toPx()
            val knob = (if (active) 14.dp else 7.dp).toPx()
            val y = size.height / 2
            val w = size.width
            drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, y - h / 2), Size(w, h), CornerRadius(h / 2))
            drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(0f, y - h / 2), Size(w * shown, h), CornerRadius(h / 2))
            drawCircle(Color.White, knob / 2, Offset((w * shown).coerceIn(knob / 2, w - knob / 2), y))
        }
    }
}

/**
 * SpeedHint shows a press and hold on the video at work: double speed, and
 * which way to swipe to lock it on or turn it off; and, for a moment, what
 * a swipe did.
 */
@Composable
private fun SpeedHint(hud: PlayerHud) {
    val player = LocalModel.current.player
    val toast = hud.toast
    LaunchedEffect(toast) {
        if (toast == null) return@LaunchedEffect
        delay(2000)
        if (hud.toast === toast) hud.toast = null
    }
    Box(Modifier.fillMaxWidth().padding(top = 12.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(hud.holding, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            val locked = player.doubleSpeed == DoubleSpeed.LOCKED
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (locked) "已锁定 2 倍速" else "2 倍速播放中", if (locked) Icons.Filled.Lock else Icons.Filled.FastForward)
                SwipeHint(locked, hud.swiped, player.baseRate)
            }
        }
        val level = hud.level
        if (!hud.holding && level != null) LevelHint(level) else if (!hud.holding && toast != null) Pill(toast.text)
    }
}

/**
 * LevelHint shows the brightness or the volume a swipe up or down at the
 * side of the video sets, as it goes: the volume past 100% in orange, and a
 * mark where the system's loudest ends.
 */
@Composable
private fun LevelHint(level: Level) {
    val most = level.most
    val over = level.value > 1.001f
    val v = level.value
    val icon = when {
        !level.volume -> if (v < 0.5f) Icons.Filled.BrightnessLow else Icons.Filled.BrightnessHigh
        v < 0.01f -> Icons.AutoMirrored.Filled.VolumeOff
        v < 0.34f -> Icons.AutoMirrored.Filled.VolumeMute
        v < 0.67f -> Icons.AutoMirrored.Filled.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }
    Row(
        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = if (level.volume) "音量" else "亮度", tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.width(140.dp).height(10.dp)) {
            val h = 6.dp.toPx()
            val y = (size.height - h) / 2
            drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
            drawRoundRect(if (over) Color(0xFFFF9F0A) else Color.White, Offset(0f, y), Size(size.width * (v / most), h), CornerRadius(h / 2))
            if (most > 1f) {
                val x = size.width / most
                drawRect(Color.White.copy(alpha = 0.7f), Offset(x - 0.75.dp.toPx(), 0f), Size(1.5.dp.toPx(), size.height))
            }
        }
        Text(
            "${(v * 100).roundToInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun Pill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Which way to swipe, or, once swiped far enough, what letting go does. */
@Composable
private fun SwipeHint(locked: Boolean, swiped: Boolean, base: Float) {
    val (icon, text) = when {
        !locked && !swiped -> Icons.Filled.KeyboardArrowDown to "下滑锁定 2 倍速"
        !locked -> Icons.Filled.Lock to "松手锁定 2 倍速"
        !swiped -> Icons.Filled.KeyboardArrowUp to "上滑取消 2 倍速"
        else -> Icons.AutoMirrored.Filled.Undo to "松手" + restoring(base)
    }
    val bounce by rememberInfiniteTransition(label = "bounce").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "bounce",
    )
    val fg = if (swiped) Color.Black else Color.White
    Row(
        Modifier.clip(CircleShape).background(if (swiped) Color.White else Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dy = if (swiped) 0f else (if (locked) -3f else 3f) * bounce
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp).offset(y = dy.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

// MARK: - Under and beside the video

/** EpisodeBar opens the episodes, under the video on phones. */
@Composable
private fun EpisodeBar(open: () -> Unit) {
    val total = LocalModel.current.player.page?.episodes?.size ?: 0
    Row(
        Modifier.windowInsetsPadding(navigationBars()).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
            .fillMaxWidth().height(44.dp).clip(MaterialTheme.shapes.medium).background(Color(0xFF262626))
            .clickable(onClick = open).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.GridView, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (total > 0) "选集 · 全 $total 集" else "选集", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color.White)
    }
}

private fun episodeLine(player: Player): String {
    val total = player.page?.episodes?.size ?: 0
    return if (total > 0) "第 ${player.number} 集 / 共 $total 集" else "第 ${player.number} 集"
}

/**
 * EpisodePanel is the series' title and its episodes, beside the video on
 * tablets, with buttons to move between them, and in a sheet on phones,
 * which `picked` closes once something plays. `focus` takes a remote's
 * focus to the episode playing.
 */
@Composable
private fun EpisodePanel(modifier: Modifier = Modifier, steps: Boolean = false, focus: FocusRequester? = null, picked: () -> Unit = {}) {
    val model = LocalModel.current
    val player = model.player
    val sp = player.page
    val series = sp?.series
    if (sp == null || series == null) {
        Spinner(modifier)
        return
    }
    val watched = model.history.get(sp.id)?.watched?.toSet() ?: emptySet()
    Column(modifier) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(series.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(episodeLine(player), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (steps) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { player.step(-1) }, Modifier.focusRing(Color.White, CircleShape), enabled = player.hasPrevious, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("上一集")
                    }
                    OutlinedButton(onClick = { player.step(1) }, Modifier.focusRing(Color.White, CircleShape), enabled = player.hasNext, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("下一集")
                    }
                }
            }
            SeasonChips(sp, 20.dp, Modifier.bleed(20.dp)) { se ->
                model.playSeason(se.seriesId)
                picked()
            }
            PartPicker(sp, wide = false)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
        val grid = rememberLazyGridState()
        val episodes = sp.partEpisodes
        LaunchedEffect(player.number, sp.part) {
            val i = episodes.indexOfFirst { it.number == player.number }
            if (i < 0) return@LaunchedEffect
            grid.scrollToItem(i)
            grid.scrollBy(-(grid.layoutInfo.viewportSize.height / 2f) + 60f)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(46.dp),
            state = grid,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(episodes, key = { it.id }) { e ->
                val current = e.number == player.number
                EpisodeChip(e, current, e.number in watched, if (current && focus != null) Modifier.focusRequester(focus) else Modifier) {
                    if (e.number != player.number) player.play(sp.id, e.number)
                    picked()
                }
            }
        }
    }
}

// MARK: - Searching from the player

/**
 * PlayerSearch looks for another series without leaving the player: the
 * hot searches, then what was searched for. Picking a series plays it; the
 * back button beside the field goes back to the episode.
 */
@Composable
private fun PlayerSearch(close: (Card?) -> Unit) {
    val model = LocalModel.current
    val focusManager = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(text) {
        if (text.isEmpty()) query = ""
        if (text.isEmpty() || text == query) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        suggestions = model.suggestions(text)
    }
    fun submit(s: String) {
        val q = s.trim()
        if (q.isEmpty()) return
        text = q
        query = q
        model.searchAgain(q)
        suggestions = emptyList()
        focusManager.clearFocus()
    }
    CompositionLocalProvider(LocalPlayCard provides { card: Card -> close(card) }) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { close(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "返回播放") }
                SearchField(text, { text = it }, { submit(text) }, Modifier.weight(1f), focus)
                TextButton(onClick = { submit(text) }) { Text("搜索", fontWeight = FontWeight.SemiBold) }
            }
            Box(Modifier.weight(1f)) {
                when {
                    suggestions.isNotEmpty() && text != query -> SuggestionList(suggestions, ::submit)
                    query.isEmpty() -> HotView()
                    else -> {
                        val feed = model.searchFeed(query)
                        FeedKey(feed) { FeedGrid(feed, "没有找到“$query”相关的短剧，换个词试试") }
                    }
                }
            }
        }
    }
}
