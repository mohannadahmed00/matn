package com.giraffe.matn.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.PlaybackMode
import com.giraffe.matn.domain.model.PlaybackSpeed
import com.giraffe.matn.domain.model.PlaybackStatus
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.presentation.common.PauseGlyph
import com.giraffe.matn.presentation.common.PlayGlyph
import com.giraffe.matn.presentation.common.SkipNextGlyph
import com.giraffe.matn.presentation.common.SkipPreviousGlyph
import com.giraffe.matn.presentation.common.StopGlyph
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.loading
import matn.shared.generated.resources.mode_ab_loop
import matn.shared.generated.resources.mode_memorization
import matn.shared.generated.resources.mode_normal
import matn.shared.generated.resources.player_next
import matn.shared.generated.resources.player_now_playing
import matn.shared.generated.resources.player_pause
import matn.shared.generated.resources.player_play
import matn.shared.generated.resources.player_previous
import matn.shared.generated.resources.player_speed
import matn.shared.generated.resources.player_stop
import org.jetbrains.compose.resources.stringResource

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
 * Designed as the page's **colophon**: it sits on parchment (not a Material tinted band), opens with
 * a gold rule broken by a central rosette bead (the reading header's ornament, echoed), and draws
 * transport as manuscript-palette vector glyphs — play/pause a single filled scholar-green bead
 * (the emphasized action, kin to the آية rosette), previous/next/stop quiet ink, speed a small gold
 * cartouche. The scrub is an illumination-gold rule (FR-013). RTL-native (`Row` start→end).
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
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surface,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ColophonRule()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The mode chip reads the derived `PlaybackMode` only — it is never stored or
                // inferred locally, so it can never contradict the configuration (FR-008).
                if (!state.isLoading && state.mode != PlaybackMode.NORMAL) {
                    ModeChip(mode = state.mode)
                }
                Text(
                    text = when {
                        // Concatenate word + number rather than a `%d` format arg: Compose-resources
                        // format substitution is unreliable here (the Phase 1 header shows the same
                        // "%d" glitch), and the label is word-then-number in both locales.
                        state.activeVerseDisplayNumber != null ->
                            "${stringResource(Res.string.player_now_playing)} ${state.activeVerseDisplayNumber}"
                        state.isLoading -> stringResource(Res.string.loading)
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                if (!state.isLoading && state.activeVerseDisplayNumber != null) {
                    Text(
                        text = repetitionLabel(state.repetition, state.verseRepeatTarget),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.secondary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    if (state.matnRepeatTarget != RepeatCount.ONE) {
                        Text(
                            text = passLabel(state.pass, state.matnRepeatTarget),
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.secondary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))

                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = scheme.primary,
                    )
                } else {
                    IconButton(onClick = onPrevious, enabled = state.canPrevious) {
                        SkipPreviousGlyph(
                            color = transportInk(state.canPrevious),
                            contentDescription = stringResource(Res.string.player_previous),
                        )
                    }
                    TransportDisc(
                        onClick = onPlayPause,
                        enabled = state.canPlayPause,
                        contentDescription = stringResource(
                            if (state.isPlaying) Res.string.player_pause else Res.string.player_play,
                        ),
                    ) {
                        if (state.isPlaying) {
                            PauseGlyph(color = scheme.onPrimary, size = 20.dp)
                        } else {
                            PlayGlyph(color = scheme.onPrimary, size = 20.dp)
                        }
                    }
                    IconButton(onClick = onNext, enabled = state.canNext) {
                        SkipNextGlyph(
                            color = transportInk(state.canNext),
                            contentDescription = stringResource(Res.string.player_next),
                        )
                    }
                    IconButton(onClick = onStop) {
                        StopGlyph(
                            color = scheme.onSurfaceVariant,
                            contentDescription = stringResource(Res.string.player_stop),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    SpeedPill(speed = state.speed, onClick = onSpeedSelected)
                }
            }

            val duration = state.durationMs.coerceAtLeast(1L)
            if (state.durationMs > 0 && !state.isLoading) {
                GoldScrub(
                    positionMs = state.positionMs,
                    durationMs = duration,
                    onSeek = onSeek,
                )
            } else if (state.isLoading) {
                LinearProgressIndicator(
                    color = scheme.secondary,
                    trackColor = scheme.outlineVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

/** A hairline rule broken by a small central gold rosette bead — the reading header's ornament. */
@Composable
private fun ColophonRule() {
    val gold = MaterialTheme.colorScheme.secondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(gold.copy(alpha = 0.4f)))
        Box(modifier = Modifier.padding(horizontal = 8.dp).size(5.dp).background(gold, CircleShape))
        Box(modifier = Modifier.weight(1f).height(1.dp).background(gold.copy(alpha = 0.4f)))
    }
}

/** Play/pause as a single filled scholar-green disc — the one emphasized control (kin to the rosette). */
@Composable
private fun TransportDisc(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(46.dp)
            .clip(CircleShape)
            .background(if (enabled) scheme.primary else scheme.primary.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** The derived mode chip (Normal / Memorization / A–B Loop) — FR-008: reads `state.mode` only. */
@Composable
private fun ModeChip(mode: PlaybackMode) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.primaryContainer,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(end = 8.dp),
    ) {
        Text(
            text = stringResource(
                when (mode) {
                    PlaybackMode.NORMAL -> Res.string.mode_normal
                    PlaybackMode.MEMORIZATION -> Res.string.mode_memorization
                    PlaybackMode.A_B_LOOP -> Res.string.mode_ab_loop
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Speed as a small gold cartouche (pill) — GoldSoft field, GoldDeep numerals. */
@Composable
private fun SpeedPill(speed: PlaybackSpeed, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.secondaryContainer,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable(onClickLabel = stringResource(Res.string.player_speed), onClick = onClick),
    ) {
        Text(
            text = speedLabel(speed),
            // Force LTR: "1×" is a number + the bidi-neutral × sign, which RTL would flip to "×1".
            style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr),
            color = scheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** The scrub reimagined as an illumination-gold rule with a gold bead thumb (FR-013). */
@Composable
private fun GoldScrub(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }
    val shown = if (scrubbing) scrubValue else positionMs.toFloat() / durationMs
    Slider(
        value = shown.coerceIn(0f, 1f),
        onValueChange = {
            scrubbing = true
            scrubValue = it
        },
        onValueChangeFinished = {
            scrubbing = false
            onSeek((scrubValue * durationMs).toLong())
        },
        colors = SliderDefaults.colors(
            thumbColor = scheme.secondary,
            activeTrackColor = scheme.secondary,
            inactiveTrackColor = scheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    )
}

/** Enabled transport arrows in ink; disabled ones fade toward the parchment. */
@Composable
private fun transportInk(enabled: Boolean) =
    if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

/** "3 / 7", or "3 / ∞" when the target is unlimited (data-model.md §6.1). */
private fun repetitionLabel(current: Int, target: RepeatCount): String {
    val targetLabel = when (target) {
        is RepeatCount.Finite -> target.value.toString()
        RepeatCount.Unlimited -> "∞"
    }
    return "$current / $targetLabel"
}

/** "pass 2 / 3", or "pass 2 / ∞" when the target is unlimited. */
private fun passLabel(current: Int, target: RepeatCount): String {
    val targetLabel = when (target) {
        is RepeatCount.Finite -> target.value.toString()
        RepeatCount.Unlimited -> "∞"
    }
    return "pass $current / $targetLabel"
}

private fun speedLabel(speed: PlaybackSpeed): String = when (speed) {
    PlaybackSpeed.X0_5 -> "0.5×"
    PlaybackSpeed.X0_75 -> "0.75×"
    PlaybackSpeed.X1 -> "1×"
    PlaybackSpeed.X1_25 -> "1.25×"
    PlaybackSpeed.X1_5 -> "1.5×"
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun PlayerBarPlayingPreview() {
    MatnTheme {
        PlayerBarContent(
            state = PlayerBarUiState(
                visible = true,
                status = PlaybackStatus.PLAYING,
                matnId = "m1",
                activeVerseDisplayNumber = 2,
                positionMs = 2400,
                durationMs = 8000,
                speed = PlaybackSpeed.X1,
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
                status = PlaybackStatus.PAUSED,
                activeVerseDisplayNumber = 1,
                positionMs = 1200,
                durationMs = 5000,
                speed = PlaybackSpeed.X1_25,
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
                status = PlaybackStatus.LOADING,
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

@Preview
@Composable
private fun PlayerBarRepetitionPreview() {
    MatnTheme {
        PlayerBarContent(
            state = PlayerBarUiState(
                visible = true,
                status = PlaybackStatus.PLAYING,
                matnId = "m1",
                activeVerseDisplayNumber = 2,
                positionMs = 2400,
                durationMs = 8000,
                speed = PlaybackSpeed.X1,
                canNext = true,
                canPrevious = true,
                repetition = 2,
                verseRepeatTarget = RepeatCount.of(5),
                mode = PlaybackMode.MEMORIZATION,
            ),
        )
    }
}

@Preview
@Composable
private fun PlayerBarMultiPassPreview() {
    MatnTheme {
        PlayerBarContent(
            state = PlayerBarUiState(
                visible = true,
                status = PlaybackStatus.PLAYING,
                matnId = "m1",
                activeVerseDisplayNumber = 2,
                positionMs = 2400,
                durationMs = 8000,
                speed = PlaybackSpeed.X1,
                canNext = true,
                canPrevious = true,
                repetition = 1,
                verseRepeatTarget = RepeatCount.ONE,
                pass = 2,
                matnRepeatTarget = RepeatCount.of(3),
                mode = PlaybackMode.A_B_LOOP,
            ),
        )
    }
}
