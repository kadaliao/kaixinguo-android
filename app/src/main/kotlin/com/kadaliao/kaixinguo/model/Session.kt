package com.kadaliao.kaixinguo.model

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Session is where the viewer was as they last left the app: the tab, the
 * pages open in each, the search, the chart and day chosen, the speed and
 * definition picked, and the episode the player showed. The next launch
 * opens there. The model fills in its part, the app's router the tab and
 * pages; each reads back what it wrote.
 */
data class Session(
    /** The tab showing, by its name; empty for the first. */
    val tab: String = "",
    /** The pages pushed in each tab, by the tab's name, bottom first, each as the router writes it. */
    val paths: Map<String, List<String>> = emptyMap(),
    val searchText: String = "",
    val searchQuery: String = "",
    val rankContent: String = "",
    val rankBoard: String = "",
    val newDate: String = "",
    /** The day the session was saved, as [todayKey] writes it: the day chosen among new releases only holds that day. */
    val day: String = "",
    val speed: Float = 1f,
    val quality: Int? = null,
    /** The episode the player showed; null when it didn't show. */
    val player: SessionPlayer? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("tab", tab)
        .put("paths", JSONObject().apply { for ((t, p) in paths) put(t, JSONArray(p)) })
        .put("search_text", searchText)
        .put("search_query", searchQuery)
        .put("rank_content", rankContent)
        .put("rank_board", rankBoard)
        .put("new_date", newDate)
        .put("day", day)
        .put("speed", speed.toDouble())
        .apply {
            if (quality != null) put("quality", quality)
            if (player != null) put("player", player.toJson())
        }

    companion object {
        fun fromJson(o: JSONObject): Session {
            val paths = LinkedHashMap<String, List<String>>()
            o.optJSONObject("paths")?.let { p ->
                for (t in p.keys()) {
                    val a = p.optJSONArray(t) ?: continue
                    paths[t] = (0 until a.length()).mapNotNull { a.optString(it).ifEmpty { null } }
                }
            }
            val speed = o.optDouble("speed", 1.0).toFloat()
            return Session(
                tab = o.optString("tab"),
                paths = paths,
                searchText = o.optString("search_text"),
                searchQuery = o.optString("search_query"),
                rankContent = o.optString("rank_content"),
                rankBoard = o.optString("rank_board"),
                newDate = o.optString("new_date"),
                day = o.optString("day"),
                speed = if (speed in 0.25f..4f) speed else 1f,
                quality = o.optInt("quality", 0).takeIf { it > 0 },
                player = o.optJSONObject("player")?.let(SessionPlayer::fromJson),
            )
        }
    }
}

/** SessionPlayer is the episode the player showed, and whether it played or was paused. */
data class SessionPlayer(val seriesID: String, val episode: Int, val playing: Boolean) {
    fun toJson(): JSONObject = JSONObject()
        .put("series_id", seriesID)
        .put("episode", episode)
        .put("playing", playing)

    companion object {
        fun fromJson(o: JSONObject): SessionPlayer? {
            val id = o.optString("series_id")
            val n = o.optInt("episode")
            if (id.isEmpty() || n < 1) return null
            return SessionPlayer(id, n, o.optBoolean("playing"))
        }
    }
}

/** The session in session.json. */
internal fun sessionFile(dir: File) = JSONFile(
    File(dir, "session.json"),
    encode = { s: Session -> s.toJson().toString() },
    decode = { Session.fromJson(JSONObject(it)) },
)
