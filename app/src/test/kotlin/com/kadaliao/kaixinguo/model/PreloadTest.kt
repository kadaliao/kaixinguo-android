package com.kadaliao.kaixinguo.model

import dev.openhongguo.Playback
import dev.openhongguo.PlaybackResult
import dev.openhongguo.PlatformType
import dev.openhongguo.Series
import dev.openhongguo.Stream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreloadTest {
    /** Plays the episode loaded last as if ExoPlayer did, for `ms` of virtual time. */
    private fun TestScope.playFor(video: FakeVideo, ms: Long) {
        runCurrent()
        video.reports(TimeControl.PLAYING)
        advanceTimeBy(ms)
        runCurrent()
    }

    /**
     * Once an episode has played about nine seconds, the next one's start is
     * read ahead; going on to it plays that file, not a new one, and reads
     * the one after ahead in its turn.
     */
    @Test
    fun readsTheNextEpisodeAhead() = runTest {
        val catalog = FakeCatalog()
        val video = FakeVideo()
        val streams = FakeStreams()
        val model = testModel(backgroundScope, catalog, video, streams = streams)
        catalog.playable("1", 3)
        val p = model.player
        p.play("1", 1)
        playFor(video, 6_100)
        assertTrue(streams.preloaded.isEmpty()) // two ticks: not yet
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf("https://cdn/1e1.mp4", "https://cdn/1e2.mp4"), streams.registered)
        val ahead = streams.preloaded.keys.single()
        assertEquals(4L shl 20, streams.preloaded[ahead]) // no bitrate: 4 MB

        // More ticks read nothing more.
        advanceTimeBy(9_000)
        runCurrent()
        assertEquals(1, streams.preloaded.size)

        p.step(1)
        runCurrent()
        assertEquals(ahead, video.loaded?.uri)
        assertEquals(2, streams.registered.size)

        playFor(video, 9_100)
        assertEquals("https://cdn/1e3.mp4", streams.registered.last())
        assertEquals(2, streams.preloaded.size)
    }

    /** Buffering and pauses don't count toward the nine seconds, and a new episode starts the count again. */
    @Test
    fun countsOnlyPlaying() = runTest {
        val catalog = FakeCatalog()
        val video = FakeVideo()
        val streams = FakeStreams()
        val model = testModel(backgroundScope, catalog, video, streams = streams)
        catalog.playable("1", 3)
        val p = model.player
        p.play("1", 1)
        playFor(video, 6_100)
        video.reports(TimeControl.WAITING)
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(streams.preloaded.isEmpty())

        p.play("1", 2) // jumped to another: its count starts afresh
        playFor(video, 6_100)
        assertTrue(streams.preloaded.isEmpty())
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals("https://cdn/1e3.mp4", streams.registered.last())
    }

    /**
     * What was read ahead is let go when another episode is read ahead
     * instead, and not played when the definition picked since differs; the
     * amount follows the stream's bitrate, within 2 to 8 MB.
     */
    @Test
    fun readAheadFollowsTheStream() = runTest {
        val catalog = FakeCatalog()
        val video = FakeVideo()
        val streams = FakeStreams()
        val model = testModel(backgroundScope, catalog, video, streams = streams)
        catalog.series["1"] = Series(id = "1", title = "甲", episodeCount = 4, platform = PlatformType.OUTER)
        catalog.episodes["1"] = episodes("1", 4)
        for (e in catalog.episodes.getValue("1")) {
            catalog.playbacks[e.id] = PlaybackResult(
                playback = Playback(
                    episodeId = e.id,
                    streams = listOf(
                        Stream(definition = "1080p", url = "https://cdn/${e.id}-1080.mp4", bitrate = 2_000_000),
                        Stream(definition = "720p", url = "https://cdn/${e.id}-720.mp4", bitrate = 1_000_000),
                    ),
                ),
            )
        }
        val p = model.player
        p.play("1", 1)
        playFor(video, 9_100)
        val ahead = streams.preloaded.keys.single()
        assertEquals(5_000_000L, streams.preloaded[ahead]) // 20 s at 2 Mbit/s

        // Jumping on past it, another episode read ahead lets it go.
        p.play("1", 3)
        playFor(video, 9_100)
        assertTrue(ahead in streams.dropped)

        // A definition picked since: the episode plays a file of its own.
        p.chooseQuality(720)
        playFor(video, 1)
        p.step(1)
        runCurrent()
        assertEquals("https://cdn/1e4-720.mp4", streams.registered.last())
        assertEquals(streams.registered.size.toString(), video.loaded?.uri?.substringAfterLast('/')?.removeSuffix(".mp4"))
    }

    @Test
    fun preloadBytesStayInBounds() {
        assertEquals(2L shl 20, preloadBytes(Stream(bitrate = 100_000)))
        assertEquals(8L shl 20, preloadBytes(Stream(bitrate = 50_000_000)))
        assertEquals(4L shl 20, preloadBytes(Stream()))
    }

    /** Cast to a television, leaving the app leaves the episode playing there. */
    @Test
    fun leavingTheAppWhileCast() = runTest {
        val catalog = FakeCatalog()
        val video = FakeVideo()
        val model = testModel(backgroundScope, catalog, video)
        catalog.playable("1", 2)
        val p = model.player
        p.play("1", 1)
        playFor(video, 1)
        video.remote = true
        p.leaveApp()
        assertTrue(video.wantsToPlay)
        assertTrue(!p.away)
        video.remote = false
        p.leaveApp()
        assertTrue(!video.wantsToPlay)
        assertTrue(p.away)
    }

    /** The volume past the system's loudest goes to the video, within 1 and 1.5. */
    @Test
    fun boostStaysInBounds() = runTest {
        val video = FakeVideo()
        val p = testModel(backgroundScope, video = video).player
        p.chooseBoost(1.3f)
        assertEquals(1.3f, video.boost)
        assertEquals(1.3f, p.boost)
        p.chooseBoost(3f)
        assertEquals(Player.BOOST_LIMIT, p.boost)
        p.chooseBoost(0.2f)
        assertEquals(1f, video.boost)
    }
}
