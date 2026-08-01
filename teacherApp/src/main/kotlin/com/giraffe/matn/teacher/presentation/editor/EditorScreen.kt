package com.giraffe.matn.teacher.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.giraffe.matn.domain.audio.PreviewState
import com.giraffe.matn.domain.catalog.AudioCompleteness
import com.giraffe.matn.domain.catalog.DraftChapter
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.MatnDraftFactory
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.di.TeacherKoinHolder
import com.giraffe.matn.teacher.platform.AudioPickResult
import com.giraffe.matn.teacher.platform.ImagePickResult
import com.giraffe.matn.teacher.platform.JvmFileChooser
import com.giraffe.matn.teacher.presentation.common.GLYPH_PLAY
import com.giraffe.matn.teacher.presentation.common.GLYPH_STOP
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.common.SaveStateIndicator
import com.giraffe.matn.teacher.presentation.common.TeacherTextField
import com.giraffe.matn.teacher.presentation.common.VerseRow
import com.giraffe.matn.teacher.presentation.importer.ImportPreviewDialog
import com.giraffe.matn.teacher.presentation.publish.PublishConfirmDialog
import com.giraffe.matn.teacher.presentation.publish.ValidationPanel
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage
import org.jetbrains.skia.Image as SkiaImage
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
    val onEditChapterStart: (String, Int?) -> Unit,
    val onDeleteChapter: (String) -> Unit,
    val onAddVerse: () -> Unit,
    val onVerseTextChange: (String, String) -> Unit,
    val onDeleteVerse: (String) -> Unit,
    val onMoveVerse: (Int, Int) -> Unit,
    val onAttachVerseAudio: (String) -> Unit,
    val onPlayVerseAudio: (String) -> Unit,
    val onRemoveVerseAudio: (String) -> Unit,
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
    val onOpenSplit: () -> Unit,
    val onPreviewMatn: () -> Unit,
    val onPreviewPause: () -> Unit,
    val onPreviewResume: () -> Unit,
    val onPreviewStop: () -> Unit,
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
                    CoverArtCard(
                        coverImageRef = draft.coverImageRef,
                        coverPreview = state.coverPreview,
                        coverError = state.coverError,
                        onPick = intents.onPickCover,
                        onRemove = intents.onRemoveCover,
                    )
                }

                if (draft.structureKind == StructureKind.STRUCTURED) {
                    item {
                        ChaptersSection(
                            chapters = draft.chapters,
                            onAddChapter = intents.onAddChapter,
                            onEditChapterTitle = intents.onEditChapterTitle,
                            onEditChapterStart = intents.onEditChapterStart,
                            onDeleteChapter = intents.onDeleteChapter,
                        )
                    }
                }

                item {
                    VerseListHeader(
                        onAddVerse = intents.onAddVerse,
                        onBulkImport = intents.onImportRequested,
                        onClearAll = intents.onRequestClearAllVerses,
                        onSplitFromRecording = intents.onOpenSplit,
                        onPreviewMatn = intents.onPreviewMatn,
                        onStopPreview = intents.onPreviewStop,
                        clearAllEnabled = draft.verses.isNotEmpty(),
                        splitEnabled = draft.verses.isNotEmpty(),
                        previewEnabled = draft.audioCompleteness != AudioCompleteness.NONE,
                        previewState = state.previewState,
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
                        audioState = state.audioStateFor(verse),
                        onTextChange = { text -> intents.onVerseTextChange(verse.id, text) },
                        onDelete = { intents.onDeleteVerse(verse.id) },
                        onMoveUp = { if (index > 0) intents.onMoveVerse(index, index - 1) },
                        onMoveDown = { if (index < draft.verses.lastIndex) intents.onMoveVerse(index, index + 1) },
                        onAttachAudio = { intents.onAttachVerseAudio(verse.id) },
                        onPlayAudio = { intents.onPlayVerseAudio(verse.id) },
                        onRemoveAudio = { intents.onRemoveVerseAudio(verse.id) },
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
                PublishConfirmDialog(
                    verseCount = draft.verseCount,
                    audioCompleteness = draft.audioCompleteness,
                    onConfirm = intents.onConfirmPublish,
                    onDismiss = intents.onDismissPublishConfirm,
                )
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
private fun VerseListHeader(
    onAddVerse: () -> Unit,
    onBulkImport: () -> Unit,
    onClearAll: () -> Unit,
    onSplitFromRecording: () -> Unit,
    onPreviewMatn: () -> Unit,
    onStopPreview: () -> Unit,
    clearAllEnabled: Boolean,
    splitEnabled: Boolean,
    previewEnabled: Boolean,
    previewState: PreviewState,
) {
    val strings = LocalTeacherStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(strings.verseListHeading, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
            // One toggle rather than the pinned transport bar this replaced: that bar occupied a
            // permanent strip above the action row to expose controls the per-verse play buttons
            // already cover, and the only thing it added that a single button cannot is a pause —
            // which for a whole-matn playthrough is worth less than the space it cost.
            PreviewMatnButton(previewState, previewEnabled, onPreviewMatn, onStopPreview)
            TextButton(onClick = onBulkImport) { Text(strings.bulkImport) }
            TextButton(onClick = onSplitFromRecording, enabled = splitEnabled) { Text(strings.splitFromRecording) }
            TextButton(onClick = onClearAll, enabled = clearAllEnabled) { Text(strings.clearAllVerses) }
            TextButton(onClick = onAddVerse) { Text(strings.addVerse) }
        }
    }
}

/** Starts a whole-matn playthrough, and becomes its stop control while one is running — so there is
 * always a way out of a preview without a bar dedicated to holding one. */
@Composable
private fun PreviewMatnButton(
    previewState: PreviewState,
    enabled: Boolean,
    onPreviewMatn: () -> Unit,
    onStopPreview: () -> Unit,
) {
    val strings = LocalTeacherStrings.current
    when (previewState) {
        is PreviewState.Playing -> TextButton(onClick = onStopPreview) {
            Text("$GLYPH_STOP ${strings.previewPlaying.replace("%s", previewState.verseNumber.toString())}")
        }
        is PreviewState.Paused -> TextButton(onClick = onStopPreview) {
            Text("$GLYPH_STOP ${strings.previewPaused.replace("%s", previewState.verseNumber.toString())}")
        }
        is PreviewState.Buffering -> TextButton(onClick = onStopPreview) { Text(strings.previewBuffering) }
        is PreviewState.MissingAudio -> TextButton(onClick = onStopPreview) {
            Text(
                text = strings.previewMissingAudio.replace("%s", previewState.verseNumber.toString()),
                color = MaterialTheme.colorScheme.error,
            )
        }
        is PreviewState.Idle -> TextButton(onClick = onPreviewMatn, enabled = enabled) {
            Text("$GLYPH_PLAY ${strings.previewMatn}")
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
private fun CoverArtCard(
    coverImageRef: String?,
    coverPreview: ByteArray?,
    coverError: CoverError?,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
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
        // The image is the point of a cover; an object path told the teacher a file existed but
        // nothing about whether it was the right one, or right way up.
        CoverThumbnail(coverPreview)
        // Only the hint, and only when there is nothing to show. Once the image is on screen the
        // path is noise — the picture already answers "which cover is this".
        if (coverImageRef == null) {
            Text(strings.coverArtHint, style = MaterialTheme.typography.labelSmall)
        }
        if (coverError != null) {
            val message = when (coverError) {
                CoverError.INVALID_FILE -> strings.coverInvalidFileError
                CoverError.UPLOAD_FAILED -> strings.coverUploadFailedError
            }
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
            TextButton(onClick = onPick) { Text(strings.coverArtLabel) }
            if (coverImageRef != null) {
                TextButton(onClick = onRemove) { Text(strings.coverArtRemove) }
            }
        }
    }
}

/**
 * The cover at roughly the 2:3 aspect the hint asks for, so the frame itself communicates the shape
 * a cover should be — and a wrongly proportioned image is visibly letterboxed rather than silently
 * accepted.
 *
 * Decoding is `remember`ed on the byte array: it is a full image decode, and the card recomposes on
 * every keystroke in the metadata fields above it. Undecodable bytes leave the placeholder rather
 * than throwing — the file picker already screens for type, so this is the belt to that braces, not
 * an error worth a message.
 */
@Composable
private fun CoverThumbnail(bytes: ByteArray?) {
    val bitmap = remember(bytes) {
        bytes?.let { runCatching { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
    }
    Box(
        modifier = Modifier
            .size(width = COVER_PREVIEW_WIDTH, height = COVER_PREVIEW_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MatnShapes.lg),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = LocalTeacherStrings.current.coverArtLabel,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(MatnShapes.lg),
            )
        }
    }
}

/** Matches the 800 × 1200 guidance in `coverArtHint`, scaled to a thumbnail. */
private val COVER_PREVIEW_WIDTH = MatnSpacing.unit * 16
private val COVER_PREVIEW_HEIGHT = MatnSpacing.unit * 24

@Composable
private fun ChaptersSection(
    chapters: List<DraftChapter>,
    onAddChapter: (String) -> Unit,
    onEditChapterTitle: (String, String) -> Unit,
    onEditChapterStart: (String, Int?) -> Unit,
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
        // `key` on the chapter's id, not its position: without it each row's remembered field text
        // is tied to a slot rather than a chapter, so any reordering — adding, deleting — hands one
        // chapter's typed value to another.
        chapters.sortedBy { it.order }.forEach { chapter ->
            key(chapter.id) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
                    TeacherTextField(
                        value = chapter.title,
                        onValueChange = { onEditChapterTitle(chapter.id, it) },
                        label = strings.chapterTitleLabel,
                        modifier = Modifier.weight(1f),
                    )
                    ChapterStartField(
                        startVerseNumber = chapter.startVerseNumber,
                        onChanged = { onEditChapterStart(chapter.id, it) },
                    )
                    TextButton(onClick = { onDeleteChapter(chapter.id) }) { Text(strings.deleteChapter) }
                }
            }
        }
        TextButton(onClick = { onAddChapter("") }) { Text(strings.addChapter) }
    }
}

/**
 * Where a chapter opens, entered as a verse number.
 *
 * The field owns its text so a half-typed number survives: `"1"` on the way to `"15"` is a valid
 * `Int`, and echoing the assignment it triggers back into the field would fight the second
 * keystroke. Emptying it clears the start, which is a real state — a chapter owns nothing until the
 * teacher says where it begins.
 */
@Composable
private fun ChapterStartField(startVerseNumber: Int?, onChanged: (Int?) -> Unit) {
    val strings = LocalTeacherStrings.current
    // Not keyed on the value: the caller's `key(chapter.id)` already ties this state to one
    // chapter, and re-keying here would reset the text on the change the teacher's own keystroke
    // caused — typing `0` parses to "no start", which would wipe the digit as it was typed.
    var text by remember { mutableStateOf(startVerseNumber?.toString() ?: "") }
    LaunchedEffect(startVerseNumber) {
        if (startVerseNumber != text.toIntOrNull()) text = startVerseNumber?.toString() ?: ""
    }
    TeacherTextField(
        value = text,
        onValueChange = { value ->
            val digits = value.filter { it.isDigit() }
            text = digits
            onChanged(digits.toIntOrNull()?.takeIf { it > 0 })
        },
        label = strings.chapterStartsAtVerse,
        modifier = Modifier.width(MatnSpacing.unit * 18),
    )
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
    /** Called once the matn has been saved as a draft or published — the portal replaces this
     * editor with a blank one so the next matn can be entered straight away. */
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val koin = TeacherKoinHolder.koin
    // Keyed by draft id — `viewModel {}` otherwise caches the first-ever instance across
    // destination switches (same as the Library screen's staleness bug) and every subsequent
    // "open to edit" click would keep showing whichever matn was loaded first.
    //
    // The `editor:` prefix is **not** decoration. `ViewModelProvider` stores an explicit key
    // verbatim — unlike the default key, it does not fold in the class name — so two screens that
    // key on the same id share one slot. `SplitScreen` is composed inside this one and also keys on
    // the draft id: without distinct prefixes, opening it evicted and cleared this ViewModel, and
    // closing it built a fresh one from `initialDraft`, throwing away everything the split had just
    // written. See `ViewModelKeyCollisionTest`.
    val viewModel: EditorViewModel = viewModel(key = "editor:${initialDraft.id}") {
        EditorViewModel(
            initialDraft = initialDraft,
            saveDraft = koin.get(),
            uploadCoverImage = koin.get(),
            loadCoverImage = koin.get(),
            validateMatn = koin.get(),
            publishMatn = koin.get(),
            loadMatnForEdit = koin.get(),
            attachVerseAudio = koin.get(),
            removeVerseAudio = koin.get(),
            previewPlayer = koin.get(),
            previewMatnAudio = koin.get(),
            newId = { Uuid.random().toString() },
            nowMillis = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The ViewModel is discarded with the editor it belongs to, so the flag never needs clearing —
    // the next matn gets a new one whose `finished` is false again.
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(state.finished) { if (state.finished) currentOnFinished() }

    EditorContent(
        state = state,
        intents = EditorIntents(
            onTitleChange = viewModel::onTitleChange,
            onAuthorChange = viewModel::onAuthorChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onStructureKindChange = viewModel::onStructureKindChange,
            onPickCover = {
                when (val result = JvmFileChooser.pickImage()) {
                    is ImagePickResult.Picked -> viewModel.onCoverPicked(result.bytes, result.extension)
                    is ImagePickResult.Rejected -> viewModel.onCoverRejected()
                    is ImagePickResult.Cancelled -> Unit
                }
            },
            onRemoveCover = viewModel::onRemoveCover,
            onAddChapter = viewModel::onAddChapter,
            onEditChapterTitle = viewModel::onEditChapterTitle,
            onEditChapterStart = viewModel::onEditChapterStart,
            onDeleteChapter = viewModel::onDeleteChapter,
            onAddVerse = viewModel::onAddVerse,
            onVerseTextChange = viewModel::onVerseTextChange,
            onDeleteVerse = viewModel::onDeleteVerse,
            onMoveVerse = viewModel::onMoveVerse,
            onAttachVerseAudio = { verseId ->
                when (val result = JvmFileChooser.pickAudio(JvmFileChooser.MAX_PER_VERSE_AUDIO_BYTES)) {
                    is AudioPickResult.Picked -> viewModel.onAttachVerseAudio(verseId, java.io.File(result.file.path).readBytes())
                    is AudioPickResult.TooLarge ->
                        viewModel.onVerseAudioPickRejected(verseId, com.giraffe.matn.domain.error.AudioAttachError.TooLarge)
                    is AudioPickResult.Rejected ->
                        viewModel.onVerseAudioPickRejected(verseId, com.giraffe.matn.domain.error.AudioAttachError.WrongFormat)
                    is AudioPickResult.Cancelled -> Unit
                }
            },
            onPlayVerseAudio = viewModel::onPlayVerseAudio,
            onRemoveVerseAudio = viewModel::onRemoveVerseAudio,
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
            onOpenSplit = viewModel::onOpenSplit,
            onPreviewMatn = { viewModel.onPreviewMatn() },
            onPreviewPause = viewModel::onPreviewPause,
            onPreviewResume = viewModel::onPreviewResume,
            onPreviewStop = viewModel::onPreviewStop,
        ),
        modifier = modifier,
    )

    if (state.showSplitScreen) {
        com.giraffe.matn.teacher.presentation.split.SplitScreen(
            draft = state.draft,
            onSplitComplete = viewModel::onSplitApplied,
            onCancel = viewModel::onCloseSplit,
        )
    }
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
    remoteRevision = null,
)

private val noOpIntents = EditorIntents(
    onTitleChange = {}, onAuthorChange = {}, onDescriptionChange = {}, onStructureKindChange = {},
    onPickCover = {}, onRemoveCover = {}, onAddChapter = {}, onEditChapterTitle = { _, _ -> },
    onEditChapterStart = { _, _ -> },
    onDeleteChapter = {}, onAddVerse = {}, onVerseTextChange = { _, _ -> }, onDeleteVerse = {},
    onMoveVerse = { _, _ -> }, onAttachVerseAudio = {}, onPlayVerseAudio = {}, onRemoveVerseAudio = {},
    onSaveDraft = {}, onCheckForProblems = {}, onRequestPublish = {},
    onConfirmPublish = {}, onDismissPublishConfirm = {}, onProblemSelected = {}, onReloadAfterConflict = {},
    onImportRequested = {}, onImportCancel = {}, onImportConfirm = {},
    onRequestClearAllVerses = {}, onDismissClearAllVerses = {}, onConfirmClearAllVerses = {},
    onOpenSplit = {}, onPreviewMatn = {}, onPreviewPause = {}, onPreviewResume = {}, onPreviewStop = {},
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
