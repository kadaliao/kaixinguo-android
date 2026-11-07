package com.kadaliao.kaixinguo.model

import dev.openhongguo.Album
import dev.openhongguo.DiscoverOptions
import dev.openhongguo.Episode
import dev.openhongguo.EpisodeOptions
import dev.openhongguo.ErrorCode
import dev.openhongguo.HotSearch
import dev.openhongguo.NewReleasePage
import dev.openhongguo.NewReleaseQuery
import dev.openhongguo.OpenhongguoException
import dev.openhongguo.Page
import dev.openhongguo.PlatformType
import dev.openhongguo.Playback
import dev.openhongguo.PlaybackResult
import dev.openhongguo.SearchOptions
import dev.openhongguo.Series
import dev.openhongguo.SeriesDetail
import dev.openhongguo.SeriesSummary
import dev.openhongguo.ShortRankQuery
import dev.openhongguo.ShortRanking
import dev.openhongguo.Stream
import dev.openhongguo.Suggestion
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.nio.file.Files

/** FakeCatalog answers from what a test gives it, without the network. */
class FakeCatalog : Catalog {
    val discoverPages = HashMap<String, Page<SeriesSummary>>() // by cursor
    val series = HashMap<String, Series>()
    val episodes = HashMap<String, List<Episode>>()
    val albums = HashMap<String, Album>()
    var search: (String, SearchOptions) -> Page<SeriesSummary> = { _, _ -> Page() }
    val batch = HashMap<String, Series>()
    var suggestions = emptyList<Suggestion>()
    val calls = ArrayList<String>()

    override suspend fun discover(options: DiscoverOptions): Page<SeriesSummary> {
        calls += "discover:${options.cursor.orEmpty()}"
        return discoverPages[options.cursor.orEmpty()] ?: Page()
    }

    override suspend fun search(query: String, options: SearchOptions): Page<SeriesSummary> {
        calls += "search:$query:${options.tab.orEmpty()}"
        return search.invoke(query, options)
    }

    override suspend fun suggest(prefix: String) = suggestions
    override suspend fun hotSearch() = HotSearch()
    override suspend fun newReleases(query: NewReleaseQuery) = NewReleasePage()

    override suspend fun getSeries(seriesId: String): Series {
        calls += "getSeries:$seriesId"
        return series[seriesId] ?: throw OpenhongguoException(ErrorCode.CONTENT_UNAVAILABLE, "gone")
    }

    override suspend fun getSeriesBatch(seriesIds: List<String>) = seriesIds.mapNotNull { id -> batch[id]?.let { SeriesDetail(series = it) } }

    override suspend fun listEpisodes(seriesId: String, options: EpisodeOptions) = Page(items = episodes[seriesId].orEmpty())

    override suspend fun seasons(seriesId: String) =
        albums[seriesId] ?: throw OpenhongguoException(ErrorCode.ALBUM_UNAVAILABLE, "none")

    override suspend fun shortRank(query: ShortRankQuery) = ShortRanking()

    /** The episodes the server gives, by ID; the others it won't give anonymously. */
    val playbacks = HashMap<String, PlaybackResult>()

    override suspend fun resolvePlayback(episodeId: String): PlaybackResult {
        calls += "resolve:$episodeId"
        return playbacks[episodeId] ?: throw OpenhongguoException(ErrorCode.ANONYMOUS_LIMITED, "limited")
    }
}

/** FakeVideo plays nothing, and does at once what it is told. */
class FakeVideo : Video {
    override var hasItem = false
    override var position = Double.NaN
    override var duration = Double.NaN
    override var wantsToPlay = false
    override var status = TimeControl.PAUSED
    override var speed = 1f
    override var listener: VideoListener? = null
    var loaded: VideoItem? = null

    override fun load(item: VideoItem, start: Double) {
        loaded = item
        hasItem = true
        position = start
    }

    override fun clear() {
        loaded = null
        hasItem = false
        position = Double.NaN
        wantsToPlay = false
    }

    override fun play() {
        wantsToPlay = true
    }

    override fun pause() {
        wantsToPlay = false
    }

    override fun seek(seconds: Double) {
        position = seconds
    }

    /** Says what the video is doing, as ExoPlayer does a moment after it is told. */
    fun reports(s: TimeControl) {
        status = s
        listener?.statusChanged()
    }
}

fun tempDir(): File = Files.createTempDirectory("kxg").toFile()

/** A model on a fake catalog and video, its files in a fresh directory, or in dir to read what a model before left. */
fun testModel(
    scope: CoroutineScope,
    catalog: FakeCatalog = FakeCatalog(),
    video: FakeVideo = FakeVideo(),
    cache: File? = null,
    dir: File = tempDir(),
): AppModel = AppModel(catalog, File(dir, "data"), cache ?: File(dir, "cache"), scope, video) { _, _ -> "kaixinguo-stream://episode/test.mp4" }

/** A series of n episodes in the catalog, each of which the server gives. */
fun FakeCatalog.playable(id: String, n: Int) {
    series[id] = Series(id = id, title = "剧$id", episodeCount = n, platform = PlatformType.OUTER)
    episodes[id] = episodes(id, n)
    for (e in episodes.getValue(id)) {
        playbacks[e.id] = PlaybackResult(playback = Playback(episodeId = e.id, streams = listOf(Stream(definition = "720p", url = "https://cdn/${e.id}.mp4"))))
    }
}

fun episodes(seriesID: String, n: Int, locked: Set<Int> = emptySet()): List<Episode> =
    (1..n).map { Episode(id = "${seriesID}e$it", seriesId = seriesID, number = it, playable = it !in locked) }
