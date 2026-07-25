package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.repository.NoteRepository

@org.koin.core.annotation.Factory
class DeleteNoteUseCase(private val repo: NoteRepository) : UseCase<String, Unit> {
    override suspend fun invoke(params: String): Resource<Unit> = repo.delete(params)
}
