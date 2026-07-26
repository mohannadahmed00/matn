package com.giraffe.matn.presentation.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.presentation.details.VerseRow
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.repetition_setup_ab_toggle
import matn.shared.generated.resources.repetition_setup_end_verse
import matn.shared.generated.resources.repetition_setup_repeat_decrease
import matn.shared.generated.resources.repetition_setup_repeat_increase
import matn.shared.generated.resources.repetition_setup_segment_repeat
import matn.shared.generated.resources.repetition_setup_start_action
import matn.shared.generated.resources.repetition_setup_start_verse
import matn.shared.generated.resources.repetition_setup_title
import matn.shared.generated.resources.repetition_setup_verse_repeat
import matn.shared.generated.resources.player_next
import matn.shared.generated.resources.player_previous
import org.jetbrains.compose.resources.stringResource

/**
 * Stateful holder (Principle II) for the repetition-setup entry point — owns the sheet's
 * open/closed flag and its **draft** configuration as local Compose state (never a ViewModel):
 * nothing here is real until [RepetitionSetupSheet]'s `onStart` fires, at which point it is
 * translated 1:1 into the existing `PlaybackController` intents already used by
 * `MatnDetailsViewModel`/`PlayerBarViewModel` — this composable never calls a domain type
 * directly (specs/010-design-system-adoption/contracts/repetition-setup-contract.md).
 *
 * Dismissing without starting (spec Edge Cases) simply drops this composable's local state —
 * nothing was ever written to a ViewModel, so there is nothing to roll back.
 */
@Composable
fun RepetitionSetupHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    verses: List<VerseRow>,
    playerBar: PlayerBarViewModel,
    onSetLoopStart: (String) -> Unit,
    onSetLoopEnd: (String) -> Unit,
    onClearLoop: () -> Unit,
    onStartPlayback: () -> Unit,
) {
    if (!visible || verses.isEmpty()) return
    val playerState by playerBar.state.collectAsStateWithLifecycle()
    var draft by remember(visible) {
        mutableStateOf(
            RepetitionSetupUiState(
                abLoopEnabled = false,
                startVerseIndex = 0,
                endVerseIndex = verses.lastIndex,
                verseRepeatCount = playerState.verseRepeatTarget,
                matnRepeatCount = playerState.matnRepeatTarget,
                totalVerseCount = verses.size,
            ),
        )
    }
    RepetitionSetupSheet(
        state = draft,
        verses = verses,
        onAbLoopToggled = { draft = draft.copy(abLoopEnabled = it) },
        onStartVerseChanged = { index -> draft = draft.copy(startVerseIndex = index.coerceIn(0, verses.lastIndex)) },
        onEndVerseChanged = { index -> draft = draft.copy(endVerseIndex = index.coerceIn(0, verses.lastIndex)) },
        onVerseRepeatChanged = { draft = draft.copy(verseRepeatCount = it) },
        onSegmentRepeatChanged = { draft = draft.copy(matnRepeatCount = it) },
        onStart = {
            // Per contract § 2: translate the draft into the same existing intents DrillPanel /
            // the verse-row long-press menu already used — no new domain call is introduced.
            if (draft.abLoopEnabled) {
                onSetLoopStart(verses[draft.startVerseIndex].id)
                onSetLoopEnd(verses[draft.endVerseIndex].id)
            } else {
                onClearLoop()
            }
            playerBar.onVerseRepeatSelected(draft.verseRepeatCount)
            playerBar.onMatnRepeatSelected(draft.matnRepeatCount)
            onStartPlayback()
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

/**
 * Stateless bottom sheet — a pure function of [RepetitionSetupUiState] plus intent lambdas
 * (Principle II). Consolidates what was previously split across `DrillPanel` (verse/segment
 * repeat steppers) and a per-verse long-press menu (A–B range) into one coherent flow with a
 * single explicit start action, per the canonical "Repetition Setup" Stitch screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepetitionSetupSheet(
    state: RepetitionSetupUiState,
    verses: List<VerseRow>,
    onAbLoopToggled: (Boolean) -> Unit = {},
    onStartVerseChanged: (Int) -> Unit = {},
    onEndVerseChanged: (Int) -> Unit = {},
    onVerseRepeatChanged: (RepeatCount) -> Unit = {},
    onSegmentRepeatChanged: (RepeatCount) -> Unit = {},
    onStart: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    // T098 (US5, adaptive-motion-contract.md §B3): a content-level fade layered on top of
    // ModalBottomSheet's own slide-up (not developer-overridable in this Material3 version).
    // Reduce motion skips the extra fade — the sheet still appears via the framework's slide.
    val reduceMotion = com.giraffe.matn.presentation.theme.LocalReduceMotion.current
    val fade = remember {
        androidx.compose.animation.core.Animatable(if (reduceMotion) 1f else 0f)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        fade.animateTo(1f, androidx.compose.animation.core.tween(com.giraffe.matn.presentation.theme.MatnMotion.durationMedium))
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = scheme.surface,
        shape = MatnShapes.xl,
    ) {
        // T088 (US4, FR-029): bounded to MatnSpacing.surfaceMaxWidth and centred on wide windows.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MatnSpacing.surfaceMaxWidth)
                .align(Alignment.CenterHorizontally)
                .alpha(fade.value)
                .padding(horizontal = MatnSpacing.gutter)
                .padding(bottom = MatnSpacing.gutter)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(Res.string.repetition_setup_title),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(bottom = MatnSpacing.gutter),
            )

            SettingRow(label = stringResource(Res.string.repetition_setup_ab_toggle)) {
                Switch(
                    checked = state.abLoopEnabled,
                    onCheckedChange = onAbLoopToggled,
                    colors = SwitchDefaults.colors(checkedTrackColor = scheme.primary),
                )
            }

            if (state.abLoopEnabled) {
                SettingRow(label = stringResource(Res.string.repetition_setup_start_verse)) {
                    IndexStepper(
                        displayNumber = verses[state.startVerseIndex].displayNumber,
                        onDecrement = { onStartVerseChanged(state.startVerseIndex - 1) },
                        onIncrement = { onStartVerseChanged(state.startVerseIndex + 1) },
                        decrementEnabled = state.startVerseIndex > 0,
                        incrementEnabled = state.startVerseIndex < verses.lastIndex,
                    )
                }
                SettingRow(label = stringResource(Res.string.repetition_setup_end_verse)) {
                    IndexStepper(
                        displayNumber = verses[state.endVerseIndex].displayNumber,
                        onDecrement = { onEndVerseChanged(state.endVerseIndex - 1) },
                        onIncrement = { onEndVerseChanged(state.endVerseIndex + 1) },
                        decrementEnabled = state.endVerseIndex > 0,
                        incrementEnabled = state.endVerseIndex < verses.lastIndex,
                    )
                }
            }

            SettingRow(label = stringResource(Res.string.repetition_setup_verse_repeat)) {
                RepeatCountStepper(value = state.verseRepeatCount, onValueChange = onVerseRepeatChanged)
            }
            SettingRow(label = stringResource(Res.string.repetition_setup_segment_repeat)) {
                RepeatCountStepper(value = state.matnRepeatCount, onValueChange = onSegmentRepeatChanged)
            }

            Button(
                onClick = onStart,
                enabled = state.canStart,
                shape = MatnShapes.full,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                modifier = Modifier.fillMaxWidth().padding(top = MatnSpacing.unit * 2),
            ) {
                Text(stringResource(Res.string.repetition_setup_start_action), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A label on the start side, its control on the end side — every row in the sheet follows this. */
@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = MatnSpacing.unit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.weight(1f))
        content()
    }
}

/** A −/displayNumber/+ stepper over a verse index, showing the verse's display number, not the raw index. */
@Composable
private fun IndexStepper(
    displayNumber: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val nextLabel = stringResource(Res.string.player_next)
    val previousLabel = stringResource(Res.string.player_previous)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onIncrement,
            enabled = incrementEnabled,
            modifier = Modifier.semantics { contentDescription = nextLabel },
        ) {
            Text(">", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
        Surface(color = scheme.secondaryContainer, shape = MatnShapes.full) {
            Text(
                text = displayNumber.toString(),
                style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr),
                color = scheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(horizontal = MatnSpacing.unit * 2, vertical = MatnSpacing.unit * 3 / 4)
                    .size(width = 20.dp, height = 20.dp),
            )
        }
        IconButton(
            onClick = onDecrement,
            enabled = decrementEnabled,
            modifier = Modifier.semantics { contentDescription = previousLabel },
        ) {
            Text("<", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
    }
}

/**
 * A −/value/+ stepper over [RepeatCount], with ∞ as an explicit, always-selectable step. Moved
 * here from the retired `DrillPanel.kt` (specs/010 US2) — reused unchanged in behavior, re-skinned
 * with the Phase 10 token set.
 */
@Composable
fun RepeatCountStepper(
    value: RepeatCount,
    onValueChange: (RepeatCount) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val increaseLabel = stringResource(Res.string.repetition_setup_repeat_increase)
    val decreaseLabel = stringResource(Res.string.repetition_setup_repeat_decrease)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                onValueChange(
                    when (value) {
                        RepeatCount.Unlimited -> RepeatCount.of(RepeatCount.MAX)
                        is RepeatCount.Finite -> RepeatCount.of(value.value - 1)
                    },
                )
            },
            modifier = Modifier.semantics { contentDescription = decreaseLabel },
        ) {
            Text("−", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
        Surface(color = scheme.secondaryContainer, shape = MatnShapes.full) {
            Text(
                text = if (value.isUnlimited) "∞" else (value as RepeatCount.Finite).value.toString(),
                style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr),
                color = scheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(horizontal = MatnSpacing.unit * 2, vertical = MatnSpacing.unit * 3 / 4)
                    .size(width = 20.dp, height = 20.dp),
            )
        }
        IconButton(
            onClick = {
                onValueChange(
                    when (value) {
                        RepeatCount.Unlimited -> RepeatCount.Unlimited
                        is RepeatCount.Finite ->
                            if (value.value >= RepeatCount.MAX) RepeatCount.Unlimited
                            else RepeatCount.of(value.value + 1)
                    },
                )
            },
            modifier = Modifier.semantics { contentDescription = increaseLabel },
        ) {
            Text("+", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
    }
}

// --------------------------------------------------------------------------- Previews

private val previewVerses = listOf(
    VerseRow("v1", 1, "verse one", 4000, null),
    VerseRow("v2", 2, "verse two", 4000, null),
    VerseRow("v3", 3, "verse three", 4000, null),
    VerseRow("v4", 4, "verse four", 4000, null),
)

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun RepetitionSetupSheetAbOffPreview() {
    MatnTheme {
        RepetitionSetupSheet(
            state = RepetitionSetupUiState(
                abLoopEnabled = false,
                verseRepeatCount = RepeatCount.of(7),
                totalVerseCount = previewVerses.size,
            ),
            verses = previewVerses,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun RepetitionSetupSheetAbOnValidRangePreview() {
    MatnTheme {
        RepetitionSetupSheet(
            state = RepetitionSetupUiState(
                abLoopEnabled = true,
                startVerseIndex = 1,
                endVerseIndex = 2,
                verseRepeatCount = RepeatCount.of(3),
                matnRepeatCount = RepeatCount.Unlimited,
                totalVerseCount = previewVerses.size,
            ),
            verses = previewVerses,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun RepetitionSetupSheetAbOnInvalidRangePreview() {
    MatnTheme {
        RepetitionSetupSheet(
            state = RepetitionSetupUiState(
                abLoopEnabled = true,
                startVerseIndex = 3,
                endVerseIndex = 0,
                totalVerseCount = previewVerses.size,
            ),
            verses = previewVerses,
        )
    }
}
