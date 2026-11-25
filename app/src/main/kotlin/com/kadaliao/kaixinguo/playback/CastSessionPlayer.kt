package com.kadaliao.kaixinguo.playback

import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.kadaliao.kaixinguo.model.TimeControl
import kotlin.math.roundToInt

/**
 * CastSessionPlayer is what the media session shows while an episode is
 * cast: the episode, playing or paused on the television, how far it has
 * got, and the television's volume, which the volume buttons set. The
 * notification and the lock screen work the television through it, and
 * the session playing keeps the app running, to serve the television, with
 * the screen off.
 */
class CastSessionPlayer(private val cast: CastVideo) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val commands = Player.Commands.Builder().addAll(
        Player.COMMAND_PLAY_PAUSE,
        Player.COMMAND_STOP,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_TIMELINE,
        Player.COMMAND_GET_METADATA,
        Player.COMMAND_GET_DEVICE_VOLUME,
        Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
        Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
    ).build()

    /** Tells the session the television's state has changed. */
    fun changed() = invalidateState()

    override fun getState(): State {
        val item = cast.castItem
        val volume = cast.volume.takeIf { it.isFinite() }
        val b = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(cast.wantsToPlay, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setDeviceInfo(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMinVolume(0).setMaxVolume(if (volume != null) 100 else 0).build())
            .setDeviceVolume(if (volume != null) (volume * 100).roundToInt() else 0)
        if (item == null) return b.setPlaybackState(Player.STATE_IDLE).build()
        val meta = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.subtitle)
            .setSubtitle(item.subtitle)
            .setDisplayTitle(item.title)
            .apply {
                if (item.artwork != null) {
                    setArtworkData(item.artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                } else if (item.artworkUri.isNotEmpty()) {
                    setArtworkUri(Uri.parse(item.artworkUri))
                }
            }
            .build()
        val d = cast.duration
        val data = MediaItemData.Builder(item.episodeId)
            .setMediaItem(MediaItem.Builder().setMediaId(item.episodeId).setMediaMetadata(meta).build())
            .setDurationUs(if (d.isFinite() && d > 0) (d * 1_000_000).toLong() else C.TIME_UNSET)
            .setIsSeekable(true)
            .build()
        return b.setPlaylist(listOf(data))
            .setCurrentMediaItemIndex(0)
            .setPlaybackState(if (cast.status == TimeControl.WAITING) Player.STATE_BUFFERING else Player.STATE_READY)
            .setContentPositionMs {
                val t = cast.position
                if (t.isFinite()) (t * 1000).toLong() else 0L
            }
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) cast.play() else cast.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        cast.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (positionMs != C.TIME_UNSET) cast.seek(positionMs / 1000.0)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        cast.chooseVolume(deviceVolume / 100f)
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        if (cast.volume.isFinite()) cast.chooseVolume(cast.volume + 0.05f)
        return Futures.immediateVoidFuture()
    }

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        if (cast.volume.isFinite()) cast.chooseVolume(cast.volume - 0.05f)
        return Futures.immediateVoidFuture()
    }
}
