package com.giraffe.matn.presentation.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.SearchResult
import com.giraffe.matn.presentation.common.BackGlyph
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.back
import matn.shared.generated.resources.search_clear
import matn.shared.generated.resources.search_field_placeholder
import matn.shared.generated.resources.search_idle_prompt
import matn.shared.generated.resources.search_no_results
import org.jetbrains.compose.resources.stringResource

/**
 * Dedicated search screen (US1; ui-contract.md § SearchScreen) — **stateful** entry. Hoists the
 * ViewModel's state and delegates rendering to [SearchScreenContent] (Principle II).
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onResultClick: (SearchResult) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreenContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onResultClick = onResultClick,
        onBack = onBack,
    )
}

/**
 * Stateless search surface (T003 design notes, *Search Matn* `fa8b63b0…`): a top bar with a back
 * action and an autofocused search field, a lazy result list of [SearchResultRow], and a distinct
 * idle prompt vs. no-results empty state (FR-008/FR-009). No bottom bar — this is a focused,
 * pushed surface like the matn details route.
 */
@Composable
fun SearchScreenContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit = {},
    onClearQuery: () -> Unit = {},
    onResultClick: (SearchResult) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MatnSpacing.unit, vertical = MatnSpacing.unit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                BackGlyph(color = scheme.onSurface, contentDescription = stringResource(Res.string.back))
            }
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(Res.string.search_field_placeholder)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = scheme.surfaceContainerLow,
                    focusedContainerColor = scheme.surfaceContainerLow,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = onClearQuery) {
                            Text("×", style = MaterialTheme.typography.titleLarge, color = scheme.onSurfaceVariant)
                        }
                    }
                } else null,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (val phase = state.phase) {
                is SearchPhase.Idle -> EmptyMessage(stringResource(Res.string.search_idle_prompt))
                is SearchPhase.Searching -> Unit // sub-frame; no spinner to avoid flicker
                is SearchPhase.NoResults -> EmptyMessage(stringResource(Res.string.search_no_results))
                // T086 (US4, FR-028): bounded to the reading measure and centred on wide windows.
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
    }
}

private fun SearchResult.resultKey(): String = when (this) {
    is SearchResult.VerseMatch -> "verse:${ref.verseId}"
    is SearchResult.ChapterMatch -> "chapter:$chapterId"
    is SearchResult.MatnMatch -> "matn:$matnId"
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(MatnSpacing.gutter),
    )
}

// --------------------------------------------------------------------------- Previews

private val previewResults = listOf(
    SearchResult.MatnMatch(matnId = "m1", matnTitle = "الأجرومية"),
    SearchResult.ChapterMatch(
        matnId = "m2",
        matnTitle = "متن الآجرومية مبوب",
        chapterId = "c1",
        chapterTitle = "باب الكلام",
        firstVerseId = "v1",
    ),
    SearchResult.VerseMatch(
        AnnotatedVerseRef(
            matnId = "m1",
            matnTitle = "الأجرومية",
            verseId = "v1",
            verseNumber = 1,
            verseText = "الكَلامُ هُوَ اللَّفظُ المُرَكَّبُ المُفيدُ بِالوَضعِ",
        ),
    ),
)

@Preview
@Composable
private fun SearchScreenIdlePreview() {
    MatnTheme { SearchScreenContent(state = SearchUiState()) }
}

@Preview
@Composable
private fun SearchScreenResultsPreview() {
    MatnTheme {
        SearchScreenContent(
            state = SearchUiState(query = "الكلام", phase = SearchPhase.Results(previewResults)),
        )
    }
}

/** T092 (US4): expanded window width — confirms the results list stays bounded/centred. */
@Preview(widthDp = 900)
@Composable
private fun SearchScreenWidePreview() {
    MatnTheme {
        SearchScreenContent(
            state = SearchUiState(query = "الكلام", phase = SearchPhase.Results(previewResults)),
        )
    }
}

/** T067 (US2, accessibility-contract.md §5/§7): largest reachable font scale, narrowest width. */
@Preview(fontScale = 2.0f, widthDp = 320)
@Composable
private fun SearchScreenMaxScalePreview() {
    MatnTheme {
        SearchScreenContent(
            state = SearchUiState(query = "الكلام", phase = SearchPhase.Results(previewResults)),
        )
    }
}

/** T052 (US2): dark-theme coverage for populated results. */
@Preview
@Composable
private fun SearchScreenResultsDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) {
        SearchScreenContent(
            state = SearchUiState(query = "الكلام", phase = SearchPhase.Results(previewResults)),
        )
    }
}

@Preview
@Composable
private fun SearchScreenNoResultsPreview() {
    MatnTheme {
        SearchScreenContent(state = SearchUiState(query = "xyz", phase = SearchPhase.NoResults))
    }
}
