package com.kadaliao.kaixinguo.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.openhongguo.Episode
import dev.openhongguo.PlatformType
import dev.openhongguo.Season
import dev.openhongguo.Series

/** SeriesPage is a series and its episodes, as its page shows them. */
class SeriesPage internal constructor(val id: String) {
    companion object {
        const val EPISODES_PER_PART = 100
    }

    var series by mutableStateOf<Series?>(null)
        internal set
    var episodes by mutableStateOf(emptyList<Episode>())
        internal set
    var loading by mutableStateOf(false)
        internal set
    var error by mutableStateOf<Throwable?>(null)
        internal set

    /** Which hundred of episodes shows. */
    var part by mutableIntStateOf(0)

    /**
     * The seasons of the series, the same list on each season's page;
     * empty when there is one season, null until known.
     */
    var seasons by mutableStateOf<List<Season>?>(null)
        internal set
    internal var seasonsLoading = false

    fun episode(n: Int): Episode? {
        // The list comes in order, so the nth is nearly always at n - 1.
        episodes.getOrNull(n - 1)?.let { if (it.number == n) return it }
        return episodes.firstOrNull { it.number == n }
    }

    /** The season after this one, once it has episodes. */
    fun nextSeason(): Season? {
        val list = seasons ?: return null
        val i = list.indexOfFirst { it.seriesId == id }
        if (i < 0 || i + 1 >= list.size || list[i + 1].episodeCount <= 0) return null
        return list[i + 1]
    }

    /** The labels of the hundreds of episodes, for a long series. */
    val parts: List<String>
        get() {
            val n = episodes.size
            if (n <= EPISODES_PER_PART) return emptyList()
            return (0 until n step EPISODES_PER_PART).map { lo -> "${lo + 1}–${minOf(n, lo + EPISODES_PER_PART)}" }
        }

    /** The episodes of the part showing. */
    val partEpisodes: List<Episode>
        get() {
            val n = episodes.size
            if (n == 0) return emptyList()
            val p = minOf(part, maxOf(0, (n - 1) / EPISODES_PER_PART))
            return episodes.subList(p * EPISODES_PER_PART, minOf(n, (p + 1) * EPISODES_PER_PART))
        }

    /** Shows the part that holds episode n. */
    fun reveal(n: Int) {
        if (n > 0) part = (n - 1) / EPISODES_PER_PART
    }

    /** Whether the series is a novel or audiobook, which can't play. */
    val isBook: Boolean get() = series?.platform == PlatformType.UNKNOWN
}
