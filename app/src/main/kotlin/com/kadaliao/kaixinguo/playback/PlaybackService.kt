package com.kadaliao.kaixinguo.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.kadaliao.kaixinguo.KaixinguoApplication
import com.kadaliao.kaixinguo.MainActivity

/**
 * PlaybackService keeps the episode playing with the screen off, and shows
 * it in the notification shade and on the lock screen, with buttons for
 * the episodes either side.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as KaixinguoApplication
        val model = app.model ?: return
        val player = model.player
        // The video holds one episode at a time: the buttons either side
        // move between episodes, as the player's swipes do.
        val episodes = object : ForwardingPlayer(app.video.exo) {
            private val extra = listOf(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )

            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon().addAll(*extra.toIntArray()).build()

            override fun isCommandAvailable(command: Int): Boolean = command in extra || super.isCommandAvailable(command)

            override fun hasNextMediaItem() = player.hasNext

            override fun hasPreviousMediaItem() = player.hasPrevious

            override fun seekToNext() = player.step(1)

            override fun seekToNextMediaItem() = player.step(1)

            override fun seekToPrevious() = player.step(-1)

            override fun seekToPreviousMediaItem() = player.step(-1)
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, episodes).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away from the recent apps stops the video, as closing a player does. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        // The video belongs to the app, which goes on using it.
        session?.release()
        session = null
        super.onDestroy()
    }
}
