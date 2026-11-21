package com.archivetv

import android.os.Bundle
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

    companion object {
        const val EXTRA_OPEN_DETAIL = "open_detail"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    )
                }
            }
        }
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
