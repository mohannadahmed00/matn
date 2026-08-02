package com.giraffe.matn.domain.catalog

/**
 * When the catalog was last successfully synced, and whether the most recent attempt failed
 * (data-model §2.6).
 *
 * This is the basis for FR-044's three-way distinction, which the student must be able to tell
 * apart on screen:
 *
 * | State | Meaning |
 * |---|---|
 * | `!hasEverSynced` | never reached the catalog — show connect-to-browse, never an empty grid |
 * | `hasEverSynced` + zero overviews | reached it and the teacher has published nothing |
 * | overviews exist, none downloaded | "you have not downloaded anything yet" |
 *
 * It is also what the 1-hour staleness window is measured against (FR-006).
 */
data class CatalogSyncState(
    val lastSuccessAtMillis: Long?,
    val lastAttemptFailed: Boolean,
) {
    val hasEverSynced: Boolean get() = lastSuccessAtMillis != null
}
