package com.giraffe.matn.presentation.goals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.DailyProgress
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.presentation.common.DailyGoalRing
import com.giraffe.matn.presentation.common.MatnProgressBar
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.goals_daily_target_label
import matn.shared.generated.resources.goals_decrease_target
import matn.shared.generated.resources.goals_empty_message
import matn.shared.generated.resources.goals_empty_title
import matn.shared.generated.resources.goals_increase_target
import matn.shared.generated.resources.goals_progress_section_title
import matn.shared.generated.resources.nav_goals
import org.jetbrains.compose.resources.stringResource

/**
 * Stateful holder (Principle II) for the Goals tab — hoists [GoalsViewModel]'s state and forwards
 * the goal-edit intent to the stateless [GoalsScreenContent].
 */
@Composable
fun GoalsScreen(viewModel: GoalsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GoalsScreenContent(state = state, onGoalChanged = viewModel::onGoalChanged)
}

/**
 * Stateless Goals dashboard (contracts/goals-ui-contract.md §1) — replaces the `ComingSoonScreen`
 * placeholder (FR-015/SC-007). Ring + goal editor at the top, then a per-matn progress list, or a
 * purposeful zero state when the library itself has no متون.
 */
@Composable
fun GoalsScreenContent(
    state: GoalsUiState,
    onGoalChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            state.isEmpty -> GoalsEmptyState(modifier = Modifier.align(Alignment.Center))

            // T086 (US4, FR-028): bounded to the reading measure and centred on wide windows.
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = MatnSpacing.readingMaxWidth),
                contentPadding = PaddingValues(
                    horizontal = MatnSpacing.marginMobile,
                    vertical = MatnSpacing.gutter,
                ),
            ) {
                item(key = "header") {
                    Text(
                        text = stringResource(Res.string.nav_goals),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = MatnSpacing.gutter),
                    )
                }
                item(key = "ring") {
                    val progress = state.dailyProgress ?: DailyProgress(0, 10)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = MatnSpacing.gutter),
                    ) {
                        DailyGoalRing(
                            fraction = progress.fraction,
                            practiced = progress.practicedToday,
                            goal = progress.goal,
                            isComplete = progress.isComplete,
                        )
                        GoalStepperRow(
                            goal = progress.goal,
                            onGoalChanged = onGoalChanged,
                            modifier = Modifier.padding(top = MatnSpacing.unit * 2),
                        )
                    }
                }
                item(key = "section-title") {
                    Text(
                        text = stringResource(Res.string.goals_progress_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = MatnSpacing.unit),
                    )
                }
                items(items = state.matnProgress, key = { it.matnId }) { progress ->
                    MatnProgressBar(
                        fraction = progress.fraction,
                        label = state.matnTitles[progress.matnId].orEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = MatnSpacing.unit + 4.dp),
                    )
                }
            }
        }
    }
}

/** Whole-number −/goal/+ stepper, floored at 1 (FR-009 positive; contracts/progress-contract.md). */
@Composable
private fun GoalStepperRow(goal: Int, onGoalChanged: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = stringResource(Res.string.goals_daily_target_label),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        val increaseLabel = stringResource(Res.string.goals_increase_target)
        val decreaseLabel = stringResource(Res.string.goals_decrease_target)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onGoalChanged(goal + 1) },
                modifier = Modifier.semantics { contentDescription = increaseLabel },
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            }
            androidx.compose.material3.Surface(color = scheme.secondaryContainer, shape = MatnShapes.full) {
                Text(
                    text = goal.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr),
                    color = scheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = MatnSpacing.unit * 2, vertical = MatnSpacing.unit * 3 / 4),
                )
            }
            IconButton(
                onClick = { onGoalChanged((goal - 1).coerceAtLeast(1)) },
                enabled = goal > 1,
                modifier = Modifier.semantics { contentDescription = decreaseLabel },
            ) {
                Text("−", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            }
        }
    }
}

/** Purposeful zero state (SC-007) — never the "coming soon" placeholder. */
@Composable
private fun GoalsEmptyState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(MatnSpacing.gutter),
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

@Preview
@Composable
private fun GoalsScreenPopulatedPreview() {
    MatnTheme {
        GoalsScreenContent(
            state = GoalsUiState(
                isLoading = false,
                dailyProgress = DailyProgress(practicedToday = 4, goal = 10),
                matnProgress = listOf(
                    MatnProgress("m1", 3, 4),
                    MatnProgress("m2", 1, 5),
                ),
                matnTitles = mapOf("m1" to "الأجرومية", "m2" to "متن الآجرومية مبوب"),
            ),
        )
    }
}

/** T092 (US4): expanded window width — confirms the list stays bounded/centred. */
@Preview(widthDp = 900)
@Composable
private fun GoalsScreenWidePreview() {
    MatnTheme {
        GoalsScreenContent(
            state = GoalsUiState(
                isLoading = false,
                dailyProgress = DailyProgress(practicedToday = 4, goal = 10),
                matnProgress = listOf(
                    MatnProgress("m1", 3, 4),
                    MatnProgress("m2", 1, 5),
                ),
                matnTitles = mapOf("m1" to "الأجرومية", "m2" to "متن الآجرومية مبوب"),
            ),
        )
    }
}

/** T067 (US2, accessibility-contract.md §5/§7): largest reachable font scale, narrowest width. */
@Preview(fontScale = 2.0f, widthDp = 320)
@Composable
private fun GoalsScreenMaxScalePreview() {
    MatnTheme {
        GoalsScreenContent(
            state = GoalsUiState(
                isLoading = false,
                dailyProgress = DailyProgress(practicedToday = 4, goal = 10),
                matnProgress = listOf(
                    MatnProgress("m1", 3, 4),
                    MatnProgress("m2", 1, 5),
                ),
                matnTitles = mapOf("m1" to "الأجرومية", "m2" to "متن الآجرومية مبوب"),
            ),
        )
    }
}

/** T052 (US2): dark-theme coverage for the populated content state. */
@Preview
@Composable
private fun GoalsScreenPopulatedDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) {
        GoalsScreenContent(
            state = GoalsUiState(
                isLoading = false,
                dailyProgress = DailyProgress(practicedToday = 4, goal = 10),
                matnProgress = listOf(
                    MatnProgress("m1", 3, 4),
                    MatnProgress("m2", 1, 5),
                ),
                matnTitles = mapOf("m1" to "الأجرومية", "m2" to "متن الآجرومية مبوب"),
            ),
        )
    }
}

@Preview
@Composable
private fun GoalsScreenPopulatedRtlPreview() {
    MatnTheme {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
            GoalsScreenContent(
                state = GoalsUiState(
                    isLoading = false,
                    dailyProgress = DailyProgress(practicedToday = 4, goal = 10),
                    matnProgress = listOf(
                        MatnProgress("m1", 3, 4),
                        MatnProgress("m2", 1, 5),
                    ),
                    matnTitles = mapOf("m1" to "الأجرومية", "m2" to "متن الآجرومية مبوب"),
                ),
            )
        }
    }
}

@Preview
@Composable
private fun GoalsScreenZeroStatePreview() {
    MatnTheme {
        GoalsScreenContent(state = GoalsUiState(isLoading = false, isEmpty = true))
    }
}

@Preview
@Composable
private fun GoalsScreenLoadingPreview() {
    MatnTheme {
        GoalsScreenContent(state = GoalsUiState(isLoading = true))
    }
}
