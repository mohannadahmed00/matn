package com.giraffe.matn.domain.secret

import com.giraffe.matn.core.Resource

/**
 * Credential-at-rest seam (FR-003a, research D7). Implemented per-OS in `:teacherApp`; faked in
 * tests. [isProtected] is `false` only for the last-resort restricted-file fallback, which the UI
 * must surface as a visible warning.
 */
interface SecretStore {
    suspend fun put(key: String, value: String): Resource<Unit>
    suspend fun get(key: String): Resource<String?>
    suspend fun clear(key: String): Resource<Unit>
    val isProtected: Boolean
}
