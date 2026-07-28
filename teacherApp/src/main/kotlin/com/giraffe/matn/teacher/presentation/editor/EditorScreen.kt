package com.giraffe.matn.teacher.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giraffe.matn.domain.catalog.DraftChapter
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.MatnDraftFactory
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.di.TeacherKoinHolder
import com.giraffe.matn.teacher.platform.JvmFileChooser
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.common.SaveStateIndicator
import com.giraffe.matn.teacher.presentation.common.TeacherTextField
import com.giraffe.matn.teacher.presentation.common.VerseRow
import com.giraffe.matn.teacher.presentation.importer.ImportPreviewDialog
import com.giraffe.matn.teacher.presentation.publish.PublishConfirmDialog
import com.giraffe.matn.teacher.presentation.publish.ValidationPanel
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Every editor intent, bundled so `EditorContent`'s signature grows without a wall of positional
 * lambdas (later phases add import intents here rather than to the call site). */
data class EditorIntents(
    val onTitleChange: (String) -> Unit,
    val onAuthorChange: (String) -> Unit,
    val onDescriptionChange: (String) -> Unit,
    val onStructureKindChange: (StructureKind) -> Unit,
    val onPickCover: () -> Unit,
    val onRemoveCover: () -> Unit,
    val onAddChapter: (String) -> Unit,
    val onEditChapterTitle: (String, String) -> Unit,
    val onDeleteChapter: (String) -> Unit,
    val onAddVerse: () -> Unit,
    val onVerseTextChange: (String, String) -> Unit,
    val onDeleteVerse: (String) -> Unit,
    val onMoveVerse: (Int, Int) -> Unit,
    val onSaveDraft: () -> Unit,
    val onCheckForProblems: () -> Unit,
    val onRequestPublish: () -> Unit,
    val onConfirmPublish: () -> Unit,
    val onDismissPublishConfirm: () -> Unit,
    val onProblemSelected: (String) -> Unit,
    val onReloadAfterConflict: () -> Unit,
    val onImportRequested: () -> Unit,
    val onImportCancel: () -> Unit,
    val onImportConfirm: () -> Unit,
    val onRequestClearAllVerses: () -> Unit,
    val onDismissClearAllVerses: () -> Unit,
    val onConfirmClearAllVerses: () -> Unit,
)

/** `contracts/teacher-ui-contract.md` §3.4. Metadata, chapters, the verse list, and validation. */
@Composable
fun EditorContent(state: EditorUiState, intents: EditorIntents, modifier: Modifier = Modifier) {
    val strings = LocalTeacherStrings.current
    val draft = state.draft
    val listState = rememberLazyListState()

    // Header items before the verse list, so a focused problem scrolls to the right index.
    val headerItemCount = 3 + (if (draft.structureKind == StructureKind.STRUCTURED) 1 else 0)
    LaunchedEffect(state.focusedProblem) {
        val subjectId = state.focusedProblem ?: return@LaunchedEffect
        val verseIndex = draft.verses.indexOfFirst { it.id == subjectId }
        if (verseIndex >= 0) listState.animateScrollToItem(headerItemCount + verseIndex)
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(MatnSpacing.gutter)) {
            Text(text = strings.uploadNewMatnHeading, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = strings.uploadNewMatnLead,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = MatnSpacing.unit, bottom = MatnSpacing.gutter),
            )

            if (state.isPublished) {
                Text(
                    text = strings.publishedEditingBanner,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), MatnShapes.lg)
                        .padding(MatnSpacing.unit * 2),
                )
            }

            // Keyed by verse id — reorder/delete must not remount unrelated rows (FR-025).
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MatnSpacing.gutter),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MatnShapes.xl)
                            .padding(MatnSpacing.unit * 3),
                        verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit * 3),
                    ) {
                        Text(strings.generalInformationHeading, style = MaterialTheme.typography.titleMedium)

                        TeacherTextField(
                            value = draft.title,
                            onValueChange = intents.onTitleChange,
                            label = strings.matnTitleLabel,
                            placeholder = strings.matnTitlePlaceholder,
                            isError = state.missingTitle,
                        )
                        TeacherTextField(
                            value = draft.author,
                            onValueChange = intents.onAuthorChange,
                            label = strings.authorLabel,
                            placeholder = strings.authorPlaceholder,
                            isError = state.missingAuthor,
                        )
                        TeacherTextField(
                            value = draft.description,
                            onValueChange = intents.onDescriptionChange,
                            label = strings.descriptionLabel,
                            placeholder = strings.descriptionPlaceholder,
                            singleLine = false,
                        )

                        StructureKindPicker(draft.structureKind, intents.onStructureKindChange)
                    }
                }

                item {
                    CoverArtCard(draft.coverImageRef, intents.onPickCover, intents.onRemoveCover)
                }

                if (draft.structureKind == StructureKind.STRUCTURED) {
                    item {
                        ChaptersSection(draft.chapters, intents.onAddChapter, intents.onEditChapterTitle, intents.onDeleteChapter)
                    }
                }

                item {
                    VerseListHeader(
                        onAddVerse = intents.onAddVerse,
                        onBulkImport = intents.onImportRequested,
                        onClearAll = intents.onRequestClearAllVerses,
                        clearAllEnabled = draft.verses.isNotEmpty(),
                    )
                }

                if (state.importError) {
                    item {
                        Text(strings.importInvalidEncodingError, color = MaterialTheme.colorScheme.error)
                    }
                }

                itemsIndexed(draft.verses, key = { _, verse -> verse.id }) { index, verse ->
                    val rowHeightPx = with(LocalDensity.current) { (MatnSpacing.unit * 7).toPx() }
                    var dragAccumPx by remember(verse.id) { mutableFloatStateOf(0f) }
                    VerseRow(
                        verse = verse,
                        isFlagged = state.focusedProblem == verse.id,
                        onTextChange = { text -> intents.onVerseTextChange(verse.id, text) },
                        onDelete = { intents.onDeleteVerse(verse.id) },
                        onMoveUp = { if (index > 0) intents.onMoveVerse(index, index - 1) },
                        onMoveDown = { if (index < draft.verses.lastIndex) intents.onMoveVerse(index, index + 1) },
                        dragHandleModifier = Modifier.pointerInput(verse.id, draft.verses.size) {
                            detectDragGestures(
                                onDragEnd = { dragAccumPx = 0f },
                                onDragCancel = { dragAccumPx = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragAccumPx += dragAmount.y
                                    when {
                                        dragAccumPx > rowHeightPx && index < draft.verses.lastIndex -> {
                                            intents.onMoveVerse(index, index + 1)
                                            dragAccumPx = 0f
                                        }
                                        dragAccumPx < -rowHeightPx && index > 0 -> {
                                            intents.onMoveVerse(index, index - 1)
                                            dragAccumPx = 0f
                                        }
                                    }
                                },
                            )
                        },
                    )
                }

                state.validation?.let { report ->
                    item {
                        ValidationPanel(report = report, draft = draft, onProblemClick = intents.onProblemSelected)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = MatnSpacing.unit * 2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
                    SaveStateIndicator(state.saveState)
                    val failedError = (state.saveState as? SaveState.Failed)?.error
                    if (failedError == com.giraffe.matn.domain.error.RemoteError.Conflict) {
                        TextButton(onClick = intents.onReloadAfterConflict) { Text(strings.errorConflictAction) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
                    OutlinedButton(onClick = intents.onCheckForProblems) { Text(strings.checkForProblems) }
                    Button(onClick = intents.onSaveDraft) { Text(strings.saveAsDraft) }
                    Button(
                        onClick = intents.onRequestPublish,
                        enabled = state.validation?.blocking?.isEmpty() ?: true,
                    ) {
                        Text(strings.publishMatn)
                    }
                }
            }

            if (state.showPublishConfirm) {
                PublishConfirmDialog(onConfirm = intents.onConfirmPublish, onDismiss = intents.onDismissPublishConfirm)
            }

            state.importPreview?.let { preview ->
                ImportPreviewDialog(preview = preview, onCancel = intents.onImportCancel, onConfirm = intents.onImportConfirm)
            }

            if (state.showClearAllConfirm) {
                ClearAllVersesConfirmDialog(onConfirm = intents.onConfirmClearAllVerses, onDismiss = intents.onDismissClearAllVerses)
            }
        }
    }
}

@Composable
private fun VerseListHeader(onAddVerse: () -> Unit, onBulkImport: () -> Unit, onClearAll: () -> Unit, clearAllEnabled: Boolean) {
    val strings = LocalTeacherStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(strings.verseListHeading, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
            TextButton(onClick = onBulkImport) { Text(strings.bulkImport) }
            TextButton(onClick = onClearAll, enabled = clearAllEnabled) { Text(strings.clearAllVerses) }
            TextButton(onClick = onAddVerse) { Text(strings.addVerse) }
        }
    }
}

@Composable
private fun StructureKindPicker(selected: StructureKind, onSelected: (StructureKind) -> Unit) {
    val strings = LocalTeacherStrings.current
    Column {
        Text(strings.structureKindLabel, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit * 2)) {
            listOf(
                StructureKind.SIMPLE to strings.structureKindSimple,
                StructureKind.STRUCTURED to strings.structureKindStructured,
            ).forEach { (kind, label) ->
                Row(
                    modifier = Modifier.selectable(selected = selected == kind, onClick = { onSelected(kind) }, role = Role.RadioButton),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == kind, onClick = { onSelected(kind) })
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun CoverArtCard(coverImageRef: String?, onPick: () -> Unit, onRemove: () -> Unit) {
    val strings = LocalTeacherStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MatnShapes.xl)
            .padding(MatnSpacing.unit * 3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit),
    ) {
        Text(strings.coverArtLabel, style = MaterialTheme.typography.titleMedium)
        Text(coverImageRef ?: strings.coverArtHint, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
            TextButton(onClick = onPick) { Text(strings.coverArtLabel) }
            if (coverImageRef != null) {
                TextButton(onClick = onRemove) { Text(strings.coverArtRemove) }
            }
        }
    }
}

@Composable
private fun ChaptersSection(
    chapters: List<DraftChapter>,
    onAddChapter: (String) -> Unit,
    onEditChapterTitle: (String, String) -> Unit,
    onDeleteChapter: (String) -> Unit,
) {
    val strings = LocalTeacherStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MatnShapes.xl)
            .padding(MatnSpacing.unit * 3),
        verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit * 2),
    ) {
        Text(strings.chaptersHeading, style = MaterialTheme.typography.titleMedium)
        chapters.sortedBy { it.order }.forEach { chapter ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
                TeacherTextField(
                    value = chapter.title,
                    onValueChange = { onEditChapterTitle(chapter.id, it) },
                    label = strings.chaptersHeading,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onDeleteChapter(chapter.id) }) { Text(strings.deleteVerse) }
            }
        }
        TextButton(onClick = { onAddChapter("") }) { Text(strings.addChapter) }
    }
}

/** [initialDraft] is a fresh [MatnDraftFactory.newDraft] for "create new"; the Library flow
 * (T086) passes a [com.giraffe.matn.domain.usecase.LoadMatnForEditUseCase] result to reopen one. */
@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
@Composable
fun EditorScreen(
    // `remember` — a plain expression here would re-evaluate on every recomposition (every
    // keystroke), handing `viewModel(key = initialDraft.id)` a fresh random id each time and
    // silently replacing the in-progress ViewModel with a blank one.
    initialDraft: MatnDraft = remember {
        MatnDraftFactory.newDraft(
            newId = { Uuid.random().toString() },
            nowMillis = { Clock.System.now().toEpochMilliseconds() },
        )
    },
    modifier: Modifier = Modifier,
) {
    val koin = TeacherKoinHolder.koin
    // Keyed by draft id — `viewModel {}` otherwise caches the first-ever instance across
    // destination switches (same as the Library screen's staleness bug) and every subsequent
    // "open to edit" click would keep showing whichever matn was loaded first.
    val viewModel: EditorViewModel = viewModel(key = initialDraft.id) {
        EditorViewModel(
            initialDraft = initialDraft,
            saveDraft = koin.get(),
            uploadCoverImage = koin.get(),
            validateMatn = koin.get(),
            publishMatn = koin.get(),
            loadMatnForEdit = koin.get(),
            newId = { Uuid.random().toString() },
            nowMillis = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    EditorContent(
        state = state,
        intents = EditorIntents(
            onTitleChange = viewModel::onTitleChange,
            onAuthorChange = viewModel::onAuthorChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onStructureKindChange = viewModel::onStructureKindChange,
            onPickCover = {
                val bytes = JvmFileChooser.pickImage()
                if (bytes != null) viewModel.onCoverPicked(bytes, "png")
            },
            onRemoveCover = viewModel::onRemoveCover,
            onAddChapter = viewModel::onAddChapter,
            onEditChapterTitle = viewModel::onEditChapterTitle,
            onDeleteChapter = viewModel::onDeleteChapter,
            onAddVerse = viewModel::onAddVerse,
            onVerseTextChange = viewModel::onVerseTextChange,
            onDeleteVerse = viewModel::onDeleteVerse,
            onMoveVerse = viewModel::onMoveVerse,
            onSaveDraft = viewModel::onSaveDraft,
            onCheckForProblems = viewModel::onCheckForProblems,
            onRequestPublish = viewModel::onRequestPublish,
            onConfirmPublish = viewModel::onConfirmPublish,
            onDismissPublishConfirm = viewModel::onDismissPublishConfirm,
            onProblemSelected = viewModel::onProblemSelected,
            onReloadAfterConflict = viewModel::onReloadAfterConflict,
            onImportRequested = {
                val bytes = JvmFileChooser.pickTextFile()
                if (bytes != null) viewModel.onImportRequested(bytes)
            },
            onImportCancel = viewModel::onImportCancel,
            onImportConfirm = viewModel::onImportConfirm,
            onRequestClearAllVerses = viewModel::onRequestClearAllVerses,
            onDismissClearAllVerses = viewModel::onDismissClearAllVerses,
            onConfirmClearAllVerses = viewModel::onConfirmClearAllVerses,
        ),
        modifier = modifier,
    )
}

private fun previewDraft(structureKind: StructureKind = StructureKind.SIMPLE) = MatnDraft(
    id = "m1",
    title = if (structureKind == StructureKind.STRUCTURED) "Al-Jazariyyah" else "",
    author = "",
    description = "",
    coverImageRef = null,
    structureKind = structureKind,
    defaultReciterId = MatnDraftFactory.INSTITUTIONAL_RECITER_ID,
    chapters = if (structureKind == StructureKind.STRUCTURED) listOf(DraftChapter("c1", "Chapter 1", 0)) else emptyList(),
    verses = emptyList(),
    publicationState = PublicationState.DRAFT,
    createdAt = 0L,
    updatedAt = 0L,
    remoteUpdateTime = null,
)

private val noOpIntents = EditorIntents(
    onTitleChange = {}, onAuthorChange = {}, onDescriptionChange = {}, onStructureKindChange = {},
    onPickCover = {}, onRemoveCover = {}, onAddChapter = {}, onEditChapterTitle = { _, _ -> },
    onDeleteChapter = {}, onAddVerse = {}, onVerseTextChange = { _, _ -> }, onDeleteVerse = {},
    onMoveVerse = { _, _ -> }, onSaveDraft = {}, onCheckForProblems = {}, onRequestPublish = {},
    onConfirmPublish = {}, onDismissPublishConfirm = {}, onProblemSelected = {}, onReloadAfterConflict = {},
    onImportRequested = {}, onImportCancel = {}, onImportConfirm = {},
    onRequestClearAllVerses = {}, onDismissClearAllVerses = {}, onConfirmClearAllVerses = {},
)

@Preview
@Composable
private fun EditorNewDraftArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    EditorContent(EditorUiState(previewDraft()), noOpIntents)
}

@Preview
@Composable
private fun EditorNewDraftEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    EditorContent(EditorUiState(previewDraft()), noOpIntents)
}

@Preview
@Composable
private fun EditorLoadedStructuredArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    EditorContent(EditorUiState(previewDraft(StructureKind.STRUCTURED)), noOpIntents)
}

@Preview
@Composable
private fun EditorLoadedStructuredEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    EditorContent(EditorUiState(previewDraft(StructureKind.STRUCTURED)), noOpIntents)
}

@Preview
@Composable
private fun EditorPublishedEditingPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    EditorContent(EditorUiState(previewDraft().copy(publicationState = PublicationState.PUBLISHED)), noOpIntents)
}

@Preview
@Composable
private fun EditorValidationFailedPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    val draft = previewDraft()
    EditorContent(
        state = EditorUiState(
            draft = draft,
            validation = com.giraffe.matn.domain.catalog.ValidationReport(
                blocking = listOf(com.giraffe.matn.domain.error.ContentIntegrityError.EmptyMatn(draft.id)),
                deferred = emptyList(),
            ),
        ),
        intents = noOpIntents,
    )
}
