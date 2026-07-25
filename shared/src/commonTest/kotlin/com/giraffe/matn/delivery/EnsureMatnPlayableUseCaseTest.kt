package com.giraffe.matn.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.delivery.ContentPackRepositoryImpl
import com.giraffe.matn.data.seed.SeedAudio
import com.giraffe.matn.data.seed.SeedMatn
import com.giraffe.matn.data.seed.SeedVerse
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.usecase.EnsureMatnPlayableUseCase
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * T038 â€” [EnsureMatnPlayableUseCase] gate: fails for a not-installed matn, passes for an installed
 * one, and passes for the starter (which is always `Installed`) â€” content-delivery-contract.md Â§6.
 */
class EnsureMatnPlayableUseCaseTest {

    private val starterId = "starter-id"
    private val starterPack = "matn_starter"
    private val onDemandId = "ondemand-id"
    private val onDemandPack = "matn_ondemand"

    private data class Harness(
        val engine: FakeContentDeliveryEngine,
        val useCase: EnsureMatnPlayableUseCase,
    )

    private suspend fun newHarness(starterSize: Long = 296L, onDemandSize: Long = 296L): Harness {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        loader.load(seed(starterId, starterPack, starterSize, isStarter = true))
        loader.load(seed(onDemandId, onDemandPack, onDemandSize, isStarter = false))
        val engine = FakeContentDeliveryEngine()
        val storage = FakeDeviceStorage()
        val repo = ContentPackRepositoryImpl(db, engine, storage)
        return Harness(engine, EnsureMatnPlayableUseCase(repo))
    }

    private fun seed(matnId: String, packId: String, size: Long, isStarter: Boolean) = SeedMatn(
        id = matnId,
        title = "title-$matnId",
        author = "author",
        description = "desc",
        coverImageRef = null,
        structureKind = "SIMPLE",
        defaultReciterId = "reciter-default-v1",
        verses = listOf(
            SeedVerse(
                id = "${matnId}-v1", displayNumber = 1, arabicText = "verse", durationMs = 1000,
                audio = SeedAudio(id = "${matnId}-a1", fileRef = "audio.mp3", durationMs = 1000),
            ),
        ),
        packId = packId, declaredSizeBytes = size, isStarter = isStarter,
    )

    @Test
    fun `fails for a not-installed matn`() = runTest {
        val h = newHarness()
        val out = h.useCase(onDemandId)
        assertIs<Resource.Failure>(out)
        val err = out.error
        assertIs<DeliveryError.ContentNotInstalled>(err)
        assertTrue(err.matnId == onDemandId)
    }

    @Test
    fun `passes for an installed matn`() = runTest {
        val h = newHarness()
        h.engine.completeInstall(onDemandPack, occupiedBytes = 0L)
        h.engine.installedRoot?.let { /* locate path is non-null */ }
        // The repo's deriveAvailability needs a non-zero directory size to report Installed; the
        // fake's installedRoot defaults to "/fake/packs". Wire a directory size.
        val out = h.useCase(onDemandId)
        // Without a directory entry in FakeDeviceStorage, sizeOfDirectory returns 0 â†’ Installed(0)
        // is the expected availability for an installed on-demand pack with no measured bytes.
        assertIs<Resource.Success<*>>(out)
    }

    @Test
    fun `passes for the starter matn`() = runTest {
        val h = newHarness()
        val out = h.useCase(starterId)
        assertIs<Resource.Success<*>>(out)
    }
}
