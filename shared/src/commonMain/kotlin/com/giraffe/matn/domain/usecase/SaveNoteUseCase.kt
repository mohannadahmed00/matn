package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.Note
import com.giraffe.matn.domain.repository.NoteRepository

data class SaveNoteParams(val verseId: String, val text: String)

class SaveNoteUseCase(private val repo: NoteRepository) : UseCase<SaveNoteParams, Note> {
    override suspend fun invoke(params: SaveNoteParams): Resource<Note> = repo.save(params.verseId, params.text)
}
