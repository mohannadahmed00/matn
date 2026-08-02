package com.giraffe.matn.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.catalog.CatalogSyncState
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.presentation.common.ContinueLearningCard
import com.giraffe.matn.presentation.common.DailyGoalRing
import com.giraffe.matn.presentation.common.MatnCard
import com.giraffe.matn.presentation.theme.LocalWindowWidthClass
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.app_title
import matn.shared.generated.resources.catalog_connect_to_browse
import matn.shared.generated.resources.catalog_empty
import matn.shared.generated.resources.catalog_nothing_downloaded
import matn.shared.generated.resources.catalog_refresh
import matn.shared.generated.resources.catalog_sync_failed
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
        onRefresh = viewModel::onRefreshClicked,
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
    onRefresh: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(onOpenSearch = onOpenSearch, onRefresh = onRefresh, isSyncing = state.isSyncing)
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                // Phase 13 (FR-044): three distinct empty states where there used to be one.
                // A student who has never reached the catalog, a teacher who has published
                // nothing, and a library with nothing downloaded are different situations calling
                // for different actions — collapsing them into "library is empty" is exactly the
                // confusion the requirement forbids.

                // Never synced — the honest first-launch state now that nothing ships in the
                // binary. Offers a retry; never a bare spinner or a blank grid (SC-007).
                state.showConnectPrompt -> CatalogMessage(
                    message = stringResource(Res.string.catalog_connect_to_browse),
                    actionLabel = stringResource(Res.string.catalog_refresh),
                    onAction = onRefresh,
                    isBusy = state.isSyncing,
                    modifier = Modifier.align(Alignment.Center),
                )

                // Reached the catalog; the teacher has published nothing. Not a failure, so no
                // retry is offered — retrying cannot conjure content.
                state.showEmptyCatalog -> CatalogMessage(
                    message = stringResource(Res.string.catalog_empty),
                    actionLabel = null,
                    onAction = onRefresh,
                    isBusy = state.isSyncing,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> {
                val widthClass = LocalWindowWidthClass.current
                LazyVerticalGrid(
                    // T084 (US4, FR-027, SC-011): column count and margin follow available width,
                    // not device type — a narrow split-screen pane gets the COMPACT layout.
                    columns = GridCells.Fixed(MatnSpacing.libraryColumns(widthClass)),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(MatnSpacing.horizontalMargin(widthClass)),
                    horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit + 4.dp),
                    verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit + 4.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DailyGoalSection(state.dailyGoal, modifier = Modifier.padding(bottom = MatnSpacing.unit))
                    }
                    // FR-007: a failed refresh is a non-blocking notice ABOVE a still-usable
                    // library, never an emptied one.
                    if (state.showSyncFailedNotice) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CatalogNotice(
                                message = stringResource(Res.string.catalog_sync_failed),
                                actionLabel = stringResource(Res.string.catalog_refresh),
                                onAction = onRefresh,
                                isBusy = state.isSyncing,
                                modifier = Modifier.padding(bottom = MatnSpacing.unit),
                            )
                        }
                    }
                    // FR-044's third case: the catalog has متون but none are on the device. Said
                    // once, above the grid, rather than repeated on every card.
                    if (state.showNothingDownloaded) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(Res.string.catalog_nothing_downloaded),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = MatnSpacing.unit),
                            )
                        }
                    }
                    state.continueLearning?.let { entry ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            // FR-015: nothing at all — no placeholder, no reserved space — when null.
                            ContinueLearningCard(
                                entry = entry,
                                onResume = onResume,
                                onDismiss = onDismiss,
                                isContentInstalled = state.isContinueLearningContentInstalled,
                                // Reinstalling happens from the matn's own details screen, where
                                // the real install action lives (FR-022) — a resume that would
                                // fail the playback gate is never offered here (SC-007).
                                onReinstall = { onOpenMatn(entry.matnId) },
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
                            availability = state.availability[summary.matn.id],
                            declaredSizeBytes = summary.declaredSizeBytes,
                            coverBytes = state.covers[summary.matn.id],
                        )
                    }
                }
                }
            }
        }
    }
}

/** The app-identity top bar — wordmark plus the US1 search entry point; see [HomeContent]'s
 *  KDoc for why the menu icon is still omitted. */
/**
 * A centered catalog state — the full-screen form, used when there is no grid to show.
 *
 * Stateless and parameterized (Principle II). `actionLabel = null` renders no button, which is how
 * "the teacher has published nothing" differs from "we could not reach the catalog": one is a
 * situation to wait out, the other is a situation to retry, and offering a retry for the first
 * would be a dead affordance.
 *
 * **Stitch note (Constitution VIII)**: the design source has no screen for these three states —
 * they did not exist before Phase 13, when a network-less first launch became possible. This reuses
 * the existing centered empty-state pattern (previously `library_empty`) with token-routed
 * type/spacing rather than inventing a new layout.
 */
@Composable
private fun CatalogMessage(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(MatnSpacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(modifier = Modifier.height(MatnSpacing.unit))
            TextButton(onClick = onAction, enabled = !isBusy) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * The inline form of the same idea: a full-width notice that sits above a library which is still
 * perfectly usable (FR-007). Distinct from [CatalogMessage] because it must not centre or take the
 * screen — an unreachable source is not a reason to hide content the student already has.
 */
@Composable
private fun CatalogNotice(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onAction, enabled = !isBusy) {
            Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun HomeTopBar(onOpenSearch: () -> Unit = {}, onRefresh: () -> Unit = {}, isSyncing: Boolean = false) {
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
            // FR-006's explicit refresh, reachable from a populated library too — the empty-state
            // and sync-failure entry points below are unreachable once the grid has content, which
            // left the 1-hour staleness window as the only path to a newly published matn.
            androidx.compose.material3.IconButton(
                onClick = onRefresh,
                enabled = !isSyncing,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                com.giraffe.matn.presentation.common.RefreshGlyph(
                    color = if (isSyncing) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    contentDescription = stringResource(Res.string.catalog_refresh),
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

/** T092 (US4): expanded window width — confirms the grid gains columns, not a stretched single
 *  one. `widthDp` alone doesn't set [LocalWindowWidthClass] (only `App.kt`'s `BoxWithConstraints`
 *  does that at runtime), so it is provided explicitly here to exercise the same code path. */
@Preview(widthDp = 900)
@Composable
private fun HomeContentWidePreview() {
    MatnTheme {
        androidx.compose.runtime.CompositionLocalProvider(
            com.giraffe.matn.presentation.theme.LocalWindowWidthClass provides
                com.giraffe.matn.presentation.theme.WindowWidthClass.EXPANDED,
        ) {
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
}

/** T067 (US2, accessibility-contract.md §5/§7): largest reachable font scale, narrowest width. */
@Preview(fontScale = 2.0f, widthDp = 320)
@Composable
private fun HomeContentMaxScalePreview() {
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

/** T052 (US2): dark-theme coverage for the populated content state. */
@Preview
@Composable
private fun HomeContentPopulatedDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) {
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

// Phase 13 (T053): one preview per FR-044 case. The old single `isEmpty` preview could not show
// the difference between them, which is precisely the defect the requirement is about.

/** Never synced — a first launch with no network. Offers a retry (SC-007). */
@Preview
@Composable
private fun HomeContentConnectPromptPreview() {
    MatnTheme {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                syncState = CatalogSyncState(lastSuccessAtMillis = null, lastAttemptFailed = true),
            ),
            onOpenMatn = {},
        )
    }
}

/** Reached the catalog; the teacher has published nothing. No retry — retrying cannot help. */
@Preview
@Composable
private fun HomeContentEmptyCatalogPreview() {
    MatnTheme {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                syncState = CatalogSyncState(lastSuccessAtMillis = 1_000L, lastAttemptFailed = false),
            ),
            onOpenMatn = {},
        )
    }
}

/** A full catalog with nothing downloaded yet — the normal state after a first successful sync. */
@Preview
@Composable
private fun HomeContentNothingDownloadedPreview() {
    MatnTheme {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                items = listOf(
                    previewSummary("m1", "الأجرومية", 4, 0),
                    previewSummary("m2", "متن الآجرومية مبوب", 5, 0),
                ),
                availability = mapOf(
                    "m1" to ContentAvailability.NotDownloaded(),
                    "m2" to ContentAvailability.NotDownloaded(),
                ),
                syncState = CatalogSyncState(lastSuccessAtMillis = 1_000L, lastAttemptFailed = false),
            ),
            onOpenMatn = {},
        )
    }
}

/** A usable library with a failed refresh over it — never an emptied one (FR-007). */
@Preview
@Composable
private fun HomeContentSyncFailedPreview() {
    MatnTheme {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                items = listOf(previewSummary("m1", "الأجرومية", 4, 31_300)),
                availability = mapOf("m1" to ContentAvailability.Downloaded(2_400_000)),
                syncState = CatalogSyncState(lastSuccessAtMillis = 1_000L, lastAttemptFailed = true),
            ),
            onOpenMatn = {},
        )
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
