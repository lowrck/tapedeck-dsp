package com.tapedeck.dsp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TapeDeckTheme {
                AppRoot()
            }
        }
    }
}

private val backgroundColor = Color(0xFF171310)
private val panelColor = Color(0xFF2B2118)
private val accent = Color(0xFFD9A441)
private val textColor = Color(0xFFEDE3D0)
private val subtitleColor = Color(0xFFB0A08A)
private val errorColor = Color(0xFFD9534F)

private enum class AppTab { PLAYER, LIBRARY }

@Composable
private fun TapeDeckTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = accent,
        background = backgroundColor,
        surface = backgroundColor,
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun AppRoot(viewModel: PlayerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.PLAYER) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment ?: "Selected track"
            val extension = name.substringAfterLast('.', "").lowercase()
            if (extension == "m3u" || extension == "m3u8") {
                viewModel.reportUnsupportedFile(
                    "That's a playlist file - use the playlist icon instead so its folder can be searched for the tracks it references.",
                )
            } else {
                viewModel.loadTrack(uri, name)
            }
        }
    }

    val playlistFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.loadPlaylistFromTree(uri)
        }
    }

    val libraryFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.setLibraryFolder(uri)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* Playback works either way; this only affects notification visibility. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        bottomBar = {
            NavigationBar(containerColor = panelColor) {
                NavigationBarItem(
                    selected = selectedTab == AppTab.PLAYER,
                    onClick = { selectedTab = AppTab.PLAYER },
                    icon = { Icon(Icons.Filled.MusicNote, contentDescription = "Player") },
                    label = { Text("Player") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accent,
                        selectedTextColor = accent,
                        unselectedIconColor = subtitleColor,
                        unselectedTextColor = subtitleColor,
                        indicatorColor = Color(0xFF3A2F22),
                    ),
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.LIBRARY,
                    onClick = { selectedTab = AppTab.LIBRARY },
                    icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "Library") },
                    label = { Text("Library") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accent,
                        selectedTextColor = accent,
                        unselectedIconColor = subtitleColor,
                        unselectedTextColor = subtitleColor,
                        indicatorColor = Color(0xFF3A2F22),
                    ),
                )
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                AppTab.PLAYER -> PlayerTabContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenFile = { filePicker.launch(arrayOf("audio/*")) },
                    onOpenPlaylist = { playlistFolderPicker.launch(null) },
                )

                AppTab.LIBRARY -> LibraryScreen(
                    uiState = uiState,
                    onScanFolder = { libraryFolderPicker.launch(null) },
                    onSelectPlaylist = { item ->
                        viewModel.playLibraryPlaylist(item)
                        selectedTab = AppTab.PLAYER
                    },
                    onSelectSong = { songs, song ->
                        viewModel.playSongInContext(songs, song)
                        selectedTab = AppTab.PLAYER
                    },
                )
            }

            if (uiState.availablePlaylists.isNotEmpty()) {
                PlaylistPickerDialog(
                    entries = uiState.availablePlaylists,
                    onSelect = { entry -> viewModel.selectPlaylist(entry) },
                    onDismiss = { viewModel.dismissPlaylistPicker() },
                )
            }
        }
    }
}

@Composable
private fun PlayerTabContent(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onOpenFile: () -> Unit,
    onOpenPlaylist: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(text = uiState.trackName ?: "No tape loaded", color = textColor)
        uiState.albumName?.let { album -> Text(text = album, color = subtitleColor) }
        uiState.error?.let { message -> Text(text = message, color = errorColor) }

        val albumArtImage = remember(uiState.albumArt) { uiState.albumArt?.asImageBitmap() }
        CassetteDeckView(
            isPlaying = uiState.isPlaying,
            progressFraction = if (uiState.durationMs > 0) {
                (uiState.positionMs.toFloat() / uiState.durationMs).coerceIn(0f, 1f)
            } else {
                0f
            },
            vuLeft = uiState.vuLeft,
            vuRight = uiState.vuRight,
            albumArt = albumArtImage,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        TransportControls(
            isPlaying = uiState.isPlaying,
            onOpenFile = onOpenFile,
            onOpenPlaylist = onOpenPlaylist,
            onRewind = { viewModel.seekBy(-10_000) },
            onPlayPause = { viewModel.togglePlayPause() },
            onFastForward = { viewModel.seekBy(10_000) },
            onStop = { viewModel.stop() },
        )

        if (uiState.playlist.isNotEmpty()) {
            PlaylistBar(
                currentIndex = uiState.currentTrackIndex,
                trackCount = uiState.playlist.size,
                trackTitle = uiState.playlist.getOrNull(uiState.currentTrackIndex)?.title ?: "",
                shuffleEnabled = uiState.shuffleEnabled,
                onPrevious = { viewModel.playPreviousTrack() },
                onNext = { viewModel.playNextTrack() },
                onToggleShuffle = { viewModel.toggleShuffle() },
            )
        }

        ConditionPanel(
            tapeAge = uiState.tapeAge,
            dustDirt = uiState.dustDirt,
            tapeType = uiState.tapeType,
            onTapeAgeChange = viewModel::setTapeAge,
            onDustDirtChange = viewModel::setDustDirt,
            onTapeTypeChange = viewModel::setTapeType,
        )
    }
}
