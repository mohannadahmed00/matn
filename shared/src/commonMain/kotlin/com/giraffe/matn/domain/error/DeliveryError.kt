package com.giraffe.matn.domain.error

import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.model.DeliveryFailure

/**
 * Domain errors surfaced by the storage/delivery use cases (FR-007, FR-011, FR-015, FR-027).
 * Every value wraps the reason the UI must state, never a raw platform code.
 */
sealed interface DeliveryError : AppError {

    /** A play/remove action targeted a matn whose audio is not installed (FR-011). */
    data class ContentNotInstalled(val matnId: String) : DeliveryError

    /** The starter matn cannot be installed or removed (FR-027). */
    data object StarterMatnNotRemovable : DeliveryError

    /** An install or remove failed; carries the retryable [failure] (research D5). */
    data class DeliveryFailed(val failure: DeliveryFailure) : DeliveryError
}