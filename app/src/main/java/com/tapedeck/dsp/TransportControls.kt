package com.tapedeck.dsp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

private val accent = Color(0xFFD9A441)
private val bodyDark = Color(0xFF2B2118)

@Composable
fun TransportControls(
    isPlaying: Boolean,
    onOpenFile: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onFastForward: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    fun click(action: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        action()
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = { click(onOpenFile) }) {
            Icon(Icons.Filled.FolderOpen, contentDescription = "Open file", tint = accent)
        }
        IconButton(onClick = { click(onOpenPlaylist) }) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Open playlist folder", tint = accent)
        }
        IconButton(onClick = { click(onRewind) }) {
            Icon(Icons.Filled.FastRewind, contentDescription = "Rewind 10s", tint = accent)
        }
        FilledIconButton(
            onClick = { click(onPlayPause) },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = bodyDark,
            )
        }
        IconButton(onClick = { click(onFastForward) }) {
            Icon(Icons.Filled.FastForward, contentDescription = "Forward 10s", tint = accent)
        }
        IconButton(onClick = { click(onStop) }) {
            Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = accent)
        }
    }
}
