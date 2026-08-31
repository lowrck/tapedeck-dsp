package com.tapedeck.dsp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val accent = Color(0xFFD9A441)
private val labelColor = Color(0xFFEDE3D0)
private val panelColor = Color(0xFF2B2118)

@Composable
fun PlaylistPickerDialog(
    entries: List<PlaylistFileEntry>,
    onSelect: (PlaylistFileEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = panelColor,
        title = { Text(text = "Choose a playlist", color = accent) },
        text = {
            LazyColumn {
                items(entries) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(entry) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = accent)
                        Spacer(modifier = Modifier.width(12.dp))
                        val displayName = (entry.file.name ?: entry.relativePath).substringBeforeLast('.')
                        Text(text = displayName, color = labelColor)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel", color = accent) }
        },
    )
}
