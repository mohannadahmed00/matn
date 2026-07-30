package com.giraffe.matn.domain.error

import com.giraffe.matn.core.AppError

/**
 * Backend-access failures for the teacher tool's REST layer (`data-model.md` §10).
 * [retryable] is what FR-005's messaging and FR-032's publish-failure reporting key off.
 */
sealed interface RemoteError : AppError {
    val retryable: Boolean

    /** No connection, DNS failure, or timeout. */
    data object Network : RemoteError {
        override val retryable: Boolean = true
    }

    /** Missing, expired, or rejected credential. */
    data object Unauthorized : RemoteError {
        override val retryable: Boolean = false
    }

    /** Authenticated, but row-level security refused the operation. */
    data object Forbidden : RemoteError {
        override val retryable: Boolean = false
    }

    /** The `revision` precondition failed (research D4) — someone else saved first. */
    data object Conflict : RemoteError {
        override val retryable: Boolean = false
    }

    /** Storage or write quota exhausted. */
    data object QuotaExceeded : RemoteError {
        override val retryable: Boolean = false
    }

    /** A 5xx response. */
    data object Server : RemoteError {
        override val retryable: Boolean = true
    }

    /** The response did not match the expected shape. */
    data object Decode : RemoteError {
        override val retryable: Boolean = false
    }
}
