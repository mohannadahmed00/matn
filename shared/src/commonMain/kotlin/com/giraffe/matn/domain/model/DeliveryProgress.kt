package com.giraffe.matn.domain.model

/**
 * Live progress of an in-flight content transfer (data-model §2.2). [totalBytes] is the catalog
 * overview's `download_size_bytes` — always present offline, so progress never waits on a size
 * lookup (spec Assumptions). [fraction] is the derived progress in `[0f, 1f]` and is `0f` when
 * [totalBytes] is non-positive, so a zero-audio matn never divides by zero.
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
 * Phases of a transfer. Phase 13 removed `WAITING_FOR_NETWORK_POLICY` and `REQUIRES_CONFIRMATION`:
 * both described Play Asset Delivery parking states, and plain HTTP has no equivalent — a transfer
 * is either about to start or moving bytes (data-model §2.4). A transfer that is *accepted but not
 * started* is not a phase at all; it is `ContentAvailability.Queued`.
 */
enum class DeliveryPhase {
    PENDING,
    TRANSFERRING,
}