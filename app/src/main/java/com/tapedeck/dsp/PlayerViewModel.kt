package com.tapedeck.dsp

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.IBinder
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaylistTrack(val title: String, val uri: Uri)

data class PlayerUiState(
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val trackName: String? = null,
    val albumName: String? = null,
    val albumArt: Bitmap? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val vuLeft: Float = 0f,
    val vuRight: Float = 0f,
    val tapeAge: Float = 0.3f,
    val dustDirt: Float = 0.2f,
    val tapeType: TapeType = TapeType.TYPE_I,
    val playlist: List<PlaylistTrack> = emptyList(),
    val currentTrackIndex: Int = -1,
    val availablePlaylists: List<PlaylistFileEntry> = emptyList(),
    val library: LibraryData = LibraryData(),
    val isLibraryLoading: Boolean = false,
    val libraryError: String? = null,
    val libraryFolderName: String? = null,
    val error: String? = null,
)

/**
 * UI-facing bridge to [PlaybackService], which owns the actual audio engine
 * so playback survives this ViewModel (and its Activity) being destroyed.
 * Library scanning and playlist-file resolution are pure I/O with no engine
 * dependency, so they stay here and hand a resolved track list to the
 * service only once ready to play.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var pendingPlaylistRoot: DocumentFile? = null
    private val prefs = application.getSharedPreferences("tapedeck_prefs", Application.MODE_PRIVATE)

    private var playbackService: PlaybackService? = null

    // Local-only concerns that must not be clobbered by the frequent
    // (meter-loop-driven) service state emissions merged into _uiState.
    private var resolvingLocalWork = false
    private var localErrorOverride: String? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? PlaybackService.LocalBinder)?.getService() ?: return
            playbackService = service
            viewModelScope.launch {
                service.state.collect { s ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = s.isLoading || resolvingLocalWork,
                            isPlaying = s.isPlaying,
                            trackName = s.trackName,
                            albumName = s.albumName,
                            albumArt = s.albumArt,
                            positionMs = s.positionMs,
                            durationMs = s.durationMs,
                            vuLeft = s.vuLeft,
                            vuRight = s.vuRight,
                            tapeAge = s.tapeAge,
                            dustDirt = s.dustDirt,
                            tapeType = s.tapeType,
                            playlist = s.playlist,
                            currentTrackIndex = s.currentTrackIndex,
                            error = localErrorOverride ?: s.error,
                        )
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
        }
    }

    init {
        val context = getApplication<Application>()
        val intent = Intent(context, PlaybackService::class.java)
        context.startService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        prefs.getString(KEY_LIBRARY_FOLDER_URI, null)?.let { saved ->
            scanLibrary(Uri.parse(saved))
        }
    }

    fun loadTrack(uri: Uri, displayName: String?, autoPlay: Boolean = false) {
        localErrorOverride = null
        playbackService?.loadTrack(uri, displayName, autoPlay)
    }

    fun reportUnsupportedFile(message: String) {
        localErrorOverride = message
        _uiState.update { it.copy(error = message) }
    }

    fun loadPlaylistFromTree(treeUri: Uri) {
        viewModelScope.launch {
            beginLocalWork()
            try {
                val context = getApplication<Application>()
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )

                val root = DocumentFile.fromTreeUri(context, treeUri)
                    ?: throw IllegalStateException("Could not open that folder")
                val found = withContext(Dispatchers.IO) { PlaylistResolver.findAllPlaylistFiles(root) }
                if (found.isEmpty()) throw IllegalStateException("No .m3u or .m3u8 file found in that folder")

                pendingPlaylistRoot = root
                if (found.size == 1) {
                    openPlaylistFile(root, found.first().file)
                } else {
                    endLocalWork()
                    _uiState.update { it.copy(availablePlaylists = found) }
                }
            } catch (t: Throwable) {
                endLocalWork(error = t.message ?: "Failed to load playlist")
            }
        }
    }

    fun selectPlaylist(entry: PlaylistFileEntry) {
        val root = pendingPlaylistRoot ?: return
        viewModelScope.launch {
            beginLocalWork()
            _uiState.update { it.copy(availablePlaylists = emptyList()) }
            openPlaylistFile(root, entry.file)
        }
    }

    fun dismissPlaylistPicker() {
        _uiState.update { it.copy(availablePlaylists = emptyList()) }
    }

    fun setLibraryFolder(treeUri: Uri) {
        val context = getApplication<Application>()
        context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putString(KEY_LIBRARY_FOLDER_URI, treeUri.toString()).apply()
        scanLibrary(treeUri)
    }

    private fun scanLibrary(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLibraryLoading = true, libraryError = null) }
            try {
                val context = getApplication<Application>()
                val folderName = DocumentFile.fromTreeUri(context, treeUri)?.name
                val data = LibraryScanner.scan(context, treeUri)
                _uiState.update {
                    it.copy(isLibraryLoading = false, library = data, libraryFolderName = folderName)
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(isLibraryLoading = false, libraryError = t.message ?: "Failed to scan folder") }
            }
        }
    }

    fun playSongInContext(songs: List<LibrarySong>, selected: LibrarySong) {
        val index = songs.indexOf(selected).coerceAtLeast(0)
        playbackService?.playQueue(songs.map { PlaylistTrack(it.title, it.uri) }, index)
    }

    fun playLibraryPlaylist(item: LibraryPlaylistItem) {
        val treeUri = prefs.getString(KEY_LIBRARY_FOLDER_URI, null) ?: return
        val root = DocumentFile.fromTreeUri(getApplication(), Uri.parse(treeUri)) ?: return
        viewModelScope.launch {
            beginLocalWork()
            openPlaylistFile(root, item.entry.file)
        }
    }

    private suspend fun openPlaylistFile(root: DocumentFile, playlistFile: DocumentFile) {
        try {
            val context = getApplication<Application>()
            val content = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(playlistFile.uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
            } ?: throw IllegalStateException("Could not read the playlist file")

            val tracks = PlaylistParser.parse(content).mapNotNull { entry ->
                val resolved = PlaylistResolver.resolveTrack(root, entry.path) ?: return@mapNotNull null
                val fallbackTitle = resolved.name?.substringBeforeLast('.') ?: entry.path
                PlaylistTrack(title = entry.title ?: fallbackTitle, uri = resolved.uri)
            }
            if (tracks.isEmpty()) throw IllegalStateException("Playlist had no resolvable tracks")

            endLocalWork()
            playbackService?.playQueue(tracks, 0)
        } catch (t: Throwable) {
            endLocalWork(error = t.message ?: "Failed to load playlist")
        }
    }

    private fun beginLocalWork() {
        resolvingLocalWork = true
        localErrorOverride = null
        _uiState.update { it.copy(isLoading = true, error = null) }
    }

    private fun endLocalWork(error: String? = null) {
        resolvingLocalWork = false
        localErrorOverride = error
        _uiState.update { it.copy(isLoading = false, error = error) }
    }

    fun playTrackAt(index: Int) = playbackService?.playTrackAt(index) ?: Unit

    fun playNextTrack() = playbackService?.playNextTrack() ?: Unit

    fun playPreviousTrack() = playbackService?.playPreviousTrack() ?: Unit

    fun togglePlayPause() = playbackService?.togglePlayPause() ?: Unit

    fun stop() = playbackService?.stopPlayback() ?: Unit

    fun seekBy(deltaMs: Long) = playbackService?.seekBy(deltaMs) ?: Unit

    fun setTapeAge(value01: Float) = playbackService?.setTapeAge(value01) ?: Unit

    fun setDustDirt(value01: Float) = playbackService?.setDustDirt(value01) ?: Unit

    fun setTapeType(type: TapeType) = playbackService?.setTapeType(type) ?: Unit

    override fun onCleared() {
        getApplication<Application>().unbindService(serviceConnection)
        super.onCleared()
    }

    private companion object {
        const val KEY_LIBRARY_FOLDER_URI = "library_folder_uri"
    }
}
