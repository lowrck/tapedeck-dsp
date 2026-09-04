package com.tapedeck.dsp

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LibrarySong(
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int,
    val albumArt: Bitmap?,
)

data class LibraryAlbum(
    val name: String,
    val artist: String,
    val albumArt: Bitmap?,
    val songs: List<LibrarySong>,
)

data class LibraryArtist(
    val name: String,
    val songs: List<LibrarySong>,
    val albumCount: Int,
)

data class LibraryPlaylistItem(val name: String, val entry: PlaylistFileEntry)

data class LibraryData(
    val songs: List<LibrarySong> = emptyList(),
    val albums: List<LibraryAlbum> = emptyList(),
    val artists: List<LibraryArtist> = emptyList(),
    val playlists: List<LibraryPlaylistItem> = emptyList(),
)

/**
 * Recursively scans a picked SAF folder tree for audio files, reading ID3
 * (or equivalent container) tags via MediaMetadataRetriever - no audio
 * decoding is involved, so this is fast even for a few hundred tracks.
 * Embedded album art is decoded at most once per (artist, album) pair and
 * shared by reference across every track/grouping that uses it, since
 * decoding a full bitmap per track would be wasteful for both time and
 * memory.
 *
 * The directory walk itself goes through [PlaylistResolver.buildIndex] (one
 * cursor query per directory) rather than [DocumentFile.listFiles] plus
 * per-child property access, which would cost a separate binder round trip
 * per file and dominate scan time for anything beyond a handful of tracks.
 */
object LibraryScanner {

    private val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg")
    private const val ALBUM_ART_TARGET_PX = 400

    suspend fun scan(context: Context, index: SafFileIndex): LibraryData = withContext(Dispatchers.IO) {
        val audioFiles = index.allFiles.filter {
            it.name.substringAfterLast('.', "").lowercase() in audioExtensions
        }

        val albumArtCache = HashMap<String, Bitmap?>()
        val songs = mutableListOf<LibrarySong>()

        for (indexed in audioFiles) {
            val song = readSong(context, indexed.file, albumArtCache) ?: continue
            songs.add(song)
        }

        val sortedSongs = songs.sortedBy { it.title.lowercase() }

        val albums = songs.groupBy { albumKey(it.artist, it.album) }
            .map { (_, groupSongs) ->
                val sample = groupSongs.first()
                LibraryAlbum(
                    name = sample.album,
                    artist = sample.artist,
                    albumArt = groupSongs.firstNotNullOfOrNull { it.albumArt },
                    songs = groupSongs.sortedBy { s -> if (s.trackNumber > 0) s.trackNumber else Int.MAX_VALUE },
                )
            }
            .sortedBy { it.name.lowercase() }

        val artists = songs.groupBy { it.artist }
            .map { (artistName, groupSongs) ->
                LibraryArtist(
                    name = artistName,
                    songs = groupSongs.sortedBy { s -> s.title.lowercase() },
                    albumCount = groupSongs.map { s -> s.album }.distinct().size,
                )
            }
            .sortedBy { it.name.lowercase() }

        val playlists = PlaylistResolver.findAllPlaylistFiles(index)
            .map { entry -> LibraryPlaylistItem(name = playlistDisplayName(entry), entry = entry) }
            .sortedBy { it.name.lowercase() }

        LibraryData(songs = sortedSongs, albums = albums, artists = artists, playlists = playlists)
    }

    private fun readSong(
        context: Context,
        file: DocumentFile,
        albumArtCache: MutableMap<String, Bitmap?>,
    ): LibrarySong? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, file.uri)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf { it.isNotEmpty() }
                ?: file.name?.substringBeforeLast('.')
                ?: "Unknown Title"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown Album"
            val trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0

            val key = albumKey(artist, album)
            val art = if (albumArtCache.containsKey(key)) {
                albumArtCache[key]
            } else {
                val decoded = retriever.embeddedPicture?.let { BitmapUtils.decodeSampledBitmap(it, ALBUM_ART_TARGET_PX) }
                albumArtCache[key] = decoded
                decoded
            }

            LibrarySong(file.uri, title, artist, album, trackNumber, art)
        } catch (t: Throwable) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun albumKey(artist: String, album: String) = "$artist||$album"

    private fun playlistDisplayName(entry: PlaylistFileEntry): String =
        entry.name.substringBeforeLast('.')

    /** Groups an artist's flat song list into per-album buckets, for drill-down navigation. */
    fun albumsForArtist(artist: LibraryArtist): List<LibraryAlbum> =
        artist.songs.groupBy { it.album }
            .map { (albumName, groupSongs) ->
                LibraryAlbum(
                    name = albumName,
                    artist = artist.name,
                    albumArt = groupSongs.firstNotNullOfOrNull { it.albumArt },
                    songs = groupSongs.sortedBy { s -> if (s.trackNumber > 0) s.trackNumber else Int.MAX_VALUE },
                )
            }
            .sortedBy { it.name.lowercase() }
}
