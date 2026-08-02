package com.giraffe.matn.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.delivery.ContentDeliveryEngine
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.model.DeliveryPhase
import com.giraffe.matn.domain.model.DeliveryProgress
import com.giraffe.matn.domain.model.RemovalOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake of [ContentDeliveryEngine] for `commonTest` (Principle V). No real IO, no network.
 *
 * Phase 13 reshaped it along with the interface: `packId` → `matnId`, `querySize` gone, and
 * `simulateEviction` gone with `DeliveryFailure.Evicted` (an OS-evicted asset pack is not a thing
 * that can happen any more). What replaces eviction is [simulateExternalDeletion] — files vanishing
 * behind the app's back, which is FR-045 and is genuinely reachable.
 *
 * The important new capability is [holdGate]: a download can be held open so a test can observe the
 * queue while a transfer is genuinely in flight. Without it the one-at-a-time ordering
 * (Clarification 4) is unprovable — every download would complete before the next was requested.
 */
class FakeContentDeliveryEngine : ContentDeliveryEngine {

    /** Matns currently present on the fake device, with their occupied bytes. */
    val downloaded: MutableMap<String, Long> = mutableMapOf()

    /** Filesystem root reported by [contentRootFor] for a downloaded matn. */
    var downloadRoot: String? = "/fake/downloads"

    /** Per-matn root override, for tests that need distinct measurable directories. */
    val downloadRoots: MutableMap<String, String> = mutableMapOf()

    /** Outcome returned by the next [remove] call. */
    var nextRemovalOutcome: RemovalOutcome = RemovalOutcome.Reclaimed(0L)

    /** Set before calling [download] to make the next download fail with the given reason. */
    var failDownloadWith: DeliveryFailure? = null

    /** Bytes a successful [download] reports as occupied. */
    var downloadedBytes: Long = 1_000L

    /**
     * When held for a matn, [download] suspends until the test releases it. This is what makes
     * "one transfer at a time, the rest queued" observable.
     */
    private val gates: MutableMap<String, CompletableDeferred<Unit>> = mutableMapOf()

    private val progressFlows: MutableMap<String, MutableStateFlow<DeliveryProgress>> = mutableMapOf()
    private val cancelledMatns: MutableSet<String> = mutableSetOf()

    /** Every [download] invocation, in order — the queue-ordering assertion reads this. */
    val downloadInvocations: MutableList<String> = mutableListOf()

    /** Every [remove] invocation. */
    val removeInvocations: MutableList<String> = mutableListOf()

    fun reset() {
        downloaded.clear()
        progressFlows.clear()
        cancelledMatns.clear()
        downloadInvocations.clear()
        removeInvocations.clear()
        gates.clear()
        downloadRoot = "/fake/downloads"
        downloadRoots.clear()
        nextRemovalOutcome = RemovalOutcome.Reclaimed(0L)
        failDownloadWith = null
        downloadedBytes = 1_000L
    }

    /** Hold [matnId]'s download open until [releaseGate] is called. */
    fun holdGate(matnId: String) {
        gates[matnId] = CompletableDeferred()
    }

    fun releaseGate(matnId: String) {
        gates[matnId]?.complete(Unit)
    }

    fun emitProgress(matnId: String, bytes: Long, total: Long, phase: DeliveryPhase) {
        progressFlows.getOrPut(matnId) {
            MutableStateFlow(DeliveryProgress(0, total, DeliveryPhase.PENDING))
        }.value = DeliveryProgress(bytes, total, phase)
    }

    /** Mark a matn as present without going through [download]. */
    fun completeDownload(matnId: String, occupiedBytes: Long) {
        downloaded[matnId] = occupiedBytes
        emitProgress(matnId, occupiedBytes, occupiedBytes, DeliveryPhase.TRANSFERRING)
    }

    /**
     * FR-045: a device cleaner or OS storage reclamation removed the files while the app was not
     * looking. Availability must report not-downloaded on the next read, with **no** failure —
     * the student did nothing wrong.
     */
    fun simulateExternalDeletion(matnId: String) {
        downloaded.remove(matnId)
        progressFlows.remove(matnId)
    }

    override suspend fun download(matnId: String): Resource<Unit> {
        downloadInvocations.add(matnId)
        gates[matnId]?.await()
        if (matnId in cancelledMatns) {
            return Resource.Failure(DeliveryError.DeliveryFailed(DeliveryFailure.Cancelled))
        }
        failDownloadWith?.let { return Resource.Failure(DeliveryError.DeliveryFailed(it)) }
        downloaded[matnId] = downloadedBytes
        emitProgress(matnId, downloadedBytes, downloadedBytes, DeliveryPhase.TRANSFERRING)
        return Resource.Success(Unit)
    }

    override fun observe(matnId: String): Flow<DeliveryProgress> =
        progressFlows.getOrPut(matnId) {
            MutableStateFlow(DeliveryProgress(0, 0, DeliveryPhase.PENDING))
        }

    override suspend fun cancel(matnId: String) {
        cancelledMatns.add(matnId)
        progressFlows.remove(matnId)
        gates[matnId]?.complete(Unit)
    }

    override suspend fun remove(matnId: String): Resource<RemovalOutcome> {
        removeInvocations.add(matnId)
        val bytes = downloaded.remove(matnId) ?: 0L
        progressFlows.remove(matnId)
        val configured = nextRemovalOutcome
        // Default dial (Reclaimed(0)) means "report what was actually there"; an explicitly set
        // outcome wins, so tests can still pin an exact figure.
        val outcome = if (configured is RemovalOutcome.Reclaimed && configured.bytes == 0L) {
            RemovalOutcome.Reclaimed(bytes)
        } else {
            configured
        }
        return Resource.Success(outcome)
    }

    override suspend fun contentRootFor(matnId: String): String? =
        if (matnId in downloaded) downloadRoots[matnId] ?: downloadRoot else null

    override suspend fun isDownloaded(matnId: String): Boolean = matnId in downloaded

    /** Was [cancel] called for this matn? */
    fun wasCancelled(matnId: String): Boolean = cancelledMatns.contains(matnId)
}
