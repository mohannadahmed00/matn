package com.giraffe.matn.data.remote

import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.error.RemoteError
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** HTTP status → [RemoteError] mapping (`contracts/rest-contract.md` §4.1). */
object RemoteErrorMapper {

    fun mapHttpError(status: Int, body: String?): AppError {
        val code = body?.let { runCatching { postgrestErrorCode(it) }.getOrNull() }
        return when {
            // A duplicate primary key: the "create only if absent" path found the matn already
            // there. PostgREST reports the SQLSTATE, and 409 alone can also mean a FK conflict.
            code == UNIQUE_VIOLATION -> RemoteError.Conflict
            // An insert or update blocked by row-level security. PostgREST answers 403 for a
            // `WITH CHECK` violation; the SQLSTATE is what names it unambiguously.
            code == INSUFFICIENT_PRIVILEGE -> RemoteError.Forbidden
            status == 401 -> RemoteError.Unauthorized
            status == 403 -> RemoteError.Forbidden
            status == 404 -> AppError.NotFound
            status == 409 -> RemoteError.Conflict
            status == 413 -> RemoteError.QuotaExceeded
            status == 429 -> RemoteError.QuotaExceeded
            status in 500..599 -> RemoteError.Server
            // Everything else the server refused — chiefly a 400 from Storage when the bucket's
            // `allowed_mime_types` does not admit the upload. Reporting that as `Decode` blamed the
            // response for being malformed when it was in fact a clear, well-formed refusal.
            status in 400..499 -> RemoteError.Rejected
            else -> RemoteError.Decode
        }
    }

    /** Rethrows [CancellationException] untouched; every other throwable is a connection failure. */
    fun mapThrowable(t: Throwable): RemoteError {
        if (t is CancellationException) throw t
        return RemoteError.Network
    }

    /** PostgREST errors are `{"code","details","hint","message"}`, where `code` is the Postgres
     * SQLSTATE (or a `PGRST…` code for errors PostgREST raises itself). */
    private fun postgrestErrorCode(body: String): String? =
        Json.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.contentOrNull

    private const val UNIQUE_VIOLATION = "23505"
    private const val INSUFFICIENT_PRIVILEGE = "42501"
}
