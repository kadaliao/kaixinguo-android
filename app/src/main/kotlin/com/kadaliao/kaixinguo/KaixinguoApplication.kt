package com.kadaliao.kaixinguo

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.media3.datasource.DefaultHttpDataSource
import com.kadaliao.kaixinguo.covers.CoverStore
import com.kadaliao.kaixinguo.model.AppModel
import com.kadaliao.kaixinguo.model.SdkCatalog
import com.kadaliao.kaixinguo.model.debugLogger
import com.kadaliao.kaixinguo.playback.CastVideo
import com.kadaliao.kaixinguo.playback.ExoVideo
import com.kadaliao.kaixinguo.playback.StreamRegistry
import com.kadaliao.kaixinguo.playback.StreamServer
import com.kadaliao.kaixinguo.ui.Router
import dev.openhongguo.ClientOptions
import dev.openhongguo.OpenhongguoClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
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

    /** The video the player plays: [video] here, or cast to a television. */
    lateinit var cast: CastVideo
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
        cast = CastVideo(this, video, StreamServer(streams, http))
        covers = CoverStore(File(cacheDir, "covers"))
        try {
            val options = ClientOptions(retryAttempts = 3, deviceResets = 3, logger = if (debug) { m -> Log.d("sdk", m) } else null)
            val client = OpenhongguoClient.create(this, options)
            // The client for the signing service has a device of its own, so
            // a device reset on one does not overwrite the other's.
            val catalog = SdkCatalog(client) { url, token ->
                OpenhongguoClient.create(
                    File(noBackupFilesDir, "openhongguo/anonymous-signer.json"),
                    options.copy(signerUrl = url, signerToken = token.ifEmpty { null }),
                )
            }
            val scope = MainScope()
            model = AppModel(
                catalog, filesDir, cacheDir, scope, cast,
                signerFile = File(noBackupFilesDir, "signer.json"),
                streams = streams,
            ).also { m ->
                m.artwork = covers::jpeg
                // The app opens where the viewer left it: the tab and pages
                // now, the player as the first window opens. Where they are
                // goes to disk as it changes, so the next launch finds it
                // however this one ends.
                router.restore(m.lastSession)
                scope.launch { snapshotFlow { router.saveTo(m.session()) }.collect(m::saveSession) }
            }
        } catch (e: Exception) {
            startError = e.message ?: e.toString()
            Log.e("kxg", "start", e)
        }
    }

    /** Saves where the viewer is now, as the app leaves the screen. */
    fun saveSession() {
        val m = model ?: return
        m.saveSession(router.saveTo(m.session()))
    }
}
