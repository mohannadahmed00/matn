package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.auth.TeacherAuthRepository
import com.giraffe.matn.domain.auth.TeacherSession

/** No stored session is a normal outcome (`null`), not an error the launch screen should surface. */
class RestoreSessionUseCase(private val repository: TeacherAuthRepository) : UseCase<Unit, TeacherSession?> {
    override suspend fun invoke(params: Unit): Resource<TeacherSession?> =
        when (val result = repository.restoreSession()) {
            is Resource.Success -> Resource.Success(result.data)
            is Resource.Failure -> Resource.Success(null)
        }
}
