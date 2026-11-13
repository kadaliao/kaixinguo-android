package com.kadaliao.kaixinguo.model

import dev.openhongguo.Danmaku

/** Comment is a line a viewer wrote at a moment of an episode, `ms` from its start: it flies across the video then. */
data class Comment(val text: String, val ms: Long)

/** The comment as it flies, on one line; null when it says nothing. */
internal fun comment(d: Danmaku): Comment? {
    val text = d.text.replace(Regex("\\s+"), " ").trim()
    return if (text.isEmpty()) null else Comment(text, d.offsetMs)
}

/** How long a comment takes to cross the screen, right edge to left, in ms of the episode. */
const val CROSSING_MS = 7000L

/** How many lanes the comments fly in, down from the top of the video. */
private const val LANE_COUNT = 4

/** The room between one comment and the next in a lane, in ems. */
private const val GAP = 2f

/** How long a comment may wait for a lane, in ms, before it is dropped. */
private const val MAX_WAIT_MS = 1500L

/**
 * About how wide the text is, in ems, erring wide: Chinese and the like an
 * em a letter, emoji a little more, and the rest, Latin and digits among
 * them, six tenths.
 */
internal fun ems(text: String): Float {
    var w = 0f
    var i = 0
    while (i < text.length) {
        val c = text.codePointAt(i)
        w += when {
            c < 0x1100 -> 0.6f
            c > 0xFFFF -> 1.2f
            else -> 1f
        }
        i += Character.charCount(c)
    }
    return w
}

/**
 * Flight is a comment on its way across the screen: its lane, from the top;
 * its width in ems; and when, in ms of the episode, it comes in at the right
 * edge and has gone past the left.
 */
class Flight(val comment: Comment, val lane: Int, val width: Float, val enter: Long, val leave: Long)

/**
 * Lanes is where an episode's comments fly over a screen `screen` ems wide,
 * the em being the comments' font size, so nothing need be measured to lay
 * them out. All cross in the same time, so none catches up with the one
 * ahead of it in its lane: each comes in at its moment in the topmost lane
 * the one ahead has cleared, else waits for the first to clear; one that
 * would wait longer than [MAX_WAIT_MS], as too many come at once, is dropped.
 * Where each is follows from the episode's time alone, so the comments stop
 * as the video pauses and keep to it as it moves on or back, faster or slower.
 */
class Lanes(comments: List<Comment>, val screen: Float, lanes: Int = LANE_COUNT) {
    /** The comments that fly, in the order they come in. */
    val flights: List<Flight>

    init {
        val out = ArrayList<Flight>()
        if (screen > 0 && lanes > 0) {
            val clear = LongArray(lanes) { Long.MIN_VALUE } // when each lane can take the next comment
            for (c in comments.sortedBy { it.ms }) {
                var lane = 0
                for (l in 1 until lanes) if (maxOf(clear[l], c.ms) < maxOf(clear[lane], c.ms)) lane = l
                val enter = maxOf(clear[lane], c.ms)
                if (enter - c.ms > MAX_WAIT_MS) continue
                val w = ems(c.text)
                clear[lane] = enter + crossing(w + GAP)
                out += Flight(c, lane, w, enter, enter + crossing(screen + w + GAP))
            }
        }
        flights = out
    }

    /** How long a comment takes to move so many ems, in ms. */
    private fun crossing(ems: Float): Long = (ems * CROSSING_MS / screen).toLong()

    /**
     * Calls `each` with the index of each comment on the screen `ms` into the
     * episode, and how far its left edge is from the screen's, in ems.
     */
    inline fun at(ms: Long, each: (Int, Float) -> Unit) {
        for (i in flights.indices) {
            val f = flights[i]
            if (ms < f.enter || ms >= f.leave) continue
            each(i, screen - (ms - f.enter) * screen / CROSSING_MS)
        }
    }
}
