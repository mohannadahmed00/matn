package com.giraffe.matn.teacher.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** `contracts/teacher-ui-contract.md` §3.4. Metadata + chapters this phase; verses in Phase 5. */
@Composable
fun EditorContent(
    state: EditorUiState,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onStructureKindChange: (StructureKind) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onAddChapter: (String) -> Unit,
    onEditChapterTitle: (String, String) -> Unit,
    onDeleteChapter: (String) -> Unit,
    onSaveDraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalTeacherStrings.current
    val draft = state.draft
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(MatnSpacing.gutter)) {
            Text(text = strings.uploadNewMatnHeading, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = strings.uploadNewMatnLead,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = MatnSpacing.unit, bottom = MatnSpacing.gutter),
            )

            if (draft.publicationState == PublicationState.PUBLISHED) {
                Text(
                    text = strings.publishedEditingBanner,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), MatnShapes.lg)
                        .padding(MatnSpacing.unit * 2),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MatnSpacing.gutter)) {
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
                            onValueChange = onTitleChange,
                            label = strings.matnTitleLabel,
                            placeholder = strings.matnTitlePlaceholder,
                            isError = state.missingTitle,
                        )
                        TeacherTextField(
                            value = draft.author,
                            onValueChange = onAuthorChange,
                            label = strings.authorLabel,
                            placeholder = strings.authorPlaceholder,
                            isError = state.missingAuthor,
                        )
                        TeacherTextField(
                            value = draft.description,
                            onValueChange = onDescriptionChange,
                            label = strings.descriptionLabel,
                            placeholder = strings.descriptionPlaceholder,
                            singleLine = false,
                        )

                        StructureKindPicker(draft.structureKind, onStructureKindChange)
                    }
                }

                item {
                    CoverArtCard(draft.coverImageRef, onPickCover, onRemoveCover)
                }

                if (draft.structureKind == StructureKind.STRUCTURED) {
                    item {
                        ChaptersSection(draft.chapters, onAddChapter, onEditChapterTitle, onDeleteChapter)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = MatnSpacing.unit * 2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SaveStateIndicator(state.saveState)
                Button(onClick = onSaveDraft) { Text(strings.saveAsDraft) }
            }
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
    initialDraft: MatnDraft = MatnDraftFactory.newDraft(
        newId = { Uuid.random().toString() },
        nowMillis = { Clock.System.now().toEpochMilliseconds() },
    ),
    modifier: Modifier = Modifier,
) {
    val koin = TeacherKoinHolder.koin
    val viewModel: EditorViewModel = viewModel {
        EditorViewModel(
            initialDraft = initialDraft,
            saveDraft = koin.get(),
            uploadCoverImage = koin.get(),
            newId = { Uuid.random().toString() },
            nowMillis = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    EditorContent(
        state = state,
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
        onSaveDraft = viewModel::onSaveDraft,
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

@Preview
@Composable
private fun EditorNewDraftArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    EditorContent(EditorUiState(previewDraft()), {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
}

@Preview
@Composable
private fun EditorNewDraftEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    EditorContent(EditorUiState(previewDraft()), {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
}

@Preview
@Composable
private fun EditorLoadedStructuredArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    EditorContent(EditorUiState(previewDraft(StructureKind.STRUCTURED)), {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
}

@Preview
@Composable
private fun EditorLoadedStructuredEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    EditorContent(EditorUiState(previewDraft(StructureKind.STRUCTURED)), {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
}
