package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.auth.TeacherAuthRepository
import com.giraffe.matn.domain.auth.TeacherSession

class SignInUseCase(private val repository: TeacherAuthRepository) : UseCase<SignInUseCase.Params, TeacherSession> {
    data class Params(val email: String, val password: String)

    override suspend fun invoke(params: Params): Resource<TeacherSession> =
        repository.signIn(params.email, params.password)
}
