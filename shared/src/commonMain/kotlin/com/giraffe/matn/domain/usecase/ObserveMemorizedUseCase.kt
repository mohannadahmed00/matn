package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.MemorizedEntry
import com.giraffe.matn.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow

/** The Saved tab's *Memorized* segment — mirrors [ObserveBookmarksUseCase] / [ObserveNotesUseCase]. */
@org.koin.core.annotation.Factory
class ObserveMemorizedUseCase(private val repo: ProgressRepository) : FlowUseCase<Unit, List<MemorizedEntry>> {
    override fun invoke(params: Unit): Flow<List<MemorizedEntry>> = repo.observeMemorized()
}
