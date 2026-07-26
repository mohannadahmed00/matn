package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.NoteEntry
import com.giraffe.matn.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow

@org.koin.core.annotation.Factory
class ObserveNotesUseCase(private val repo: NoteRepository) : FlowUseCase<Unit, List<NoteEntry>> {
    override fun invoke(params: Unit): Flow<List<NoteEntry>> = repo.observeAll()
}
