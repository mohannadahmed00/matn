package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.audio.PreviewPlayer
import com.giraffe.matn.domain.audio.PreviewVerse
import com.giraffe.matn.domain.catalog.MatnDraft

/**
 * Builds the matn's [PreviewVerse] queue in display order and starts [PreviewPlayer] from
 * [Params.startVerseId] (or the first verse). A verse with no `fileRef` is **not** skipped —
 * [PreviewPlayer] reports it as `PreviewState.MissingAudio` naming its display number (FR-025),
 * rather than the queue silently omitting it.
 */
class PreviewMatnAudioUseCase(
    private val previewPlayer: PreviewPlayer,
) : UseCase<PreviewMatnAudioUseCase.Params, Unit> {

    data class Params(val draft: MatnDraft, val startVerseId: String? = null)

    override suspend fun invoke(params: Params): Resource<Unit> {
        val verses = params.draft.verses.map { PreviewVerse(it.id, it.displayNumber, it.audio?.fileRef) }
        val startIndex = params.startVerseId
            ?.let { id -> verses.indexOfFirst { it.verseId == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        previewPlayer.play(verses, startIndex)
        return Resource.Success(Unit)
    }
}
