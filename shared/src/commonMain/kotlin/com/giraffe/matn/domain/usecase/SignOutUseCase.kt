package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.auth.TeacherAuthRepository

class SignOutUseCase(private val repository: TeacherAuthRepository) : UseCase<Unit, Unit> {
    override suspend fun invoke(params: Unit): Resource<Unit> = repository.signOut()
}
