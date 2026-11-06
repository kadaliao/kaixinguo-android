package com.kadaliao.kaixinguo.model

import dev.openhongguo.ContentType
import dev.openhongguo.InfoKind
import dev.openhongguo.RankContent
import dev.openhongguo.Season
import dev.openhongguo.Series
import dev.openhongguo.SeriesStatus
import dev.openhongguo.SeriesSummary
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Card is a series as a grid shows it. */
data class Card(
    val id: String,
    val title: String,
    val cover: String = "",
    /** The line under the title. */
    val sub: String = "",
    /** Its place on a chart; 0 off charts. */
    val rank: Int = 0,
    /**
     * How many episodes the series has, when the page that listed it said:
     * `sub` may say something else, as a chart's score.
     */
    val episodes: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("cover", cover)
        .put("sub", sub)
        .put("rank", rank)
        .put("episodes", episodes)

    companion object {
        fun of(s: SeriesSummary) =
            Card(s.id, s.title, s.coverUrl, episodeCount(s.episodeCount), episodes = s.episodeCount)

        /** The card of a series' page, as the grids show it. */
        fun of(s: Series) =
            Card(s.id, s.title, s.coverUrl, episodeCount(s.episodeCount), episodes = s.episodeCount)

        fun fromJson(o: JSONObject) = Card(
            id = o.optString("id"),
            title = o.optString("title"),
            cover = o.optString("cover"),
            sub = o.optString("sub"),
            rank = o.optInt("rank"),
            episodes = o.optInt("episodes"),
        )
    }
}

/** FeedPage is what a feed's fetch returns. */
class FeedPage(
    var cards: List<Card> = emptyList(),
    var cursor: String = "",
    var more: Boolean = false,
) {
    // What some feeds also tell.
    var contents: List<RankContent> = emptyList()
    var board = ""
    var dates: List<String> = emptyList()
}

// MARK: - Labels

/** A card's line for a series of n episodes, or nothing when n is unknown. */
fun episodeCount(n: Int): String = if (n > 0) "$n 集" else ""

/** The series' episodes as its page says them: 更新至 12 集, 全 80 集. */
fun episodesLabel(s: Series): String = when {
    s.rightText.isNotEmpty() -> s.rightText
    s.status == SeriesStatus.UPDATING -> "更新至 ${s.episodeCount} 集"
    else -> "全 ${s.episodeCount} 集"
}

/** Writes a count as the Hongguo app does: 4165万, 1.2亿. */
fun heat(n: Long): String = when {
    n >= 100_000_000 -> {
        var s = String.format(Locale.ROOT, "%.1f", n / 1e8)
        if (s.endsWith(".0")) s = s.dropLast(2)
        s + "亿"
    }
    n >= 10_000 -> "${n / 10_000}万"
    else -> "$n"
}

/** The series at a glance: its kind, its episodes, its heat. */
fun metaLine(s: Series): String {
    val parts = ArrayList<String>()
    s.rows.firstOrNull { it.kind == InfoKind.CATEGORY && it.text.isNotEmpty() }?.let { parts += it.text }
    when (s.contentType) {
        ContentType.MOTION_COMIC -> parts += "漫剧"
        ContentType.MOVIE -> parts += "电影"
    }
    if (s.episodeCount > 0) parts += episodesLabel(s)
    if (s.heat > 0) parts += "热度 " + heat(s.heat)
    return parts.joinToString("  ·  ")
}

private val dayFormat = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)

/** Names a day of the calendar (YYYYMMDD) as people say it. */
fun dayLabel(d: String, today: String): String {
    val day = runCatching { LocalDate.parse(d, dayFormat) }.getOrNull() ?: return d
    val now = runCatching { LocalDate.parse(today, dayFormat) }.getOrNull() ?: return d
    return when (ChronoUnit.DAYS.between(now, day)) {
        0L -> "今天"
        1L -> "明天"
        -1L -> "昨天"
        else -> "${day.monthValue}月${day.dayOfMonth}日"
    }
}

/** Today as the calendar of new releases names days. */
fun todayKey(now: LocalDate = LocalDate.now()): String = now.format(dayFormat)

fun seasonLabel(se: Season): String = "第 ${se.index} 季"

/** Names the action on a series that is, or is not, saved. */
fun favLabel(saved: Boolean): String = if (saved) "取消收藏" else "收藏"
