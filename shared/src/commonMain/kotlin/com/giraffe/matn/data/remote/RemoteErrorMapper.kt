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
        val firestoreStatus = body?.let { runCatching { firestoreErrorStatus(it) }.getOrNull() }
        return when {
            status == 400 && firestoreStatus == "FAILED_PRECONDITION" -> RemoteError.Conflict
            status == 400 -> RemoteError.Decode
            status == 401 -> RemoteError.Unauthorized
            status == 403 -> RemoteError.Forbidden
            status == 404 -> AppError.NotFound
            status == 429 -> RemoteError.QuotaExceeded
            status in 500..599 -> RemoteError.Server
            else -> RemoteError.Decode
        }
    }

    /** Rethrows [CancellationException] untouched; every other throwable is a connection failure. */
    fun mapThrowable(t: Throwable): RemoteError {
        if (t is CancellationException) throw t
        return RemoteError.Network
    }

    private fun firestoreErrorStatus(body: String): String? =
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
}
