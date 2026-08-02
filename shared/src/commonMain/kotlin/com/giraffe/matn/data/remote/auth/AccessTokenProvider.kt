package com.giraffe.matn.data.remote.auth

import com.giraffe.matn.core.Resource

/**
 * Supplies the bearer token for a backend call, **or `null` when the caller has no identity**
 * (student-read-contract.md §1.1).
 *
 * This exists because Phase 13's student clients read anonymously (FR-027) while `:teacherApp` still
 * authenticates. Before this, `PostgrestClient` and `StorageRestClient` both opened every method by
 * demanding a token from `TokenRefresher` and returning `Unauthorized` when there was none — so with
 * no signed-in teacher **every student read failed before a request was even issued**, in a way that
 * looks like a network error but is a missing credential (research D2).
 *
 * A `null` token is not a failure. It means "send `apikey` only, omit `Authorization`", which is
 * exactly how Postgres sees the caller as the `anon` role — the role Phase 11's `matns_read` and
 * `matn_content_published_read` policies already grant published-only access to.
 */
fun interface AccessTokenProvider {
    suspend fun currentAccessToken(): Resource<String?>
}
