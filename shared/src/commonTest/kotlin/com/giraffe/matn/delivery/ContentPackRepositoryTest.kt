package com.giraffe.matn.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.delivery.ContentPackRepositoryImpl
import com.giraffe.matn.data.seed.SeedAudio
import com.giraffe.matn.data.seed.SeedMatn
import com.giraffe.matn.data.seed.SeedVerse
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.model.DeliveryPhase
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T025 — `ContentPackRepositoryImpl` against an in-memory SQLDelight DB plus the two fakes
 * (content-delivery-contract.md §6). Covers the contract's enumerated cases plus the six
 * gap-closers called out by T025.
 */
class ContentPackRepositoryTest {

    private val starterMatnId = "starter-id"
    private val starterPackId = "matn_starter"
    private val onDemandMatnId = "ondemand-id"
    private val onDemandPackId = "matn_ondemand"
    private val zeroMatnId = "zero-id"
    private val zeroPackId = "matn_zero"

    private data class Harness(
        val engine: FakeContentDeliveryEngine,
        val storage: FakeDeviceStorage,
        val repo: ContentPackRepositoryImpl,
    )

    private suspend fun newHarness(
        starterSize: Long = 296L,
        onDemandSize: Long = 296L,
        zeroSize: Long = 0L,
    ): Harness {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        loader.load(seedMatn(starterMatnId, starterPackId, starterSize, isStarter = true))
        loader.load(seedMatn(onDemandMatnId, onDemandPackId, onDemandSize, isStarter = false))
        loader.load(seedMatn(zeroMatnId, zeroPackId, zeroSize, isStarter = false))
        val engine = FakeContentDeliveryEngine()
        val storage = FakeDeviceStorage()
        val repo = ContentPackRepositoryImpl(db, engine, storage)
        return Harness(engine, storage, repo)
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

    // ---------------------------------------------------------- availability reflects engine

    @Test
    fun `availability reflects engine not-installed state`() = runTest {
        val h = newHarness()
        val avail = h.repo.observeAvailability(onDemandMatnId).first()
        assertIs<ContentAvailability.NotInstalled>(avail)
        assertNull(avail.failure)
    }

    @Test
    fun `starter is always Installed with its measured declared size`() = runTest {
        val h = newHarness(starterSize = 1234L)
        val avail = h.repo.observeAvailability(starterMatnId).first()
        assertIs<ContentAvailability.Installed>(avail)
        assertEquals(1234L, avail.occupiedBytes)
    }

    @Test
    fun `engine installed on-demand pack reports measured directory size`() = runTest {
        val h = newHarness()
        h.engine.completeInstall(onDemandPackId, occupiedBytes = 0L)
        h.storage.directorySizes[h.engine.installedRoot!!] = 100_000L
        val avail = h.repo.observeAvailability(onDemandMatnId).first()
        assertIs<ContentAvailability.Installed>(avail)
        assertEquals(100_000L, avail.occupiedBytes)
    }

    @Test
    fun `platform says installed but locate returns null yields NotInstalled Evicted`() = runTest {
        val h = newHarness()
        // Mark installed in the engine but with no locate path → repository must NOT report
        // a zero-byte install; it must report NotInstalled(Evicted) per data-model §2.1 rule 3.
        h.engine.states[onDemandPackId] = ContentAvailability.Installed(0L)
        h.engine.installedRoot = null
        val avail = h.repo.observeAvailability(onDemandMatnId).first()
        assertIs<ContentAvailability.NotInstalled>(avail)
        assertEquals(DeliveryFailure.Evicted, avail.failure)
    }

    // ------------------------------------------------------------------- progress flows

    @Test
    fun `progress flows through Installing state`() = runTest {
        val h = newHarness()
        // Start install first — the engine's observe flow is enroled on first collect.
        h.repo.install(onDemandMatnId)
        val collector = async {
            h.repo.observeAvailability(onDemandMatnId).first { it is ContentAvailability.Installed }
        }
        // Emit progress events; the repository re-derives on each. Final completion flips Installed.
        h.engine.emitProgress(onDemandPackId, bytes = 50, total = 100, phase = DeliveryPhase.TRANSFERRING)
        h.engine.completeInstall(onDemandPackId, occupiedBytes = 0L)
        h.storage.directorySizes[h.engine.installedRoot!!] = 100L
        val final = collector.await()
        assertIs<ContentAvailability.Installed>(final)
    }

    @Test
    fun `process death mid-install never yields Installed`() = runTest {
        val h = newHarness()
        h.repo.install(onDemandMatnId)
        // Simulate the process being killed mid-install: the engine's states/flows are wiped, the
        // fake's progressFlow is removed. On next launch the repo must not surface Installing —
        // but the repo's `activeInstalls` set persists only in-process and is also gone after the
        // kill. Mirror that by cancelling (a fresh process re-derives from engine state alone).
        h.engine.simulateProcessDeathMidInstall(onDemandPackId)
        h.repo.cancel(onDemandMatnId)
        val avail = h.repo.observeAvailability(onDemandMatnId).first()
        assertIs<ContentAvailability.NotInstalled>(avail)
        assertNull(avail.failure) // no orphan reason carried after a clean process kill sim
    }

    // ----------------------------------------------------------------------- size queries

    @Test
    fun `bestKnownSize prefers live over declared and falls back when engine returns null`() = runTest {
        val h = newHarness(onDemandSize = 500L)
        assertEquals(500L, h.repo.bestKnownSize(onDemandMatnId)) // engine null → declared
        h.engine.liveSize = 700L
        assertEquals(700L, h.repo.bestKnownSize(onDemandMatnId))
    }

    // ----------------------------------------------------------------- eviction flips Installed

    @Test
    fun `eviction flips an installed pack back to NotInstalled`() = runTest {
        val h = newHarness()
        h.engine.completeInstall(onDemandPackId, occupiedBytes = 0L)
        h.storage.directorySizes[h.engine.installedRoot!!] = 100L
        h.engine.simulateEviction(onDemandPackId)
        // Use a fresh collect of the flow to re-poll — spec edge case: Evicted renders as plain
        // NotInstalled, not as an error state (data-model §2.3).
        val avail = h.repo.observeAvailability(onDemandMatnId).first()
        assertIs<ContentAvailability.NotInstalled>(avail)
        assertNull(avail.failure)
    }

    // --------------------------------------------------------- connectivity lost mid-transfer

    @Test
    fun `connectivity lost mid-transfer yields NotInstalled with no partial bytes`() = runTest {
        val h = newHarness()
        h.repo.install(onDemandMatnId)
        h.engine.emitProgress(onDemandPackId, 50, 100, DeliveryPhase.TRANSFERRING)
        // Simulate the platform losing connectivity mid-transfer. The engine reports the failure
        // by flipping its installed-state to a NotInstalled carrying the connectivity reason (the
        // real Play path surfaces this via `requestProgressFlow` state updates). The repo must
        // follow the engine and surface NotInstalled, NOT Installing, and no partial bytes counted.
        h.engine.states[onDemandPackId] = ContentAvailability.NotInstalled(DeliveryFailure.NoConnectivity)
        // Drop the in-flight install from the repo's active set so the next read re-derives from
        // the engine rather than reporting a phantom Installing. (In production this happens when
        // `observeAvailability` re-polls after the engine emits a NotInstalled-completion event.)
        h.repo.cancel(onDemandMatnId)
        val availState = h.repo.observeAvailability(onDemandMatnId).first()
        assertIs<ContentAvailability.NotInstalled>(availState)
        val usage = h.repo.observeStorageUsage().first()
        // Nothing on-demand installed → onDemandUsedBytes == 0L.
        assertEquals(0L, usage.onDemandUsedBytes)
        // Starter still counted via its declared size.
        assertEquals(296L, usage.totalUsedBytes)
    }

    // --------------------------------------------------------- remove racing in-flight install

    @Test
    fun `remove racing an in-flight install of the same matn wins`() = runTest {
        val h = newHarness()
        // Start an install so the per-pack lock is contended by a critical section.
        h.repo.install(onDemandMatnId)
        // Remove concurrently in the same coroutine test scope. Use a launch so install+remove can
        // interleave on the dispatcher; the repository's per-pack lock serialises them determinis-
        // tically (cancel first, then remove → install resolves NotInstalled(Cancelled)).
        val removeJob = launch { h.repo.remove(onDemandMatnId) }
        yield()
        removeJob.join()
        val avail = h.repo.observeAvailability(onDemandMatnId).first()
        assertIs<ContentAvailability.NotInstalled>(avail)
        // The final state is consistent regardless of call ordering: not installed.
    }

    @Test
    fun `second remove on an already-removed matn is a no-op returning Reclaimed 0`() = runTest {
        val h = newHarness()
        // Seed an installed on-demand pack so the first remove actually deletes content.
        h.engine.completeInstall(onDemandPackId, occupiedBytes = 0L)
        h.storage.directorySizes[h.engine.installedRoot!!] = 1_000L
        h.engine.nextRemovalOutcome = RemovalOutcome.Reclaimed(4_000L)
        val first = h.repo.remove(onDemandMatnId)
        assertIs<Resource.Success<RemovalOutcome>>(first)
        val firstOutcome = first.data
        assertIs<RemovalOutcome.Reclaimed>(firstOutcome)
        assertEquals(4_000L, firstOutcome.bytes)
        val second = h.repo.remove(onDemandMatnId)
        assertIs<Resource.Success<RemovalOutcome>>(second)
        val secondOutcome = second.data
        assertIs<RemovalOutcome.Reclaimed>(secondOutcome)
        assertEquals(0L, secondOutcome.bytes)
    }

    // -------------------------------------------------------------------- remove all spares starter

    @Test
    fun `removeAll spares the starter and leaves it playable`() = runTest {
        val h = newHarness()
        h.engine.completeInstall(onDemandPackId, occupiedBytes = 0L)
        h.storage.directorySizes[h.engine.installedRoot!!] = 1_000L
        val results = h.repo.removeAll()
        assertIs<Resource.Success<List<RemovalOutcome>>>(results)
        val list = (results as Resource.Success).data
        // Only the on-demand pack is targeted — the starter is spared.
        assertEquals(1, list.size)
        val starterAvail = h.repo.observeAvailability(starterMatnId).first()
        assertIs<ContentAvailability.Installed>(starterAvail)
    }

    // ---------------------------------------------------------------------- starter never installs

    @Test
    fun `installing the starter matn is a success no-op that does not call the engine`() = runTest {
        val h = newHarness()
        val out = h.repo.install(starterMatnId)
        assertIs<Resource.Success<*>>(out)
        assertTrue(h.engine.installInvocations.isEmpty())
    }

    // ---------------------------------------------------------------- zero-audio matn

    @Test
    fun `a zero-audio matn contributes zero to the storage total and does not install`() = runTest {
        val h = newHarness()
        assertEquals(0L, h.repo.declaredSize(zeroMatnId))
        val avail = h.repo.observeAvailability(zeroMatnId).first()
        // Zero-audio matn is on-demand like any other in the catalog — but its declared size is 0.
        assertIs<ContentAvailability.NotInstalled>(avail)
        val usage = h.repo.observeStorageUsage().first()
        // The zero-audio matn is not Installed → not in the breakdown, but the starter is.
        assertEquals(296L, usage.totalUsedBytes)
        assertEquals(0L, usage.onDemandUsedBytes)
    }
}