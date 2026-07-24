package com.giraffe.matn.presentation.notes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.Bookmark
import com.giraffe.matn.domain.model.BookmarkEntry
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.notes_tab_bookmarks_empty
import matn.shared.generated.resources.notes_tab_bookmarks_header
import matn.shared.generated.resources.notes_tab_notes_empty
import matn.shared.generated.resources.notes_tab_notes_header
import matn.shared.generated.resources.notes_tab_title
import org.jetbrains.compose.resources.stringResource

/**
 * The Notes tab (US2 FR-020; T004 design notes — *Bookmarks & Notes* `bc99ab7f…`) — **stateful**
 * entry. Replaces the `ComingSoonScreen` stub (T037).
 */
@Composable
fun NotesTabScreen(viewModel: NotesTabViewModel, onNavigateToVerse: (matnId: String, verseId: String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { (matnId, verseId) -> onNavigateToVerse(matnId, verseId) }
    }
    NotesTabContent(state = state, onBookmarkClick = viewModel::onBookmarkClick)
}

/**
 * Stateless — two stacked sections (research.md D6; T004 design notes confirm stacked sections,
 * not tabs), each with its own purposeful empty state (SC-007). The notes section always shows
 * its empty state in this task — real notes arrive in T047.
 */
@Composable
fun NotesTabContent(
    state: NotesTabUiState,
    onBookmarkClick: (BookmarkEntry) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "title") {
            Text(
                text = stringResource(Res.string.notes_tab_title),
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(MatnSpacing.marginMobile),
            )
        }
        item(key = "bookmarks-header") {
            SectionHeader(stringResource(Res.string.notes_tab_bookmarks_header))
        }
        if (state.bookmarks.isEmpty()) {
            item(key = "bookmarks-empty") {
                EmptySection(stringResource(Res.string.notes_tab_bookmarks_empty))
            }
        } else {
            items(items = state.bookmarks, key = { "bookmark:${it.bookmark.id}" }) { entry ->
                BookmarkRow(entry = entry, onClick = { onBookmarkClick(entry) })
                HorizontalDivider(color = scheme.outlineVariant)
            }
        }
        item(key = "notes-header") {
            SectionHeader(stringResource(Res.string.notes_tab_notes_header))
        }
        item(key = "notes-empty") {
            EmptySection(stringResource(Res.string.notes_tab_notes_empty))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MatnSpacing.marginMobile, vertical = MatnSpacing.unit),
    )
}

@Composable
private fun EmptySection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MatnSpacing.marginMobile, vertical = MatnSpacing.gutter),
    )
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun NotesTabBothEmptyPreview() {
    MatnTheme { NotesTabContent(state = NotesTabUiState()) }
}

@Preview
@Composable
private fun NotesTabBookmarksPopulatedPreview() {
    MatnTheme {
        NotesTabContent(
            state = NotesTabUiState(
                bookmarks = listOf(
                    BookmarkEntry(
                        Bookmark("b1", "v1", 1000),
                        AnnotatedVerseRef("m1", "الأجرومية", "v1", 2, "الْحَمْدُ لِلَّهِ وَصَلَّى اللَّهُ عَلَى نَبِيِّهِ"),
                    ),
                ),
            ),
        )
    }
}
