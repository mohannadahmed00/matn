package com.giraffe.matn.teacher.presentation.split

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.audio.AudioProfile
import com.giraffe.matn.domain.audio.SourceRecording
import com.giraffe.matn.domain.audio.SplitPlan
import com.giraffe.matn.domain.audio.SplitPlanValidator
import com.giraffe.matn.domain.audio.SplitProblem
import com.giraffe.matn.domain.audio.VerseRange
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.error.AudioAttachError
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.di.TeacherKoinHolder
import com.giraffe.matn.teacher.platform.AudioPickResult
import com.giraffe.matn.teacher.platform.JvmFileChooser
import com.giraffe.matn.teacher.presentation.common.GLYPH_DOWN
import com.giraffe.matn.teacher.presentation.common.GLYPH_PLAY
import com.giraffe.matn.teacher.presentation.common.GLYPH_STOP
import com.giraffe.matn.teacher.presentation.common.GLYPH_UP
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage
import com.giraffe.matn.teacher.presentation.strings.messageFor

data class SplitIntents(
    val onPickSource: () -> Unit,
    val onReplaceSource: () -> Unit,
    val onScopeChanged: (String, String) -> Unit,
    val onRangeChanged: (String, Long, Long) -> Unit,
    val onBoundarySelected: (String, Boolean) -> Unit,
    val onBoundaryCleared: (String, Boolean) -> Unit,
    val onRangeCleared: (String) -> Unit,
    val onEndCommitted: (String) -> Unit,
    val onScrub: (Long) -> Unit,
    val onScrubEnd: () -> Unit,
    val onSeekStart: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onSeekEnd: () -> Unit,
    val onTogglePlaySource: () -> Unit,
    val onVerseSelected: (String) -> Unit,
    val onAuditionRange: (String) -> Unit,
    val onSplitAndUpload: () -> Unit,
    val onCancel: () -> Unit,
)

/** `contracts/teacher-ui-contract.md` §2. Stateless content; [SplitScreen] below is the thin
 * ViewModel-collecting holder. */
@Composable
fun SplitContent(state: SplitUiState, intents: SplitIntents, modifier: Modifier = Modifier) {
    val strings = LocalTeacherStrings.current
    val focusManager = LocalFocusManager.current
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Clicking any empty part of the screen releases the focused Start/End field, which
                // disarms its boundary and hands the waveform back to the playhead. Without a way
                // out, whichever field was touched last stayed armed for good — and with markers
                // often milliseconds apart, that made the playhead unreachable outside its lane.
                // No ripple: this is a dismissal, not a control.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { focusManager.clearFocus() },
                )
                .padding(MatnSpacing.gutter),
        ) {
            Text(strings.splitFromRecording, style = MaterialTheme.typography.headlineSmall)

            // `weight(1f)` is load-bearing: without it the Ready state's verse list takes the whole
            // remaining height and squeezes the action bar below it to nothing. The list scrolls
            // inside this box; the problem list and actions stay pinned and always reachable, which
            // is what a 100-verse matn needs.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    is SplitUiState.NoSource -> NoSourceContent(intents.onPickSource)
                    is SplitUiState.Loading -> LoadingContent(strings.splitLoadingSource)
                    is SplitUiState.Ready -> ReadyContent(state, intents)
                    is SplitUiState.Splitting -> LoadingContent(strings.splitUploading)
                    is SplitUiState.Failed -> FailedContent(state.error, intents.onPickSource)
                }
            }

            if (state is SplitUiState.Ready) {
                val displayNumberOf: (String) -> Int? = { id -> state.allVerses.find { it.id == id }?.displayNumber }
                ProgressAndProblems(state, displayNumberOf)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = MatnSpacing.unit * 2),
                horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = intents.onCancel) { Text(strings.cancelAction) }
                if (state is SplitUiState.Ready) {
                    OutlinedButton(onClick = intents.onReplaceSource) { Text(strings.replaceSourceRecording) }
                    Button(onClick = intents.onSplitAndUpload, enabled = state.report.blocking.isEmpty()) {
                        Text(strings.splitAndUpload)
                    }
                }
            }
        }
    }
}

/** States the accepted format and limits up front (FR-004) — as a *hint*, not by reusing the
 * rejection messages, which read as a failure that has not happened yet. */
@Composable
private fun NoSourceContent(onPickSource: () -> Unit) {
    val strings = LocalTeacherStrings.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(MatnSpacing.unit * 3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit),
    ) {
        Text(
            text = strings.splitSourceHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onPickSource) { Text(strings.pickRecording) }
    }
}

/**
 * **Indeterminate on purpose.** Neither frame indexing nor peak decoding reports incremental
 * progress yet, so a determinate indicator would sit frozen at 0% for the whole (potentially
 * many-second) load of a long recording — indistinguishable from a hung screen. [label] names what
 * is happening instead. Switch to determinate only once the underlying pass actually emits
 * progress.
 */
@Composable
private fun LoadingContent(label: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(MatnSpacing.unit * 4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit),
    ) {
        CircularProgressIndicator()
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Only reached when there is no loaded source to fall back to — starting over *is* the whole
 * remaining option. An upload failure never lands here; it stays in [ReadyContent] with the plan
 * intact (see [SplitUiState.Ready.uploadError]). */
@Composable
private fun FailedContent(error: AppError, onPickSource: () -> Unit) {
    val strings = LocalTeacherStrings.current
    Column(modifier = Modifier.fillMaxWidth().padding(MatnSpacing.unit * 2)) {
        Text(strings.messageFor(error), color = MaterialTheme.colorScheme.error)
        Button(onClick = onPickSource) { Text(strings.pickRecording) }
    }
}

/** The waveform and scope picker stay pinned; only the per-verse range list scrolls, so the
 * waveform remains a fixed reference while the teacher works down a long matn. */
@Composable
private fun ReadyContent(state: SplitUiState.Ready, intents: SplitIntents) {
    val displayNumberOf: (String) -> Int? = { id -> state.allVerses.find { it.id == id }?.displayNumber }

    Column(modifier = Modifier.fillMaxSize()) {
        // While dragging the pointer wins; once released the marker settles on the committed value
        // and stays there for as long as the field keeps focus.
        val markerMs = state.scrubMs ?: state.armedBoundaryMs
        Box {
            WaveformCanvas(
                peaks = state.peaks,
                ranges = state.ranges,
                durationMs = state.source.durationMs,
                selectedVerseId = state.selectedVerseId,
                markerMs = markerMs,
                playheadMs = state.playheadMs,
            )
            MarkerLayer(
                durationMs = state.source.durationMs,
                markerMs = markerMs,
                playheadMs = state.playheadMs,
                onScrub = intents.onScrub,
                onScrubEnd = intents.onScrubEnd,
                onSeekStart = intents.onSeekStart,
                onSeek = intents.onSeek,
                onSeekEnd = intents.onSeekEnd,
            )
        }

        TransportRow(state, intents, displayNumberOf)

        ScopePicker(state.allVerses, state.scopeVerseIds, intents.onScopeChanged)

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = MatnSpacing.unit * 2)) {
            items(state.scopeVerseIds, key = { it }) { verseId ->
                val verse = state.allVerses.find { it.id == verseId } ?: return@items
                RangeFieldsRow(
                    verse = verse,
                    range = state.ranges.find { it.verseId == verseId },
                    isSelected = state.selectedVerseId == verseId,
                    isAuditioning = state.auditioningVerseId == verseId,
                    onSelected = { intents.onVerseSelected(verseId) },
                    onArmBoundary = { isStart -> intents.onBoundarySelected(verseId, isStart) },
                    onDisarmBoundary = { isStart -> intents.onBoundaryCleared(verseId, isStart) },
                    onAudition = { intents.onAuditionRange(verseId) },
                    onChanged = { start, end -> intents.onRangeChanged(verseId, start, end) },
                    onCleared = { intents.onRangeCleared(verseId) },
                    onEndCommitted = { intents.onEndCommitted(verseId) },
                )
            }
        }
    }
}

/** Transport controls plus the hint that says what a waveform drag will do right now. */
@Composable
private fun TransportRow(state: SplitUiState.Ready, intents: SplitIntents, displayNumberOf: (String) -> Int?) {
    val strings = LocalTeacherStrings.current
    val active = state.activeBoundary

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = MatnSpacing.unit / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit),
    ) {
        TextButton(onClick = intents.onTogglePlaySource) {
            Text(if (state.isPlayingSource) "$GLYPH_STOP ${strings.pauseSource}" else "$GLYPH_PLAY ${strings.playSource}")
        }
        // The playhead's own readout is copyable for the same reason the on-waveform labels are:
        // it is usually the number the teacher wants in a Start or End field.
        CopyableTimestamp(ms = state.playheadMs, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "/ " + formatTimestamp(state.source.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = when {
                active == null -> strings.scrubHintIdle
                else -> strings.scrubHintArmed
                    .replaceFirst("%s", displayNumberOf(active.verseId)?.toString().orEmpty())
                    .replaceFirst("%s", if (active.isStart) strings.rangeStart else strings.rangeEnd)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (active == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ScopePicker(allVerses: List<DraftVerse>, scopeVerseIds: List<String>, onScopeChanged: (String, String) -> Unit) {
    val strings = LocalTeacherStrings.current
    val firstId = scopeVerseIds.firstOrNull() ?: allVerses.firstOrNull()?.id ?: return
    val lastId = scopeVerseIds.lastOrNull() ?: allVerses.lastOrNull()?.id ?: return

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = MatnSpacing.unit),
        horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit * 2),
    ) {
        VersePicker(strings.scopeFirstVerse, allVerses, firstId) { onScopeChanged(it, lastId) }
        VersePicker(strings.scopeLastVerse, allVerses, lastId) { onScopeChanged(firstId, it) }
    }
}

@Composable
private fun VersePicker(label: String, verses: List<DraftVerse>, selectedId: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = verses.find { it.id == selectedId }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = { expanded = true }) { Text(selected?.displayNumber?.toString() ?: "—") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            verses.forEach { verse ->
                DropdownMenuItem(text = { Text(verse.displayNumber.toString()) }, onClick = { onSelected(verse.id); expanded = false })
            }
        }
    }
}

/**
 * One verse's row: its **text** (you are marking boundaries by ear against specific words, so the
 * number alone is not enough to know what you are listening for), its numeric range, and the
 * audition control.
 *
 * Selection is implicit — clicking anywhere on the row highlights that verse's range on the
 * waveform. There is deliberately no separate "select" button: an unlabelled icon whose only job
 * is to highlight is a puzzle, not an affordance.
 */
@Composable
private fun RangeFieldsRow(
    verse: DraftVerse,
    range: VerseRange?,
    isSelected: Boolean,
    isAuditioning: Boolean,
    onSelected: () -> Unit,
    onArmBoundary: (isStart: Boolean) -> Unit,
    onDisarmBoundary: (isStart: Boolean) -> Unit,
    onAudition: () -> Unit,
    onChanged: (Long, Long) -> Unit,
    onCleared: () -> Unit,
    onEndCommitted: () -> Unit,
) {
    val strings = LocalTeacherStrings.current
    // Keyed by verse, not by the range's values: keying on the values meant clearing one field
    // dropped the range, which reset *both* keys and wiped the other field's text along with it.
    var startText by remember(verse.id) { mutableStateOf(range?.startMs?.toString() ?: "") }
    var endText by remember(verse.id) { mutableStateOf(range?.endMs?.toString() ?: "") }

    // Changes that came from somewhere else — a waveform drag, or the previous verse's End
    // chaining into this Start — are pushed into the text. A cleared range deliberately does not
    // write back, so emptying a field leaves it empty instead of the text springing back.
    LaunchedEffect(range?.startMs) {
        range?.startMs?.let { if (it.toString() != startText) startText = it.toString() }
    }
    LaunchedEffect(range?.endMs) {
        range?.endMs?.let { if (it.toString() != endText) endText = it.toString() }
    }

    // A range needs both ends. Half a range is not a smaller range, it is no range — so an empty or
    // unparseable field removes it, taking its marker and highlight off the timeline with it.
    fun push() {
        val start = startText.toLongOrNull()
        val end = endText.toLongOrNull()
        if (start != null && end != null) onChanged(start, end) else onCleared()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                MatnShapes.lg,
            )
            .clickable(onClick = onSelected)
            .padding(MatnSpacing.unit),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit),
    ) {
        Text(verse.displayNumber.toString(), style = MaterialTheme.typography.labelMedium)

        // Always RTL regardless of interface language — it is Arabic scripture (FR-021).
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(
                text = verse.arabicText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(2f),
            )
        }

        // Focusing a field arms it for waveform dragging (FR-014): tap Start, drag the waveform,
        // release. Typing still works — the two paths write the same range. Losing focus disarms,
        // which is what makes clicking elsewhere or pressing Escape hand the waveform back to the
        // playhead.
        BoundaryField(
            value = startText,
            label = strings.rangeStart,
            onValueChange = { value -> startText = value; push() },
            onNudge = { delta ->
                startText = ((startText.toLongOrNull() ?: 0L) + delta).coerceAtLeast(0L).toString()
                push()
            },
            onFocusChange = { focused -> if (focused) onArmBoundary(true) else onDisarmBoundary(true) },
            modifier = Modifier.weight(1f),
        )
        BoundaryField(
            value = endText,
            label = strings.rangeEnd,
            onValueChange = { value -> endText = value; push() },
            onNudge = { delta ->
                endText = ((endText.toLongOrNull() ?: 0L) + delta).coerceAtLeast(0L).toString()
                push()
            },
            // Leaving the End field is what "I have finished this verse" means, and only then does
            // the next verse's Start get filled in.
            onFocusChange = { focused ->
                if (focused) {
                    onArmBoundary(false)
                } else {
                    onDisarmBoundary(false)
                    onEndCommitted()
                }
            },
            onCommit = onEndCommitted,
            modifier = Modifier.weight(1f),
        )
        // Auditions the slice this range would actually produce (FR-014), and stops it on a second
        // press — disabled until the verse has a range at all, since there is nothing to cut yet.
        TextButton(
            onClick = onAudition,
            enabled = range != null,
            modifier = Modifier.semantics {
                contentDescription = if (isAuditioning) strings.stopAudition else strings.auditionRange
            },
        ) {
            Text(if (isAuditioning) "$GLYPH_STOP ${strings.stopAudition}" else GLYPH_PLAY)
        }
    }
}

/**
 * One boundary field, with a stepper tucked into its trailing edge.
 *
 * Dragging places a boundary to within a few pixels, which at a typical zoom is tens of
 * milliseconds — close, but the last stretch is exactly where a boundary matters, and chasing it
 * with the pointer means overshooting in both directions. [NUDGE_STEP_MS] per press is small enough
 * to converge and large enough to be audible.
 *
 * Escape releases the field, which disarms the boundary and gives the waveform back to the
 * playhead. Enter commits without leaving.
 */
@Composable
private fun BoundaryField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    onNudge: (Long) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onCommit: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = { Stepper(onNudge) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit() }),
        modifier = modifier
            .onFocusChanged { onFocusChange(it.isFocused) }
            // Escape is the keyboard's way out, matching "click anywhere else" for the pointer.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    focusManager.clearFocus()
                    true
                } else {
                    false
                }
            },
    )
}

/**
 * The two arrows inside the field's trailing edge — the shape people already know from every other
 * numeric input, rather than buttons flanking the field and stealing its width.
 *
 * Both arrows are **non-focusable on purpose**. A stepper that took focus would release the text
 * field, which now disarms the boundary — so adjusting a boundary by 50 ms would silently stop it
 * being the one the waveform drags.
 */
@Composable
private fun Stepper(onNudge: (Long) -> Unit) {
    Column(
        modifier = Modifier.padding(end = MatnSpacing.unit / 2),
        verticalArrangement = Arrangement.Center,
    ) {
        StepperArrow(GLYPH_UP, NUDGE_STEP_MS, onNudge)
        StepperArrow(GLYPH_DOWN, -NUDGE_STEP_MS, onNudge)
    }
}

@Composable
private fun StepperArrow(glyph: String, delta: Long, onNudge: (Long) -> Unit) {
    val strings = LocalTeacherStrings.current
    Box(
        modifier = Modifier
            .size(width = STEPPER_WIDTH, height = STEPPER_ARROW_HEIGHT)
            .focusProperties { canFocus = false }
            .clickable { onNudge(delta) }
            .semantics {
                contentDescription = (if (delta < 0) strings.nudgeEarlier else strings.nudgeLater)
                    .replace("%d", NUDGE_STEP_MS.toString())
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Pinned above the action bar, so status is visible without scrolling a long verse list — which
 * means it **must** be bounded in height (see `design-notes.md`: an unbounded version took the
 * whole screen).
 *
 * `MissingRange` is deliberately **not** rendered as an error. On a freshly loaded matn every verse
 * is missing a range, so treating "not done yet" as a failure means opening the screen to 109 red
 * lines before the teacher has done anything wrong — it reports work remaining, so it is shown as a
 * progress count. It still *blocks* the split (the validator is unchanged); it just does not shout.
 * Genuine mistakes — overlaps, inverted or too-short ranges, out-of-bounds — are the only things
 * styled as errors, and they are what the teacher can actually act on.
 */
@Composable
private fun ProgressAndProblems(state: SplitUiState.Ready, displayNumberOf: (String) -> Int?) {
    val strings = LocalTeacherStrings.current
    val ranged = state.scopeVerseIds.count { id -> state.ranges.any { it.verseId == id } }
    val realProblems = state.report.blocking.filterNot { it is SplitProblem.MissingRange }
    val warnings = state.report.warnings

    val visibleProblems = realProblems.take(MAX_VISIBLE_PROBLEMS)
    val visibleWarnings = warnings.take(MAX_VISIBLE_PROBLEMS - visibleProblems.size)
    val hidden = (realProblems.size + warnings.size) - visibleProblems.size - visibleWarnings.size

    val hasErrors = realProblems.isNotEmpty() || state.uploadError != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (hasErrors) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                MatnShapes.lg,
            )
            .padding(MatnSpacing.unit * 2),
        verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit / 2),
    ) {
        // First, and in full: this is the one message here that reports something that already
        // happened rather than something still to do, and it sits directly above the button that
        // caused it.
        state.uploadError?.let { error ->
            Text(strings.messageFor(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        }
        Text(
            text = strings.splitRangedCount
                .replaceFirst("%d", ranged.toString())
                .replaceFirst("%d", state.scopeVerseIds.size.toString()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        visibleProblems.forEach { problem ->
            Text(strings.messageFor(problem, displayNumberOf), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
        visibleWarnings.forEach { problem ->
            Text(strings.messageFor(problem, displayNumberOf), style = MaterialTheme.typography.labelSmall)
        }
        if (hidden > 0) {
            Text(
                text = strings.splitProblemsMore.replace("%d", hidden.toString()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MAX_VISIBLE_PROBLEMS = 4

/** One press of a stepper arrow. Comfortably under the validator's 300 ms minimum range, so nudging
 * cannot walk a boundary through a valid range in a single press. */
private const val NUDGE_STEP_MS = 50L

/** Sized so both arrows together clear the field's inner height without stretching it. */
private val STEPPER_WIDTH = MatnSpacing.unit * 3
private val STEPPER_ARROW_HEIGHT = MatnSpacing.unit * 2

/** [draft] is the matn being split; [onSplitComplete] receives the updated draft with the new
 * per-verse audio, and [onCancel] returns to the editor unchanged. */
@Composable
fun SplitScreen(draft: MatnDraft, onSplitComplete: (MatnDraft) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val koin = TeacherKoinHolder.koin
    val focusManager = LocalFocusManager.current
    val viewModel: SplitViewModel = viewModel(key = draft.id) {
        SplitViewModel(
            draft = draft,
            loadSplitSource = koin.get(),
            applySplit = koin.get(),
            audioProbe = koin.get(),
            slicer = koin.get(),
            previewPlayer = koin.get(),
            onSplitComplete = onSplitComplete,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SplitContent(
        state = state,
        intents = SplitIntents(
            onPickSource = {
                // The split source's ceiling is 300 MB, not the 10 MB per-verse one — a continuous
                // recording covering a matn is expected to be far larger than any single verse.
                when (val result = JvmFileChooser.pickAudio(JvmFileChooser.MAX_SPLIT_SOURCE_BYTES)) {
                    is AudioPickResult.Picked -> viewModel.onSourcePicked(result.file.path, result.file.sizeBytes)
                    is AudioPickResult.TooLarge -> viewModel.onSourceRejected(AudioAttachError.SourceTooLarge)
                    is AudioPickResult.Rejected -> viewModel.onSourceRejected(AudioAttachError.WrongFormat)
                    is AudioPickResult.Cancelled -> Unit
                }
            },
            onReplaceSource = viewModel::onReplaceSource,
            onScopeChanged = viewModel::onScopeChanged,
            onRangeChanged = viewModel::onRangeChanged,
            onBoundarySelected = viewModel::onBoundarySelected,
            onBoundaryCleared = viewModel::onBoundaryCleared,
            onRangeCleared = viewModel::onRangeCleared,
            onEndCommitted = viewModel::onEndCommitted,
            onScrub = viewModel::onScrub,
            onScrubEnd = viewModel::onScrubEnd,
            onSeekStart = {
                // Drop the caret out of whichever Start/End field held it, so the field's focus ring
                // cannot keep claiming an armed boundary the ViewModel has just disarmed.
                focusManager.clearFocus()
                viewModel.onSeekStart()
            },
            onSeek = viewModel::onSeek,
            onSeekEnd = viewModel::onSeekEnd,
            onTogglePlaySource = viewModel::onTogglePlaySource,
            onVerseSelected = viewModel::onVerseSelected,
            onAuditionRange = viewModel::onAuditionRange,
            onSplitAndUpload = viewModel::onSplitAndUpload,
            onCancel = onCancel,
        ),
        modifier = modifier,
    )
}

private fun previewDraft() = MatnDraft(
    id = "m1", title = "t", author = "a", description = "d", coverImageRef = null,
    structureKind = com.giraffe.matn.domain.model.StructureKind.SIMPLE, defaultReciterId = "r1",
    chapters = emptyList(),
    verses = listOf(
        DraftVerse("v1", null, 1, "بيت واحد", null, 0L),
        DraftVerse("v2", null, 2, "بيت اثنان", null, 0L),
        DraftVerse("v3", null, 3, "بيت ثلاثة", null, 0L),
    ),
    publicationState = com.giraffe.matn.domain.catalog.PublicationState.DRAFT,
    createdAt = 0L, updatedAt = 0L, remoteRevision = null,
)

private val noOpSplitIntents = SplitIntents(
    onPickSource = {}, onReplaceSource = {}, onScopeChanged = { _, _ -> }, onRangeChanged = { _, _, _ -> },
    onBoundarySelected = { _, _ -> }, onBoundaryCleared = { _, _ -> }, onRangeCleared = {}, onEndCommitted = {},
    onScrub = {}, onScrubEnd = {},
    onSeekStart = {}, onSeek = {}, onSeekEnd = {}, onTogglePlaySource = {},
    onVerseSelected = {}, onAuditionRange = {}, onSplitAndUpload = {}, onCancel = {},
)

private fun previewSource() = SourceRecording(localPath = "/tmp/rec.mp3", sizeBytes = 1_000_000, durationMs = 10_000, profile = AudioProfile(44100, 1), frameCount = 100)

private fun previewReadyState(ranges: List<VerseRange>) = SplitUiState.Ready(
    allVerses = previewDraft().verses,
    source = previewSource(),
    peaks = FloatArray(50) { (it % 10) / 10f },
    scopeVerseIds = previewDraft().verses.map { it.id },
    ranges = ranges,
    report = SplitPlanValidator.validate(SplitPlan(previewSource(), previewDraft().verses.map { it.id }, ranges)),
)

@Preview
@Composable
private fun SplitNoSourceArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    SplitContent(SplitUiState.NoSource, noOpSplitIntents)
}

@Preview
@Composable
private fun SplitNoSourceEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    SplitContent(SplitUiState.NoSource, noOpSplitIntents)
}

@Preview
@Composable
private fun SplitReadyValidArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    SplitContent(previewReadyState(listOf(VerseRange("v1", 0, 2000), VerseRange("v2", 2000, 4000), VerseRange("v3", 4000, 6000))), noOpSplitIntents)
}

@Preview
@Composable
private fun SplitReadyValidEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    SplitContent(previewReadyState(listOf(VerseRange("v1", 0, 2000), VerseRange("v2", 2000, 4000), VerseRange("v3", 4000, 6000))), noOpSplitIntents)
}

@Preview
@Composable
private fun SplitReadyWithProblemsArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    SplitContent(previewReadyState(listOf(VerseRange("v1", 0, 2000), VerseRange("v2", 1000, 4000))), noOpSplitIntents)
}

@Preview
@Composable
private fun SplitReadyWithProblemsEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    SplitContent(previewReadyState(listOf(VerseRange("v1", 0, 2000), VerseRange("v2", 1000, 4000))), noOpSplitIntents)
}

/**
 * The regression case: a just-loaded long matn, where **every** verse is a `MissingRange` problem.
 * An unbounded problem list here consumed the whole screen and pushed the action bar off it, so
 * this preview exists to keep the bounded rendering honest.
 */
@Preview
@Composable
private fun SplitReadyManyProblemsPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    val verses = (1..109).map { i -> DraftVerse("v$i", null, i, "verse $i", null, 0L) }
    val ids = verses.map { it.id }
    SplitContent(
        state = SplitUiState.Ready(
            allVerses = verses,
            source = previewSource(),
            peaks = FloatArray(50) { (it % 10) / 10f },
            scopeVerseIds = ids,
            ranges = emptyList(),
            report = SplitPlanValidator.validate(SplitPlan(previewSource(), ids, emptyList())),
        ),
        intents = noOpSplitIntents,
    )
}

@Preview
@Composable
private fun SplitSplittingArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    SplitContent(SplitUiState.Splitting(0.5f), noOpSplitIntents)
}

@Preview
@Composable
private fun SplitSplittingEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    SplitContent(SplitUiState.Splitting(0.5f), noOpSplitIntents)
}
