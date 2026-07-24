package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.Note
import com.giraffe.matn.domain.repository.NoteRepository

class GetNoteUseCase(private val repo: NoteRepository) : UseCase<String, Note?> {
    override suspend fun invoke(params: String): Resource<Note?> = repo.get(params)
}
