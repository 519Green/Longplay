package com.archivetv

import android.content.Intent
import android.os.Bundle
import com.archivetv.playback.EXTRA_OPEN_NOW_PLAYING
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.archivetv.ui.AppNavHost
import com.archivetv.ui.theme.ArchiveTvTheme
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private val voiceSearchRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** The playback notification (and session activity) asks for Now Playing. */
    private val nowPlayingRequests = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    companion object {
        const val EXTRA_OPEN_DETAIL = "open_detail"
    }

    private fun handleNowPlaying(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) {
            intent.removeExtra(EXTRA_OPEN_NOW_PLAYING)
            nowPlayingRequests.tryEmit(Unit)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNowPlaying(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNowPlaying(intent)
        setContent {
            ArchiveTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                ) {
                    AppNavHost(
                        voiceSearchRequests = voiceSearchRequests,
                        // Testing / future deep links: adb shell am start -n
                        // com.archivetv/.MainActivity --es open_detail <identifier>
                        openDetail = intent?.getStringExtra(EXTRA_OPEN_DETAIL),
                        nowPlayingRequests = nowPlayingRequests,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as ArchiveTvApp).container.audio.connect()
    }

    override fun onStop() {
        (application as ArchiveTvApp).container.audio.disconnect()
        super.onStop()
    }

    /**
     * Remotes whose mic/search button sends KEYCODE_SEARCH to the foreground app
     * open in-app voice search. KEYCODE_VOICE_ASSIST is handled by the system
     * (Assistant) and never reaches apps.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            voiceSearchRequests.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // Some remotes trigger the system search request instead of a raw key.
    override fun onSearchRequested(): Boolean {
        voiceSearchRequests.tryEmit(Unit)
        return true
    }
}
