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
import com.giraffe.matn.domain.model.DeliveryPhase
import com.giraffe.matn.domain.model.DeliveryProgress
import com.giraffe.matn.domain.repository.ContentPackRepository
import com.giraffe.matn.domain.usecase.InstallMatnContentUseCase
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * T037 â€” covers [InstallMatnContentUseCase]'s ordered preconditions and gap cases (content-delivery-
 * contract.md Â§6): happy path, starter refused, duplicate install is a no-op (engine called once),
 * offline refusal carries NoConnectivity, insufficient-space refusal carries both figures, two
 * independent Ù…ØªÙˆÙ† install and one failing leaves the other's state untouched.
 */
class InstallMatnContentUseCaseTest {

    private val starterId = "starter-matn-id"
    private val starterPack = "matn_starter"
    private val onDemandId = "ondemand-matn-id"
    private val onDemandPack = "matn_ondemand"
    private val onDemandId2 = "ondemand-matn-id-2"
    private val onDemandPack2 = "matn_ondemand_2"

    private data class Harness(
        val engine: FakeContentDeliveryEngine,
        val storage: FakeDeviceStorage,
        val repo: ContentPackRepository,
        val useCase: InstallMatnContentUseCase,
    )

    private suspend fun newHarness(
        starterSize: Long = 296L,
        onDemandSize: Long = 296L,
        onDemandSize2: Long = 296L,
        freeSpaceBytes: Long = Long.MAX_VALUE,
    ): Harness {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        loader.load(seedMatn(starterId, starterPack, starterSize, isStarter = true))
        loader.load(seedMatn(onDemandId, onDemandPack, onDemandSize, isStarter = false))
        loader.load(seedMatn(onDemandId2, onDemandPack2, onDemandSize2, isStarter = false))
        val engine = FakeContentDeliveryEngine()
        val storage = FakeDeviceStorage().also { it.freeSpace = freeSpaceBytes }
        val repo = ContentPackRepositoryImpl(db, engine, storage)
        return Harness(engine, storage, repo, InstallMatnContentUseCase(repo, storage))
    }

    private fun seedMatn(matnId: String, packId: String, size: Long, isStarter: Boolean) = SeedMatn(
        id = matnId,
        title = "title-$matnId",
        author = "author",
        description = "desc",
        coverImageRef = null,
        structureKind = "SIMPLE",
        defaultReciterId = "reciter-default-v1",
        verses = listOf(
            SeedVerse(
                id = "${matnId}-v1",
                displayNumber = 1,
                arabicText = "verse",
                durationMs = 1000,
                audio = SeedAudio(id = "${matnId}-a1", fileRef = "audio.mp3", durationMs = 1000),
            ),
        ),
        packId = packId,
        declaredSizeBytes = size,
        isStarter = isStarter,
    )

    @Test
    fun `happy path delegates install to the engine`() = runTest {
        val h = newHarness()
        val out = h.useCase(onDemandId)
        assertIs<Resource.Success<*>>(out)
        assertEquals(listOf(onDemandPack), h.engine.installInvocations)
    }

    @Test
    fun `installing the starter matn is refused`() = runTest {
        val h = newHarness()
        val out = h.useCase(starterId)
        assertIs<Resource.Failure>(out)
        assertIs<DeliveryError.StarterMatnNotRemovable>(out.error)
        assertTrue(h.engine.installInvocations.isEmpty(), "starter install must not reach the engine")
    }

    @Test
    fun `duplicate install request is a no-op so the engine is called once`() = runTest {
        val h = newHarness()
        h.useCase(onDemandId) // first call â€” kicks off the install
        // The fake marks the pack as Installing on install(). Re-collecting availability flips the
        // current state to Installing, which the use case must treat as a no-op (engine called once).
        h.engine.states[onDemandPack] = ContentAvailability.Installing(
            DeliveryProgress(10, 296, DeliveryPhase.TRANSFERRING),
        )
        val second = h.useCase(onDemandId)
        assertIs<Resource.Success<*>>(second)
        assertEquals(1, h.engine.installInvocations.size, "engine must be called exactly once")
    }

    @Test
    fun `offline refusal carries NoConnectivity and leaves the matn not installed`() = runTest {
        val h = newHarness()
        h.engine.failInstallWith = DeliveryFailure.NoConnectivity
        val out = h.useCase(onDemandId)
        assertIs<Resource.Failure>(out)
        val err = out.error
        assertIs<DeliveryError.DeliveryFailed>(err)
        assertIs<DeliveryFailure.NoConnectivity>(err.failure)
        // No partial bytes counted â€” availability remains NotInstalled.
        val avail = h.repo.observeAvailability(onDemandId).first()
        assertIs<ContentAvailability.NotInstalled>(avail)
    }

    @Test
    fun `insufficient-space refusal carries both the required and available byte figures`() = runTest {
        val h = newHarness(onDemandSize = 5_000L, freeSpaceBytes = 1_000L)
        val out = h.useCase(onDemandId)
        assertIs<Resource.Failure>(out)
        val err = out.error
        assertIs<DeliveryError.DeliveryFailed>(err)
        val reason = err.failure
        assertIs<DeliveryFailure.InsufficientStorage>(reason)
        assertEquals(5_000L, reason.requiredBytes)
        assertEquals(1_000L, reason.availableBytes)
        assertTrue(h.engine.installInvocations.isEmpty(), "engine must not be called on insufficient space")
    }

    @Test
    fun `two independent matns install and one failing leaves the other's state untouched`() = runTest {
        val h = newHarness()
        val first = h.useCase(onDemandId)
        assertIs<Resource.Success<*>>(first)
        // Pin the first pack to Installing explicitly â€” a "successful happy path" leaves it there
        // (the fake doesn't auto-complete).
        h.engine.states[onDemandPack] = ContentAvailability.Installing(
            DeliveryProgress(50, 296, DeliveryPhase.TRANSFERRING),
        )
        // Now make the second matn's install fail.
        h.engine.failInstallWith = DeliveryFailure.NoConnectivity
        val second = h.useCase(onDemandId2)
        assertIs<Resource.Failure>(second)
        // The first pack's state is still pinned to Installing â€” failure of the second did not
        // derail it.
        val firstAvail = h.repo.observeAvailability(onDemandId).first()
        assertIs<ContentAvailability.Installing>(firstAvail)
        val secondAvail = h.repo.observeAvailability(onDemandId2).first()
        assertIs<ContentAvailability.NotInstalled>(secondAvail)
    }
}
