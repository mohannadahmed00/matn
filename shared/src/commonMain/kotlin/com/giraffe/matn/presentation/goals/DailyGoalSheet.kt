package com.giraffe.matn.presentation.goals

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.DailyProgress
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.common.DailyGoalRing
import com.giraffe.matn.presentation.common.MatnProgressBar
import com.giraffe.matn.presentation.common.autoIsolated
import com.giraffe.matn.presentation.common.ltrIsolated
import com.giraffe.matn.presentation.theme.LocalReduceMotion
import com.giraffe.matn.presentation.theme.MatnMotion
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.goals_complete_today
import matn.shared.generated.resources.goals_daily_target_label
import matn.shared.generated.resources.goals_decrease_target
import matn.shared.generated.resources.goals_done
import matn.shared.generated.resources.goals_empty_message
import matn.shared.generated.resources.goals_empty_title
import matn.shared.generated.resources.goals_increase_target
import matn.shared.generated.resources.goals_progress_section_title
import matn.shared.generated.resources.goals_remaining_today
import matn.shared.generated.resources.nav_goals
import matn.shared.generated.resources.verses_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The daily-goal surface. Formerly a bottom-nav destination; now a bottom sheet opened by tapping
 * Library's goal ring (Matn Design System §02 — *Navigation consolidation*: "Daily goal · sheet").
 *
 * Two things drove the move. The ring was already rendered on Library, so the Goals tab was a
 * second copy of a control the student was looking at when they reached for it; and the surface has
 * no scroll position, no deep link and no back-stack entry worth keeping, which is exactly the
 * design's rule for what is not a route.
 */
@Composable
fun DailyGoalSheet(viewModel: GoalsViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DailyGoalSheetContent(
        state = state,
        onGoalChanged = viewModel::onGoalChanged,
        onDismiss = onDismiss,
    )
}

/** The preset targets from the design's goal sheet. The stepper below them still reaches any
 *  value — these are the fast path, not the whole range. */
private val GoalPresets = listOf(5, 10, 25, 50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyGoalSheetContent(
    state: GoalsUiState,
    onGoalChanged: (Int) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    // Same content-level fade the other sheets layer over ModalBottomSheet's slide-up; reduced
    // motion skips it (adaptive-motion-contract.md §B3).
    val reduceMotion = LocalReduceMotion.current
    val fade = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(MatnMotion.durationMedium)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surface,
        shape = MatnShapes.sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MatnSpacing.surfaceMaxWidth)
                .align(Alignment.CenterHorizontally)
                .alpha(fade.value)
                .padding(horizontal = MatnSpacing.gutter),
        ) {
            if (state.isEmpty) {
                GoalsEmptyState(modifier = Modifier.fillMaxWidth())
            } else {
                val progress = state.dailyProgress ?: DailyProgress(0, 10)
                GoalHeader(progress)
                GoalPresetRow(
                    goal = progress.goal,
                    onGoalChanged = onGoalChanged,
                    modifier = Modifier.padding(top = MatnSpacing.gutter),
                )
                GoalStepperRow(
                    goal = progress.goal,
                    onGoalChanged = onGoalChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MatnSpacing.cozy),
                )
                if (state.matnProgress.isNotEmpty()) {
                    MatnProgressSection(
                        state = state,
                        modifier = Modifier.padding(top = MatnSpacing.gutter),
                    )
                }
            }
            Button(
                onClick = onDismiss,
                shape = MatnShapes.full,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MatnSpacing.gutter, bottom = MatnSpacing.gutter)
                    .navigationBarsPadding(),
            ) {
                Text(stringResource(Res.string.goals_done), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Ring plus the one sentence that says what is left — the design's "Eight verses to go today". */
@Composable
private fun GoalHeader(progress: DailyProgress, modifier: Modifier = Modifier) {
    val remaining = (progress.goal - progress.practicedToday).coerceAtLeast(0)
    val remainingVerses = pluralStringResource(Res.plurals.verses_count, remaining, remaining)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(top = MatnSpacing.unit),
    ) {
        DailyGoalRing(
            fraction = progress.fraction,
            practiced = progress.practicedToday,
            goal = progress.goal,
            isComplete = progress.isComplete,
        )
        Column(modifier = Modifier.padding(start = MatnSpacing.cozy)) {
            Text(
                text = if (progress.isComplete) {
                    stringResource(Res.string.goals_complete_today)
                } else {
                    autoIsolated(stringResource(Res.string.goals_remaining_today, remainingVerses))
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.nav_goals),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MatnSpacing.hairline),
            )
        }
    }
}

/** The four preset targets, as a row of pills. The active one carries `primary`. */
@Composable
private fun GoalPresetRow(goal: Int, onGoalChanged: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit),
        modifier = modifier.fillMaxWidth(),
    ) {
        GoalPresets.forEach { preset ->
            val isSelected = preset == goal
            val presetVerses = pluralStringResource(Res.plurals.verses_count, preset, preset)
            Surface(
                shape = MatnShapes.lg,
                color = if (isSelected) scheme.primary else scheme.surfaceContainerLow,
                onClick = { onGoalChanged(preset) },
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = autoIsolated(presetVerses)
                        selected = isSelected
                    },
            ) {
                Text(
                    text = ltrIsolated(preset.toString()),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MatnSpacing.snug),
                )
            }
        }
    }
}

/** Whole-number −/goal/+ stepper, floored at 1 (FR-009 positive; contracts/progress-contract.md). */
@Composable
private fun GoalStepperRow(goal: Int, onGoalChanged: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val increaseLabel = stringResource(Res.string.goals_increase_target)
    val decreaseLabel = stringResource(Res.string.goals_decrease_target)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(Res.string.goals_daily_target_label),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onGoalChanged((goal - 1).coerceAtLeast(1)) },
                enabled = goal > 1,
                modifier = Modifier.semantics { contentDescription = decreaseLabel },
            ) {
                Text("−", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            }
            Surface(color = scheme.secondaryContainer, shape = MatnShapes.full) {
                Text(
                    text = goal.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr),
                    color = scheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = MatnSpacing.cozy, vertical = MatnSpacing.unit * 3 / 4),
                )
            }
            IconButton(
                onClick = { onGoalChanged(goal + 1) },
                modifier = Modifier.semantics { contentDescription = increaseLabel },
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            }
        }
    }
}

/**
 * The per-matn progress list the Goals tab used to own (FR-016). Kept here rather than dropped
 * with the route: it is the only place the student sees how far each matn has come, and the sheet
 * is where they are already thinking about progress.
 */
@Composable
private fun MatnProgressSection(state: GoalsUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.goals_progress_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = MatnSpacing.snug),
        )
        // The sheet is bounded, so a long library scrolls inside this section instead of pushing
        // the Done button past the fold.
        Column(
            verticalArrangement = Arrangement.spacedBy(MatnSpacing.snug),
            modifier = Modifier
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            state.matnProgress.forEach { progress ->
                MatnProgressBar(
                    fraction = progress.fraction,
                    label = state.matnTitles[progress.matnId].orEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Purposeful zero state (SC-007) — the library itself has no متون yet. */
@Composable
private fun GoalsEmptyState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = MatnSpacing.gutter),
    ) {
        DailyGoalRing(fraction = 0f, practiced = 0, goal = 10, isComplete = false)
        Text(
            text = stringResource(Res.string.goals_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MatnSpacing.gutter),
        )
        Text(
            text = stringResource(Res.string.goals_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MatnSpacing.unit),
        )
    }
}

// --------------------------------------------------------------------------- Previews

private val previewState = GoalsUiState(
    isLoading = false,
    dailyProgress = DailyProgress(practicedToday = 17, goal = 25),
    matnProgress = listOf(MatnProgress("m1", 3, 4), MatnProgress("m2", 1, 5)),
    matnTitles = mapOf("m1" to "الأجرومية", "m2" to "متن الآجرومية مبوب"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun DailyGoalSheetPreview() {
    MatnTheme { DailyGoalSheetContent(state = previewState) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun DailyGoalSheetDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) { DailyGoalSheetContent(state = previewState) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun DailyGoalSheetCompletePreview() {
    MatnTheme {
        DailyGoalSheetContent(state = previewState.copy(dailyProgress = DailyProgress(25, 25)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun DailyGoalSheetEmptyPreview() {
    MatnTheme { DailyGoalSheetContent(state = GoalsUiState(isLoading = false, isEmpty = true)) }
}

/** Largest reachable font scale, narrowest width (accessibility-contract.md §5/§7). */
@OptIn(ExperimentalMaterial3Api::class)
@Preview(fontScale = 2.0f, widthDp = 320)
@Composable
private fun DailyGoalSheetMaxScalePreview() {
    MatnTheme { DailyGoalSheetContent(state = previewState) }
}
