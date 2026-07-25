package com.giraffe.matn.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.delivery.ContentDeliveryEngine
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.model.DeliveryPhase
import com.giraffe.matn.domain.model.DeliveryProgress
import com.giraffe.matn.domain.model.RemovalOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake of [ContentDeliveryEngine] for `commonTest` (Principle V). Scripts every path
 * the contract enumerates: progress, completion, eviction, mid-install failures, process death,
 * and both removal outcomes. Per T022: a `MutableMap<String, ContentAvailability>` of current
 * state, plus settable dials and helper methods. No real IO.
 */
class FakeContentDeliveryEngine : ContentDeliveryEngine {

    /** Current availability snapshot per pack — read by [isInstalled]/[locate]/[observe]. */
    val states: MutableMap<String, ContentAvailability> = mutableMapOf()

    /** Live size override; null ⇒ fall back to declared (matches the engine contract). */
    var liveSize: Long? = null

    /** Filesystem root reported by [locate] for an installed pack. */
    var installedRoot: String? = "/fake/packs"

    /** Outcome returned by the next [remove] call. */
    var nextRemovalOutcome: RemovalOutcome = RemovalOutcome.Reclaimed(0L)

    /** Set before calling [install] to make the next install fail with the given reason. */
    var failInstallWith: DeliveryFailure? = null

    /** Per-pack progress [MutableStateFlow]s so [observe] mirrors the real engine's behavior. */
    private val progressFlows: MutableMap<String, MutableStateFlow<DeliveryProgress>> = mutableMapOf()

    /** Tracks whether [cancel] was invoked for a pack — used by repository "remove wins" tests. */
    private val cancelledPacks: MutableSet<String> = mutableSetOf()

    /** Records every [install] invocation so use-case tests can assert "engine called once". */
    val installInvocations: MutableList<String> = mutableListOf()

    /** Records every [remove] invocation. */
    val removeInvocations: MutableList<String> = mutableListOf()

    fun reset() {
        states.clear()
        progressFlows.clear()
        cancelledPacks.clear()
        installInvocations.clear()
        removeInvocations.clear()
        liveSize = null
        installedRoot = "/fake/packs"
        nextRemovalOutcome = RemovalOutcome.Reclaimed(0L)
        failInstallWith = null
    }

    /** Emit progress for a pack from the test driver. */
    fun emitProgress(packId: String, bytes: Long, total: Long, phase: DeliveryPhase) {
        progressFlows.getOrPut(packId) {
            MutableStateFlow(DeliveryProgress(0, total, DeliveryPhase.PENDING))
        }.value = DeliveryProgress(bytes, total, phase)
    }

    /** Mark a pack as installed with the given occupied bytes; emits a final progress snapshot. */
    fun completeInstall(packId: String, occupiedBytes: Long) {
        states[packId] = ContentAvailability.Installed(occupiedBytes)
        emitProgress(packId, occupiedBytes, occupiedBytes, DeliveryPhase.TRANSFERRING)
    }

    /** Flip an Installed pack back to absent — used by eviction tests. */
    fun simulateEviction(packId: String) {
        states[packId] = ContentAvailability.NotInstalled(DeliveryFailure.Evicted)
    }

    /** Simulate a process kill mid-install: state becomes "not installed, no progress". */
    fun simulateProcessDeathMidInstall(packId: String) {
        states[packId] = ContentAvailability.NotInstalled(null)
        progressFlows.remove(packId)
    }

    override suspend fun querySize(packId: String): Long? = liveSize

    override suspend fun install(packId: String): Resource<Unit> {
        installInvocations.add(packId)
        failInstallWith?.let {
            states[packId] = ContentAvailability.NotInstalled(it)
            return Resource.Failure(DeliveryError.DeliveryFailed(it))
        }
        if (states[packId] !is ContentAvailability.Installed) {
            states[packId] = ContentAvailability.Installing(
                progressFlows[packId]?.value ?: DeliveryProgress(0, 0, DeliveryPhase.PENDING),
            )
        }
        return Resource.Success(Unit)
    }

    override fun observe(packId: String): Flow<DeliveryProgress> =
        progressFlows.getOrPut(packId) {
            MutableStateFlow(DeliveryProgress(0, 0, DeliveryPhase.PENDING))
        }

    override suspend fun cancel(packId: String) {
        cancelledPacks.add(packId)
        progressFlows.remove(packId)
        if (states[packId] is ContentAvailability.Installing) {
            states[packId] = ContentAvailability.NotInstalled(DeliveryFailure.Cancelled)
        }
    }

    override suspend fun remove(packId: String): Resource<RemovalOutcome> {
        removeInvocations.add(packId)
        val outcome = nextRemovalOutcome
        states[packId] = ContentAvailability.NotInstalled(null)
        progressFlows.remove(packId)
        return Resource.Success(outcome)
    }

    override suspend fun locate(packId: String): String? =
        if (states[packId] is ContentAvailability.Installed) installedRoot else null

    override suspend fun isInstalled(packId: String): Boolean =
        states[packId] is ContentAvailability.Installed

    /** Was [cancel] called for this pack since install? */
    fun wasCancelled(packId: String): Boolean = cancelledPacks.contains(packId)
}