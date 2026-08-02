package com.giraffe.matn.data.remote.auth

import com.giraffe.matn.core.Resource

/**
 * The student clients' token provider: there is no token, and that is the design (FR-027).
 *
 * Students must not need an account, sign-in, or any credential to browse the catalog or download
 * content. No embedded key either — one shipped in a client binary is trivially extractable, so it
 * would add secret-management cost without adding protection (spec Clarifications 2026-08-02).
 * Authorisation happens server-side against the `published` flag, not against a caller identity.
 */
class AnonymousAccessTokenProvider : AccessTokenProvider {
    override suspend fun currentAccessToken(): Resource<String?> = Resource.Success(null)
}
