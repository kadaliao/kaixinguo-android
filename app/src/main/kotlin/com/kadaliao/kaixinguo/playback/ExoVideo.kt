package com.kadaliao.kaixinguo.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.kadaliao.kaixinguo.model.TimeControl
import com.kadaliao.kaixinguo.model.Video
import com.kadaliao.kaixinguo.model.VideoItem
import com.kadaliao.kaixinguo.model.VideoListener
import com.kadaliao.kaixinguo.model.debugLog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * ExoVideo plays the episodes with ExoPlayer, reading each through
 * [CencDataSource]. It is made once, for the whole app: the player's
 * screen, picture in picture and the media session all show the same one.
 */
class ExoVideo(context: Context, registry: StreamRegistry, http: DataSource.Factory) : Video {
    val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(
            // A device whose hardware decoder fails on a stream falls back to another.
            DefaultRenderersFactory(context).setEnableDecoderFallback(true),
        )
        .setMediaSourceFactory(ProgressiveMediaSource.Factory(CencDataSource.Factory(registry, http)))
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    override var listener: VideoListener? = null
    override var status = TimeControl.PAUSED
        private set

    init {
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) listener?.ended()
            }

            override fun onPlayerError(error: PlaybackException) {
                debugLog { "exo: ${error.errorCodeName}: ${error.cause}" }
                listener?.failed(describe(error))
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                val now = computeStatus()
                if (now != status) {
                    status = now
                    listener?.statusChanged()
                }
            }
        })
    }

    private fun computeStatus(): TimeControl = when {
        !exo.playWhenReady || exo.playbackState == Player.STATE_IDLE || exo.playbackState == Player.STATE_ENDED -> TimeControl.PAUSED
        exo.isPlaying -> TimeControl.PLAYING
        else -> TimeControl.WAITING
    }

    override val hasItem: Boolean get() = exo.currentMediaItem != null

    override val position: Double
        get() = if (hasItem) exo.currentPosition / 1000.0 else Double.NaN

    override val duration: Double
        get() = exo.duration.let { if (it == C.TIME_UNSET || it <= 0) Double.NaN else it / 1000.0 }

    override val wantsToPlay: Boolean get() = exo.playWhenReady

    override var speed: Float
        get() = exo.playbackParameters.speed
        set(value) = exo.setPlaybackSpeed(value)

    override fun load(item: VideoItem, start: Double) {
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
        val media = MediaItem.Builder()
            .setUri(item.uri)
            .setMediaId(item.episodeId)
            .setMimeType(MimeTypes.VIDEO_MP4)
            .setMediaMetadata(meta)
            .build()
        exo.setMediaItem(media, (start * 1000).toLong().coerceAtLeast(0))
        exo.prepare()
    }

    override fun clear() {
        exo.stop()
        exo.clearMediaItems()
    }

    override fun play() {
        if (exo.playbackState == Player.STATE_IDLE && exo.currentMediaItem != null) exo.prepare()
        exo.play()
    }

    override fun pause() = exo.pause()

    override fun seek(seconds: Double) = exo.seekTo((seconds * 1000).toLong().coerceAtLeast(0))

    /** The video's picture, as width over height; 9:16 until it is known. Picture in picture follows it. */
    var aspect by mutableFloatStateOf(9f / 16f)
        private set
}

/** Says why the video stopped, in the viewer's words. */
private fun describe(e: PlaybackException): String = when (e.errorCode) {
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    -> "这台设备解码不了这一集的视频（HEVC），可以在「⋯ › 清晰度」里换低一档试试"
    else -> "视频加载失败，请重试"
}
