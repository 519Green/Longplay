package com.archivetv.playback

import com.archivetv.domain.ItemKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueMappingTest {
    private val entry = QueueEntry(
        identifier = "OTRR_Gunsmoke_Singles",
        fileName = "Gunsmoke 52-04-26 (001) Billy the Kid.mp3",
        title = "Billy the Kid",
        itemTitle = "Gunsmoke - Single Episodes",
        durationSec = 1740.04,
        kind = ItemKind.SPOKEN,
        isLibriVox = false,
        fileIndex = 0,
        fileCount = 487,
    )

    @Test fun mediaItemRoundTrips() {
        val item = entry.toMediaItem()
        assertThat(item.mediaId).isEqualTo("OTRR_Gunsmoke_Singles/Gunsmoke 52-04-26 (001) Billy the Kid.mp3")
        assertThat(item.localConfiguration!!.uri.toString())
            .endsWith("/OTRR_Gunsmoke_Singles/Gunsmoke%2052-04-26%20%28001%29%20Billy%20the%20Kid.mp3")
        assertThat(item.mediaMetadata.title.toString()).isEqualTo("Billy the Kid")
        assertThat(item.mediaMetadata.albumTitle.toString()).isEqualTo("Gunsmoke - Single Episodes")
        assertThat(item.mediaMetadata.artworkUri.toString())
            .isEqualTo("https://archive.org/services/img/OTRR_Gunsmoke_Singles")
        assertThat(item.toQueueEntry()).isEqualTo(entry)
    }

    @Test fun mediaItemWithoutUriIsRebuiltFromExtras() {
        // Controllers send items to the session without their URI.
        val stripped = entry.toMediaItem().buildUpon().setUri(null as String?).build()
        assertThat(stripped.withPlaybackUri().localConfiguration!!.uri.toString())
            .isEqualTo(entry.toMediaItem().localConfiguration!!.uri.toString())
    }

    @Test fun entityRoundTrips() {
        val librivox = entry.copy(kind = ItemKind.MUSIC, isLibriVox = true, durationSec = null)
        assertThat(librivox.toEntity(3).position).isEqualTo(3)
        assertThat(librivox.toEntity(3).kind).isEqualTo("music")
        assertThat(librivox.toEntity(3).toEntry()).isEqualTo(librivox)
    }

    @Test fun addedItemsAreRebuiltOrRejected() {
        val stripped = entry.toMediaItem().buildUpon().setUri(null as String?).build()
        assertThat(playableItems(listOf(stripped))!!.single().localConfiguration).isNotNull()
        // An external controller's item (mediaId only, no extras) cannot be played: reject the request.
        val foreign = androidx.media3.common.MediaItem.Builder().setMediaId("something").build()
        assertThat(playableItems(listOf(stripped, foreign))).isNull()
    }

    @Test fun onlyFileSpecificErrorsSkipToNext() {
        val skip = listOf(
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        )
        val stay = listOf(
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED,
        )
        skip.forEach { assertThat(skipsToNextFile(it)).isTrue() }
        stay.forEach { assertThat(skipsToNextFile(it)).isFalse() }
    }
}
