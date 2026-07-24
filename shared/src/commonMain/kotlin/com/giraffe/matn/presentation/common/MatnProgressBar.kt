package com.giraffe.matn.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import kotlin.math.roundToInt

/**
 * Shared determinate progress bar (Phase 7, data-model.md §2.1) — used by BOTH the matn details
 * header and the Goals dashboard per-matn rows (Constitution VIII, extract at second use).
 * Stateless and parameterized: driven entirely by [fraction] and the optional [label].
 */
@Composable
fun MatnProgressBar(fraction: Float, modifier: Modifier = Modifier, label: String? = null) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(fraction.coerceIn(0f, 1f) * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (label != null) MatnSpacing.unit / 2 else 0.dp)
                .clip(MatnShapes.full),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun MatnProgressBarZeroPreview() {
    MatnTheme { MatnProgressBar(fraction = 0f, label = "الأجرومية") }
}

@Preview
@Composable
private fun MatnProgressBarPartialPreview() {
    MatnTheme { MatnProgressBar(fraction = 0.45f, label = "الأجرومية") }
}

@Preview
@Composable
private fun MatnProgressBarFullPreview() {
    MatnTheme { MatnProgressBar(fraction = 1f, label = "الأجرومية") }
}
