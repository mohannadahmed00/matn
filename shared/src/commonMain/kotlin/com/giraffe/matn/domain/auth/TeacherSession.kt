package com.giraffe.matn.domain.auth

/** `data-model.md` §8. [accessToken] is in-memory only; only [refreshToken] is persisted (FR-003a). */
data class TeacherSession(
    val uid: String,
    val displayName: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Long,
)
