package com.giraffe.matn.teacher.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giraffe.matn.domain.catalog.AudioCompleteness
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.di.TeacherKoinHolder
import com.giraffe.matn.teacher.presentation.common.AudioCompletenessBadge
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.common.PublicationBadge
import com.giraffe.matn.teacher.presentation.publish.UnpublishConfirmDialog
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage
import com.giraffe.matn.teacher.presentation.strings.messageFor

/** `contracts/teacher-ui-contract.md` §3.3. */
@Composable
fun LibraryContent(
    state: LibraryUiState,
    onOpenMatn: (String) -> Unit,
    onRequestUnpublish: (String) -> Unit,
    onDismissUnpublish: () -> Unit,
    onConfirmUnpublish: () -> Unit,
    onRetry: () -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalTeacherStrings.current
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize().padding(MatnSpacing.gutter)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(strings.libraryLoading, modifier = Modifier.padding(top = MatnSpacing.unit * 2))
                    }
                }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(strings.messageFor(state.error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry, modifier = Modifier.padding(top = MatnSpacing.unit)) { Text(strings.libraryRetry) }
                    }
                }
                state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(strings.libraryEmptyTitle, style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onCreateNew, modifier = Modifier.padding(top = MatnSpacing.unit * 2)) {
                            Text(strings.libraryEmptyAction)
                        }
                    }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
                    items(state.entries, key = { it.id }) { entry ->
                        LibraryRow(entry = entry, onClick = { onOpenMatn(entry.id) }, onRequestUnpublish = { onRequestUnpublish(entry.id) })
                    }
                }
            }

            state.pendingUnpublishId?.let {
                UnpublishConfirmDialog(onConfirm = onConfirmUnpublish, onDismiss = onDismissUnpublish)
            }
        }
    }
}

@Composable
private fun LibraryRow(entry: CatalogEntry, onClick: () -> Unit, onRequestUnpublish: () -> Unit) {
    val strings = LocalTeacherStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MatnShapes.lg)
            .padding(MatnSpacing.unit * 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit * 2),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
            Text(entry.author, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
                PublicationBadge(entry.publicationState)
                AudioCompletenessBadge(entry.audioCompleteness)
            }
        }
        if (entry.publicationState == PublicationState.PUBLISHED) {
            TextButton(onClick = onRequestUnpublish) { Text(strings.unpublishMatn) }
        }
    }
}

@Composable
fun LibraryScreen(onOpenMatn: (String) -> Unit, onCreateNew: () -> Unit, modifier: Modifier = Modifier) {
    val koin = TeacherKoinHolder.koin
    val viewModel: LibraryViewModel = viewModel { LibraryViewModel(koin.get(), koin.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryContent(
        state = state,
        onOpenMatn = onOpenMatn,
        onRequestUnpublish = viewModel::onRequestUnpublish,
        onDismissUnpublish = viewModel::onDismissUnpublish,
        onConfirmUnpublish = viewModel::onConfirmUnpublish,
        onRetry = viewModel::load,
        onCreateNew = onCreateNew,
        modifier = modifier,
    )
}

private fun previewEntry(id: String, state: PublicationState) = CatalogEntry(
    id = id, title = "الأجرومية", author = "ابن آجروم", description = "", coverImageRef = null,
    verseCount = 12, declaredSizeBytes = 4_000L, publicationState = state,
    audioCompleteness = AudioCompleteness.NONE, updatedAt = 0L,
)

@Preview
@Composable
private fun LibraryLoadedPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    LibraryContent(
        state = LibraryUiState(isLoading = false, entries = listOf(previewEntry("m1", PublicationState.DRAFT), previewEntry("m2", PublicationState.PUBLISHED))),
        onOpenMatn = {}, onRequestUnpublish = {}, onDismissUnpublish = {}, onConfirmUnpublish = {}, onRetry = {}, onCreateNew = {},
    )
}

@Preview
@Composable
private fun LibraryEmptyPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    LibraryContent(
        state = LibraryUiState(isLoading = false, entries = emptyList()),
        onOpenMatn = {}, onRequestUnpublish = {}, onDismissUnpublish = {}, onConfirmUnpublish = {}, onRetry = {}, onCreateNew = {},
    )
}

@Preview
@Composable
private fun LibraryLoadingPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    LibraryContent(
        state = LibraryUiState(isLoading = true),
        onOpenMatn = {}, onRequestUnpublish = {}, onDismissUnpublish = {}, onConfirmUnpublish = {}, onRetry = {}, onCreateNew = {},
    )
}

@Preview
@Composable
private fun LibraryErrorPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    LibraryContent(
        state = LibraryUiState(isLoading = false, error = com.giraffe.matn.domain.error.RemoteError.Network),
        onOpenMatn = {}, onRequestUnpublish = {}, onDismissUnpublish = {}, onConfirmUnpublish = {}, onRetry = {}, onCreateNew = {},
    )
}
