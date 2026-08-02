package com.giraffe.matn.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.delivery.ContentFileStore
import com.giraffe.matn.data.delivery.DownloadedContentRepositoryImpl
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.usecase.EnsureMatnPlayableUseCase
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * [EnsureMatnPlayableUseCase] — the FR-021 playback gate.
 *
 * Phase 13 removed the third case this test used to have ("passes for the starter matn"). There is
 * no permanently-available matn any more (FR-040), so the gate applies uniformly: downloaded passes,
 * anything else fails with an actionable error rather than a silent no-op.
 */
class EnsureMatnPlayableUseCaseTest {

    private val matnId = "ondemand-id"

    private fun newUseCase(scope: TestScope): Pair<FakeContentDeliveryEngine, EnsureMatnPlayableUseCase> {
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
        return engine to EnsureMatnPlayableUseCase(repo)
    }

    @Test
    fun `fails for a matn that is not downloaded`() = runTest {
        val (_, useCase) = newUseCase(this)
        val out = useCase(matnId)
        val failure = assertIs<Resource.Failure>(out)
        val error = assertIs<DeliveryError.ContentNotDownloaded>(failure.error)
        assertEquals(matnId, error.matnId)
    }

    @Test
    fun `passes for a downloaded matn`() = runTest {
        val (engine, useCase) = newUseCase(this)
        engine.completeDownload(matnId, occupiedBytes = 2_048L)
        assertIs<Resource.Success<*>>(useCase(matnId))
    }
}
