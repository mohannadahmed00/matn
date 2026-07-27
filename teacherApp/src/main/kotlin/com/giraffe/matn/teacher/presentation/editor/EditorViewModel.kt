package com.giraffe.matn.teacher.presentation.editor

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.catalog.DraftAutosaveScheduler
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.MatnDraftFactory
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.usecase.SaveDraftUseCase
import com.giraffe.matn.domain.usecase.UploadCoverImageUseCase
import com.giraffe.matn.presentation.base.BaseViewModel
import androidx.lifecycle.viewModelScope

/** `contracts/teacher-ui-contract.md` §3.4 save-state machine. */
sealed interface SaveState {
    data object Idle : SaveState
    data object Autosaving : SaveState
    data object Saving : SaveState
    data class Saved(val at: Long) : SaveState
    data class Failed(val error: RemoteError) : SaveState
}

data class EditorUiState(
    val draft: MatnDraft,
    val saveState: SaveState = SaveState.Idle,
    val missingTitle: Boolean = false,
    val missingAuthor: Boolean = false,
    val coverError: String? = null,
)

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

    fun onCoverPicked(bytes: ByteArray, ext: String) {
        setState { it.copy(coverError = null) }
        runUseCase(
            useCase = uploadCoverImage,
            params = UploadCoverImageUseCase.Params(stateValue.draft.id, bytes, ext),
            onSuccess = { ref -> mutateDraft { it.copy(coverImageRef = ref) } },
            onError = { setState { it.copy(coverError = "cover-upload-failed") } },
        )
    }

    fun onRemoveCover() = mutateDraft { it.copy(coverImageRef = null) }

    /** FR-018: the field-level required-check for a draft save — distinct from full validation,
     * which is the publish-time gate (`contracts/validation-contract.md` §5). `structureKind` is a
     * non-null enum defaulted by [MatnDraftFactory], so it can never be blank the way title/author
     * can; the check is kept here for FR-018 completeness. */
    fun onSaveDraft() {
        val draft = stateValue.draft
        val missingTitle = draft.title.isBlank()
        val missingAuthor = draft.author.isBlank()
        if (missingTitle || missingAuthor) {
            setState { it.copy(missingTitle = missingTitle, missingAuthor = missingAuthor) }
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
