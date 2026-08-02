package com.giraffe.matn.data.catalog

import com.giraffe.matn.domain.catalog.CatalogOverview

/**
 * What a sync should change, decided as a **pure function** of three inputs (data-model §4.2).
 *
 * No database, no network, no clock — so every row of the reconciliation table is unit-testable
 * without a fake of anything (`CatalogReconcilerTest`). The caller applies [Plan] in one
 * transaction; deciding and applying are deliberately separate, because that is what lets a failed
 * sync change nothing at all (FR-007).
 */
data class ReconciliationPlan(
    val upsert: List<CatalogOverview>,
    val delete: List<String>,
    val markWithdrawn: List<String>,
)

object CatalogReconciler {

    /**
     * @param remote every published overview the sync fetched. Must be a **complete** successful
     *   response — a partial list here would delete متون that merely did not arrive.
     * @param local the overviews currently stored.
     * @param downloadedIds matns whose content is on the device. This is what makes withdrawal
     *   two-outcome rather than one.
     */
    fun reconcile(
        remote: List<CatalogOverview>,
        local: List<CatalogOverview>,
        downloadedIds: Set<String>,
    ): ReconciliationPlan {
        val remoteIds = remote.map { it.matnId }.toSet()
        val localById = local.associateBy { it.matnId }

        // Present remotely: insert if new, update in place if the revision moved. An update never
        // touches the student's downloaded copy — it only re-points the overview, which is what
        // makes the matn *flag* an available update instead of silently replacing content (FR-010,
        // FR-011).
        val upsert = remote.filter { incoming ->
            val existing = localById[incoming.matnId]
            existing == null || existing.revision != incoming.revision || existing.withdrawn
        }

        // Absent remotely — the matn was unpublished. Two different outcomes, and which one applies
        // depends entirely on whether the student already has it.
        val vanished = local.filter { it.matnId !in remoteIds }

        // Not downloaded: it simply disappears from the catalog (FR-008).
        val delete = vanished.filter { it.matnId !in downloadedIds }.map { it.matnId }

        // Downloaded: the overview STAYS, flagged withdrawn (FR-009). Deleting it would strip the
        // title, author and cover from a matn the student can still read offline — withdrawal
        // removes what can be newly downloaded, never what someone already has.
        val markWithdrawn = vanished
            .filter { it.matnId in downloadedIds && !it.withdrawn }
            .map { it.matnId }

        return ReconciliationPlan(upsert = upsert, delete = delete, markWithdrawn = markWithdrawn)
    }
}
