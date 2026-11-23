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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.archivetv.ui.AppNavHost
import com.archivetv.ui.settings.PinDialog
import com.archivetv.ui.settings.PinMode
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
                    val container = (application as ArchiveTvApp).container
                    // Parental PIN on first run, before the app itself, so a parent sets it before a kid can.
                    var askPin by remember { mutableStateOf(!container.settings.hasPin() && !container.pinPromptShown) }
                    if (askPin) {
                        PinDialog(
                            mode = PinMode.SET,
                            gate = container.pinGate,
                            settings = container.settings,
                            firstTitle = "Set a parental PIN",
                            intro = "The PIN is needed to turn off the adult-content and public-domain filters.",
                            dismissLabel = "Not now",
                        ) {
                            container.pinPromptShown = true
                            askPin = false
                        }
                    } else {
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
