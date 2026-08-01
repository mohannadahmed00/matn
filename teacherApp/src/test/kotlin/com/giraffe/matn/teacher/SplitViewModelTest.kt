package com.giraffe.matn.teacher

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.audio.ByteSource
import com.giraffe.matn.domain.audio.AudioSlicer
import com.giraffe.matn.domain.audio.PendingUpload
import com.giraffe.matn.domain.audio.VerseRange
import com.giraffe.matn.domain.audio.VerseSlice
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.MatnDraftFactory
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.usecase.ApplySplitUseCase
import com.giraffe.matn.domain.usecase.LoadSplitSourceUseCase
import com.giraffe.matn.teacher.platform.JLayerAudioProbe
import com.giraffe.matn.teacher.presentation.split.ActiveBoundary
import com.giraffe.matn.teacher.presentation.split.SplitUiState
import com.giraffe.matn.teacher.presentation.split.SplitViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Writes a valid, synthetic MPEG-1 Layer III mono 44.1 kHz file — same construction as
 * `Mp3Fixtures` in `:shared` commonTest (not visible from this module's test source set), silent
 * bodies, real headers. */
private fun createTempMp3(frames: Int = 40): File {
    val header = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0xC0.toByte())
    val frameLen = 417
    val file = File.createTempFile("split-test", ".mp3")
    file.deleteOnExit()
    file.outputStream().use { out ->
        repeat(frames) {
            out.write(header)
            out.write(ByteArray(frameLen - 4))
        }
    }
    return file
}

/** Records what it was asked to cut, so the audition path can be checked for slicing *only* the
 * range under audition rather than the whole plan. */
private class FakeSlicer : AudioSlicer {
    val sliceCalls = mutableListOf<List<VerseRange>>()

    override suspend fun slice(source: ByteSource, index: com.giraffe.matn.data.audio.Mp3FrameIndex, ranges: List<VerseRange>): Resource<List<VerseSlice>> {
        sliceCalls += ranges
        return Resource.Success(ranges.map { r -> VerseSlice(r.verseId, "${r.verseId}:${r.startMs}:${r.endMs}".encodeToByteArray(), r.endMs - r.startMs) })
    }
}

/**
 * Honours the real contract: `playClip` **suspends for the duration of the clip**, and starting one
 * stops whatever was playing. A fake that returned immediately would make the ViewModel look
 * correct while hiding exactly the desync bug these tests exist for — the audition job would finish
 * instantly, clear its own state, and every "is it still playing?" assertion would pass vacuously.
 *
 * [liveCount] is what proves two clips never overlap; [finish] ends a clip as if it had played out.
 */
private class RecordingPreviewPlayer : com.giraffe.matn.domain.audio.PreviewPlayer {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow<com.giraffe.matn.domain.audio.PreviewState>(
        com.giraffe.matn.domain.audio.PreviewState.Idle,
    )
    override val state: kotlinx.coroutines.flow.StateFlow<com.giraffe.matn.domain.audio.PreviewState> = _state

    val playedClips = mutableListOf<String>()
    @Volatile var started = false
    @Volatile var liveCount = 0
    private var gate = kotlinx.coroutines.CompletableDeferred<Unit>().apply { complete(Unit) }

    override suspend fun play(verses: List<com.giraffe.matn.domain.audio.PreviewVerse>, startIndex: Int) = Unit

    override suspend fun playClip(bytes: ByteArray, displayNumber: Int) {
        stop()
        val myGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        gate = myGate
        synchronized(playedClips) { playedClips += bytes.decodeToString() }
        liveCount++
        started = true
        _state.value = com.giraffe.matn.domain.audio.PreviewState.Playing(displayNumber, 0)
        try {
            myGate.await()
        } finally {
            liveCount--
        }
    }

    fun finish() {
        gate.complete(Unit)
        _state.value = com.giraffe.matn.domain.audio.PreviewState.Idle
    }

    /** Reports progress **within the clip** — position 0 is the clip's first frame, exactly as the
     * real player does. Mapping that onto the source timeline is the ViewModel's job. */
    fun emitPosition(displayNumber: Int, positionMs: Long) {
        _state.value = com.giraffe.matn.domain.audio.PreviewState.Playing(displayNumber, positionMs)
    }

    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop() {
        gate.cancel()
        _state.value = com.giraffe.matn.domain.audio.PreviewState.Idle
    }
}

/** [splitFailure] stands in for the backend refusing the commit — the bucket turning away
 * `audio/mpeg`, RLS denying the write, the connection dropping mid-upload. */
private class NoOpRepository(private val splitFailure: com.giraffe.matn.core.AppError? = null) : CatalogRepository {
    override fun observeAuthored(): Flow<List<CatalogEntry>> = flowOf(emptyList())
    override suspend fun load(matnId: String): Resource<MatnDraft> = Resource.Failure(com.giraffe.matn.core.AppError.NotFound)
    override suspend fun save(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun publish(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun unpublish(matnId: String): Resource<MatnDraft> = Resource.Failure(com.giraffe.matn.core.AppError.NotFound)
    override suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String> = Resource.Success("ref")
    override suspend fun downloadCover(objectPath: String): Resource<ByteArray> = Resource.Success(ByteArray(0))
    override suspend fun attachVerseAudio(draft: MatnDraft, verseId: String, audio: DraftAudio, bytes: ByteArray): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun removeVerseAudio(draft: MatnDraft, verseId: String): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun applySplit(draft: MatnDraft, updates: Map<String, DraftAudio>, payloads: List<PendingUpload>): Resource<MatnDraft> =
        splitFailure?.let { Resource.Failure(it) }
            ?: Resource.Success(draft.copy(verses = draft.verses.map { v -> updates[v.id]?.let { v.copy(audio = it) } ?: v }))
}

private fun draftWithVerses(count: Int): MatnDraft {
    val base = MatnDraftFactory.newDraft(newId = { "m1" }, nowMillis = { 0L }, title = "T", author = "A")
    val verses = (1..count).map { i -> DraftVerse(id = "v$i", chapterId = null, displayNumber = i, arabicText = "text $i", audio = null, durationMs = 0L) }
    return base.copy(verses = verses)
}

class SplitViewModelTest {

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    /** `Dispatchers.setMain` must share **this** `runTest`'s own `testScheduler` — a
     * `StandardTestDispatcher()` created separately (e.g. in `@BeforeTest`) carries a different,
     * disconnected scheduler, so `advanceUntilIdle()` would advance the wrong one and never drain
     * work the ViewModel launches on `Dispatchers.Main`. */
    private fun kotlinx.coroutines.test.TestScope.setUpMain() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    /** [SplitViewModel.onSourcePicked] genuinely crosses onto `Dispatchers.IO` for the real file
     * read (`JvmFileByteSource`), which is not tied to the `StandardTestDispatcher` scheduler —
     * `advanceUntilIdle()` alone cannot wait for that real background-thread completion. A short
     * bounded real-time poll is the pragmatic fix without threading a test dispatcher into
     * production file I/O. */
    private fun kotlinx.coroutines.test.TestScope.awaitReady(vm: SplitViewModel) =
        awaitUntil { vm.state.value is SplitUiState.Ready }

    /** Same reason as [awaitReady]: any ViewModel path that hops to `Dispatchers.Default` (the
     * audition slice, the load) completes on a real thread the test scheduler does not own, so
     * `advanceUntilIdle()` alone can return before it has run. */
    private fun kotlinx.coroutines.test.TestScope.awaitUntil(predicate: () -> Boolean) {
        repeat(50) {
            advanceUntilIdle()
            if (predicate()) return
            Thread.sleep(20)
        }
        advanceUntilIdle()
    }

    private fun newViewModel(
        draft: MatnDraft,
        slicer: FakeSlicer = FakeSlicer(),
        player: com.giraffe.matn.domain.audio.PreviewPlayer = RecordingPreviewPlayer(),
        onDone: (MatnDraft) -> Unit = {},
        splitFailure: com.giraffe.matn.core.AppError? = null,
    ): SplitViewModel {
        val probe = JLayerAudioProbe()
        return SplitViewModel(
            draft = draft,
            loadSplitSource = LoadSplitSourceUseCase(probe),
            applySplit = ApplySplitUseCase(NoOpRepository(splitFailure), slicer, newId = { "new-id" }),
            audioProbe = probe,
            slicer = slicer,
            previewPlayer = player,
            onSplitComplete = onDone,
        )
    }

    /**
     * Regression for a shipped bug. The bucket had not yet been widened to accept `audio/mpeg`, so
     * the upload came back 400 — and the screen threw the entire plan away and offered nothing but
     * "pick a recording". Minutes of boundary work, gone to a server-side setting the teacher could
     * neither see nor have caused. The failure now reports itself *inside* `Ready`, leaving every
     * range where it was so the same upload can simply be retried.
     */
    @Test
    fun `a failed upload keeps the plan and reports the error in place`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2), splitFailure = RemoteError.Rejected)
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        // Both ranges must fall inside the 150-frame source (~3.9 s), or the plan is blocking and
        // the upload never leaves the button.
        vm.onRangeChanged("v1", 0, 1000)
        vm.onRangeChanged("v2", 1000, 2000)

        vm.onSplitAndUpload()
        awaitUntil { (vm.state.value as? SplitUiState.Ready)?.uploadError != null }

        val after = vm.state.value as SplitUiState.Ready
        assertEquals(RemoteError.Rejected, after.uploadError)
        assertEquals(2, after.ranges.size)
        assertEquals(1000L, after.ranges.first { it.verseId == "v1" }.endMs)

        // Touching anything retracts the verdict — it was about the plan as submitted.
        vm.onRangeChanged("v1", 0, 1200)
        assertEquals(null, (vm.state.value as SplitUiState.Ready).uploadError)
    }

    /** Consecutive verses in one recording abut, so the teacher asked not to type the same number
     * twice. Setting an End seeds the next verse's range starting exactly there. */
    @Test
    fun `committing a verse End seeds the next verse's Start at the same instant`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(3))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)

        vm.onRangeChanged("v1", 0, 1000)
        vm.onEndCommitted("v1")

        val after = vm.state.value as SplitUiState.Ready
        assertEquals(1000L, after.pendingStarts["v2"])
        // The Start only. Guessing an End would be the tool deciding where a verse finishes, and it
        // would put a marker and a highlight on the timeline for a range nobody has chosen.
        assertTrue(after.ranges.none { it.verseId == "v2" })
    }

    /** Typing the End of a verse whose Start was auto-filled turns the pair into a real range, and
     * accepting the offered Start does not count as overriding it. */
    @Test
    fun `setting the End of an auto-filled verse completes its range`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(3))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 1000)
        vm.onEndCommitted("v1")

        vm.onRangeChanged("v2", 1000, 2000)

        val after = vm.state.value as SplitUiState.Ready
        assertEquals(VerseRange("v2", 1000, 2000), after.ranges.first { it.verseId == "v2" })
        assertTrue("v2" !in after.pendingStarts)

        // Still linked: nudging v1's End moves v2's Start, because the teacher accepted it rather
        // than replacing it.
        vm.onRangeChanged("v1", 0, 1100)
        vm.onEndCommitted("v1")
        assertEquals(1100L, (vm.state.value as SplitUiState.Ready).ranges.first { it.verseId == "v2" }.startMs)
    }

    /**
     * The teacher's first complaint: nothing may appear in the *next* verse while the current one is
     * still being typed. Digits arrive one at a time, so chaining per keystroke wrote a start of 5,
     * then 50, then 500 into a verse they had not reached yet.
     */
    @Test
    fun `typing an End does not touch the next verse until it is committed`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(3))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)

        // Digit by digit, as the field reports it.
        vm.onRangeChanged("v1", 0, 1)
        vm.onRangeChanged("v1", 0, 10)
        vm.onRangeChanged("v1", 0, 100)

        val after = vm.state.value as SplitUiState.Ready
        assertTrue(after.ranges.none { it.verseId == "v2" })
        assertTrue("v2" !in after.pendingStarts)
    }

    /** Fine-tuning the same End must carry the next verse with it, or every adjustment opens a gap
     * the teacher has to close by hand. */
    @Test
    fun `adjusting an End again moves the next Start that is still auto-filled`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(3))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 1000)
        vm.onEndCommitted("v1")

        vm.onRangeChanged("v1", 0, 1200)
        vm.onEndCommitted("v1")

        assertEquals(1200L, (vm.state.value as SplitUiState.Ready).pendingStarts["v2"])
    }

    /** …but only while it *is* auto-filled. Once the teacher places that start themselves it is
     * theirs, and a convenience feature may not overwrite a deliberate decision. */
    @Test
    fun `a next Start the teacher moved is left alone`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(3))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 1000)
        vm.onEndCommitted("v1")
        // The teacher skips a breath: v2 now starts later than v1 ends, on purpose.
        vm.onRangeChanged("v2", 1500, 2500)

        vm.onRangeChanged("v1", 0, 1100)
        vm.onEndCommitted("v1")

        assertEquals(1500L, (vm.state.value as SplitUiState.Ready).ranges.first { it.verseId == "v2" }.startMs)
    }

    /** Emptying a field must take the marker and the highlight with it — both are drawn from
     * `ranges`, so a range left behind is a marker for a boundary that no longer exists. */
    @Test
    fun `clearing a boundary removes the range so its marker disappears`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 1000)

        vm.onRangeCleared("v1")

        assertTrue((vm.state.value as SplitUiState.Ready).ranges.none { it.verseId == "v1" })
    }

    /** Losing focus hands the waveform back to the playhead. Without it the last field touched
     * stayed armed and every drag moved that boundary instead of the transport. */
    @Test
    fun `releasing a focused boundary field disarms it`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onBoundarySelected("v1", isStart = true)

        vm.onBoundaryCleared("v1", isStart = true)

        assertEquals(null, (vm.state.value as SplitUiState.Ready).activeBoundary)
    }

    /** Tabbing from Start to End fires "gained" for End *before* "lost" for Start. Only the field
     * that still holds the boundary may release it, or the arrival disarms itself. */
    @Test
    fun `a stale release does not disarm the boundary that replaced it`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)

        vm.onBoundarySelected("v1", isStart = true)
        vm.onBoundarySelected("v1", isStart = false) // End gains focus
        vm.onBoundaryCleared("v1", isStart = true) // Start then reports it lost focus

        assertEquals(ActiveBoundary("v1", isStart = false), (vm.state.value as SplitUiState.Ready).activeBoundary)
    }

    @Test
    fun `scrubbing an armed End boundary moves it and reports the live position`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 2000)

        vm.onBoundarySelected("v1", isStart = false)
        vm.onScrub(2500)

        val updated = vm.state.value as SplitUiState.Ready
        assertEquals(2500, updated.ranges.first { it.verseId == "v1" }.endMs)
        assertEquals(2500, updated.scrubMs) // live readout while the pointer is down
        vm.onScrubEnd()
        assertEquals(null, (vm.state.value as SplitUiState.Ready).scrubMs)
    }

    /**
     * The on-waveform marker and its timestamp are driven by this value, and must survive the end
     * of a drag: previously they were tied to `scrubMs` alone, so releasing the pointer wiped the
     * only on-waveform indication of where the boundary being edited actually sits.
     */
    @Test
    fun `the armed boundary keeps a position after the drag ends`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 2000)

        vm.onBoundarySelected("v1", isStart = false)
        vm.onScrub(2500)
        vm.onScrubEnd()

        val ready = vm.state.value as SplitUiState.Ready
        assertEquals(null, ready.scrubMs) // the drag is over…
        assertEquals(2500, ready.armedBoundaryMs) // …but the marker still has somewhere to be
    }

    @Test
    fun `arming a Start field reports that boundary's position, not the End's`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 750, 2000)

        vm.onBoundarySelected("v1", isStart = true)
        assertEquals(750, (vm.state.value as SplitUiState.Ready).armedBoundaryMs)

        vm.onBoundarySelected("v1", isStart = false)
        assertEquals(2000, (vm.state.value as SplitUiState.Ready).armedBoundaryMs)
    }

    @Test
    fun `an armed verse with no range yet has no marker position`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)

        vm.onBoundarySelected("v2", isStart = true)

        assertEquals(null, (vm.state.value as SplitUiState.Ready).armedBoundaryMs)
    }

    /** A drag with nothing armed must not silently rewrite whichever boundary happened to be last
     * touched — the teacher has to point at a field first. */
    @Test
    fun `scrubbing with no armed boundary changes nothing`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 2000)

        vm.onScrub(9000)

        val updated = vm.state.value as SplitUiState.Ready
        assertEquals(2000, updated.ranges.first { it.verseId == "v1" }.endMs)
        assertEquals(null, updated.scrubMs)
    }

    /** First drag on a verse that has no range yet should create one, not be ignored. */
    @Test
    fun `scrubbing a verse with no range seeds one around the scrub position`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)

        vm.onBoundarySelected("v2", isStart = true)
        vm.onScrub(1000)

        val range = (vm.state.value as SplitUiState.Ready).ranges.first { it.verseId == "v2" }
        assertEquals(1000, range.startMs)
        assertTrue(range.endMs > range.startMs)
    }

    @Test
    fun `a scrub is clamped to the source duration`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        val durationMs = (vm.state.value as SplitUiState.Ready).source.durationMs
        vm.onRangeChanged("v1", 0, 500)

        vm.onBoundarySelected("v1", isStart = false)
        vm.onScrub(durationMs + 60_000)

        assertEquals(durationMs, (vm.state.value as SplitUiState.Ready).ranges.first { it.verseId == "v1" }.endMs)
    }

    @Test
    fun `an overlap disables the split action`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)

        vm.onRangeChanged("v1", 0, 2000)
        vm.onRangeChanged("v2", 1000, 3000)

        val state = vm.state.value as SplitUiState.Ready
        assertTrue(state.report.blocking.isNotEmpty())
    }

    @Test
    fun `changing the scope drops out-of-scope ranges`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(3))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)

        vm.onRangeChanged("v1", 0, 1000)
        vm.onRangeChanged("v2", 1000, 2000)
        vm.onRangeChanged("v3", 2000, 3000)

        vm.onScopeChanged("v1", "v2") // drops v3 from scope

        val state = vm.state.value as SplitUiState.Ready
        assertEquals(listOf("v1", "v2"), state.scopeVerseIds)
        assertEquals(setOf("v1", "v2"), state.ranges.map { it.verseId }.toSet())
    }

    @Test
    fun `replacing the source clears every range`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 1000)

        vm.onReplaceSource()

        assertIs<SplitUiState.NoSource>(vm.state.value)
    }

    @Test
    fun `auditioning a range slices only that range and plays the resulting clip`() = runTest {
        setUpMain()
        val slicer = FakeSlicer()
        val player = RecordingPreviewPlayer()
        val vm = newViewModel(draftWithVerses(3), slicer = slicer, player = player)
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 1000)
        vm.onRangeChanged("v2", 1000, 2000)
        slicer.sliceCalls.clear()

        vm.onAuditionRange("v2")
        awaitUntil { player.playedClips.isNotEmpty() }

        // Exactly one cut, for v2 alone — not the whole plan.
        assertEquals(1, slicer.sliceCalls.size)
        assertEquals(listOf(VerseRange("v2", 1000, 2000)), slicer.sliceCalls.single())
        // And what plays is the sliced bytes, i.e. what would be uploaded.
        assertEquals(listOf("v2:1000:2000"), player.playedClips)
        assertEquals("v2", (vm.state.value as SplitUiState.Ready).auditioningVerseId)
    }

    @Test
    fun `auditioning the already-playing verse stops it instead of restarting`() = runTest {
        setUpMain()
        val player = RecordingPreviewPlayer()
        val vm = newViewModel(draftWithVerses(2), player = player)
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 1000)
        vm.onAuditionRange("v1")
        awaitUntil { player.playedClips.isNotEmpty() }

        vm.onAuditionRange("v1")

        assertEquals(1, player.playedClips.size) // not played a second time
        assertEquals(null, (vm.state.value as SplitUiState.Ready).auditioningVerseId)
    }

    /**
     * Regression for a shipped bug: `playClip` internally stops the previous playback, which emitted
     * `Idle`, and the ViewModel cleared `auditioningVerseId` on *any* `Idle` — so the moment
     * playback started the state claimed nothing was playing. The Stop control never appeared, and
     * because the state said "idle" a second press started a second overlapping clip instead of
     * stopping the first.
     */
    @Test
    fun `the auditioning verse stays set while the clip is actually playing`() = runTest {
        setUpMain()
        val player = RecordingPreviewPlayer()
        val vm = newViewModel(draftWithVerses(2), player = player)
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 1000)

        vm.onAuditionRange("v1")
        awaitUntil { player.started }

        // Still playing — the control must be offering Stop, not Play.
        assertEquals("v1", (vm.state.value as SplitUiState.Ready).auditioningVerseId)

        player.finish()
        awaitUntil { (vm.state.value as SplitUiState.Ready).auditioningVerseId == null }
        assertEquals(null, (vm.state.value as SplitUiState.Ready).auditioningVerseId)
    }

    /** The other half of the same bug: repeated presses must never leave two clips sounding. */
    @Test
    fun `pressing play repeatedly never leaves more than one playback live`() = runTest {
        setUpMain()
        val player = RecordingPreviewPlayer()
        val vm = newViewModel(draftWithVerses(2), player = player)
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 1000)
        vm.onRangeChanged("v2", 1000, 2000)

        vm.onAuditionRange("v1")
        awaitUntil { player.started }
        vm.onAuditionRange("v2")
        awaitUntil { player.liveCount == 1 && player.started }

        assertEquals(1, player.liveCount)
        assertEquals("v2", (vm.state.value as SplitUiState.Ready).auditioningVerseId)
    }

    /**
     * The playhead is drawn against the *source* waveform, but the player reports position within
     * the clip (starting at 0). Without the range offset the marker would jump to the start of the
     * recording every time an audition began, whatever verse was playing.
     */
    @Test
    fun `the playhead maps clip position onto the source timeline`() = runTest {
        setUpMain()
        val player = RecordingPreviewPlayer()
        val vm = newViewModel(draftWithVerses(2), player = player)
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 1000, 3000)

        vm.onAuditionRange("v1")
        awaitUntil { player.started }
        player.emitPosition(displayNumber = 1, positionMs = 500)
        awaitUntil { (vm.state.value as SplitUiState.Ready).playheadMs == 1500L }

        assertEquals(1500, (vm.state.value as SplitUiState.Ready).playheadMs)
    }

    /** The playhead is the transport position, not a property of the audition — stopping must leave
     * it where playback reached so the teacher keeps their place in the recording. */
    @Test
    fun `stopping the audition leaves the playhead where it reached`() = runTest {
        setUpMain()
        val player = RecordingPreviewPlayer()
        val vm = newViewModel(draftWithVerses(2), player = player)
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 1000, 3000)
        vm.onAuditionRange("v1")
        awaitUntil { player.started }
        player.emitPosition(displayNumber = 1, positionMs = 500)
        awaitUntil { (vm.state.value as SplitUiState.Ready).playheadMs == 1500L }

        vm.onAuditionRange("v1") // second press = stop

        val ready = vm.state.value as SplitUiState.Ready
        assertEquals(null, ready.auditioningVerseId)
        assertEquals(1500, ready.playheadMs)
    }

    /**
     * Regression: a cancelled source playback's `finally` runs asynchronously, so it used to land
     * *after* its replacement had set `isPlayingSource = true` and clear the flag out from under
     * it — the button showed "play" while audio kept sounding, with no way back.
     */
    @Test
    fun `restarting source playback is not un-set by the job it replaced`() = runTest {
        setUpMain()
        val player = RecordingPreviewPlayer()
        val vm = newViewModel(draftWithVerses(2), player = player)
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)

        vm.onTogglePlaySource()
        awaitUntil { player.started }
        assertTrue((vm.state.value as SplitUiState.Ready).isPlayingSource)

        // Stop and immediately restart: the first job's teardown races the second's startup.
        vm.onTogglePlaySource()
        vm.onTogglePlaySource()
        awaitUntil { player.liveCount == 1 }

        assertTrue(
            (vm.state.value as SplitUiState.Ready).isPlayingSource,
            "the replaced playback cleared the flag belonging to the one that replaced it",
        )
    }

    /** Grabbing the transport must leave no armed boundary behind, or the next drag would silently
     * edit a verse the teacher thought they had moved on from. */
    @Test
    fun `starting a seek stops playback and disarms the focused boundary`() = runTest {
        setUpMain()
        val player = RecordingPreviewPlayer()
        val vm = newViewModel(draftWithVerses(2), player = player)
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 2000)
        vm.onBoundarySelected("v1", isStart = true)
        vm.onTogglePlaySource()
        awaitUntil { player.started }

        vm.onSeekStart()

        val ready = vm.state.value as SplitUiState.Ready
        assertEquals(null, ready.activeBoundary)
        assertEquals(null, ready.armedBoundaryMs)
        assertTrue(!ready.isPlayingSource)
    }

    /** The core separation the transport exists for: seeking to listen must never edit the plan. */
    @Test
    fun `seeking moves the playhead and leaves every range untouched`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        vm.onRangeChanged("v1", 0, 2000)
        vm.onRangeChanged("v2", 2000, 3000)
        // Arm a boundary too: even with a field focused, a *seek* must not touch it.
        vm.onBoundarySelected("v1", isStart = false)
        val before = (vm.state.value as SplitUiState.Ready).ranges.sortedBy { it.verseId }

        vm.onSeekStart()
        vm.onSeek(1234)
        vm.onSeekEnd()

        val ready = vm.state.value as SplitUiState.Ready
        assertEquals(1234, ready.playheadMs)
        assertEquals(before, ready.ranges.sortedBy { it.verseId })
    }

    @Test
    fun `a seek is clamped to the source duration`() = runTest {
        setUpMain()
        val vm = newViewModel(draftWithVerses(2))
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        val durationMs = (vm.state.value as SplitUiState.Ready).source.durationMs

        vm.onSeek(durationMs + 60_000)

        assertEquals(durationMs, (vm.state.value as SplitUiState.Ready).playheadMs)
    }

    /** While the pointer owns the playhead, buffered audio must not drag it back. */
    @Test
    fun `player position updates are ignored while seeking`() = runTest {
        setUpMain()
        val player = RecordingPreviewPlayer()
        val vm = newViewModel(draftWithVerses(2), player = player)
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)

        // Well inside the fixture's ~3.9 s duration, so nothing is clamped and the assertion is
        // about the seek/playback precedence rather than the bounds check.
        vm.onSeek(2000) // drag in progress — isSeeking is set
        player.emitPosition(displayNumber = 0, positionMs = 250)

        assertEquals(2000, (vm.state.value as SplitUiState.Ready).playheadMs)
    }

    @Test
    fun `auditioning a verse with no range does nothing`() = runTest {
        setUpMain()
        val slicer = FakeSlicer()
        val player = RecordingPreviewPlayer()
        val vm = newViewModel(draftWithVerses(2), slicer = slicer, player = player)
        val file = createTempMp3(150)
        vm.onSourcePicked(file.absolutePath, file.length())
        awaitReady(vm)
        slicer.sliceCalls.clear()

        vm.onAuditionRange("v1")
        advanceUntilIdle()

        assertTrue(slicer.sliceCalls.isEmpty())
        assertTrue(player.playedClips.isEmpty())
    }
}
