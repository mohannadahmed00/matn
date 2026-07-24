package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.repository.DailyGoalRepository

class SetDailyGoalUseCase(private val repo: DailyGoalRepository) : UseCase<Int, Unit> {
    override suspend fun invoke(params: Int): Resource<Unit> = repo.setGoal(params)
}
