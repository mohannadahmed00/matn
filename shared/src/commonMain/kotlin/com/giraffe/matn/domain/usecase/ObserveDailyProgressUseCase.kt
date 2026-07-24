package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.DailyProgress
import com.giraffe.matn.domain.repository.DailyGoalRepository
import com.giraffe.matn.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveDailyProgressUseCase(
    private val progressRepo: ProgressRepository,
    private val goalRepo: DailyGoalRepository,
) : FlowUseCase<Unit, DailyProgress> {
    override fun invoke(params: Unit): Flow<DailyProgress> =
        combine(progressRepo.observeTodayPracticeCount(), goalRepo.observeGoal()) { count, goal ->
            DailyProgress(count, goal)
        }
}
