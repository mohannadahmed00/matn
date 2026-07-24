package com.giraffe.matn.playback

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.audio.AudioEngineEvent
import com.giraffe.matn.domain.audio.AudioSourceResolver
import com.giraffe.matn.domain.model.AudioAsset
import com.giraffe.matn.domain.model.AudioTrack
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.model.PlaybackQueue
import com.giraffe.matn.domain.model.PlaybackStatus
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.domain.repository.AudioAssetRepository
import com.giraffe.matn.domain.repository.ProgressRepository
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
import kotlin.test.assertTrue

/**
 * contracts/practice-signal-contract.md § 4 — drives a REAL [PlaybackController] + [FakeAudioEngine]
 * through scripted [AudioEngineEvent]s (same harness as [PlaybackControllerTest]) so the recall-mode
 * gate and the suppress-on-user-transport behavior are exercised against the actual production
 * logic, not a hand-fabricated state transition.
 */
class PracticeSignalRecorderTest {

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

    private class RecordingProgressRepository : ProgressRepository {
        val recordedVerseIds = mutableListOf<String>()
        override suspend fun setVerseMemorized(verseId: String, memorized: Boolean): Resource<Unit> =
            Resource.Success(Unit)
        override suspend fun setChapterMemorized(chapterId: String, memorized: Boolean): Resource<Unit> =
            Resource.Success(Unit)
        override fun observeMemorizedVerseIds(matnId: String) = throw NotImplementedError()
        override fun observeMatnProgress(matnId: String): Flow<MatnProgress> = throw NotImplementedError()
        override fun observeLibraryProgress(): Flow<List<MatnProgress>> = throw NotImplementedError()
        override suspend fun recordPractice(verseId: String): Resource<Unit> {
            recordedVerseIds += verseId
            return Resource.Success(Unit)
        }
        override fun observeTodayPracticeCount() = throw NotImplementedError()
    }

    private fun TestScope.newSetup(
        queueResult: Resource<PlaybackQueue> = Resource.Success(queue),
    ): Triple<PlaybackController, FakeAudioEngine, RecordingProgressRepository> {
        val engine = FakeAudioEngine()
        val buildQueue = FakeBuildQueueForRecorder(queueResult)
        val ctrl = PlaybackController(
            engine = engine,
            buildQueue = buildQueue,
            wakeLock = FakeWakeLock(),
            scope = CoroutineScope(SupervisorJob(backgroundScope.coroutineContext[Job]) + dispatcher),
        )
        controllers.add(ctrl)
        val repo = RecordingProgressRepository()
        val recorder = PracticeSignalRecorder(ctrl.state, repo, CoroutineScope(SupervisorJob(backgroundScope.coroutineContext[Job]) + dispatcher))
        recorder.start()
        return Triple(ctrl, engine, repo)
    }

    @Test
    fun `memorization-mode session credits exactly one row for the completed verse`() = runTest {
        val (ctrl, engine, repo) = newSetup()
        ctrl.setVerseRepeat(RepeatCount.of(2))
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(1)) // v1 finished naturally -> v2

        assertEquals(listOf("v1"), repo.recordedVerseIds)
    }

    @Test
    fun `normal-mode session credits nothing`() = runTest {
        val (ctrl, engine, repo) = newSetup()
        ctrl.playFromStart("matn-1") // default settings: Vr=1, Mr=1, no loop
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(1))
        engine.emit(AudioEngineEvent.TrackTransition(2))

        assertTrue(repo.recordedVerseIds.isEmpty())
    }

    @Test
    fun `A-B loop session credits a completed verse in the loop`() = runTest {
        val (ctrl, engine, repo) = newSetup()
        ctrl.setLoopStart("v1")
        ctrl.setLoopEnd("v2")
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(1))

        assertEquals(listOf("v1"), repo.recordedVerseIds)
    }

    @Test
    fun `same verse completed twice in one day credits twice - dedup is the repository's job`() = runTest {
        val (ctrl, engine, repo) = newSetup()
        ctrl.setVerseRepeat(RepeatCount.of(2)) // v1 windows as two repetition passes before v2
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(1)) // pass 1 -> pass 2: completes v1
        engine.emit(AudioEngineEvent.TrackTransition(1)) // pass 2 -> v2: completes v1 again

        // The recorder calls recordPractice twice for the same verse — it never dedups itself;
        // the DB's UNIQUE(day_epoch, verse_id) + OR IGNORE absorbs the duplicate (proven in T013).
        assertEquals(listOf("v1", "v1"), repo.recordedVerseIds)
    }

    @Test
    fun `user next followed by a TrackTransition credits nothing`() = runTest {
        val (ctrl, engine, repo) = newSetup()
        ctrl.setVerseRepeat(RepeatCount.of(2))
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        ctrl.next() // user-initiated: not a completion
        engine.emit(AudioEngineEvent.TrackTransition(1))

        assertTrue(repo.recordedVerseIds.isEmpty())
    }

    @Test
    fun `QueueEnded in a recall session credits the final verse`() = runTest {
        val (ctrl, engine, repo) = newSetup()
        ctrl.setMatnRepeat(RepeatCount.of(2)) // Mr != ONE: recall mode, finite (ends eventually)
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.QueueEnded) // ends while v1 (the final active verse) is up

        assertEquals(listOf("v1"), repo.recordedVerseIds)
    }

    @Test
    fun `the recorder never mutates controller state`() = runTest {
        val (ctrl, engine, _) = newSetup()
        ctrl.setVerseRepeat(RepeatCount.of(2))
        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)
        engine.emit(AudioEngineEvent.TrackTransition(1))

        // The recorder is a pure observer: the controller's own decisions are untouched. With
        // Vr=2 this single transition is the pass-1 -> pass-2 repeat, so the active verse is
        // still v1 (matches PlaybackControllerTest's own C1 window-repeat precedent).
        assertEquals(PlaybackStatus.PLAYING, ctrl.state.value.status)
        assertEquals("v1", ctrl.state.value.activeVerseId)
        assertEquals(0, ctrl.state.value.activeIndex)
        assertFalse(engine.stopCalled)
    }

    // ---- fake queue use case (mirrors PlaybackControllerTest's FakeBuildQueue) ----

    private class FakeBuildQueueForRecorder(private val result: Resource<PlaybackQueue>) : BuildPlaybackQueueUseCase(
        verseRepository = StubVerseRepo,
        audioRepository = StubAudioRepo,
        audioSourceResolver = StubResolver,
    ) {
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
