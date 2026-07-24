package com.giraffe.matn.domain.error

import com.giraffe.matn.core.AppError

/** FR-019: blank/whitespace-only note text is a domain error, never persisted as `""`. */
sealed interface NoteError : AppError {
    data object EmptyNote : NoteError
}
