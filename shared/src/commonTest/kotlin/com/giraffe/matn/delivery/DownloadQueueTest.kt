package com.giraffe.matn.delivery

import com.giraffe.matn.data.delivery.ContentFileStore
import com.giraffe.matn.data.delivery.DownloadedContentRepositoryImpl
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * T068 — the one-at-a-time download queue (FR-015, FR-018, FR-032, Clarification 4).
 *
 * Everything here hinges on [FakeContentDeliveryEngine.holdGate]: without a way to hold a transfer
 * open, every download would finish before the next was requested and the queue would be
 * unobservable — the test would pass whether or not the queue existed.
 */
class DownloadQueueTest {

    private val first = "matn-first"
    private val second = "matn-second"

    private class Harness(
        val db: ContentDatabase,
        val engine: FakeContentDeliveryEngine,
        val repo: DownloadedContentRepositoryImpl,
    )

    private fun newHarness(scope: TestScope): Harness {
        val db = newTestDatabase()
        val engine = FakeContentDeliveryEngine()
        val storage = FakeDeviceStorage()
        val repo = DownloadedContentRepositoryImpl(
            db = db,
            engine = engine,
            storage = storage,
            files = ContentFileStore(storage),
            scope = scope,
        )
        listOf(first, second).forEach { db.insertOverview(it) }
        return Harness(db, engine, repo)
    }

    @Test
    fun `a second request is queued - not transferred - until the first settles`() = runTest {
        val h = newHarness(this)
        h.engine.holdGate(first)

        h.repo.download(first)
        h.repo.download(second)
        runCurrent()

        // The first is genuinely in flight; the second has NOT been handed to the engine.
        assertIs<ContentAvailability.Downloading>(h.repo.observeAvailability(first).first())
        assertEquals(ContentAvailability.Queued, h.repo.observeAvailability(second).first())
        assertEquals(listOf(first), h.engine.downloadInvocations)

        h.engine.releaseGate(first)
        runCurrent()

        // Only now does the queued one start, and both end up downloaded (US2 scenario 10).
        assertEquals(listOf(first, second), h.engine.downloadInvocations)
        assertIs<ContentAvailability.Downloaded>(h.repo.observeAvailability(first).first())
        assertIs<ContentAvailability.Downloaded>(h.repo.observeAvailability(second).first())
    }

    @Test
    fun `cancelling a queued matn leaves the running one untouched`() = runTest {
        val h = newHarness(this)
        h.engine.holdGate(first)

        h.repo.download(first)
        h.repo.download(second)
        runCurrent()

        h.repo.cancel(second)
        runCurrent()

        // US2 scenario 11: nothing was ever transferred for the cancelled one.
        assertIs<ContentAvailability.NotDownloaded>(h.repo.observeAvailability(second).first())
        assertTrue(second !in h.engine.downloadInvocations)
        assertIs<ContentAvailability.Downloading>(h.repo.observeAvailability(first).first())

        h.engine.releaseGate(first)
        runCurrent()
        assertIs<ContentAvailability.Downloaded>(h.repo.observeAvailability(first).first())
    }

    @Test
    fun `removing a queued matn drops it from the queue and transfers nothing`() = runTest {
        val h = newHarness(this)
        h.engine.holdGate(first)

        h.repo.download(first)
        h.repo.download(second)
        runCurrent()

        h.repo.remove(second) // FR-032
        runCurrent()
        h.engine.releaseGate(first)
        runCurrent()

        assertTrue(second !in h.engine.downloadInvocations)
        assertIs<ContentAvailability.NotDownloaded>(h.repo.observeAvailability(second).first())
    }

    @Test
    fun `a duplicate request while queued is a no-op`() = runTest {
        val h = newHarness(this)
        h.engine.holdGate(first)

        h.repo.download(first)
        h.repo.download(second)
        h.repo.download(second) // duplicate
        runCurrent()

        h.engine.releaseGate(first)
        runCurrent()

        // The engine saw each matn exactly once, never twice.
        assertEquals(listOf(first, second), h.engine.downloadInvocations)
    }

    @Test
    fun `the queue keeps draining without the caller awaiting it`() = runTest {
        // FR-018: the pump runs on the injected application scope, not the caller's. Enqueue and
        // return immediately — the transfers still happen.
        val h = newHarness(this)
        h.repo.download(first)
        h.repo.download(second)
        runCurrent()

        assertEquals(listOf(first, second), h.engine.downloadInvocations)
    }
}

/** Minimal catalog row — the repository derives library-wide state from `catalog_overview`. */
internal fun ContentDatabase.insertOverview(matnId: String, sizeBytes: Long = 1_000L) {
    contentQueries.upsertCatalogOverview(
        matn_id = matnId,
        title = "title-$matnId",
        author = "author",
        description = "desc",
        cover_image_ref = null,
        structure_kind = "SIMPLE",
        verse_count = 1,
        download_size_bytes = sizeBytes,
        audio_completeness = "COMPLETE",
        revision = 1,
        withdrawn = 0,
    )
}
