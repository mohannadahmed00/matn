package com.giraffe.matn.playback

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.ContinueLearningEntry
import com.giraffe.matn.domain.model.PlaybackCursor
import com.giraffe.matn.domain.model.PlaybackNotice
import com.giraffe.matn.domain.model.PlaybackSpeed
import com.giraffe.matn.domain.model.PlaybackState
import com.giraffe.matn.domain.model.PlaybackStatus
import com.giraffe.matn.domain.model.PauseReason
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.RepetitionSettings
import com.giraffe.matn.domain.model.SavedMatnSession
import com.giraffe.matn.domain.repository.SessionStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStateRecorderTest {

    private class RecordingRepo : SessionStateRepository {
        val puts = mutableListOf<SavedMatnSession>()
        val pointerSets = mutableListOf<String>()
        var putThrow = false

        override suspend fun getSession(matnId: String): SavedMatnSession? = null
        override suspend fun putSession(session: SavedMatnSession): Resource<Unit> {
            puts += session
            if (putThrow) throw RuntimeException("boom")
            return Resource.Success(Unit)
        }

        override suspend fun getLastListenedMatnId(): String? = null
        override suspend fun setLastListenedMatnId(matnId: String): Resource<Unit> {
            pointerSets += matnId
            return Resource.Success(Unit)
        }

        override suspend fun clearLastListenedMatnId(): Resource<Unit> = Resource.Success(Unit)
        override fun observeContinueLearning(): Flow<ContinueLearningEntry?> = MutableStateFlow(null)
    }

    private fun state(
        matnId: String? = null,
        activeVerseId: String? = null,
        activeDisplayNumber: Int? = null,
        positionMs: Long = 0,
        settings: RepetitionSettings = RepetitionSettings(),
        status: PlaybackStatus = PlaybackStatus.IDLE,
        speed: PlaybackSpeed = PlaybackSpeed.DEFAULT,
        pauseReason: PauseReason? = null,
        notice: PlaybackNotice? = null,
        cursor: PlaybackCursor? = null,
    ) = PlaybackState(
        status = status,
        matnId = matnId,
        activeVerseId = activeVerseId,
        activeDisplayNumber = activeDisplayNumber,
        positionMs = positionMs,
        settings = settings,
        speed = speed,
        pauseReason = pauseReason,
        notice = notice,
        cursor = cursor,
    )

    private fun newFlow() = MutableStateFlow(state())

    private fun TestScope.newRecorder(flow: MutableStateFlow<PlaybackState>): Pair<SessionStateRecorder, RecordingRepo> {
        val repo = RecordingRepo()
        val recorder = SessionStateRecorder(flow, repo, backgroundScope)
        return recorder to repo
    }

    private fun TestScope.flush() { runCurrent(); advanceTimeBy(50); runCurrent() }

    @Test
    fun `W1 - structural change writes immediately`() = runTest {
        val flow = newFlow()
        val (recorder, repo) = newRecorder(flow)
        recorder.start()

        flow.value = state(matnId = "m1", activeVerseId = "v1", activeDisplayNumber = 1)
        flush()

        assertEquals(1, repo.puts.size)
        assertEquals("m1", repo.puts[0].matnId)
        assertEquals("v1", repo.puts[0].lastVerseId)
        assertEquals(listOf("m1"), repo.pointerSets) // W4
    }

    @Test
    fun `W3 - status speed pauseReason cursor changes alone produce zero writes`() = runTest {
        val flow = newFlow()
        val (recorder, repo) = newRecorder(flow)
        recorder.start()

        flow.value = state(matnId = "m1", activeVerseId = "v1", activeDisplayNumber = 1, positionMs = 10)
        flush()
        val before = repo.puts.size

        // Pure transient changes — none belong to the durable projection.
        flow.value = state(matnId = "m1", activeVerseId = "v1", activeDisplayNumber = 1, positionMs = 10, status = PlaybackStatus.PAUSED, speed = PlaybackSpeed.X1_5, pauseReason = PauseReason.USER, cursor = PlaybackCursor(0, 1, 1), notice = PlaybackNotice.ReachedEnd)
        advanceTimeBy(60_000); runCurrent()

        assertEquals(before, repo.puts.size, "transient-only changes MUST NOT write (W3 / FR-011)")
    }

    @Test
    fun `W2 - positionMs within one window writes at most once`() = runTest {
        val flow = newFlow()
        val (recorder, repo) = newRecorder(flow)
        recorder.start()

        flow.value = state(matnId = "m1", activeVerseId = "v1", activeDisplayNumber = 1, positionMs = 100)
        flush()
        val before = repo.puts.size

        for (p in 200..2_000 step 100) {
            flow.value = state(matnId = "m1", activeVerseId = "v1", activeDisplayNumber = 1, positionMs = p.toLong())
        }
        advanceTimeBy(6_000); runCurrent() // one throttle window

        val writes = repo.puts.size - before
        assertTrue(writes <= 1, "position writes must be throttled (W2); got $writes extra writes in one window")
    }

    @Test
    fun `W4 - first playback in a matn sets the pointer`() = runTest {
        val flow = newFlow()
        val (recorder, repo) = newRecorder(flow)
        recorder.start()

        assertTrue(repo.pointerSets.isEmpty())
        flow.value = state(matnId = "m1", activeVerseId = "v1", activeDisplayNumber = 1)
        flush()

        assertEquals(listOf("m1"), repo.pointerSets)
    }

    @Test
    fun `SC-009b - browsing never moves the pointer`() = runTest {
        val flow = newFlow()
        val (recorder, repo) = newRecorder(flow)
        recorder.start()

        // Matn opened but never played: matnId set, activeVerseId null -> snapshot is null.
        flow.value = state(matnId = "m1", activeVerseId = null, activeDisplayNumber = null)
        advanceTimeBy(60_000); runCurrent()

        assertEquals(0, repo.puts.size, "browsing MUST NOT write any session row (FR-002a / SC-009b)")
        assertTrue(repo.pointerSets.isEmpty(), "browsing MUST NOT set the pointer (FR-002a)")
    }

    @Test
    fun `FR-016 - the entry tracks the newest position`() = runTest {
        val flow = newFlow()
        val (recorder, repo) = newRecorder(flow)
        recorder.start()

        flow.value = state(matnId = "m1", activeVerseId = "v5", activeDisplayNumber = 5, positionMs = 10)
        flush()
        flow.value = state(matnId = "m1", activeVerseId = "v9", activeDisplayNumber = 9, positionMs = 20)
        flush()

        val last = repo.puts.last()
        assertEquals("v9", last.lastVerseId)
        assertEquals(9, last.lastVerseDisplayNumber)
    }

    @Test
    fun `W7 - a write failure is swallowed and playback-state is unaffected`() = runTest {
        val flow = newFlow()
        val repo = RecordingRepo().apply { putThrow = true }
        val recorder = SessionStateRecorder(flow, repo, backgroundScope)
        recorder.start()

        flow.value = state(matnId = "m1", activeVerseId = "v1", activeDisplayNumber = 1)
        flush()

        // Recorder must not propagate the repository exception to the caller.
        assertEquals("m1", flow.value.matnId)
        assertTrue(repo.puts.isNotEmpty())
    }

    @Test
    fun `W6 - flush is a no-op when there is no session`() = runTest {
        val flow = newFlow()
        val (recorder, repo) = newRecorder(flow)
        recorder.start()

        recorder.flush()
        runCurrent()

        assertEquals(0, repo.puts.size)
    }

    @Test
    fun `W5 - a settings change with no active verse writes nothing`() = runTest {
        val flow = newFlow()
        val (recorder, repo) = newRecorder(flow)
        recorder.start()

        flow.value = state(matnId = "m1", activeVerseId = null, settings = RepetitionSettings(verseRepeat = RepeatCount.of(7)))
        advanceTimeBy(60_000); runCurrent()

        assertEquals(0, repo.puts.size, "no activeVerseId => no snapshot => no write")
    }
}