package com.giraffe.matn.presentation

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.audio.AudioEngineEvent
import com.giraffe.matn.domain.audio.AudioSourceResolver
import com.giraffe.matn.domain.model.AudioAsset
import com.giraffe.matn.domain.model.AudioTrack
import com.giraffe.matn.domain.model.PlaybackMode
import com.giraffe.matn.domain.model.PlaybackQueue
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.domain.repository.AudioAssetRepository
import com.giraffe.matn.domain.repository.VerseRepository
import com.giraffe.matn.domain.usecase.BuildPlaybackQueueUseCase
import com.giraffe.matn.playback.FakeAudioEngine
import com.giraffe.matn.playback.FakeWakeLock
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.presentation.player.PlayerBarViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Asserts [PlayerBarViewModel]'s state projection (data-model.md §6.1): the derived mode chip,
 * "3 / 7" repetition formatting, and "∞" rendering for unlimited targets.
 */
class PlayerBarViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private val tracks = listOf(
        AudioTrack("v1", 1, "uri1", 5_000),
        AudioTrack("v2", 2, "uri2", 5_000),
        AudioTrack("v3", 3, "uri3", 5_000),
    )
    private val queue = PlaybackQueue("matn-1", tracks, startIndex = 0)

    private fun TestScope.newControllerAndEngine(): Pair<PlaybackController, FakeAudioEngine> {
        val engine = FakeAudioEngine()
        val buildQueue = object : BuildPlaybackQueueUseCase(
            verseRepository = object : VerseRepository {
                override fun observeVerses(matnId: String): Flow<List<Verse>> = flowOf(emptyList())
                override suspend fun getVersesByChapter(chapterId: String): Resource<List<Verse>> =
                    Resource.Success(emptyList())
                override suspend fun getVerse(id: String): Resource<Verse?> = Resource.Success(null)
            },
            audioRepository = object : AudioAssetRepository {
                override suspend fun getAudioForVerse(verseId: String, reciterId: String): Resource<AudioAsset?> =
                    Resource.Success(null)
                override suspend fun getAudioForMatn(matnId: String, reciterId: String): Resource<List<AudioAsset>> =
                    Resource.Success(emptyList())
            },
            audioSourceResolver = object : AudioSourceResolver {
                override suspend fun resolve(fileRef: String): String = "file://$fileRef"
            },
        ) {
            override suspend fun invoke(params: Params): Resource<PlaybackQueue> =
                Resource.Success(queue.copy(startIndex = 0))
        }
        val controller = PlaybackController(
            engine = engine,
            buildQueue = buildQueue,
            wakeLock = FakeWakeLock(),
            // See PlaybackControllerTest.newController for the full explanation: parent under
            // backgroundScope's Job (exempt from runTest's idle-drain) but keep dispatching on our
            // own UnconfinedTestDispatcher (so engine.emit(...) is still processed synchronously).
            scope = CoroutineScope(SupervisorJob(backgroundScope.coroutineContext[Job]) + dispatcher),
        )
        return controller to engine
    }

    @Test
    fun `default session projects Normal mode and 1 over 1`() = runTest {
        val (controller, engine) = newControllerAndEngine()
        val vm = PlayerBarViewModel(controller)
        controller.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)

        assertEquals(PlaybackMode.NORMAL, vm.state.value.mode)
        assertEquals(1, vm.state.value.repetition)
        assertEquals(RepeatCount.ONE, vm.state.value.verseRepeatTarget)
    }

    @Test
    fun `Vr3 at repetition 2 projects Memorization mode and 2 over 3`() = runTest {
        val (controller, engine) = newControllerAndEngine()
        val vm = PlayerBarViewModel(controller)
        controller.setVerseRepeat(RepeatCount.of(3))
        controller.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(1))

        assertEquals(PlaybackMode.MEMORIZATION, vm.state.value.mode)
        assertEquals(2, vm.state.value.repetition)
        assertEquals(RepeatCount.of(3), vm.state.value.verseRepeatTarget)
    }

    @Test
    fun `Unlimited verse repeat projects an infinite target`() = runTest {
        val (controller, engine) = newControllerAndEngine()
        val vm = PlayerBarViewModel(controller)
        controller.setVerseRepeat(RepeatCount.Unlimited)
        controller.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)

        assertEquals(RepeatCount.Unlimited, vm.state.value.verseRepeatTarget)
        assertEquals(true, vm.state.value.verseRepeatTarget.isUnlimited)
    }

    @Test
    fun `A-B loop projects A_B_LOOP mode`() = runTest {
        val (controller, engine) = newControllerAndEngine()
        val vm = PlayerBarViewModel(controller)
        controller.setLoopStart("v1")
        controller.setLoopEnd("v2")
        controller.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)

        assertEquals(PlaybackMode.A_B_LOOP, vm.state.value.mode)
    }
}
