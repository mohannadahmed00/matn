package com.giraffe.matn.presentation.home

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.catalog.CatalogSyncState
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.model.SearchResult
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.common.ContinueLearningCard
import com.giraffe.matn.presentation.common.DailyGoalRing
import com.giraffe.matn.presentation.common.MatnCard
import com.giraffe.matn.presentation.common.RefreshGlyph
import com.giraffe.matn.presentation.common.SearchGlyph
import com.giraffe.matn.presentation.search.SearchPhase
import com.giraffe.matn.presentation.search.SearchResultRow
import com.giraffe.matn.presentation.search.SearchUiState
import com.giraffe.matn.presentation.search.SearchViewModel
import com.giraffe.matn.presentation.theme.LocalWindowWidthClass
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.catalog_connect_to_browse
import matn.shared.generated.resources.catalog_empty
import matn.shared.generated.resources.catalog_nothing_downloaded
import matn.shared.generated.resources.catalog_refresh
import matn.shared.generated.resources.catalog_sync_failed
import matn.shared.generated.resources.goals_open
import matn.shared.generated.resources.nav_library
import matn.shared.generated.resources.search_clear
import matn.shared.generated.resources.search_field_placeholder
import matn.shared.generated.resources.search_no_results
import matn.shared.generated.resources.search_open
import org.jetbrains.compose.resources.stringResource

/**
 * Library — tab 1 and the app's start destination (Matn Design System §05: "Catalog + Continue
 * Learning + daily-goal ring + in-place search field"). **Stateful** entry: hoists both the
 * [HomeViewModel]'s state and the [SearchViewModel]'s, and delegates rendering to the stateless
 * [HomeContent] (Principle II).
 *
 * Search is not a route. It is a field on this screen that swaps the grid for results while the
 * query is non-blank — it has no back-stack entry, no scroll position of its own, and no deep link,
 * which is the design's test for what should not be a destination.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    onOpenMatn: (String) -> Unit,
    onOpenVerse: (matnId: String, verseId: String?) -> Unit,
    onOpenDailyGoal: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { matnId -> onOpenMatn(matnId) }
    }
    HomeContent(
        state = state,
        searchState = searchState,
        onOpenMatn = onOpenMatn,
        onResume = viewModel::onResumeClicked,
        onDismiss = viewModel::onDismissClicked,
        onRefresh = viewModel::onRefreshClicked,
        onOpenDailyGoal = onOpenDailyGoal,
        onQueryChange = searchViewModel::onQueryChange,
        onClearQuery = searchViewModel::onClearQuery,
        onResultClick = { result ->
            // search-contract.md § 6: every result kind resolves through the matn route's optional
            // focusVerseId; a chapter with no verses falls back to the plain matn route.
            when (result) {
                is SearchResult.VerseMatch -> onOpenVerse(result.ref.matnId, result.ref.verseId)
                is SearchResult.ChapterMatch -> onOpenVerse(result.matnId, result.firstVerseId)
                is SearchResult.MatnMatch -> onOpenMatn(result.matnId)
            }
        },
    )
}

/**
 * Stateless library: a header carrying the screen name and the daily-goal ring, an inline search
 * field, and then either the search results or the catalog grid — the Continue Learning card and a
 * 2-column [LazyVerticalGrid] of [MatnCard]s **keyed by stable `matn.id`** (FR-011/SC-003).
 *
 * The ring is a tap target rather than a read-only indicator: it is the entry point to the daily
 * goal sheet that replaced the Goals tab.
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    searchState: SearchUiState = SearchUiState(),
    onOpenMatn: (String) -> Unit,
    onResume: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpenDailyGoal: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onClearQuery: () -> Unit = {},
    onResultClick: (SearchResult) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LibraryHeader(
            dailyGoal = state.dailyGoal,
            isSyncing = state.isSyncing,
            onRefresh = onRefresh,
            onOpenDailyGoal = onOpenDailyGoal,
        )
        InlineSearchField(
            query = searchState.query,
            onQueryChange = onQueryChange,
            onClearQuery = onClearQuery,
            modifier = Modifier.padding(
                horizontal = MatnSpacing.marginMobile,
                vertical = MatnSpacing.snug,
            ),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                // A live query owns the body — the grid is not shown behind or below it.
                searchState.query.isNotBlank() -> SearchResults(
                    phase = searchState.phase,
                    onResultClick = onResultClick,
                )

                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

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

                else -> CatalogGrid(
                    state = state,
                    onOpenMatn = onOpenMatn,
                    onResume = onResume,
                    onDismiss = onDismiss,
                    onRefresh = onRefresh,
                )
            }
        }
    }
}

/** Screen name plus the daily-goal ring, which doubles as the sheet's entry point. */
@Composable
private fun LibraryHeader(
    dailyGoal: DailyGoalUiState,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    onOpenDailyGoal: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val goalLabel = stringResource(Res.string.goals_open)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MatnSpacing.marginMobile)
            .padding(top = MatnSpacing.snug),
    ) {
        Text(
            text = stringResource(Res.string.nav_library),
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // FR-006's explicit refresh, reachable from a populated library too — the empty-state and
        // sync-failure entry points below are unreachable once the grid has content, which would
        // leave the 1-hour staleness window as the only path to a newly published matn.
        IconButton(onClick = onRefresh, enabled = !isSyncing) {
            RefreshGlyph(
                color = if (isSyncing) scheme.onSurfaceVariant else scheme.onSurface,
                contentDescription = stringResource(Res.string.catalog_refresh),
            )
        }
        DailyGoalRing(
            fraction = dailyGoal.fraction,
            practiced = dailyGoal.practiced,
            goal = dailyGoal.goal,
            isComplete = dailyGoal.isComplete,
            diameter = MatnSpacing.unit * 6,
            modifier = Modifier
                .clickable(onClickLabel = goalLabel, onClick = onOpenDailyGoal)
                .semantics { contentDescription = goalLabel },
        )
    }
}

/**
 * The in-place search field (design system: "Search · inline on Library"). Deliberately *not*
 * autofocused: this is the library's own header, and stealing focus on every visit would raise the
 * keyboard over the grid the student came to browse. The old pushed search screen autofocused
 * because arriving there was already an explicit act.
 */
@Composable
private fun InlineSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = stringResource(Res.string.search_field_placeholder),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        singleLine = true,
        shape = MatnShapes.full,
        leadingIcon = {
            SearchGlyph(
                color = scheme.onSurfaceVariant,
                contentDescription = stringResource(Res.string.search_open),
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                val clearLabel = stringResource(Res.string.search_clear)
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.semantics { contentDescription = clearLabel },
                ) {
                    Text("×", style = MaterialTheme.typography.titleLarge, color = scheme.onSurfaceVariant)
                }
            }
        } else null,
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = scheme.surfaceContainerLow,
            focusedContainerColor = scheme.surfaceContainerLow,
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** The body while a query is live. `Searching` renders nothing rather than a spinner: the debounce
 *  is 250ms, and a spinner that brief reads as a flicker. */
@Composable
private fun SearchResults(phase: SearchPhase, onResultClick: (SearchResult) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    when (phase) {
        is SearchPhase.Idle, is SearchPhase.Searching -> Unit
        is SearchPhase.NoResults -> Text(
            text = stringResource(Res.string.search_no_results),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(MatnSpacing.gutter),
        )

        // Bounded to the reading measure and centred on wide windows (FR-028).
        is SearchPhase.Results -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = MatnSpacing.readingMaxWidth),
        ) {
            items(items = phase.results, key = { it.resultKey() }) { result ->
                SearchResultRow(result = result, onClick = { onResultClick(result) })
                HorizontalDivider(color = scheme.outlineVariant)
            }
        }
    }
}

private fun SearchResult.resultKey(): String = when (this) {
    is SearchResult.VerseMatch -> "verse:${ref.verseId}"
    is SearchResult.ChapterMatch -> "chapter:$chapterId"
    is SearchResult.MatnMatch -> "matn:$matnId"
}

@Composable
private fun CatalogGrid(
    state: HomeUiState,
    onOpenMatn: (String) -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    val widthClass = LocalWindowWidthClass.current
    LazyVerticalGrid(
        // T084 (US4, FR-027, SC-011): column count and margin follow available width, not device
        // type — a narrow split-screen pane gets the COMPACT layout.
        columns = GridCells.Fixed(MatnSpacing.libraryColumns(widthClass)),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MatnSpacing.horizontalMargin(widthClass),
            end = MatnSpacing.horizontalMargin(widthClass),
            bottom = MatnSpacing.gutter,
        ),
        horizontalArrangement = Arrangement.spacedBy(MatnSpacing.snug),
        verticalArrangement = Arrangement.spacedBy(MatnSpacing.snug),
    ) {
        // FR-007: a failed refresh is a non-blocking notice ABOVE a still-usable library, never an
        // emptied one.
        if (state.showSyncFailedNotice) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CatalogNotice(
                    message = stringResource(Res.string.catalog_sync_failed),
                    actionLabel = stringResource(Res.string.catalog_refresh),
                    onAction = onRefresh,
                    isBusy = state.isSyncing,
                )
            }
        }
        // FR-044's third case: the catalog has متون but none are on the device. Said once, above
        // the grid, rather than repeated on every card.
        if (state.showNothingDownloaded) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(Res.string.catalog_nothing_downloaded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    // Reinstalling happens from the matn's own details screen, where the real
                    // install action lives (FR-022) — a resume that would fail the playback gate is
                    // never offered here (SC-007).
                    onReinstall = { onOpenMatn(entry.matnId) },
                    modifier = Modifier.fillMaxWidth(),
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

/**
 * A centered catalog state — the full-screen form, used when there is no grid to show.
 *
 * Stateless and parameterized (Principle II). `actionLabel = null` renders no button, which is how
 * "the teacher has published nothing" differs from "we could not reach the catalog": one is a
 * situation to wait out, the other is a situation to retry, and offering a retry for the first
 * would be a dead affordance.
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

// ---------------------------------------------------------------------------------------------
// Previews — every view/component in this file has one (light Material 3, direction from locale).
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

private val previewItems = listOf(
    previewSummary("m1", "الأجرومية", 4, 31_300),
    previewSummary("m2", "متن الآجرومية مبوب", 5, 39_900),
)

@Preview
@Composable
private fun HomeContentPopulatedPreview() {
    MatnTheme {
        HomeContent(state = HomeUiState(isLoading = false, items = previewItems), onOpenMatn = {})
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
            LocalWindowWidthClass provides com.giraffe.matn.presentation.theme.WindowWidthClass.EXPANDED,
        ) {
            HomeContent(state = HomeUiState(isLoading = false, items = previewItems), onOpenMatn = {})
        }
    }
}

/** T067 (US2, accessibility-contract.md §5/§7): largest reachable font scale, narrowest width. */
@Preview(fontScale = 2.0f, widthDp = 320)
@Composable
private fun HomeContentMaxScalePreview() {
    MatnTheme {
        HomeContent(state = HomeUiState(isLoading = false, items = previewItems), onOpenMatn = {})
    }
}

/** T052 (US2): dark-theme coverage for the populated content state. */
@Preview
@Composable
private fun HomeContentPopulatedDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) {
        HomeContent(state = HomeUiState(isLoading = false, items = previewItems), onOpenMatn = {})
    }
}

/** A live query swaps the grid for results, in place — the state that used to be its own route. */
@Preview
@Composable
private fun HomeContentSearchResultsPreview() {
    MatnTheme {
        HomeContent(
            state = HomeUiState(isLoading = false, items = previewItems),
            searchState = SearchUiState(
                query = "الكلام",
                phase = SearchPhase.Results(
                    listOf(
                        SearchResult.MatnMatch(matnId = "m1", matnTitle = "الأجرومية"),
                        SearchResult.VerseMatch(
                            com.giraffe.matn.domain.model.AnnotatedVerseRef(
                                matnId = "m1",
                                matnTitle = "الأجرومية",
                                verseId = "v1",
                                verseNumber = 1,
                                verseText = "الكَلامُ هُوَ اللَّفظُ المُرَكَّبُ المُفيدُ بِالوَضعِ",
                            ),
                        ),
                    ),
                ),
            ),
            onOpenMatn = {},
        )
    }
}

@Preview
@Composable
private fun HomeContentSearchNoResultsPreview() {
    MatnTheme {
        HomeContent(
            state = HomeUiState(isLoading = false, items = previewItems),
            searchState = SearchUiState(query = "xyz", phase = SearchPhase.NoResults),
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
                items = previewItems,
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
    MatnTheme { HomeContent(state = HomeUiState(isLoading = true), onOpenMatn = {}) }
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
