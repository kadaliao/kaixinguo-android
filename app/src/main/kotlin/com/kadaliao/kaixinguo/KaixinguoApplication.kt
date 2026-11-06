package com.kadaliao.kaixinguo

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.media3.datasource.DefaultHttpDataSource
import com.kadaliao.kaixinguo.covers.CoverStore
import com.kadaliao.kaixinguo.model.AppModel
import com.kadaliao.kaixinguo.model.SdkCatalog
import com.kadaliao.kaixinguo.model.debugLogger
import com.kadaliao.kaixinguo.playback.ExoVideo
import com.kadaliao.kaixinguo.playback.StreamRegistry
import com.kadaliao.kaixinguo.ui.Router
import dev.openhongguo.ClientOptions
import dev.openhongguo.OpenhongguoClient
import kotlinx.coroutines.MainScope
import java.io.File

/**
 * KaixinguoApplication holds what lives as long as the app: the model, the
 * video, the covers and where the viewer is. The activity may come and go,
 * as the screen turns or picture in picture starts; they stay.
 */
class KaixinguoApplication : Application() {
    /** The app's model; null when the SDK could not start, as [startError] says. */
    var model: AppModel? = null
        private set
    var startError = ""
        private set
    lateinit var video: ExoVideo
        private set
    lateinit var covers: CoverStore
        private set
    val router = Router()

    override fun onCreate() {
        super.onCreate()
        val debug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debug) debugLogger = { Log.d("kxg", it) }
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        val streams = StreamRegistry(http)
        video = ExoVideo(this, streams, http)
        covers = CoverStore(File(cacheDir, "covers"))
        try {
            val client = OpenhongguoClient.create(
                this,
                ClientOptions(retryAttempts = 3, deviceResets = 3, logger = if (debug) { m -> Log.d("sdk", m) } else null),
            )
            model = AppModel(SdkCatalog(client), filesDir, cacheDir, MainScope(), video, streams).also { m ->
                m.artwork = covers::jpeg
            }
        } catch (e: Exception) {
            startError = e.message ?: e.toString()
            Log.e("kxg", "start", e)
        }
    }
}
