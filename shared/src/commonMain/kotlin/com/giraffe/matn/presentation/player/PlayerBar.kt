package com.giraffe.matn.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.PlaybackMode
import com.giraffe.matn.domain.model.PlaybackSpeed
import com.giraffe.matn.domain.model.PlaybackStatus
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.presentation.common.A11yAction
import com.giraffe.matn.presentation.common.IconActionButton
import com.giraffe.matn.presentation.common.PauseGlyph
import com.giraffe.matn.presentation.common.PlayGlyph
import com.giraffe.matn.presentation.common.RepeatGlyph
import com.giraffe.matn.presentation.common.SkipNextGlyph
import com.giraffe.matn.presentation.common.SkipPreviousGlyph
import com.giraffe.matn.presentation.common.StopGlyph
import com.giraffe.matn.presentation.common.formatDuration
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.loading
import matn.shared.generated.resources.mode_ab_loop
import matn.shared.generated.resources.mode_memorization
import matn.shared.generated.resources.mode_normal
import matn.shared.generated.resources.player_next
import matn.shared.generated.resources.player_previous
import matn.shared.generated.resources.player_speed
import matn.shared.generated.resources.player_stop
import matn.shared.generated.resources.repetition_setup_open
import org.jetbrains.compose.resources.stringResource

/**
 * Stateful holder for the player bar (Principle II): collects [PlayerBarViewModel] state and
 * forwards intents to the stateless [PlayerBarContent]. Host below the [ReadingCarousel] on the
 * reading screen; visible only while a playback session exists.
 */
@Composable
fun PlayerBar(viewModel: PlayerBarViewModel, onRepeatSettingsClicked: () -> Unit = {}) {
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
            onRepeatSettingsClicked = onRepeatSettingsClicked,
        )
    }
    // T076 (US3): the first-playback notification rationale — playback has already started
    // regardless of this sheet's outcome (onboarding-permissions-contract.md §4).
    if (state.showNotificationRationale) {
        com.giraffe.matn.presentation.common.PermissionRationaleSheet(
            onContinue = viewModel::onNotificationRationaleContinue,
            onDismiss = viewModel::onNotificationRationaleDismissed,
        )
    }
}

/**
 * Stateless player bar — a pure function of [PlayerBarUiState] plus intent lambdas (Principle II).
 *
 * Reworked (specs/010-design-system-adoption, User Story 1) as the floating glass-panel control
 * bar from the canonical "Reading & Playback (Updated)" Stitch screen: a status row (mode chip +
 * repetition/pass progress — carried over from the pre-redesign bar since the carousel has no
 * replacement surface for it yet), a scrub row with elapsed/remaining time, and a three-zone
 * transport row (repeat-entry-point + stop + speed / prev-play-next / next — RTL-native, `Row`
 * start→end). [onRepeatSettingsClicked] opens the User Story 2 [RepetitionSetupSheet]. The
 * audio-settings icon shown in the Stitch mockup is still not rendered — nothing in this or a
 * later user story gives it a destination yet, so a dead icon remains worse than omitting it.
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
    onRepeatSettingsClicked: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    // T087 (US4, FR-029): bounded to MatnSpacing.surfaceMaxWidth and centred — a tablet's full
    // width would stretch the transport controls uncomfortably far apart.
    Box(
        modifier = Modifier.fillMaxWidth().padding(MatnSpacing.unit * 2),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = scheme.surface,
            shape = MatnShapes.xl,
            shadowElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().widthIn(max = MatnSpacing.surfaceMaxWidth),
        ) {
            Column(modifier = Modifier.padding(MatnSpacing.unit * 2)) {
                StatusRow(state = state)

                val duration = state.durationMs.coerceAtLeast(1L)
                if (state.durationMs > 0 && !state.isLoading) {
                    ScrubRow(positionMs = state.positionMs, durationMs = duration, onSeek = onSeek)
                } else if (state.isLoading) {
                    LinearProgressIndicator(
                        color = scheme.primary,
                        trackColor = scheme.outlineVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = MatnSpacing.unit),
                    )
                }

                TransportRow(
                    state = state,
                    onPlayPause = onPlayPause,
                    onStop = onStop,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSpeedSelected = onSpeedSelected,
                    onRepeatSettingsClicked = onRepeatSettingsClicked,
                )
            }
        }
    }
}

/** Mode chip + repetition/pass progress, or the loading label — carried over from the prior bar. */
@Composable
private fun StatusRow(state: PlayerBarUiState) {
    val scheme = MaterialTheme.colorScheme
    if (state.isLoading) {
        Text(
            text = stringResource(Res.string.loading),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = MatnSpacing.unit),
        )
        return
    }
    if (state.mode == PlaybackMode.NORMAL && state.activeVerseDisplayNumber == null) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = MatnSpacing.unit)) {
        if (state.mode != PlaybackMode.NORMAL) {
            ModeChip(mode = state.mode)
        }
        if (state.activeVerseDisplayNumber != null) {
            Text(
                text = repetitionLabel(state.repetition, state.verseRepeatTarget),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.secondary,
                modifier = Modifier.padding(start = MatnSpacing.unit),
            )
            if (state.matnRepeatTarget != RepeatCount.ONE) {
                Text(
                    text = passLabel(state.pass, state.matnRepeatTarget),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.secondary,
                    modifier = Modifier.padding(start = MatnSpacing.unit),
                )
            }
        }
    }
}

/** Scrub slider with elapsed/remaining time labels either side (per the Stitch control bar). */
@Composable
private fun ScrubRow(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }
    val target = positionMs.toFloat() / durationMs
    // T096 (US5, adaptive-motion-contract.md §B3): the readout eases toward each ~125ms position
    // tick at durationShort rather than jumping, but never while the student is actively dragging
    // — an animated value would fight the touch input. Reduce motion snaps immediately.
    val reduceMotion = com.giraffe.matn.presentation.theme.LocalReduceMotion.current
    val animatedPosition by androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.tween(
            if (reduceMotion) 0 else com.giraffe.matn.presentation.theme.MatnMotion.durationShort,
        ),
    )
    val shown = if (scrubbing) scrubValue else animatedPosition
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
            thumbColor = scheme.primary,
            activeTrackColor = scheme.primary,
            inactiveTrackColor = scheme.surfaceVariant,
        ),
        // T064 (US2): the scrub position ticks on every frame of playback — announcing it would
        // talk over the recitation. Only the discrete transport controls (play/pause, next,
        // previous) carry semantics; this continuous readout carries none.
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
    )
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatDuration(if (scrubbing) (scrubValue * durationMs).toLong() else positionMs),
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
            color = scheme.outline,
        )
        Text(
            text = "-${formatDuration((durationMs - positionMs).coerceAtLeast(0L))}",
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
            color = scheme.outline,
        )
    }
}

/** Three-zone transport row: repeat + stop + speed (start) — prev/play-pause/next (center). */
@Composable
private fun TransportRow(
    state: PlayerBarUiState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSpeedSelected: () -> Unit,
    onRepeatSettingsClicked: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = MatnSpacing.unit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = scheme.primary,
            )
            return@Row
        }
        IconButton(onClick = onRepeatSettingsClicked) {
            RepeatGlyph(color = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.repetition_setup_open))
        }
        IconButton(onClick = onStop) {
            StopGlyph(color = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.player_stop))
        }
        SpeedPill(speed = state.speed, onClick = onSpeedSelected)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onNext, enabled = state.canNext) {
            SkipNextGlyph(
                color = transportInk(state.canNext),
                contentDescription = stringResource(Res.string.player_next),
            )
        }
        TransportDisc(
            onClick = onPlayPause,
            enabled = state.canPlayPause,
            action = if (state.isPlaying) A11yAction.PAUSE else A11yAction.PLAY,
        ) {
            if (state.isPlaying) {
                PauseGlyph(color = scheme.onPrimary, size = 24.dp)
            } else {
                PlayGlyph(color = scheme.onPrimary, size = 24.dp)
            }
        }
        IconButton(onClick = onPrevious, enabled = state.canPrevious) {
            SkipPreviousGlyph(
                color = transportInk(state.canPrevious),
                contentDescription = stringResource(Res.string.player_previous),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * Play/pause as the single filled, larger primary disc — the one emphasized transport control.
 * T058 (US2): the icon-only affordance itself is [IconActionButton], nested inside the styled
 * 56dp disc — [action] is passed through by the caller since play/pause share this composable.
 */
@Composable
private fun TransportDisc(
    onClick: () -> Unit,
    enabled: Boolean,
    action: A11yAction,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(horizontal = MatnSpacing.unit)
            .size(56.dp)
            .clip(CircleShape)
            .background(if (enabled) scheme.primary else scheme.primary.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        IconActionButton(action = action, onClick = onClick, enabled = enabled) { content() }
    }
}

/** The derived mode chip (Normal / Memorization / A–B Loop) — reads `state.mode` only. */
@Composable
private fun ModeChip(mode: PlaybackMode) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.primaryContainer, shape = MatnShapes.full) {
        Text(
            text = stringResource(
                when (mode) {
                    PlaybackMode.NORMAL -> Res.string.mode_normal
                    PlaybackMode.MEMORIZATION -> Res.string.mode_memorization
                    PlaybackMode.A_B_LOOP -> Res.string.mode_ab_loop
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = MatnSpacing.unit, vertical = MatnSpacing.unit / 2),
        )
    }
}

/** Speed as a bordered pill, per the Stitch control bar's speed control. */
@Composable
private fun SpeedPill(speed: PlaybackSpeed, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerHigh,
        shape = MatnShapes.full,
        // T060 (US2, accessibility-contract.md §1.2a): this border is the only affordance
        // identifying SpeedPill as a clickable control, so it must meet 3:1 — `outline`, not the
        // decorative `outlineVariant` every other border in this codebase deliberately uses.
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outline),
        modifier = Modifier
            .padding(start = MatnSpacing.unit)
            .clickable(onClickLabel = stringResource(Res.string.player_speed), onClick = onClick),
    ) {
        Text(
            text = speedLabel(speed),
            // Force LTR: "1×" is a number + the bidi-neutral × sign, which RTL would flip to "×1".
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MatnSpacing.unit, vertical = MatnSpacing.unit * 3 / 4),
        )
    }
}

/** Enabled transport arrows in on-surface ink; disabled ones fade toward the surface. */
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

/** T092 (US4): expanded window width — confirms the control bar stays bounded/centred, not
 *  stretched to a tablet's full width (FR-029). */
@Preview(widthDp = 900)
@Composable
private fun PlayerBarWidePreview() {
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

/** T067 (US2, accessibility-contract.md §5/§7): largest reachable font scale, narrowest width. */
@Preview(fontScale = 2.0f, widthDp = 320)
@Composable
private fun PlayerBarMaxScalePreview() {
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

/** T052 (US2): dark-theme coverage for an active playback state. */
@Preview
@Composable
private fun PlayerBarRepetitionDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) {
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
