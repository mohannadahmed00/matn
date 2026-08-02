package com.giraffe.matn.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.catalog.StudentCatalogRepositoryImpl
import com.giraffe.matn.data.delivery.ContentFileStore
import com.giraffe.matn.data.delivery.DownloadedContentRepositoryImpl
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.usecase.DownloadMatnUseCase
import com.giraffe.matn.newTestDatabase
import com.giraffe.matn.remote.throwingPostgrestClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * T071 — [DownloadMatnUseCase]'s ordered preconditions (delivery-contract.md §4).
 *
 * Rewritten from `InstallMatnContentUseCaseTest`, which drove the old bundled-content path through
 * `ContentSeedLoaderImpl`. The starter case it used to assert (`refuses to install the starter`) is
 * gone with the starter itself (FR-040).
 */
class DownloadMatnUseCaseTest {

    private val matnId = "matn-1"
    private val sizeBytes = 5_000L

    private class Harness(
        val engine: FakeContentDeliveryEngine,
        val storage: FakeDeviceStorage,
        val repo: DownloadedContentRepositoryImpl,
        val useCase: DownloadMatnUseCase,
    )

    private fun newHarness(scope: TestScope): Harness {
        val db = newTestDatabase()
        val engine = FakeContentDeliveryEngine()
        val storage = FakeDeviceStorage()
        val files = ContentFileStore(storage)
        val repo = DownloadedContentRepositoryImpl(db, engine, storage, files, scope)
        db.insertOverview(matnId, sizeBytes = sizeBytes)
        // The catalog is the size source (spec Assumptions): always present offline, so the
        // free-space check never waits on the network.
        val catalog = StudentCatalogRepositoryImpl(
            db = db,
            postgrest = throwingPostgrestClient(),
            nowMillis = { 0L },
        )
        return Harness(engine, storage, repo, DownloadMatnUseCase(repo, catalog, storage))
    }

    @Test
    fun `enqueues when preconditions pass`() = runTest {
        val h = newHarness(this)
        assertIs<Resource.Success<*>>(h.useCase(matnId))
        runCurrent()
        assertEquals(listOf(matnId), h.engine.downloadInvocations)
    }

    @Test
    fun `refuses when free space is below the published size - stating both figures`() = runTest {
        val h = newHarness(this)
        h.storage.freeSpace = sizeBytes - 1

        val out = h.useCase(matnId)
        val failure = assertIs<Resource.Failure>(out)
        val error = assertIs<DeliveryError.DeliveryFailed>(failure.error)
        val reason = assertIs<DeliveryFailure.InsufficientStorage>(error.failure)

        // FR-019 requires the message to state required AGAINST available, so both must survive.
        assertEquals(sizeBytes, reason.requiredBytes)
        assertEquals(sizeBytes - 1, reason.availableBytes)
        // …and nothing was enqueued: a doomed download never occupies the queue.
        runCurrent()
        assertTrue(h.engine.downloadInvocations.isEmpty())
    }

    @Test
    fun `a duplicate request for an already downloaded matn is a no-op`() = runTest {
        val h = newHarness(this)
        h.engine.completeDownload(matnId, occupiedBytes = sizeBytes)

        assertIs<Resource.Success<*>>(h.useCase(matnId))
        runCurrent()
        assertTrue(h.engine.downloadInvocations.isEmpty())
        assertIs<ContentAvailability.Downloaded>(h.repo.observeAvailability(matnId).first())
    }

    @Test
    fun `a matn with no published size skips the space guard rather than refusing`() = runTest {
        val db = newTestDatabase()
        val engine = FakeContentDeliveryEngine()
        val storage = FakeDeviceStorage()
        storage.freeSpace = 0L
        val repo = DownloadedContentRepositoryImpl(db, engine, storage, ContentFileStore(storage), this)
        db.insertOverview(matnId, sizeBytes = 0L)
        val catalog = StudentCatalogRepositoryImpl(db, throwingPostgrestClient(), { 0L })

        // 0 means "the teacher published no measurement", not "needs zero bytes" — refusing here
        // would make an unmeasured matn permanently undownloadable.
        assertIs<Resource.Success<*>>(DownloadMatnUseCase(repo, catalog, storage)(matnId))
        runCurrent()
        assertEquals(listOf(matnId), engine.downloadInvocations)
    }
}
