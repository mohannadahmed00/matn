package com.giraffe.matn.domain.error

import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.model.DeliveryFailure

/**
 * Domain errors surfaced by the download/storage use cases (FR-019, FR-020, FR-021, FR-043).
 * Every value wraps the reason the UI must state, never a raw platform code.
 *
 * Phase 13 removed `StarterMatnNotRemovable`: FR-031 and FR-040 delete the permanently-present
 * starter matn from the product, so there is no longer an item that can refuse removal.
 */
sealed interface DeliveryError : AppError {

    /** A play action targeted a matn that is not downloaded (FR-021) — the UI must answer this
     *  with an actionable download prompt, never a silent failure. */
    data class ContentNotDownloaded(val matnId: String) : DeliveryError

    /** A download or removal failed; carries the retryable [failure] (FR-017). */
    data class DeliveryFailed(val failure: DeliveryFailure) : DeliveryError
}
