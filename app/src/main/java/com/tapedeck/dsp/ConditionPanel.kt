package com.tapedeck.dsp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val accent = Color(0xFFD9A441)
private val labelColor = Color(0xFFEDE3D0)

@Composable
fun ConditionPanel(
    tapeAge: Float,
    dustDirt: Float,
    tapeType: TapeType,
    onTapeAgeChange: (Float) -> Unit,
    onDustDirtChange: (Float) -> Unit,
    onTapeTypeChange: (TapeType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = "Condition", color = accent)
        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Tape Age", color = labelColor)
        Slider(
            value = tapeAge,
            onValueChange = onTapeAgeChange,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Dust & Dirt", color = labelColor)
        Slider(
            value = dustDirt,
            onValueChange = onDustDirtChange,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Tape Type", color = labelColor)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            TapeType.entries.forEach { type ->
                FilterChip(
                    selected = tapeType == type,
                    onClick = { onTapeTypeChange(type) },
                    label = { Text(type.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent),
                )
            }
        }
    }
}
