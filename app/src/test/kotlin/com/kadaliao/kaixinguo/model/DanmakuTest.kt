package com.kadaliao.kaixinguo.model

import dev.openhongguo.Danmaku
import dev.openhongguo.DanmakuPage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DanmakuTest {
    @Test
    fun widths() {
        assertEquals(2f, ems("好看"), 0f)
        assertEquals(1.2f, ems("ok"), 0.001f)
        assertEquals(1.2f, ems("😂"), 0.001f)
        assertEquals(0f, ems(""), 0f)
    }

    /**
     * Comments at the same moment take the lanes from the top; when all are
     * busy, the next waits for the first to clear, and one that would wait
     * too long is dropped. A lane clears once the comment in it, and the room
     * after it, are past the right edge.
     */
    @Test
    fun lanes() {
        // 20 ems wide: a comment moves an em in 350 ms.
        val comments = listOf(
            Comment("后来", 5000), // all clear by then: the top one
            Comment("一二三四五六七八", 0), // 8 ems: its lane clears after 10 ems, 3500 ms
            Comment("好", 0), // clears at 1050
            Comment("好看", 0), // 1400
            Comment("真好看", 0), // 1750
            Comment("哈", 100), // waits for the second lane, until 1050; it clears again at 2100
            Comment("嗯", 200), // the third, until 1400
            Comment("呀", 300), // the fourth, until 1750
            Comment("啊", 400), // would wait until 2100: dropped
        )
        val l = Lanes(comments, 20f)
        assertEquals(
            listOf("一二三四五六七八:0@0", "好:1@0", "好看:2@0", "真好看:3@0", "哈:1@1050", "嗯:2@1400", "呀:3@1750", "后来:0@5000"),
            l.flights.map { "${it.comment.text}:${it.lane}@${it.enter}" },
        )

        // A comment comes in at the right edge, and is gone once it and the room after it are past the left.
        fun at(ms: Long) = buildList { l.at(ms) { i, x -> add(l.flights[i].comment.text to x) } }
        assertEquals(listOf("一二三四五六七八" to 20f, "好" to 20f, "好看" to 20f, "真好看" to 20f), at(0))
        assertEquals(10f, at(3500).first { it.first == "一二三四五六七八" }.second, 0.001f)
        assertEquals(-9.997f, at(10_499).first { it.first == "一二三四五六七八" }.second, 0.001f)
        assertTrue(at(10_500).none { it.first == "一二三四五六七八" })
        assertTrue(at(1049).none { it.first == "哈" })
        assertTrue(at(1050).any { it.first == "哈" })
        assertTrue(at(-1).isEmpty())

        assertTrue(Lanes(comments, 0f).flights.isEmpty())
    }

    /**
     * An episode's comments come a stretch at a time, past stretches with
     * none, each once and on one line; starting the episode again asks for
     * nothing more, and another episode's take their place.
     */
    @Test
    fun loadsEachEpisodesComments() = runTest {
        val catalog = FakeCatalog().apply { playable("1", 2) }
        catalog.danmaku["1e1"] = mapOf(
            "" to DanmakuPage(items = listOf(Danmaku(id = "a", text = " 好看\n好看 ", offsetMs = 1000)), hasMore = true, cursor = "2"),
            "2" to DanmakuPage(hasMore = true, cursor = "3"), // a stretch with none
            "3" to DanmakuPage(
                items = listOf(
                    Danmaku(id = "a", text = " 好看\n好看 ", offsetMs = 1000),
                    Danmaku(id = "b", text = "  ", offsetMs = 61_000),
                    Danmaku(id = "c", text = "下一集", offsetMs = 62_000),
                ),
                hasMore = false,
            ),
        )
        catalog.danmaku["1e2"] = mapOf("" to DanmakuPage(items = listOf(Danmaku(id = "d", text = "第二集", offsetMs = 0))))
        val p = testModel(backgroundScope, catalog).player
        val asked = { catalog.calls.filter { it.startsWith("danmaku:") } }
        p.play("1", 1)
        runCurrent()
        assertEquals(listOf(Comment("好看 好看", 1000), Comment("下一集", 62_000)), p.danmaku)
        assertEquals(listOf("danmaku:1e1:", "danmaku:1e1:2", "danmaku:1e1:3"), asked())

        // Tried again, and in another definition: the same episode, the same comments.
        p.retry()
        runCurrent()
        p.chooseQuality(540)
        runCurrent()
        assertEquals(PlayerState.Buffering, p.state)
        assertEquals(3, asked().size)
        assertEquals(2, p.danmaku.size)

        p.play("1", 2)
        assertTrue(p.danmaku.isEmpty())
        runCurrent()
        assertEquals(listOf(Comment("第二集", 0)), p.danmaku)
    }

    /** Comments that fail to come leave the episode playing and keep those that came; the episode's next start tries afresh. */
    @Test
    fun failedCommentsLeavePlaybackAlone() = runTest {
        val catalog = FakeCatalog().apply { playable("1", 1) }
        catalog.danmaku["1e1"] = mapOf(
            "" to DanmakuPage(items = listOf(Danmaku(id = "a", text = "好看", offsetMs = 1000)), hasMore = true, cursor = "lost"),
        )
        val video = FakeVideo()
        val p = testModel(backgroundScope, catalog, video).player
        p.play("1", 1)
        runCurrent()
        assertEquals("1e1", video.loaded?.episodeId)
        assertTrue(video.wantsToPlay)
        assertEquals(PlayerState.Buffering, p.state)
        assertEquals(listOf(Comment("好看", 1000)), p.danmaku)

        catalog.danmaku["1e1"] = mapOf(
            "" to DanmakuPage(items = listOf(Danmaku(id = "a", text = "好看", offsetMs = 1000), Danmaku(id = "b", text = "真好看", offsetMs = 2000))),
        )
        p.retry()
        runCurrent()
        assertEquals(listOf(Comment("好看", 1000), Comment("真好看", 2000)), p.danmaku)
    }

    /** Comments turned off are neither fetched nor shown; turned back on, those of the episode playing come. */
    @Test
    fun turningCommentsOffAndOn() = runTest {
        val catalog = FakeCatalog().apply { playable("1", 1) }
        catalog.danmaku["1e1"] = mapOf("" to DanmakuPage(items = listOf(Danmaku(id = "a", text = "好看", offsetMs = 1000))))
        val p = testModel(backgroundScope, catalog).player
        assertTrue(p.showDanmaku)
        p.chooseDanmaku(false)
        p.play("1", 1)
        runCurrent()
        assertTrue(p.danmaku.isEmpty())
        assertTrue(catalog.calls.none { it.startsWith("danmaku:") })

        p.chooseDanmaku(true)
        runCurrent()
        assertEquals(listOf(Comment("好看", 1000)), p.danmaku)

        p.chooseDanmaku(false)
        assertTrue(p.danmaku.isEmpty())
    }
}
