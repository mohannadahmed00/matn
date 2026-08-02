package com.giraffe.matn.domain.model

/**
 * Per-matn storage breakdown entry for the Settings tab (data-model §3.1). [bytes] is the
 * [ContentAvailability.Downloaded.occupiedBytes] figure — the measured size of
 * `downloads/{matnId}/`.
 *
 * Phase 13 removed `isStarter`: FR-031 forbids any item being exempt from removal, so there is no
 * longer a row that renders a "part of the app" label or suppresses its remove action.
 */
data class MatnStorageEntry(
    val matnId: String,
    val title: String,
    val bytes: Long,
)

/**
 * Aggregate storage usage shown in the Settings tab (data-model §3.2). [entries] covers downloaded
 * متون only, ordered by [bytes] DESC (FR-030). [totalUsedBytes] is `Σ entries.bytes`.
 * [freeSpaceBytes] is the device's remaining space.
 *
 * **Cached cover images are excluded** (FR-012, Clarification 3): they live outside `downloads/`, so
 * the removal path cannot touch them and this figure cannot count them. That is what keeps SC-009's
 * "remove all leaves 0 bytes" honest — every byte reported here is a byte removal actually reclaims.
 *
 * Phase 13 removed `onDemandUsedBytes`: with nothing exempt from removal it was always equal to
 * [totalUsedBytes], so the Settings zero state keys off [totalUsedBytes] directly.
 */
data class StorageUsage(
    val entries: List<MatnStorageEntry>,
    val totalUsedBytes: Long,
    val freeSpaceBytes: Long,
)
