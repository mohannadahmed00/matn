package com.giraffe.matn.delivery

import com.giraffe.matn.data.delivery.ContentPackRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.data.seed.SeedAudio
import com.giraffe.matn.data.seed.SeedMatn
import com.giraffe.matn.data.seed.SeedVerse
import com.giraffe.matn.domain.usecase.ObserveStorageUsageUseCase
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T063 — [ObserveStorageUsageUseCase] / `ContentPackRepositoryImpl.observeStorageUsage` (data-
 * model.md §3.2, content-delivery-contract.md §6): ordering, totals, the starter's contribution,
 * the zero-audio edge case, and free space passthrough.
 */
class ObserveStorageUsageUseCaseTest {

    private val starterId = "starter-id"
    private val starterPack = "matn_starter"
    private val starterSize = 300L
    private val bigId = "big-id"
    private val bigPack = "matn_big"
    private val smallId = "small-id"
    private val smallPack = "matn_small"
    private val zeroId = "zero-audio-id"
    private val zeroPack = "matn_zero_audio"

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
    fun `entries ordered by bytes DESC with correct totals and a zero-audio matn never distorts them`() = runTest {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        loader.load(seed(starterId, starterPack, starterSize, isStarter = true))
        loader.load(seed(bigId, bigPack, 5_000L, isStarter = false))
        loader.load(seed(smallId, smallPack, 1_000L, isStarter = false))
        loader.load(seed(zeroId, zeroPack, 0L, isStarter = false))

        val engine = FakeContentDeliveryEngine()
        engine.completeInstall(bigPack, occupiedBytes = 5_000L)
        engine.completeInstall(smallPack, occupiedBytes = 1_000L)
        // zeroId is never installed — a zero-audio matn is not an install target.
        val storage = FakeDeviceStorage().also { it.freeSpace = 999_000L }
        // Occupied size is resolved via DeviceStorage.sizeOfDirectory(engine.locate(packId)), not
        // the fake's internal Installed(occupiedBytes) — give each on-demand pack its own root so
        // their measured sizes actually differ (the shared default `installedRoot` collapses every
        // installed pack onto the same path).
        engine.installedRoots[bigPack] = "/fake/packs/$bigPack"
        engine.installedRoots[smallPack] = "/fake/packs/$smallPack"
        storage.directorySizes["/fake/packs/$bigPack"] = 5_000L
        storage.directorySizes["/fake/packs/$smallPack"] = 1_000L
        val useCase = ObserveStorageUsageUseCase(ContentPackRepositoryImpl(db, engine, storage))

        val usage = useCase(Unit).first()
        // starter + big + small are Installed; zero-audio is not.
        assertEquals(3, usage.entries.size)
        assertEquals(listOf(bigId, smallId, starterId), usage.entries.map { it.matnId })
        assertEquals(starterSize + 5_000L + 1_000L, usage.totalUsedBytes)
        assertTrue(usage.totalUsedBytes > 0L, "starter's measured size must be non-zero (SC-003)")
        assertEquals(5_000L + 1_000L, usage.onDemandUsedBytes)
        assertEquals(999_000L, usage.freeSpaceBytes)
    }

    @Test
    fun `onDemandUsedBytes is zero when nothing on-demand is installed`() = runTest {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        loader.load(seed(starterId, starterPack, starterSize, isStarter = true))
        loader.load(seed(bigId, bigPack, 5_000L, isStarter = false))

        val engine = FakeContentDeliveryEngine()
        val storage = FakeDeviceStorage()
        val useCase = ObserveStorageUsageUseCase(ContentPackRepositoryImpl(db, engine, storage))

        val usage = useCase(Unit).first()
        assertEquals(0L, usage.onDemandUsedBytes)
        // The starter alone still contributes its measured size to the total.
        assertEquals(starterSize, usage.totalUsedBytes)
        assertEquals(listOf(starterId), usage.entries.map { it.matnId })
    }
}
