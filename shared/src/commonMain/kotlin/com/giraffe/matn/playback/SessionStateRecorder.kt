package com.giraffe.matn.playback

import com.giraffe.matn.domain.model.PlaybackState
import com.giraffe.matn.domain.model.PlaybackStatus
import com.giraffe.matn.domain.model.SavedMatnSession
import com.giraffe.matn.domain.repository.SessionStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val THROTTLE_MS = 5_000L

/**
 * Observes [PlaybackController.state] and persists a durable snapshot (data-model §4) through
 * [SessionStateRepository]. Pure observer — it never touches [PlaybackController] and adds no
 * mutation sites there (research D1). The write policy is two collectors over the same flow:
 *
 * - **Structural** (W1): a change in `matnId`, `verseId`, or `settings` writes immediately, via
 *   `distinctUntilChanged` keyed on those fields. The first write for a matn also sets the pointer
 *   (W4). Because only state with an `activeVerseId` is projected, this is inherently playback-gated
 *   (FR-002a) — browsing a matn without playing writes nothing (SC-009b).
 * - **Position** (W2): the same snapshot flow, throttled with `sample(THROTTLE_MS)` so position
 *   updates flush at most once per throttle window.
 *
 * Changes confined to `status`, `speed`, `notice`, `pauseReason`, or `cursor` are dropped by the
 * projection and produce **zero** writes (W3). Write failures are swallowed (W7, FR-011) — playback
 * is never disturbed by persistence. [flush] forces an immediate write of the current snapshot and
 * is safe to call when there is no session (W6).
 *
 * No clock — nothing persisted is time-derived; tests drive the throttle with the virtual-time
 * coroutine scheduler (constitution Principle V).
 */
class SessionStateRecorder(
    private val state: StateFlow<PlaybackState>,
    private val repository: SessionStateRepository,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var latest: DurableSnapshot? = null
    private var lastPersisted: DurableSnapshot? = null

    fun start() {
        val snapshots = state.map { it.toDurableSnapshot() }

        // Tracker: keep `latest` current on every emission (no I/O) so a flush() — from a pause,
        // a stop, or a platform backgrounding hook — persists the freshest position rather than
        // one up to a throttle-window old (FR-009/FR-010). A null snapshot (session cleared by
        // stop()) deliberately does NOT clear `latest`: the pre-stop snapshot is exactly what a
        // stop-flush must persist.
        scope.launch {
            snapshots.collect { snap ->
                if (snap != null) mutex.withLock { latest = snap }
            }
        }

        scope.launch {
            snapshots
                .distinctUntilChanged { a, b ->
                    a?.matnId == b?.matnId &&
                        a?.verseId == b?.verseId &&
                        a?.settings == b?.settings
                }
                .collectLatest { write(it, structural = true) }
        }

        scope.launch {
            snapshots
                .sample(THROTTLE_MS)
                .collectLatest { write(it, structural = false) }
        }

        // T039 (FR-009): flush when playback leaves PLAYING — a pause, a stop (IDLE), or a natural
        // end. An unchanged snapshot is suppressed inside write(), so status-only transitions still
        // produce zero writes (W3). drop(1): the value seen at subscription is not a transition —
        // flushing on it would race the structural collector's first write.
        scope.launch {
            state
                .map { it.status }
                .distinctUntilChanged()
                .drop(1)
                .collect { status ->
                    if (status == PlaybackStatus.PAUSED ||
                        status == PlaybackStatus.ENDED ||
                        status == PlaybackStatus.IDLE
                    ) {
                        flush()
                    }
                }
        }
    }

    /** Forced write of the current snapshot — call on pause/stop/background. Idempotent and a no-op
     *  when there is no session (W6). */
    suspend fun flush() {
        write(latest, structural = false)
    }

    /** Fire-and-forget [flush] for platform lifecycle hooks (T039) whose callbacks are not
     *  suspending (`onStop` on Android, the scene-phase observer on iOS). Best-effort by design:
     *  an OS kill racing the launch loses at most the current verse's partial position (FR-009). */
    fun flushAsync() {
        scope.launch { flush() }
    }

    private suspend fun write(snapshot: DurableSnapshot?, structural: Boolean) {
        if (snapshot == null) return
        mutex.withLock {
            latest = snapshot
            // The throttled position collector fires on its window even when the durable snapshot
            // is unchanged (e.g. a pause/speed/cursor-only tick changes nothing persisted). Suppress
            // a re-write of an identical snapshot (W3) while still flushing genuine position moves.
            // Structural emissions always proceed (they were distinctUntilChanged-filtered already).
            if (!structural && snapshot == lastPersisted) return@withLock
            val saved = SavedMatnSession(
                matnId = snapshot.matnId,
                lastVerseId = snapshot.verseId,
                lastVerseDisplayNumber = snapshot.displayNumber,
                positionMs = snapshot.positionMs,
                settings = snapshot.settings,
            )
            try {
                repository.putSession(saved)
                if (structural) {
                    repository.setLastListenedMatnId(snapshot.matnId) // W4
                }
                lastPersisted = snapshot
            } catch (t: Throwable) {
                // W7 — swallow. A failed write must never disturb playback (FR-011, SC-008).
                // lastPersisted is deliberately NOT advanced, so the next tick retries.
            }
        }
    }
}