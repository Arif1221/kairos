package com.skarif.kairos

import android.app.Application
import com.skarif.kairos.data.ArtworkRepository
import com.skarif.kairos.data.LyricsRepository
import com.skarif.kairos.data.MediaScanner
import com.skarif.kairos.data.SettingsStore
import com.skarif.kairos.data.db.PlayerDatabase
import com.skarif.kairos.playback.PlayerConnection
import com.skarif.kairos.playback.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PlayerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Hand-written dependency container. A DI library (Hilt) would add build setup without
 * adding anything this small app needs yet; swap it in if the graph grows.
 */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = PlayerDatabase.create(app)
    val settings = SettingsStore(app, scope)
    val scanner = MediaScanner(app, db.songs())
    val lyrics = LyricsRepository(app, db.lyricsOffsets())
    val artwork = ArtworkRepository(app)
    val player = PlayerConnection(app, scope)
    val sleep = SleepTimer(player, scope)
}
