package com.giraffe.matn.domain.model

import com.giraffe.matn.domain.error.RemoteError

/**
 * The retryable reason a transfer resolved to `NotDownloaded` (FR-017, data-model §2.3). Every
 * value carries something the UI can state and act on — FR-043 forbids a raw error code or a silent
 * no-op reaching the student, so `DeliveryFailureCopy` must map every value here to a message and a
 * next action.
 */
sealed interface DeliveryFailure {

    /** No usable connectivity when the download was attempted (FR-020). */
    data object NoConnectivity : DeliveryFailure

    /**
     * Free space below what the download needs (FR-019). Both figures are carried so the message
     * can state required against available.
     */
    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : DeliveryFailure

    /** The student cancelled, either while downloading or while queued (FR-017). */
    data object Cancelled : DeliveryFailure

    /**
     * The backend refused or failed mid-transfer. Wraps Phase 11's already-mapped [RemoteError]
     * rather than a raw code, so the message can distinguish "no connection" from "server problem"
     * and honour [RemoteError.retryable] (FR-043).
     */
    data class Remote(val error: RemoteError) : DeliveryFailure

    /**
     * The matn stopped being published mid-download, or its objects are missing at the source
     * (spec edge case: "a matn is unpublished while the student is downloading it"). The transfer
     * fails cleanly to not-downloaded — never a matn that half-exists.
     */
    data object SourceUnavailable : DeliveryFailure
}
