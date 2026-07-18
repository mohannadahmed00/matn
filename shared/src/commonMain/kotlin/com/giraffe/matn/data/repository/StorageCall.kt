package com.giraffe.matn.data.repository

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a blocking SQLDelight query off the caller's thread (Dispatchers.Default)
 * and wraps the outcome in a [Resource].
 *
 * - Offloading keeps synchronous DB work off `Dispatchers.Main` (no UI jank/ANR).
 * - `CancellationException` is rethrown so structured-concurrency cancellation is
 *   never swallowed into a spurious [AppError.Storage].
 */
internal suspend fun <T> storageCall(
    errorMessage: () -> String,
    block: () -> T,
): Resource<T> = withContext(Dispatchers.Default) {
    try {
        Resource.Success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Resource.Failure(AppError.Storage(t.message ?: errorMessage()))
    }
}
