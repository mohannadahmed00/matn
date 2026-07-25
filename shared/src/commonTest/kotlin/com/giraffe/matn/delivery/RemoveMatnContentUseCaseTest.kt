package com.giraffe.matn.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.delivery.ContentPackRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.data.seed.SeedAudio
import com.giraffe.matn.data.seed.SeedMatn
import com.giraffe.matn.data.seed.SeedVerse
import com.giraffe.matn.domain.delivery.ContentDeliveryEngine
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.DeliveryProgress
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.repository.ContentPackRepository
import com.giraffe.matn.domain.usecase.BuildPlaybackQueueUseCase
import com.giraffe.matn.domain.usecase.RemoveAllContentUseCase
import com.giraffe.matn.domain.usecase.RemoveMatnContentUseCase
import com.giraffe.matn.newTestDatabase
import com.giraffe.matn.playback.FakeAudioEngine
import com.giraffe.matn.playback.FakeWakeLock
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.domain.model.AudioTrack
import com.giraffe.matn.domain.model.PlaybackQueue
import com.giraffe.matn.domain.model.PlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * T055 — [RemoveMatnContentUseCase]'s ordered effects (content-delivery-contract.md §4/§6):
 * starter refused; active session stopped before the engine's `remove` is called; both outcome
 * variants propagate unchanged; repeated remove taps call the engine once.
 */
class RemoveMatnContentUseCaseTest {

    private val starterId = "starter-id"
    private val starterPack = "matn_starter"
    private val onDemandId = "ondemand-id"
    private val onDemandPack = "matn_ondemand"

    private fun seed(matnId: String, packId: String, isStarter: Boolean) = SeedMatn(
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
        packId = packId, declaredSizeBytes = 2_000L, isStarter = isStarter,
    )

    private fun idleController(): Pair<PlaybackController, FakeAudioEngine> {
        val engine = FakeAudioEngine()
        val buildQueue = object : BuildPlaybackQueueUseCase(
            verseRepository = object : com.giraffe.matn.domain.repository.VerseRepository {
                override fun observeVerses(matnId: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.giraffe.matn.domain.model.Verse>())
                override suspend fun getVersesByChapter(chapterId: String) = Resource.Success(emptyList<com.giraffe.matn.domain.model.Verse>())
                override suspend fun getVerse(id: String) = Resource.Success<com.giraffe.matn.domain.model.Verse?>(null)
            },
            audioRepository = object : com.giraffe.matn.domain.repository.AudioAssetRepository {
                override suspend fun getAudioForVerse(verseId: String, reciterId: String) = Resource.Success<com.giraffe.matn.domain.model.AudioAsset?>(null)
                override suspend fun getAudioForMatn(matnId: String, reciterId: String) = Resource.Success(emptyList<com.giraffe.matn.domain.model.AudioAsset>())
            },
            audioSourceResolver = object : com.giraffe.matn.domain.audio.AudioSourceResolver {
                override suspend fun resolve(matnId: String, fileRef: String) = ""
            },
        ) {
            override suspend fun invoke(params: Params): Resource<PlaybackQueue> =
                Resource.Success(PlaybackQueue(params.matnId, listOf(AudioTrack("v1", 1, "uri", 1000)), 0))
        }
        val controller = PlaybackController(
            engine = engine,
            buildQueue = buildQueue,
            wakeLock = FakeWakeLock(),
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
        )
        return controller to engine
    }

    @Test
    fun `removing the starter matn is refused`() = runTest {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        loader.load(seed(starterId, starterPack, isStarter = true))
        val engine = FakeContentDeliveryEngine()
        val repo = ContentPackRepositoryImpl(db, engine, FakeDeviceStorage())
        val (controller, _) = idleController()
        val useCase = RemoveMatnContentUseCase(repo, controller)

        val out = useCase(starterId)
        assertIs<Resource.Failure>(out)
        assertIs<DeliveryError.StarterMatnNotRemovable>(out.error)
        assertTrue(engine.removeInvocations.isEmpty())
    }

    @Test
    fun `active session is stopped before the engine's remove is called`() = runTest {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        loader.load(seed(onDemandId, onDemandPack, isStarter = false))
        val fake = FakeContentDeliveryEngine()
        fake.completeInstall(onDemandPack, occupiedBytes = 2_000L)
        var sessionStoppedBeforeRemove = false
        val (controller, _) = idleController()
        // Wrap the fake so `remove()` snapshots whether the session had already stopped.
        val spyEngine = object : ContentDeliveryEngine by fake {
            override suspend fun remove(packId: String): Resource<RemovalOutcome> {
                sessionStoppedBeforeRemove = !controller.state.value.hasSession
                return fake.remove(packId)
            }
        }
        val repo = ContentPackRepositoryImpl(db, spyEngine, FakeDeviceStorage())
        val useCase = RemoveMatnContentUseCase(repo, controller)

        controller.playFromStart(onDemandId)
        assertTrue(controller.state.value.hasSession)

        val out = useCase(onDemandId)
        assertIs<Resource.Success<*>>(out)
        assertTrue(sessionStoppedBeforeRemove, "playback must stop before the engine's remove is invoked")
        assertEquals(PlaybackStatus.IDLE, controller.state.value.status)
    }

    @Test
    fun `both removal outcomes propagate unchanged`() = runTest {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        loader.load(seed(onDemandId, onDemandPack, isStarter = false))
        val engine = FakeContentDeliveryEngine()
        engine.completeInstall(onDemandPack, occupiedBytes = 2_000L)
        engine.nextRemovalOutcome = RemovalOutcome.ReleasedPendingSystemReclaim(2_000L)
        val repo = ContentPackRepositoryImpl(db, engine, FakeDeviceStorage())
        val (controller, _) = idleController()
        val useCase = RemoveMatnContentUseCase(repo, controller)

        val out = useCase(onDemandId)
        assertIs<Resource.Success<RemovalOutcome>>(out)
        assertIs<RemovalOutcome.ReleasedPendingSystemReclaim>(out.data)
    }

    @Test
    fun `repeated remove taps call the engine once and the second returns Reclaimed zero`() = runTest {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        loader.load(seed(onDemandId, onDemandPack, isStarter = false))
        val engine = FakeContentDeliveryEngine()
        engine.completeInstall(onDemandPack, occupiedBytes = 2_000L)
        val repo = ContentPackRepositoryImpl(db, engine, FakeDeviceStorage())
        val (controller, _) = idleController()
        val useCase = RemoveMatnContentUseCase(repo, controller)

        val first = useCase(onDemandId)
        assertIs<Resource.Success<RemovalOutcome>>(first)
        val second = useCase(onDemandId)
        assertIs<Resource.Success<RemovalOutcome>>(second)
        assertIs<RemovalOutcome.Reclaimed>(second.data)
        assertEquals(0L, (second.data as RemovalOutcome.Reclaimed).bytes)
        assertEquals(1, engine.removeInvocations.size)
    }

    @Test
    fun `removeAll spares the starter and leaves it playable`() = runTest {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        loader.load(seed(starterId, starterPack, isStarter = true))
        loader.load(seed(onDemandId, onDemandPack, isStarter = false))
        val engine = FakeContentDeliveryEngine()
        engine.completeInstall(onDemandPack, occupiedBytes = 2_000L)
        val repo = ContentPackRepositoryImpl(db, engine, FakeDeviceStorage())
        val useCase = RemoveAllContentUseCase(repo)

        val out = useCase(Unit)
        assertIs<Resource.Success<List<RemovalOutcome>>>(out)
        assertTrue(engine.removeInvocations.none { it == starterPack }, "starter must never reach the engine's remove")

        val starterAvailability = repo.observeAvailability(starterId).first()
        assertIs<com.giraffe.matn.domain.model.ContentAvailability.Installed>(starterAvailability)
    }
}
