package com.giraffe.matn.presentation.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.presentation.theme.MatnTheme
import androidx.compose.ui.tooling.preview.Preview

/**
 * Stateful holder for the player bar (Principle II): collects [PlayerBarViewModel] state and
 * forwards intents to the stateless [PlayerBarContent]. Host at the bottom of the reading screen;
 * visible only while a playback session exists (FR-010).
 */
@Composable
fun PlayerBar(viewModel: PlayerBarViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.visible) {
        PlayerBarContent(
            state = state,
            onPlayPause = viewModel::onPlayPause,
            onStop = viewModel::onStop,
            onNext = viewModel::onNext,
            onPrevious = viewModel::onPrevious,
            onSeek = viewModel::onSeek,
            onSpeedSelected = viewModel::onSpeedSelected,
        )
    }
}

/**
 * Stateless player bar — a pure function of [PlayerBarUiState] plus intent lambdas (Principle II).
 *
 * US1 layout: compact transport (previous / play-pause / next) + stop + speed button; a thin scrub
 * `Slider` bound to `positionMs`/`durationMs` (FR-013); a `LinearProgressIndicator` while
 * `LOADING`. Visible only while `state.visible`. RTL-friendly (`Row` with `Arrangement.Start`).
 */
@Composable
fun PlayerBarContent(
    state: PlayerBarUiState,
    onPlayPause: () -> Unit = {},
    onStop: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onSpeedSelected: () -> Unit = {},
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (state.activeVerseDisplayNumber != null) "آية ${state.activeVerseDisplayNumber}"
                    else if (state.isLoading) "…" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        IconButton(onClick = onPrevious, enabled = state.canPrevious) {
                            glyph("⏮")
                        }
                        IconButton(onClick = onPlayPause, enabled = state.canPlayPause) {
                            if (state.isPlaying) glyph("⏸") else glyph("▶")
                        }
                        IconButton(onClick = onNext, enabled = state.canNext) {
                            glyph("⏭")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onStop) {
                            glyph("⏹")
                        }
                        IconButton(onClick = onSpeedSelected) {
                            Text(
                                text = speedLabel(state.speed),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            val duration = state.durationMs.coerceAtLeast(1L)
            if (state.durationMs > 0 && !state.isLoading) {
                var scrubbing by remember { mutableStateOf(false) }
                var scrubValue by remember { mutableStateOf(0f) }
                val shown = if (scrubbing) scrubValue else state.positionMs.toFloat() / duration
                Slider(
                    value = shown.coerceIn(0f, 1f),
                    onValueChange = {
                        scrubbing = true
                        scrubValue = it
                    },
                    onValueChangeFinished = {
                        scrubbing = false
                        onSeek((scrubValue * duration).toLong())
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun glyph(symbol: String) {
    Text(
        text = symbol,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun speedLabel(speed: com.giraffe.matn.domain.model.PlaybackSpeed): String =
    when (speed) {
        com.giraffe.matn.domain.model.PlaybackSpeed.X0_5 -> "0.5x"
        com.giraffe.matn.domain.model.PlaybackSpeed.X0_75 -> "0.75x"
        com.giraffe.matn.domain.model.PlaybackSpeed.X1 -> "1x"
        com.giraffe.matn.domain.model.PlaybackSpeed.X1_25 -> "1.25x"
        com.giraffe.matn.domain.model.PlaybackSpeed.X1_5 -> "1.5x"
    }

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun PlayerBarPlayingPreview() {
    MatnTheme {
        PlayerBarContent(
            state = PlayerBarUiState(
                visible = true,
                status = com.giraffe.matn.domain.model.PlaybackStatus.PLAYING,
                matnId = "m1",
                activeVerseDisplayNumber = 2,
                positionMs = 2400,
                durationMs = 8000,
                canNext = true,
                canPrevious = true,
            ),
        )
    }
}

@Preview
@Composable
private fun PlayerBarPausedPreview() {
    MatnTheme {
        PlayerBarContent(
            state = PlayerBarUiState(
                visible = true,
                status = com.giraffe.matn.domain.model.PlaybackStatus.PAUSED,
                activeVerseDisplayNumber = 1,
                positionMs = 1200,
                durationMs = 5000,
                canNext = true,
                canPrevious = true,
            ),
        )
    }
}

@Preview
@Composable
private fun PlayerBarLoadingPreview() {
    MatnTheme {
        PlayerBarContent(
            state = PlayerBarUiState(
                visible = true,
                status = com.giraffe.matn.domain.model.PlaybackStatus.LOADING,
                matnId = "m1",
            ),
        )
    }
}

@Preview
@Composable
private fun PlayerBarEndedHiddenPreview() {
    MatnTheme {
        PlayerBarContent(state = PlayerBarUiState(visible = false))
    }
}