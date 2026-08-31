package com.tapedeck.dsp

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val accent = Color(0xFFD9A441)
private val labelColor = Color(0xFFEDE3D0)
private val subtitleColor = Color(0xFFB0A08A)
private val panelColor = Color(0xFF2B2118)
private val artPlaceholder = Color(0xFF3A2F22)
private val errorColor = Color(0xFFD9534F)

private enum class LibraryTab(val label: String) {
    PLAYLISTS("Playlists"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
}

private sealed interface LibraryDetail {
    data class ArtistDetail(val artist: LibraryArtist) : LibraryDetail
    data class AlbumDetail(val album: LibraryAlbum) : LibraryDetail
}

@Composable
fun LibraryScreen(
    uiState: PlayerUiState,
    onScanFolder: () -> Unit,
    onSelectPlaylist: (LibraryPlaylistItem) -> Unit,
    onSelectSong: (List<LibrarySong>, LibrarySong) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.SONGS) }
    var detailStack by remember { mutableStateOf(listOf<LibraryDetail>()) }
    val currentDetail = detailStack.lastOrNull()

    BackHandler(enabled = detailStack.isNotEmpty()) {
        detailStack = detailStack.dropLast(1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (currentDetail) {
            is LibraryDetail.ArtistDetail -> {
                DetailHeader(title = currentDetail.artist.name, subtitle = "Albums") {
                    detailStack = detailStack.dropLast(1)
                }
                val albums = remember(currentDetail.artist) { LibraryScanner.albumsForArtist(currentDetail.artist) }
                AlbumsGrid(albums) { album -> detailStack = detailStack + LibraryDetail.AlbumDetail(album) }
            }

            is LibraryDetail.AlbumDetail -> {
                DetailHeader(title = currentDetail.album.name, subtitle = currentDetail.album.artist) {
                    detailStack = detailStack.dropLast(1)
                }
                AlbumDetailView(currentDetail.album) { song -> onSelectSong(currentDetail.album.songs, song) }
            }

            null -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Library", color = accent)
                        Text(
                            text = uiState.libraryFolderName ?: "No folder scanned yet",
                            color = subtitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (uiState.isLibraryLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accent, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    IconButton(onClick = onScanFolder) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Scan folder", tint = accent)
                    }
                }

                uiState.libraryError?.let { message ->
                    Text(
                        text = message,
                        color = errorColor,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                SecondaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = panelColor,
                    contentColor = accent,
                ) {
                    LibraryTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.label) },
                        )
                    }
                }

                when (selectedTab) {
                    LibraryTab.PLAYLISTS -> PlaylistsList(uiState.library.playlists, onSelectPlaylist)
                    LibraryTab.SONGS -> SongsList(uiState.library.songs) { songs, song -> onSelectSong(songs, song) }
                    LibraryTab.ALBUMS -> AlbumsGrid(uiState.library.albums) { album ->
                        detailStack = detailStack + LibraryDetail.AlbumDetail(album)
                    }
                    LibraryTab.ARTISTS -> ArtistsList(uiState.library.artists) { artist ->
                        detailStack = detailStack + LibraryDetail.ArtistDetail(artist)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accent)
        }
        Column {
            Text(text = title, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(text = it, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun AlbumDetailView(album: LibraryAlbum, onSelectSong: (LibrarySong) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArtBox(bitmap = album.albumArt, modifier = Modifier.size(96.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = album.name, color = labelColor)
                    Text(text = album.artist, color = subtitleColor)
                    val songWord = if (album.songs.size == 1) "song" else "songs"
                    Text(text = "${album.songs.size} $songWord", color = subtitleColor)
                }
            }
        }
        items(album.songs) { song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSong(song) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val trackLabel = if (song.trackNumber > 0) song.trackNumber.toString() else "•"
                Text(text = trackLabel, color = subtitleColor, modifier = Modifier.width(28.dp))
                Text(text = song.title, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlaylistsList(playlists: List<LibraryPlaylistItem>, onSelect: (LibraryPlaylistItem) -> Unit) {
    if (playlists.isEmpty()) {
        EmptyState("No playlists found in the scanned folder.")
        return
    }
    LazyColumn {
        items(playlists) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(item) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = accent)
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = item.name, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SongsList(songs: List<LibrarySong>, onSelect: (List<LibrarySong>, LibrarySong) -> Unit) {
    if (songs.isEmpty()) {
        EmptyState("No songs found in the scanned folder.")
        return
    }
    LazyColumn {
        items(songs) { song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(songs, song) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArtBox(bitmap = song.albumArt, modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = song.title, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = song.artist, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AlbumsGrid(albums: List<LibraryAlbum>, onSelect: (LibraryAlbum) -> Unit) {
    if (albums.isEmpty()) {
        EmptyState("No albums found.")
        return
    }
    LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(12.dp)) {
        items(albums) { album ->
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .clickable { onSelect(album) },
            ) {
                AlbumArtBox(bitmap = album.albumArt, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = album.name, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = album.artist, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistsList(artists: List<LibraryArtist>, onSelect: (LibraryArtist) -> Unit) {
    if (artists.isEmpty()) {
        EmptyState("No artists found in the scanned folder.")
        return
    }
    LazyColumn {
        items(artists) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(artist) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = accent)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = artist.name, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val albumWord = if (artist.albumCount == 1) "album" else "albums"
                    val songWord = if (artist.songs.size == 1) "song" else "songs"
                    Text(
                        text = "${artist.albumCount} $albumWord • ${artist.songs.size} $songWord",
                        color = subtitleColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = message, color = subtitleColor)
    }
}

@Composable
private fun AlbumArtBox(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(artPlaceholder),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = subtitleColor)
        }
    }
}
