package com.giraffe.matn.presentation

import com.giraffe.matn.domain.catalog.CatalogSyncState
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.presentation.home.HomeUiState
import com.giraffe.matn.presentation.home.LibraryFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Library screen's derived state (Matn Design System §05): the chip filter and the header's
 * "N downloaded · X" summary. All pure functions of [HomeUiState], so they are worth pinning here
 * rather than only through the ViewModel.
 */
class LibraryUiStateTest {

    private fun summary(id: String) = MatnSummary(
        matn = Matn(id, "متن $id", "مؤلف", "", null, StructureKind.SIMPLE),
        verseCount = 4,
        totalDurationMs = 31_300,
        declaredSizeBytes = 2_400_000,
    )

    private val populated = HomeUiState(
        isLoading = false,
        items = listOf(summary("m1"), summary("m2"), summary("m3")),
        availability = mapOf(
            "m1" to ContentAvailability.Downloaded(20_000_000),
            "m2" to ContentAvailability.Downloaded(16_000_000),
            "m3" to ContentAvailability.NotDownloaded(),
        ),
        syncState = CatalogSyncState(lastSuccessAtMillis = 1_000L, lastAttemptFailed = false),
    )

    @Test
    fun all_is_the_default_and_shows_the_whole_catalog() {
        assertEquals(LibraryFilter.ALL, populated.filter)
        assertEquals(listOf("m1", "m2", "m3"), populated.visibleItems.map { it.matn.id })
    }

    @Test
    fun on_device_keeps_only_downloaded_matns() {
        val filtered = populated.copy(filter = LibraryFilter.ON_DEVICE)

        assertEquals(listOf("m1", "m2"), filtered.visibleItems.map { it.matn.id })
    }

    /** A matn whose availability has not resolved yet is not "on device" — silence is not presence. */
    @Test
    fun on_device_excludes_matns_with_unresolved_availability() {
        val filtered = populated.copy(
            availability = emptyMap(),
            filter = LibraryFilter.ON_DEVICE,
        )

        assertTrue(filtered.visibleItems.isEmpty())
    }

    /** Downloading is not downloaded — a partial transfer must not appear under "On device". */
    @Test
    fun on_device_excludes_in_flight_downloads() {
        val filtered = populated.copy(
            availability = mapOf(
                "m1" to ContentAvailability.Downloading(
                    com.giraffe.matn.domain.model.DeliveryProgress(
                        bytesTransferred = 1_000,
                        totalBytes = 2_000,
                        phase = com.giraffe.matn.domain.model.DeliveryPhase.TRANSFERRING,
                    ),
                ),
                "m2" to ContentAvailability.Queued,
                "m3" to ContentAvailability.NotDownloaded(),
            ),
            filter = LibraryFilter.ON_DEVICE,
        )

        assertTrue(filtered.visibleItems.isEmpty())
    }

    @Test
    fun summary_counts_downloaded_matns_and_sums_their_occupied_bytes() {
        assertEquals(2, populated.downloadedCount)
        assertEquals(36_000_000L, populated.downloadedBytes)
        assertTrue(populated.showDownloadSummary)
    }

    /** "0 downloaded · 0 B" is noise; the nothing-downloaded notice already says it with a next step. */
    @Test
    fun summary_is_suppressed_when_nothing_is_downloaded() {
        val none = populated.copy(
            availability = mapOf("m1" to ContentAvailability.NotDownloaded()),
        )

        assertEquals(0, none.downloadedCount)
        assertFalse(none.showDownloadSummary)
        assertTrue(none.showNothingDownloaded)
    }

    /** A filter hiding everything is not an empty library — the two states offer different exits. */
    @Test
    fun filtering_everything_away_is_distinct_from_an_empty_library() {
        val filtered = populated.copy(
            availability = mapOf("m1" to ContentAvailability.NotDownloaded()),
            filter = LibraryFilter.ON_DEVICE,
        )

        assertTrue(filtered.showFilteredEmpty)
        assertFalse(filtered.showConnectPrompt)
        assertFalse(filtered.showEmptyCatalog)
    }

    @Test
    fun an_empty_catalog_never_reports_a_filtered_empty() {
        val empty = HomeUiState(
            isLoading = false,
            filter = LibraryFilter.ON_DEVICE,
            syncState = CatalogSyncState(lastSuccessAtMillis = 1_000L, lastAttemptFailed = false),
        )

        assertFalse(empty.showFilteredEmpty)
        assertTrue(empty.showEmptyCatalog)
    }

    /** Nothing is derived while the first emission is still pending. */
    @Test
    fun a_loading_library_reports_neither_empty_state() {
        val loading = HomeUiState(isLoading = true, filter = LibraryFilter.ON_DEVICE)

        assertFalse(loading.showFilteredEmpty)
        assertFalse(loading.showConnectPrompt)
        assertFalse(loading.showNothingDownloaded)
    }
}
