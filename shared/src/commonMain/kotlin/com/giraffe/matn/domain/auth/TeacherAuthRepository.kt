package com.giraffe.matn.domain.auth

import com.giraffe.matn.core.Resource
import kotlinx.coroutines.flow.Flow

/** `data-model.md` §9. */
interface TeacherAuthRepository {
    suspend fun signIn(email: String, password: String): Resource<TeacherSession>
    suspend fun restoreSession(): Resource<TeacherSession>
    suspend fun signOut(): Resource<Unit>
    fun observeSession(): Flow<TeacherSession?>
}
