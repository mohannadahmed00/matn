package com.giraffe.matn.domain.model

/**
 * Per-matn storage breakdown entry for the Settings tab (data-model §3.1). [bytes] is the
 * `ContentAvailability.Installed.occupiedBytes` figure (per data-model §2.1's three-step
 * priority). [isStarter] drives the "part of the app" label and suppresses the remove action
 * (FR-027).
 */
data class MatnStorageEntry(
    val matnId: String,
    val title: String,
    val bytes: Long,
    val isStarter: Boolean,
)

/**
 * Aggregate storage usage shown in the Settings tab (data-model §3.2). [entries] covers installed
 * متون only, ordered by [bytes] DESC (FR-025). [totalUsedBytes] is `Σ entries.bytes` including the
 * starter, so it stays honest against the device's real footprint (SC-003). [onDemandUsedBytes]
 * excludes the starter, and `== 0L` is what drives the Settings zero state (FR-028). [freeSpaceBytes]
 * is the device's remaining space (FR-030).
 */
data class StorageUsage(
    val entries: List<MatnStorageEntry>,
    val totalUsedBytes: Long,
    val onDemandUsedBytes: Long,
    val freeSpaceBytes: Long,
)