package com.archivetv.ui.player

import androidx.media3.common.PlaybackException
import java.util.Locale

/** The chip shows in the last 20 s of a file that has a next one. */
const val UP_NEXT_WINDOW_MS = 20_000L

/**
 * "Up next: <label>" while [positionMs] is in the last [UP_NEXT_WINDOW_MS] of
 * file [index]; null on the last file, before the window, or while the
 * duration is still unknown.
 */
fun upNextText(index: Int, positionMs: Long, durationMs: Long, labels: List<String>): String? {
    if (index !in 0 until labels.lastIndex || durationMs <= 0) return null
    val left = durationMs - positionMs
    return if (left in 0..UP_NEXT_WINDOW_MS) "Up next: " + labels[index + 1] else null
}

/** 754_000 -> "12:34"; 3_723_000 -> "1:02:03". */
fun clockText(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
}

/** What went wrong, in words a viewer can act on. */
fun playbackErrorText(errorCode: Int): String = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    -> "The connection to Archive.org dropped."
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    -> "Archive.org did not send this file. It may be missing or restricted."
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    -> "This device can't play this file's format."
    else -> "Playback stopped unexpectedly."
}
