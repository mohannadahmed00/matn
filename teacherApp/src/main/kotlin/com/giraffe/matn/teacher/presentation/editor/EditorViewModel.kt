package com.giraffe.matn.teacher.presentation.editor

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.catalog.DraftAutosaveScheduler
import com.giraffe.matn.domain.catalog.ImportPreview
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.MatnDraftFactory
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.catalog.ValidationReport
import com.giraffe.matn.domain.catalog.VerseOrdering
import com.giraffe.matn.domain.catalog.VerseTextImport
import com.giraffe.matn.domain.error.ContentIntegrityError
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.usecase.LoadMatnForEditUseCase
import com.giraffe.matn.domain.usecase.PublishMatnUseCase
import com.giraffe.matn.domain.usecase.SaveDraftUseCase
import com.giraffe.matn.domain.usecase.UploadCoverImageUseCase
import com.giraffe.matn.domain.usecase.ValidateMatnUseCase
import com.giraffe.matn.presentation.base.BaseViewModel
import kotlinx.coroutines.launch

/** `contracts/teacher-ui-contract.md` §3.4 save-state machine. */
sealed interface SaveState {
    data object Idle : SaveState
    data object Autosaving : SaveState
    data object Saving : SaveState
    data class Saved(val at: Long) : SaveState
    data class Failed(val error: RemoteError) : SaveState
}

/** Resolved to a translated message only at render time (`TeacherStrings`) — never a raw string in
 * state, so the failure reason survives a language switch. */
enum class CoverError { UPLOAD_FAILED, INVALID_FILE }

data class EditorUiState(
    val draft: MatnDraft,
    val saveState: SaveState = SaveState.Idle,
    val missingTitle: Boolean = false,
    val missingAuthor: Boolean = false,
    val coverError: CoverError? = null,
    val validation: ValidationReport? = null,
    val focusedProblem: String? = null,
    val showPublishConfirm: Boolean = false,
    val importPreview: ImportPreview? = null,
    val importError: Boolean = false,
    val showClearAllConfirm: Boolean = false,
) {
    val isPublished: Boolean get() = draft.publicationState == PublicationState.PUBLISHED
}

/**
 * Metadata + chapter editing (US2). Verse editing is added in Phase 5, validation/publish in
 * Phase 6 — this class grows rather than being replaced.
 *
 * **A save failure must leave [EditorUiState.draft] untouched** — losing typed work is the single
 * most damaging bug this phase can ship (FR-032, SC-010).
 */
class EditorViewModel(
    initialDraft: MatnDraft,
    private val saveDraft: SaveDraftUseCase,
    private val uploadCoverImage: UploadCoverImageUseCase,
    private val validateMatn: ValidateMatnUseCase,
    private val publishMatn: PublishMatnUseCase,
    private val loadMatnForEdit: LoadMatnForEditUseCase,
    private val newId: () -> String,
    private val nowMillis: () -> Long,
) : BaseViewModel<EditorUiState>(EditorUiState(draft = initialDraft)) {

    private val autosaveScheduler = DraftAutosaveScheduler(
        scope = viewModelScope,
        nowMillis = nowMillis,
        save = ::autosave,
    )

    private fun mutateDraft(reduce: (MatnDraft) -> MatnDraft) {
        setState { it.copy(draft = reduce(it.draft), missingTitle = false, missingAuthor = false) }
        autosaveScheduler.notifyChanged(stateValue.draft)
    }

    fun onTitleChange(value: String) = mutateDraft { it.copy(title = value) }
    fun onAuthorChange(value: String) = mutateDraft { it.copy(author = value) }
    fun onDescriptionChange(value: String) = mutateDraft { it.copy(description = value) }
    fun onStructureKindChange(kind: StructureKind) = mutateDraft { it.copy(structureKind = kind) }

    fun onAddChapter(title: String) = mutateDraft { draft ->
        val order = (draft.chapters.maxOfOrNull { it.order } ?: -1) + 1
        draft.copy(chapters = draft.chapters + MatnDraftFactory.newChapter(newId = newId, title = title, order = order))
    }

    fun onEditChapterTitle(chapterId: String, title: String) = mutateDraft { draft ->
        draft.copy(chapters = draft.chapters.map { chapter -> if (chapter.id == chapterId) chapter.copy(title = title) else chapter })
    }

    /** Orphaned verses are reassigned to no chapter rather than deleted (FR-022). */
    fun onDeleteChapter(chapterId: String) = mutateDraft { draft ->
        draft.copy(
            chapters = draft.chapters.filterNot { it.id == chapterId },
            verses = draft.verses.map { verse -> if (verse.chapterId == chapterId) verse.copy(chapterId = null) else verse },
        )
    }

    // ---- Verses (US3) — every reorder/add/delete goes through VerseOrdering so numbering
    // (`displayNumber`, 1..n with no gaps) cannot drift (FR-019, FR-020). ----

    fun onAddVerse() = mutateDraft { draft ->
        val verse = MatnDraftFactory.newVerse(newId = newId, displayNumber = draft.verses.size + 1)
        draft.copy(verses = VerseOrdering.append(draft.verses, verse))
    }

    fun onVerseTextChange(verseId: String, text: String) = mutateDraft { draft ->
        draft.copy(verses = draft.verses.map { verse -> if (verse.id == verseId) verse.copy(arabicText = text) else verse })
    }

    fun onDeleteVerse(verseId: String) = mutateDraft { draft ->
        val index = draft.verses.indexOfFirst { it.id == verseId }
        if (index < 0) draft else draft.copy(verses = VerseOrdering.removeAt(draft.verses, index))
    }

    fun onMoveVerse(from: Int, to: Int) = mutateDraft { draft -> draft.copy(verses = VerseOrdering.move(draft.verses, from, to)) }

    /** Fast undo for a mistaken bulk import — wipes the whole verse list in one confirmed action
     * instead of one-by-one deletes. */
    fun onRequestClearAllVerses() = setState { it.copy(showClearAllConfirm = true) }
    fun onDismissClearAllVerses() = setState { it.copy(showClearAllConfirm = false) }
    fun onConfirmClearAllVerses() {
        setState { it.copy(showClearAllConfirm = false) }
        mutateDraft { draft -> draft.copy(verses = emptyList()) }
    }

    /** A non-existent [chapterId] is rejected — the draft is returned unchanged. */
    fun onAssignChapter(verseId: String, chapterId: String?) = mutateDraft { draft ->
        if (chapterId != null && draft.chapters.none { it.id == chapterId }) return@mutateDraft draft
        draft.copy(verses = draft.verses.map { verse -> if (verse.id == verseId) verse.copy(chapterId = chapterId) else verse })
    }

    /** US6 bulk import: preview is staged in state and nothing is written to [MatnDraft.verses]
     * until [onImportConfirm] (FR-024 — imported verses go through the same append path as
     * hand-entered ones, so numbering/ordering rules cannot diverge). */
    fun onImportRequested(bytes: ByteArray) {
        when (val result = VerseTextImport.parse(bytes)) {
            is Resource.Success -> setState { it.copy(importPreview = result.data, importError = false) }
            is Resource.Failure -> setState { it.copy(importError = true) }
        }
    }

    fun onImportCancel() = setState { it.copy(importPreview = null) }

    fun onImportConfirm() {
        val preview = stateValue.importPreview ?: return
        setState { it.copy(importPreview = null) }
        mutateDraft { draft ->
            val importedVerses = preview.lines.fold(draft.verses) { verses, text ->
                VerseOrdering.append(verses, MatnDraftFactory.newVerse(newId = newId, arabicText = text))
            }
            draft.copy(verses = importedVerses)
        }
    }

    fun onCoverPicked(bytes: ByteArray, ext: String) {
        setState { it.copy(coverError = null) }
        runUseCase(
            useCase = uploadCoverImage,
            params = UploadCoverImageUseCase.Params(stateValue.draft.id, bytes, ext),
            onSuccess = { ref -> mutateDraft { it.copy(coverImageRef = ref) } },
            onError = { setState { it.copy(coverError = CoverError.UPLOAD_FAILED) } },
        )
    }

    fun onCoverRejected() {
        setState { it.copy(coverError = CoverError.INVALID_FILE) }
    }

    fun onRemoveCover() = mutateDraft { it.copy(coverImageRef = null) }

    /** FR-018: the field-level required-check for a draft save — distinct from full validation,
     * which is the publish-time gate (`contracts/validation-contract.md` §5). `structureKind` is a
     * non-null enum defaulted by [MatnDraftFactory], so it can never be blank the way title/author
     * can; the check is kept here for FR-018 completeness.
     *
     * FR-030's second clause: a **published** matn must never be saved down to zero verses —
     * deleting every verse from a published matn would otherwise leave students an empty published
     * matn. Refused with the same [ContentIntegrityError.EmptyMatn] message the validation panel
     * already knows how to render, rather than a second ad hoc error surface. */
    fun onSaveDraft() {
        val draft = stateValue.draft
        val missingTitle = draft.title.isBlank()
        val missingAuthor = draft.author.isBlank()
        if (missingTitle || missingAuthor) {
            setState { it.copy(missingTitle = missingTitle, missingAuthor = missingAuthor) }
            return
        }
        if (draft.publicationState == PublicationState.PUBLISHED && draft.verses.isEmpty()) {
            setState {
                it.copy(validation = ValidationReport(blocking = listOf(ContentIntegrityError.EmptyMatn(draft.id)), deferred = emptyList()))
            }
            return
        }
        setState { it.copy(saveState = SaveState.Saving) }
        runUseCase(
            useCase = saveDraft,
            params = draft,
            onSuccess = { saved -> setState { it.copy(draft = saved, saveState = SaveState.Saved(nowMillis())) } },
            onError = { error -> setState { it.copy(saveState = SaveState.Failed(error as? RemoteError ?: RemoteError.Decode)) } },
        )
    }

    // ---- Validate and publish (US4) ----

    fun onCheckForProblems() {
        viewModelScope.launch {
            val report = (validateMatn(stateValue.draft) as Resource.Success).data
            setState { it.copy(validation = report) }
        }
    }

    fun onRequestPublish() = setState { it.copy(showPublishConfirm = true) }
    fun onDismissPublishConfirm() = setState { it.copy(showPublishConfirm = false) }
    fun onProblemSelected(subjectId: String) = setState { it.copy(focusedProblem = subjectId) }

    fun onConfirmPublish() {
        setState { it.copy(showPublishConfirm = false, saveState = SaveState.Saving) }
        runUseCase(
            useCase = publishMatn,
            params = stateValue.draft,
            onSuccess = { published ->
                setState { it.copy(draft = published, saveState = SaveState.Saved(nowMillis()), validation = null) }
            },
            onError = { error ->
                when (error) {
                    // Publish-time validation refused it — show the same panel `onCheckForProblems` would.
                    is ContentIntegrityError.Aggregate -> setState {
                        it.copy(saveState = SaveState.Idle, validation = ValidationReport(blocking = error.problems, deferred = emptyList()))
                    }
                    is RemoteError -> setState { it.copy(saveState = SaveState.Failed(error)) }
                    else -> setState { it.copy(saveState = SaveState.Failed(RemoteError.Decode)) }
                }
            },
        )
    }

    /** FR-037/FR-043: a conflicting save is reported, never silently overwritten. This is the
     * "Reload" action `messageFor`/`actionFor` offer for [RemoteError.Conflict] — re-fetches the
     * server's current version and replaces the on-screen draft with it. */
    fun onReloadAfterConflict() {
        setState { it.copy(saveState = SaveState.Idle) }
        runUseCase(
            useCase = loadMatnForEdit,
            params = stateValue.draft.id,
            onSuccess = { reloaded -> setState { it.copy(draft = reloaded, validation = null) } },
            onError = { error -> setState { it.copy(saveState = SaveState.Failed(error as? RemoteError ?: RemoteError.Decode)) } },
        )
    }

    private suspend fun autosave(draft: MatnDraft) {
        setState { it.copy(saveState = SaveState.Autosaving) }
        when (val result = saveDraft(draft)) {
            is Resource.Success -> setState { it.copy(draft = result.data, saveState = SaveState.Saved(nowMillis())) }
            is Resource.Failure -> setState {
                it.copy(saveState = SaveState.Failed(result.error as? RemoteError ?: RemoteError.Decode))
            }
        }
    }
}
