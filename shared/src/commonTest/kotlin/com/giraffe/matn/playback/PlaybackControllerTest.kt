package com.giraffe.matn.playback

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.audio.AudioEngineEvent
import com.giraffe.matn.domain.audio.AudioSourceResolver
import com.giraffe.matn.domain.audio.WakeLock
import com.giraffe.matn.domain.model.AudioAsset
import com.giraffe.matn.domain.model.AudioTrack
import com.giraffe.matn.domain.model.PlaybackNotice
import com.giraffe.matn.domain.model.PlaybackQueue
import com.giraffe.matn.domain.model.PlaybackSpeed
import com.giraffe.matn.domain.model.PlaybackStatus
import com.giraffe.matn.domain.model.PauseReason
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.domain.repository.AudioAssetRepository
import com.giraffe.matn.domain.repository.VerseRepository
import com.giraffe.matn.domain.usecase.BuildPlaybackQueueUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drive [PlaybackController] through every edge in data-model.md §6 with [FakeAudioEngine] +
 * [FakeWakeLock] + a faked [BuildPlaybackQueueUseCase]. No device, audio, or network
 * (Principle V — primary gate).
 */
class PlaybackControllerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val controllers = mutableListOf<PlaybackController>()

    @BeforeTest
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @AfterTest
    fun tearDown() {
        // `startInfoPolling()` runs a `while (true) { ...; delay(tick) }` loop on the test
        // dispatcher; if left running it would reschedule forever and hang the test scheduler's
        // drain. Stop every controller we built so its info/events jobs are cancelled before the
        // scheduler tears down.
        controllers.forEach { runCatching { it.stop() } }
        controllers.clear()
        Dispatchers.resetMain()
    }

    private val tracks = listOf(
        AudioTrack("v1", 1, "uri1", 5_000),
        AudioTrack("v2", 2, "uri2", 5_000),
        AudioTrack("v3", 3, "uri3", 5_000),
    )
    private val queue = PlaybackQueue("matn-1", tracks, startIndex = 0)

    /**
     * Builds a controller whose queue use case returns [queue] (startVerseId resolved) and whose
     * engine/wake lock are fakes. Pass overrides per test as needed.
     */
    private fun newController(
        engine: FakeAudioEngine = FakeAudioEngine(),
        wakeLock: FakeWakeLock = FakeWakeLock(),
        queueResult: Resource<PlaybackQueue> = Resource.Success(queue),
    ): Pair<PlaybackController, FakeAudioEngine> {
        val buildQueue = FakeBuildQueue(queueResult)
        val ctrl = PlaybackController(
            engine = engine,
            buildQueue = buildQueue,
            wakeLock = wakeLock,
            scope = CoroutineScope(SupervisorJob() + dispatcher),
        )
        controllers.add(ctrl)
        return ctrl to engine
    }

    // ---- US1 -------------------------------------------------------------

    @Test
    fun `start from verse sets correct startIndex and reaches PLAYING after Ready`() = runTest {
        val (ctrl, engine) = newController(
            queueResult = Resource.Success(queue.copy(startIndex = 2)),
        )
        ctrl.playFromVerse("matn-1", "v3")
        engine.emit(AudioEngineEvent.Ready)

        assertEquals(2, engine.startIndex)
        assertEquals(listOf("v1", "v2", "v3"), engine.lastQueue?.map { it.verseId })
        assertEquals(PlaybackStatus.PLAYING, ctrl.state.value.status)
        assertEquals("v3", ctrl.state.value.activeVerseId)
        assertEquals(2, ctrl.state.value.activeIndex)
    }

@Test
    fun `TrackTransition moves activeVerseId and activeIndex`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(1))

        assertEquals(1, ctrl.state.value.activeIndex)
        assertEquals("v2", ctrl.state.value.activeVerseId)
    }

    @Test
    fun `activeDisplayNumber flows into state`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        // startIndex 0 → v1 with displayNumber 1.
        assertEquals(1, ctrl.state.value.activeDisplayNumber)
        engine.emit(AudioEngineEvent.TrackTransition(1))
        assertEquals(2, ctrl.state.value.activeDisplayNumber)
    }

    @Test
    fun `QueueEnded goes ENDED with ReachedEnd and clears highlight`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.QueueEnded)

        assertEquals(PlaybackStatus.ENDED, ctrl.state.value.status)
        assertNull(ctrl.state.value.activeVerseId)
        assertTrue(ctrl.state.value.notice is PlaybackNotice.ReachedEnd)
    }

    @Test
    fun `TrackError skips one and emits SkippedMissingVerse`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackError(0))

        assertEquals("v2", ctrl.state.value.activeVerseId)
        assertEquals(1, ctrl.state.value.activeIndex)
        val notice = ctrl.state.value.notice
        assertTrue(notice is PlaybackNotice.SkippedMissingVerse)
        assertEquals("v1", (notice as PlaybackNotice.SkippedMissingVerse).verseId)
        assertEquals(1, engine.seekedToTrack)
    }

    @Test
    fun `TrackError with none left emits NoPlayableAudio and ENDED`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(2))
        engine.emit(AudioEngineEvent.TrackError(2))

        assertEquals(PlaybackStatus.ENDED, ctrl.state.value.status)
        assertTrue(ctrl.state.value.notice is PlaybackNotice.NoPlayableAudio)
    }

    @Test
    fun `pause holds and resume continues from PAUSED`() = runTest {
        val wake = FakeWakeLock()
        val engine = FakeAudioEngine()
        val (ctrl, _) = newController(engine = engine, wakeLock = wake)

        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        assertEquals(PlaybackStatus.PLAYING, ctrl.state.value.status)
        assertTrue(wake.acquired)

        ctrl.pause()
        assertEquals(PlaybackStatus.PAUSED, ctrl.state.value.status)
        assertEquals(PauseReason.USER, ctrl.state.value.pauseReason)
        assertTrue(engine.pauseCalled)
        assertFalse(wake.acquired)

        ctrl.resume()
        assertEquals(PlaybackStatus.PLAYING, ctrl.state.value.status)
        assertTrue(wake.acquired)
    }

    @Test
    fun `stop clears session and highlight`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        ctrl.stop()

        assertEquals(PlaybackStatus.IDLE, ctrl.state.value.status)
        assertNull(ctrl.state.value.activeVerseId)
        assertEquals(0, ctrl.state.value.positionMs)
        assertFalse(ctrl.state.value.hasSession)
    }

    @Test
    fun `wake lock acquired on PLAYING and released on PAUSED`() = runTest {
        val wake = FakeWakeLock()
        val engine = FakeAudioEngine()
        val (ctrl, _) = newController(engine = engine, wakeLock = wake)
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        assertTrue(wake.acquired)
        ctrl.pause()
        assertFalse(wake.acquired)
    }

    @Test
    fun `wake lock released on ENDED`() = runTest {
        val wake = FakeWakeLock()
        val engine = FakeAudioEngine()
        val (ctrl, _) = newController(engine = engine, wakeLock = wake)
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        assertTrue(wake.acquired)
        engine.emit(AudioEngineEvent.QueueEnded)
        assertFalse(wake.acquired)
    }

    @Test
    fun `empty matn sets NoPlayableAudio notice and IDLE`() = runTest {
        val (ctrl, _) = newController(queueResult = Resource.Failure(AppError.NotFound))
        ctrl.playFromStart("matn-missing")
        assertEquals(PlaybackStatus.IDLE, ctrl.state.value.status)
        assertTrue(ctrl.state.value.notice is PlaybackNotice.NoPlayableAudio)
    }

    // ---- US2 -------------------------------------------------------------

    @Test
    fun `next advances activeIndex`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(0))
        ctrl.next()
        assertEquals(1, ctrl.state.value.activeIndex)
        assertEquals(1, engine.seekedToTrack)
        // FIX 1: transport next() always lands in PLAYING with the wake lock held, even if it
        // was issued while PAUSED. Here it was already PLAYING, so just confirm it stayed there.
        assertEquals(PlaybackStatus.PLAYING, ctrl.state.value.status)
    }

    @Test
    fun `next while paused resumes playing`() = runTest {
        val wake = FakeWakeLock()
        val engine = FakeAudioEngine()
        val (ctrl, _) = newController(engine = engine, wakeLock = wake)
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(0))
        ctrl.pause()
        assertEquals(PlaybackStatus.PAUSED, ctrl.state.value.status)
        assertFalse(wake.acquired)
        engine.resetCalls()
        ctrl.next()
        assertEquals(PlaybackStatus.PLAYING, ctrl.state.value.status)
        assertEquals(1, ctrl.state.value.activeIndex)
        assertTrue(wake.acquired)
    }

    @Test
    fun `next at last verse goes ENDED`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(2))
        ctrl.next()
        assertEquals(PlaybackStatus.ENDED, ctrl.state.value.status)
        assertTrue(ctrl.state.value.notice is PlaybackNotice.ReachedEnd)
        assertNull(ctrl.state.value.activeVerseId)
    }

    @Test
    fun `previous after more than 2s restarts current verse`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(1))
        engine.setInfo(currentIndex = 1, positionMs = 3_000)
        engine.resetCalls()
        ctrl.previous()
        assertEquals(0L, engine.seekedToMs)
        assertNull(engine.seekedToTrack)
        // FIX 8: restart replays and enters PLAYING; position is reset.
        assertTrue(engine.playCalled)
        assertEquals(PlaybackStatus.PLAYING, ctrl.state.value.status)
        assertEquals(0, ctrl.state.value.positionMs)
    }

    @Test
    fun `previous restart resets position and plays`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(1))
        ctrl.pause()
        engine.setInfo(currentIndex = 1, positionMs = 3_000)
        engine.resetCalls()
        ctrl.previous()
        assertEquals(0L, engine.seekedToMs)
        assertTrue(engine.playCalled)
        assertEquals(PlaybackStatus.PLAYING, ctrl.state.value.status)
        assertEquals(0, ctrl.state.value.positionMs)
    }

    @Test
    fun `previous at index 0 restarts current`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(0))
        ctrl.previous()
        assertEquals(0L, engine.seekedToMs)
        assertNull(engine.seekedToTrack)
    }

    @Test
    fun `previous within 2s and not at index 0 steps back`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(1))
        ctrl.previous()
        assertEquals(0, engine.seekedToTrack)
    }

    @Test
    fun `seekTo forwards to engine`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.resetCalls()
        ctrl.seekTo(2_500)
        assertEquals(2_500L, engine.seekedToMs)
    }

    @Test
    fun `setSpeed applies to engine and persists across TrackTransition`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.resetCalls()
        ctrl.setSpeed(PlaybackSpeed.X0_75)
        assertEquals(0.75f, engine.speed)
        engine.emit(AudioEngineEvent.TrackTransition(1))
        assertEquals(PlaybackSpeed.X0_75, ctrl.state.value.speed)
        assertEquals(0.75f, engine.speed)
    }

    // ---- US3 -------------------------------------------------------------

    @Test
    fun `transient interruption auto-resumes on end`() = runTest {
        val wake = FakeWakeLock()
        val engine = FakeAudioEngine()
        val (ctrl, _) = newController(engine = engine, wakeLock = wake)
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.InterruptionBegan(transient = true))
        assertEquals(PlaybackStatus.PAUSED, ctrl.state.value.status)
        assertEquals(PauseReason.TRANSIENT_INTERRUPTION, ctrl.state.value.pauseReason)
        engine.emit(AudioEngineEvent.InterruptionEnded(shouldResume = true))
        assertEquals(PlaybackStatus.PLAYING, ctrl.state.value.status)
    }

    @Test
    fun `non-transient interruption stays paused`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.InterruptionBegan(transient = false))
        assertEquals(PlaybackStatus.PAUSED, ctrl.state.value.status)
        assertEquals(PauseReason.NON_TRANSIENT_INTERRUPTION, ctrl.state.value.pauseReason)
        engine.emit(AudioEngineEvent.InterruptionEnded(shouldResume = true))
        assertEquals(PlaybackStatus.PAUSED, ctrl.state.value.status)
    }

    @Test
    fun `user pause is not auto-resumed by InterruptionEnded`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        ctrl.pause()
        engine.emit(AudioEngineEvent.InterruptionEnded(shouldResume = true))
        assertEquals(PlaybackStatus.PAUSED, ctrl.state.value.status)
        assertEquals(PauseReason.USER, ctrl.state.value.pauseReason)
    }

    // ---- fake use case ---------------------------------------------------

    /** Fake queue use case: returns a canned [Resource], resolving `startVerseId` to a startIndex. */
    private class FakeBuildQueue(result: Resource<PlaybackQueue>) : BuildPlaybackQueueUseCase(
        verseRepository = StubVerseRepo,
        audioRepository = StubAudioRepo,
        audioSourceResolver = StubResolver,
    ) {
        private val result: Resource<PlaybackQueue>

        init {
            // resolve startVerseId once per invoke (controller calls from a coroutine)
            this.result = result
        }

        override suspend fun invoke(params: BuildPlaybackQueueUseCase.Params): Resource<PlaybackQueue> {
            val r = result
            if (r is Resource.Success) {
                val q = r.data
                val resolvedStart = if (params.startVerseId == null) q.startIndex
                else q.tracks.indexOfFirst { it.verseId == params.startVerseId }
                    .let { if (it < 0) q.startIndex else it }
                return Resource.Success(q.copy(startIndex = resolvedStart))
            }
            return r
        }
    }

    private object StubVerseRepo : VerseRepository {
        override fun observeVerses(matnId: String): Flow<List<Verse>> = flowOf(emptyList())
        override suspend fun getVersesByChapter(chapterId: String): Resource<List<Verse>> =
            Resource.Success(emptyList())
        override suspend fun getVerse(id: String): Resource<Verse?> = Resource.Success(null)
    }

    private object StubAudioRepo : AudioAssetRepository {
        override suspend fun getAudioForVerse(verseId: String, reciterId: String): Resource<AudioAsset?> =
            Resource.Success(null)
        override suspend fun getAudioForMatn(matnId: String, reciterId: String): Resource<List<AudioAsset>> =
            Resource.Success(emptyList())
    }

    private object StubResolver : AudioSourceResolver {
        override suspend fun resolve(fileRef: String): String = "file://audio/$fileRef"
    }
}