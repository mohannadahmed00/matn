package com.giraffe.matn.playback

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.repository.InMemoryRepetitionSettingsStore
import com.giraffe.matn.domain.audio.AudioEngineEvent
import com.giraffe.matn.domain.audio.AudioSourceResolver
import com.giraffe.matn.domain.model.AudioAsset
import com.giraffe.matn.domain.model.AudioTrack
import com.giraffe.matn.domain.model.PlaybackNotice
import com.giraffe.matn.domain.model.PlaybackQueue
import com.giraffe.matn.domain.model.PlaybackStatus
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.domain.repository.AudioAssetRepository
import com.giraffe.matn.domain.repository.RepetitionSettingsStore
import com.giraffe.matn.domain.repository.VerseRepository
import com.giraffe.matn.domain.usecase.BuildPlaybackQueueUseCase
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives [PlaybackController]'s repetition window against [FakeAudioEngine] — rows C1–C18 of
 * [contracts/repetition-contract.md] §4 (Phase 3 splits this file across US1–US4; this file covers
 * US1's C1, C2, C3, C4, C6, C9, C10, C14, C15).
 */
class RepetitionControllerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val controllers = mutableListOf<PlaybackController>()

    @BeforeTest
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @AfterTest
    fun tearDown() {
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

    private fun TestScope.newController(
        engine: FakeAudioEngine = FakeAudioEngine(),
        wakeLock: FakeWakeLock = FakeWakeLock(),
        settingsStore: RepetitionSettingsStore = InMemoryRepetitionSettingsStore(),
        queueResult: Resource<PlaybackQueue> = Resource.Success(queue),
    ): Pair<PlaybackController, FakeAudioEngine> {
        val buildQueue = FakeBuildQueue(queueResult)
        val ctrl = PlaybackController(
            engine = engine,
            buildQueue = buildQueue,
            wakeLock = wakeLock,
            settingsStore = settingsStore,
            // See PlaybackControllerTest.newController for the full explanation: parent under
            // backgroundScope's Job (exempt from runTest's idle-drain) but keep dispatching on our
            // own UnconfinedTestDispatcher (so engine.emit(...) is still processed synchronously).
            scope = CoroutineScope(SupervisorJob(backgroundScope.coroutineContext[Job]) + dispatcher),
        )
        controllers.add(ctrl)
        return ctrl to engine
    }

    @Test
    fun `C1 Vr3 windows the same verse and repeats it three times before advancing`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.setVerseRepeat(RepeatCount.of(3))
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        assertEquals(listOf("v1", "v1", "v1"), engine.lastQueue?.map { it.verseId })
        assertEquals("v1", ctrl.state.value.activeVerseId)
        assertEquals(1, ctrl.state.value.repetitionProgress?.repetition)

        engine.emit(AudioEngineEvent.TrackTransition(1))
        assertEquals("v1", ctrl.state.value.activeVerseId)
        assertEquals(2, ctrl.state.value.repetitionProgress?.repetition)

        engine.emit(AudioEngineEvent.TrackTransition(1))
        assertEquals("v1", ctrl.state.value.activeVerseId)
        assertEquals(3, ctrl.state.value.repetitionProgress?.repetition)

        // Third repetition consumed → advances to v2.
        engine.emit(AudioEngineEvent.TrackTransition(1))
        assertEquals("v2", ctrl.state.value.activeVerseId)
    }

    @Test
    fun `C2 every transition drops consumed and bounds the playlist`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        val dropsBefore = engine.dropConsumedCount
        val replacementsBefore = engine.upcomingReplacements.size

        engine.emit(AudioEngineEvent.TrackTransition(1))

        assertEquals(dropsBefore + 1, engine.dropConsumedCount)
        assertEquals(replacementsBefore + 1, engine.upcomingReplacements.size)
        assertTrue(engine.playlist.size <= 3)
    }

    @Test
    fun `C3 setVerseRepeat while playing only replaces the upcoming tail`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        val queueBefore = engine.lastQueue
        engine.resetCalls()

        ctrl.setVerseRepeat(RepeatCount.of(5))

        assertTrue(engine.upcomingReplacements.isNotEmpty())
        assertTrue(engine.lastQueue === queueBefore)
        assertFalse(engine.stopCalled)
        assertNull(engine.seekedToTrack)
        assertEquals(PlaybackStatus.PLAYING, ctrl.state.value.status)
    }

    @Test
    fun `C4 lowering Vr below the completed count finishes the current repetition then advances`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.setVerseRepeat(RepeatCount.Unlimited)
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        repeat(4) { engine.emit(AudioEngineEvent.TrackTransition(1)) }
        assertEquals("v1", ctrl.state.value.activeVerseId)
        assertEquals(5, ctrl.state.value.repetitionProgress?.repetition)

        ctrl.setVerseRepeat(RepeatCount.of(2))
        // The item already playing (5th repetition) is not interrupted.
        assertEquals("v1", ctrl.state.value.activeVerseId)
        assertEquals(5, ctrl.state.value.repetitionProgress?.repetition)

        engine.emit(AudioEngineEvent.TrackTransition(1))
        assertEquals("v2", ctrl.state.value.activeVerseId)
    }

    @Test
    fun `C6 next abandons remaining repetitions and starts the next verse at 1`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.setVerseRepeat(RepeatCount.of(5))
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(1))
        assertEquals("v1", ctrl.state.value.activeVerseId)
        assertEquals(2, ctrl.state.value.repetitionProgress?.repetition)

        ctrl.next()

        assertEquals("v2", ctrl.state.value.activeVerseId)
        assertEquals(1, ctrl.state.value.repetitionProgress?.repetition)
    }

    @Test
    fun `C9 pause then resume preserves the repetition count`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.setVerseRepeat(RepeatCount.of(5))
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(1))
        assertEquals(2, ctrl.state.value.repetitionProgress?.repetition)

        ctrl.pause()
        assertEquals(PlaybackStatus.PAUSED, ctrl.state.value.status)
        ctrl.resume()

        assertEquals(PlaybackStatus.PLAYING, ctrl.state.value.status)
        assertEquals("v1", ctrl.state.value.activeVerseId)
        assertEquals(2, ctrl.state.value.repetitionProgress?.repetition)
    }

    @Test
    fun `C10 seeking backward does not consume an extra repetition`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.setVerseRepeat(RepeatCount.of(3))
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        assertEquals(1, ctrl.state.value.repetitionProgress?.repetition)

        ctrl.seekTo(0)
        assertEquals(1, ctrl.state.value.repetitionProgress?.repetition)

        engine.emit(AudioEngineEvent.TrackTransition(1))
        assertEquals(2, ctrl.state.value.repetitionProgress?.repetition)
    }

    @Test
    fun `C14 stop resets cursor and failed verses but retains settings in the store`() = runTest {
        val store = InMemoryRepetitionSettingsStore()
        val (ctrl, engine) = newController(settingsStore = store)
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        ctrl.setVerseRepeat(RepeatCount.of(7))

        ctrl.stop()

        assertEquals(PlaybackStatus.IDLE, ctrl.state.value.status)
        assertNull(ctrl.state.value.cursor)
        assertEquals(RepeatCount.of(7), store.get("matn-1").verseRepeat)
    }

    @Test
    fun `C15 defaults reproduce Phase 2 behavior end to end`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)

        engine.emit(AudioEngineEvent.TrackTransition(1))
        assertEquals("v2", ctrl.state.value.activeVerseId)

        engine.emit(AudioEngineEvent.TrackTransition(1))
        assertEquals("v3", ctrl.state.value.activeVerseId)

        engine.emit(AudioEngineEvent.QueueEnded)
        assertEquals(PlaybackStatus.ENDED, ctrl.state.value.status)
        assertTrue(ctrl.state.value.notice is PlaybackNotice.ReachedEnd)
    }

    @Test
    fun `C5 loop range never enqueues a verse outside it`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        ctrl.setLoopStart("v2")
        ctrl.setLoopEnd("v3")

        repeat(6) { engine.emit(AudioEngineEvent.TrackTransition(1)) }

        val enqueued = (engine.lastQueue.orEmpty() + engine.upcomingReplacements.flatten())
            .map { it.verseId }
            .toSet()
        assertFalse("v1" in enqueued)
        assertEquals(setOf("v2", "v3"), enqueued)
    }

    @Test
    fun `C7 next at range end wraps to range start`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        ctrl.setLoopStart("v2")
        ctrl.setLoopEnd("v3")
        assertEquals("v2", ctrl.state.value.activeVerseId)

        ctrl.next()
        assertEquals("v3", ctrl.state.value.activeVerseId)

        ctrl.next()
        assertEquals("v2", ctrl.state.value.activeVerseId)
    }

    @Test
    fun `C8 previous at range start wraps to range end`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        ctrl.setLoopStart("v2")
        ctrl.setLoopEnd("v3")
        assertEquals("v2", ctrl.state.value.activeVerseId)

        ctrl.previous()
        assertEquals("v3", ctrl.state.value.activeVerseId)
    }

    @Test
    fun `C17 moving the range away from the active verse relocates to the new range start`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        assertEquals("v1", ctrl.state.value.activeVerseId)

        ctrl.setLoopStart("v2")
        ctrl.setLoopEnd("v3")

        assertEquals("v2", ctrl.state.value.activeVerseId)
        assertEquals(PlaybackStatus.PLAYING, ctrl.state.value.status)
    }

    @Test
    fun `C18 Mr3 plays exactly three passes then ends`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.setMatnRepeat(RepeatCount.of(3))
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)

        val expectedOrder = listOf("v2", "v3", "v1", "v2", "v3", "v1", "v2", "v3")
        for (expected in expectedOrder) {
            engine.emit(AudioEngineEvent.TrackTransition(1))
            assertEquals(expected, ctrl.state.value.activeVerseId)
        }
        assertEquals(3, ctrl.state.value.repetitionProgress?.pass)

        // No 4th pass: the window has run dry, and the real engine's own QueueEnded follows.
        engine.emit(AudioEngineEvent.QueueEnded)
        assertEquals(PlaybackStatus.ENDED, ctrl.state.value.status)
    }

    @Test
    fun `unlimited Vr never advances across 50 transitions then advances after a live retune`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.setVerseRepeat(RepeatCount.Unlimited)
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)

        repeat(50) {
            engine.emit(AudioEngineEvent.TrackTransition(1))
            assertEquals("v1", ctrl.state.value.activeVerseId)
            assertTrue(engine.playlist.isNotEmpty())
        }

        val queueBefore = engine.lastQueue
        engine.resetCalls()
        ctrl.setVerseRepeat(RepeatCount.of(2))
        assertTrue(engine.lastQueue === queueBefore)
        assertFalse(engine.stopCalled)

        engine.emit(AudioEngineEvent.TrackTransition(1))
        assertEquals("v2", ctrl.state.value.activeVerseId)
    }

    @Test
    fun `C11 interruption preserves the repetition count across pause and resume`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.setVerseRepeat(RepeatCount.of(9))
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        repeat(3) { engine.emit(AudioEngineEvent.TrackTransition(1)) }
        assertEquals(4, ctrl.state.value.repetitionProgress?.repetition)

        engine.emit(AudioEngineEvent.InterruptionBegan(transient = true))
        assertEquals(PlaybackStatus.PAUSED, ctrl.state.value.status)
        engine.emit(AudioEngineEvent.InterruptionEnded(shouldResume = true))

        assertEquals(PlaybackStatus.PLAYING, ctrl.state.value.status)
        assertEquals(4, ctrl.state.value.repetitionProgress?.repetition)
        assertEquals("v1", ctrl.state.value.activeVerseId)
    }

    @Test
    fun `C12 a failed verse inside a loop is skipped on later passes with one notice each time`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.setLoopStart("v1")
        ctrl.setLoopEnd("v2")
        ctrl.setMatnRepeat(RepeatCount.of(3))
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)

        // v1 fails while it is the active (window index 0) entry.
        engine.emit(AudioEngineEvent.TrackError(0))
        val notice = ctrl.state.value.notice
        assertTrue(notice is PlaybackNotice.SkippedMissingVerse)
        assertEquals("v1", (notice as PlaybackNotice.SkippedMissingVerse).verseId)
        assertEquals("v2", ctrl.state.value.activeVerseId)

        // Later passes over the range never land back on v1 — only v2 keeps repeating.
        repeat(4) { engine.emit(AudioEngineEvent.TrackTransition(1)) }
        assertEquals("v2", ctrl.state.value.activeVerseId)
    }

    @Test
    fun `C13 every verse in the range failing ends cleanly with NoPlayableAudio`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.setLoopStart("v1")
        ctrl.setLoopEnd("v2")
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)

        engine.emit(AudioEngineEvent.TrackError(0)) // v1 fails → skip to v2
        assertEquals("v2", ctrl.state.value.activeVerseId)
        engine.emit(AudioEngineEvent.TrackError(0)) // v2 (now window index 0) fails too → nothing left

        assertEquals(PlaybackStatus.ENDED, ctrl.state.value.status)
        assertTrue(ctrl.state.value.notice is PlaybackNotice.NoPlayableAudio)
    }

    @Test
    fun `C16 settings are scoped per matn`() = runTest {
        val store = InMemoryRepetitionSettingsStore()
        val (ctrl, engine) = newController(settingsStore = store)
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        ctrl.setVerseRepeat(RepeatCount.of(7))
        assertEquals(RepeatCount.of(7), ctrl.state.value.settings.verseRepeat)

        ctrl.playFromStart("matn-2")
        // The queue use case always returns the fixed `queue`/matn-1 fixture in this fake, but the
        // settings resolution is keyed on the matnId argument regardless of queue contents.
        engine.emit(AudioEngineEvent.Ready)
        assertEquals(RepeatCount.ONE, ctrl.state.value.settings.verseRepeat)

        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        assertEquals(RepeatCount.of(7), ctrl.state.value.settings.verseRepeat)
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
