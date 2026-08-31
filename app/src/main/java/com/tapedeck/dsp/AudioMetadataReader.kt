package com.tapedeck.dsp

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioMetadata(
    val title: String?,
    val album: String?,
    val albumArt: Bitmap?,
)

/** Reads ID3/container tags (title, album, embedded cover art) without decoding audio. */
object AudioMetadataReader {

    private const val ALBUM_ART_TARGET_PX = 800

    suspend fun read(context: Context, uri: Uri): AudioMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf { it.isNotEmpty() }
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim()?.takeIf { it.isNotEmpty() }
            val albumArt = retriever.embeddedPicture?.let { BitmapUtils.decodeSampledBitmap(it, ALBUM_ART_TARGET_PX) }
            AudioMetadata(title, album, albumArt)
        } catch (t: Throwable) {
            AudioMetadata(null, null, null)
        } finally {
            retriever.release()
        }
    }
}
