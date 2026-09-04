package com.tapedeck.dsp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val accent = Color(0xFFD9A441)
private val labelColor = Color(0xFFEDE3D0)
private val inactiveIconColor = Color(0xFFB0A08A)

@Composable
fun PlaylistBar(
    currentIndex: Int,
    trackCount: Int,
    trackTitle: String,
    shuffleEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = if (shuffleEnabled) "Disable shuffle" else "Enable shuffle",
                tint = if (shuffleEnabled) accent else inactiveIconColor,
            )
        }
        IconButton(onClick = onPrevious, enabled = currentIndex > 0) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track", tint = accent)
        }
        Text(
            text = "${currentIndex + 1}/$trackCount  •  $trackTitle",
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        IconButton(onClick = onNext, enabled = currentIndex < trackCount - 1) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next track", tint = accent)
        }
    }
}
