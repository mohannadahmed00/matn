package com.giraffe.matn.domain.model

/**
 * Live progress of an in-flight content transfer (data-model §2.2). [totalBytes] is the live
 * platform size when known, else the declared catalog size (research D4). [fraction] is the derived
 * progress in `[0f, 1f]` and is `0f` when [totalBytes] is non-positive, so a zero-audio matn never
 * divides by zero. [phase] lets a parked Play transfer (`WAITING_FOR_WIFI`,
 * `REQUIRES_USER_CONFIRMATION`) render as an explained wait rather than a frozen bar (SC-007).
 */
data class DeliveryProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val phase: DeliveryPhase,
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f)
}

/**
 * Phases of a transfer (research D11). `PENDING`/`TRANSFERRING` are the normal flow; the two wait
 * phases are platform-side parking, not failures — the UI MUST render them distinctly.
 */
enum class DeliveryPhase {
    PENDING,
    TRANSFERRING,
    WAITING_FOR_NETWORK_POLICY,
    REQUIRES_CONFIRMATION,
}