package com.archivetv.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.archivetv.data.db.QueueEntryEntity
import com.archivetv.domain.ItemKind
import com.archivetv.domain.PlayableFile

private const val ID = "id"
private const val FILE = "file"
private const val TITLE = "title"
private const val ITEM_TITLE = "itemTitle"
private const val INDEX = "index"
private const val COUNT = "count"
private const val KIND = "kind"
private const val LIBRIVOX = "lv"
private const val DURATION = "dur"

private fun QueueEntry.url() = PlayableFile(identifier, fileName).url

/** The playlist item for [this]; everything needed to rebuild the entry travels in extras. */
fun QueueEntry.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putString(ID, identifier)
        putString(FILE, fileName)
        putString(TITLE, title)
        putString(ITEM_TITLE, itemTitle)
        putInt(INDEX, fileIndex)
        putInt(COUNT, fileCount)
        putString(KIND, kind.name)
        putBoolean(LIBRIVOX, isLibriVox)
        durationSec?.let { putDouble(DURATION, it) }
    }
    return MediaItem.Builder()
        .setMediaId("$identifier/$fileName")
        .setUri(url())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setAlbumTitle(itemTitle)
                .setArtworkUri(Uri.parse("https://archive.org/services/img/$identifier"))
                .setExtras(extras)
                .build()
        )
        .build()
}

/** The queue entry an item was built from, or null for items this app did not make. */
fun MediaItem.toQueueEntry(): QueueEntry? {
    val x = mediaMetadata.extras ?: return null
    val id = x.getString(ID) ?: return null
    val file = x.getString(FILE) ?: return null
    return QueueEntry(
        identifier = id,
        fileName = file,
        title = x.getString(TITLE) ?: file,
        itemTitle = x.getString(ITEM_TITLE) ?: id,
        durationSec = if (x.containsKey(DURATION)) x.getDouble(DURATION) else null,
        kind = runCatching { ItemKind.valueOf(x.getString(KIND).orEmpty()) }.getOrDefault(ItemKind.SPOKEN),
        isLibriVox = x.getBoolean(LIBRIVOX),
        fileIndex = x.getInt(INDEX),
        fileCount = x.getInt(COUNT),
    )
}

/** Controllers strip the URI when sending items to the session; restore it from the extras. */
fun MediaItem.withPlaybackUri(): MediaItem =
    if (localConfiguration != null) this else toQueueEntry()?.toMediaItem() ?: this

fun QueueEntry.toEntity(position: Int) = QueueEntryEntity(
    position = position,
    identifier = identifier,
    fileName = fileName,
    title = title,
    itemTitle = itemTitle,
    durationSec = durationSec,
    kind = kind.name.lowercase(),
    isLibriVox = isLibriVox,
    fileIndex = fileIndex,
    fileCount = fileCount,
)

fun QueueEntryEntity.toEntry() = QueueEntry(
    identifier = identifier,
    fileName = fileName,
    title = title,
    itemTitle = itemTitle,
    durationSec = durationSec,
    kind = ItemKind.entries.firstOrNull { it.name.lowercase() == kind } ?: ItemKind.SPOKEN,
    isLibriVox = isLibriVox,
    fileIndex = fileIndex,
    fileCount = fileCount,
)
