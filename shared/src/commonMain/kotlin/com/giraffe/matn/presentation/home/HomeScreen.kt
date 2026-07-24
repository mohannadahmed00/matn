package com.giraffe.matn.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.presentation.common.ContinueLearningCard
import com.giraffe.matn.presentation.common.DailyGoalRing
import com.giraffe.matn.presentation.common.MatnCard
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.app_title
import matn.shared.generated.resources.home_daily_goal_label
import matn.shared.generated.resources.library_empty
import matn.shared.generated.resources.search_open
import org.jetbrains.compose.resources.stringResource

/**
 * Home / library screen (US2) — **stateful** entry. Hoists the [HomeViewModel]'s state and
 * delegates rendering to the stateless [HomeContent], so the render layer stays a pure function
 * of [HomeUiState] (Principle II) and is previewable/screenshot-testable without a ViewModel.
 *
 * Phase 4: also collects the ViewModel's one-shot [HomeViewModel.navigation] events and forwards
 * them to [onOpenMatn], keeping navigation out of the ViewModel (Principle II).
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenMatn: (String) -> Unit, onOpenSearch: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { matnId -> onOpenMatn(matnId) }
    }
    HomeContent(
        state = state,
        onOpenMatn = onOpenMatn,
        onResume = viewModel::onResumeClicked,
        onDismiss = viewModel::onDismissClicked,
        onOpenSearch = onOpenSearch,
    )
}

/**
 * Stateless library grid, per the canonical Home/Library Stitch screen
 * (specs/010-design-system-adoption User Story 4): a top bar, the daily-goal progress ring, the
 * Continue Learning card (unchanged from Phase 4), and a 2-column [LazyVerticalGrid] of
 * [MatnCard]s **keyed by stable `matn.id`** (FR-011/SC-003). When the store is empty a centered
 * localized empty state is shown instead of the grid (FR-004/SC-008). RTL throughout (provided by
 * [MatnTheme]). No network (FR-018/SC-006).
 *
 * **Deviation from the literal Stitch mockup**: the top bar's menu icon is not rendered — it has
 * no destination yet (no drawer), and per the same judgment call as `PlayerBar`'s omitted
 * audio-settings icon, a dead affordance is worse than omitting it. The search icon (Phase 6,
 * US1) IS wired now — it opens [com.giraffe.matn.presentation.search.SearchScreen].
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenMatn: (String) -> Unit,
    onResume: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(onOpenSearch = onOpenSearch)
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                state.isEmpty -> Text(
                    text = stringResource(Res.string.library_empty),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(MatnSpacing.gutter),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(MatnSpacing.marginMobile),
                    horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit + 4.dp),
                    verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit + 4.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DailyGoalSection(state.dailyGoal, modifier = Modifier.padding(bottom = MatnSpacing.unit))
                    }
                    state.continueLearning?.let { entry ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            // FR-015: nothing at all — no placeholder, no reserved space — when null.
                            ContinueLearningCard(
                                entry = entry,
                                onResume = onResume,
                                onDismiss = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = MatnSpacing.unit),
                            )
                        }
                    }
                    items(items = state.items, key = { it.matn.id }) { summary ->
                        MatnCard(
                            summary = summary,
                            onClick = { onOpenMatn(summary.matn.id) },
                            progressFraction = state.progressByMatn[summary.matn.id],
                        )
                    }
                }
            }
        }
    }
}

/** The app-identity top bar — wordmark plus the US1 search entry point; see [HomeContent]'s
 *  KDoc for why the menu icon is still omitted. */
@Composable
private fun HomeTopBar(onOpenSearch: () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = MatnSpacing.marginMobile),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.app_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            androidx.compose.material3.IconButton(
                onClick = onOpenSearch,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                com.giraffe.matn.presentation.common.SearchGlyph(
                    color = MaterialTheme.colorScheme.onSurface,
                    contentDescription = stringResource(Res.string.search_open),
                )
            }
        }
    }
}

/**
 * Home's daily-goal section (FR-009/FR-012): the shared [DailyGoalRing] plus its label, wired to
 * real practiced/goal tracking (specs/007).
 */
@Composable
private fun DailyGoalSection(state: DailyGoalUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MatnSpacing.unit)
            .padding(top = MatnSpacing.unit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DailyGoalRing(
            fraction = state.fraction,
            practiced = state.practiced,
            goal = state.goal,
            isComplete = state.isComplete,
            diameter = MatnSpacing.unit * 8,
        )
        Text(
            text = stringResource(Res.string.home_daily_goal_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = MatnSpacing.unit * 2),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — every view/component in this file has one (light Material 3, RTL via MatnTheme).
// ---------------------------------------------------------------------------------------------

private fun previewSummary(id: String, title: String, count: Int, total: Long) = MatnSummary(
    matn = Matn(
        id = id,
        title = title,
        author = "ابن آجُرُّوم",
        description = "",
        coverImageRef = null,
        structureKind = StructureKind.SIMPLE,
    ),
    verseCount = count,
    totalDurationMs = total,
)

@Preview
@Composable
private fun HomeContentPopulatedPreview() {
    MatnTheme {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                items = listOf(
                    previewSummary("m1", "الأجرومية", 4, 31_300),
                    previewSummary("m2", "متن الآجرومية مبوب", 5, 39_900),
                ),
            ),
            onOpenMatn = {},
        )
    }
}

@Preview
@Composable
private fun HomeContentEmptyPreview() {
    MatnTheme {
        HomeContent(state = HomeUiState(isLoading = false, isEmpty = true), onOpenMatn = {})
    }
}

@Preview
@Composable
private fun HomeContentLoadingPreview() {
    MatnTheme {
        HomeContent(state = HomeUiState(isLoading = true), onOpenMatn = {})
    }
}

@Preview
@Composable
private fun HomeContentPartialRingPreview() {
    MatnTheme {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                items = listOf(previewSummary("m1", "الأجرومية", 4, 31_300)),
                dailyGoal = DailyGoalUiState(practiced = 4, goal = 10, fraction = 0.4f, isComplete = false),
            ),
            onOpenMatn = {},
        )
    }
}

@Preview
@Composable
private fun HomeContentCompleteRingPreview() {
    MatnTheme {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                items = listOf(previewSummary("m1", "الأجرومية", 4, 31_300)),
                dailyGoal = DailyGoalUiState(practiced = 10, goal = 10, fraction = 1f, isComplete = true),
            ),
            onOpenMatn = {},
        )
    }
}
