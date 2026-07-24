package com.giraffe.matn.playback

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.repository.InMemoryRepetitionSettingsStore
import com.giraffe.matn.domain.audio.AudioEngineEvent
import com.giraffe.matn.domain.audio.AudioSourceResolver
import com.giraffe.matn.domain.model.AudioAsset
import com.giraffe.matn.domain.model.AudioTrack
import com.giraffe.matn.domain.model.LoopRange
import com.giraffe.matn.domain.model.PlaybackQueue
import com.giraffe.matn.domain.model.PlaybackStatus
import com.giraffe.matn.domain.model.PauseReason
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.RepetitionSettings
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.domain.repository.AudioAssetRepository
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
 * Phase 4 resume-path behaviour of [PlaybackController] (T031b/T035b — a NEW file; the
 * pre-existing [PlaybackControllerTest] is untouched, Rule 1).
 *
 * - **FR-022a / E3**: a resume that begins while another app holds audio focus must restore the
 *   session fully but sit PAUSED with [PauseReason.NON_TRANSIENT_INTERRUPTION] — never report
 *   PLAYING over a call, never silently die (Principle VII).
 * - **FR-020 / T035b**: starting a session for a matn whose stored settings contain a [LoopRange]
 *   must yield a non-empty `loopRangeVerseIds` covering exactly the inclusive range — the guard
 *   that keeps T035a's one-line fix from silently regressing.
 */
class SessionResumeFocusTest {

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
        settingsStore: InMemoryRepetitionSettingsStore = InMemoryRepetitionSettingsStore(),
    ): Pair<PlaybackController, FakeAudioEngine> {
        val engine = FakeAudioEngine()
        val ctrl = PlaybackController(
            engine = engine,
            buildQueue = FakeBuildQueue(Resource.Success(queue)),
            wakeLock = FakeWakeLock(),
            settingsStore = settingsStore,
            // Same parenting rationale as PlaybackControllerTest: the info-polling loop must live
            // under backgroundScope's Job so runTest's idle-drain isn't livelocked, while keeping
            // the UnconfinedTestDispatcher so engine.emit(...) resolves synchronously.
            scope = CoroutineScope(SupervisorJob(backgroundScope.coroutineContext[Job]) + dispatcher),
        )
        controllers.add(ctrl)
        return ctrl to engine
    }

    // ---- FR-022a: focus denial during session start ----------------------

    @Test
    fun `focus denial during resume start restores the session paused not playing`() = runTest {
        val store = InMemoryRepetitionSettingsStore()
        val savedSettings = RepetitionSettings(
            verseRepeat = RepeatCount.of(3),
            matnRepeat = RepeatCount.Unlimited,
        )
        store.put("matn-1", savedSettings)
        val (ctrl, engine) = newController(settingsStore = store)

        // Continue Learning resume: mid-verse position, before the engine reports Ready.
        ctrl.playFromVerse("matn-1", "v2", startPositionMs = 3_000)
        assertEquals(PlaybackStatus.LOADING, ctrl.state.value.status)

        // Another app holds focus (active call): the engine refuses to play and emits a
        // non-transient interruption while the controller is still LOADING.
        engine.emit(AudioEngineEvent.InterruptionBegan(transient = false))

        val state = ctrl.state.value
        assertEquals(PlaybackStatus.PAUSED, state.status, "denial during start must be honoured (FR-022a)")
        assertEquals(PauseReason.NON_TRANSIENT_INTERRUPTION, state.pauseReason)
        // The session restored fully — matn, verse, position, and settings all present.
        assertEquals("matn-1", state.matnId)
        assertEquals("v2", state.activeVerseId)
        assertEquals(3_000L, engine.seekedToMs, "resume position must be applied before the denial pauses")
        assertEquals(savedSettings.verseRepeat, state.settings.verseRepeat)
        assertEquals(savedSettings.matnRepeat, state.settings.matnRepeat)
        // The engine is not left playing.
        assertTrue(engine.pauseCalled, "engine must be paused on a focus denial (E3)")
    }

    @Test
    fun `transient interruption during LOADING is still ignored`() = runTest {
        val (ctrl, engine) = newController()
        ctrl.playFromVerse("matn-1", "v1", startPositionMs = 0)
        assertEquals(PlaybackStatus.LOADING, ctrl.state.value.status)

        // A transient dip before start completes is owned by the existing auto-resume path —
        // it must NOT flip the starting session to PAUSED (T031a scope guard).
        engine.emit(AudioEngineEvent.InterruptionBegan(transient = true))
        assertEquals(PlaybackStatus.LOADING, ctrl.state.value.status)
        assertFalse(engine.pauseCalled)
    }

    // ---- FR-020 / T035b: restored loop range is visibly marked -----------

    @Test
    fun `starting a session with stored loop settings marks the loop range verses`() = runTest {
        val store = InMemoryRepetitionSettingsStore()
        store.put(
            "matn-1",
            RepetitionSettings(loopRange = LoopRange(startVerseId = "v1", endVerseId = "v2")),
        )
        val (ctrl, engine) = newController(settingsStore = store)

        ctrl.playFromStart("matn-1")
        engine.emit(AudioEngineEvent.Ready)

        // Exactly the verse IDs inside the inclusive range — not empty (the latent Phase-3 bug),
        // not the whole matn.
        assertEquals(setOf("v1", "v2"), ctrl.state.value.loopRangeVerseIds)
    }

    // ---- harness fakes (mirrors PlaybackControllerTest; that file is untouchable) ----

    private class FakeBuildQueue(result: Resource<PlaybackQueue>) : BuildPlaybackQueueUseCase(
        verseRepository = StubVerseRepo,
        audioRepository = StubAudioRepo,
        audioSourceResolver = StubResolver,
    ) {
        private val result: Resource<PlaybackQueue> = result

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
