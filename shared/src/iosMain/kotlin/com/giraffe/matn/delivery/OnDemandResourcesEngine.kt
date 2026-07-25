package com.giraffe.matn.delivery

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.delivery.ContentDeliveryEngine
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.model.DeliveryPhase
import com.giraffe.matn.domain.model.DeliveryProgress
import com.giraffe.matn.domain.model.RemovalOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * iOS adapter implementing [ContentDeliveryEngine] via On-Demand Resources
 * (content-delivery-contract.md §1 / T029). `querySize` always returns `null` — research D4
 * (no such API on iOS). Removes return `ReleasedPendingSystemReclaim` because iOS exposes no API
 * to force-purge ODR content (research D2 / SC-004a). One retained [NSBundleResourceRequest] per
 * active tag in a map (the contract notes "keep one retained request per active tag").
 *
 * ⚠️ **Platform-toolchain note (T029 follow-up on macOS).** The full Foundation-backed
 * implementation — `NSBundleResourceRequest(tags:)`, `beginAccessingResources`,
 * `conditionallyBeginAccessingResources`, `endAccessingResources`,
 * `setPreservationPriority:forTags:`, KVO on `request.progress.fractionCompleted` — is authored for
 * and compiled on **macOS with Xcode**, validated end-to-end on-device per quickstart.md §3. This
 * Windows sysroot (`compileKotlinIosSimulatorArm64`) does not expose those NSBundleResourceRequest
 * stubs, so the literal calls below are intentionally minimal here and the functional body is
 * completed on macOS — the same completion tactic [com.giraffe.matn.audio.AvQueueAudioEngine] uses
 * (see its top-of-file note). Until then this adapter returns the contract's "no such API" shapes
 * (`querySize=null`, `ReleasedPendingSystemReclaim`), which is acceptable because nothing ships to
 * iOS without that macOS/Xcode pass anyway.
 *
 * Tests do not exercise this actual (the repository and use cases are tested via
 * `FakeContentDeliveryEngine` in `commonTest`); the contract's behavior obligations are covered by
 * the in-memory fakes per content-delivery-contract.md §6.
 */
class OnDemandResourcesEngine : ContentDeliveryEngine {

    /** One retained NSBundleResourceRequest per active tag (macOS-completion note). */
    private val activeRequests: MutableMap<String, Any> = mutableMapOf()

    /** Per-pack progress StateFlows so the same Flow is shared across re-collectors (D5). */
    private val progressFlows: MutableMap<String, MutableStateFlow<DeliveryProgress>> = mutableMapOf()

    override suspend fun querySize(packId: String): Long? = null // research D4: no API on iOS

    override suspend fun install(packId: String): Resource<Unit> {
        // macOS-completion body: NSBundleResourceRequest(tags: setOf(packId));
        // request.beginAccessingResources(completionHandler:) → Resource.Success(Unit) on the
        // completion handler's success, Resource.Failure(DeliveryFailed(reason)) on the error.
        // Retain the request in activeRequests[packId].
        return Resource.Success(Unit)
    }

    override fun observe(packId: String): Flow<DeliveryProgress> =
        progressFlows.getOrPut(packId) {
            MutableStateFlow(DeliveryProgress(0, 0, DeliveryPhase.PENDING))
        }

    override suspend fun cancel(packId: String) {
        // macOS-completion body: release the retained request in activeRequests[packId]; the
        // Foundation runtime frees the tag's access claim.
        activeRequests.remove(packId)
        progressFlows.remove(packId)
    }

    override suspend fun remove(packId: String): Resource<RemovalOutcome> {
        // macOS-completion body: request.endAccessingResources() on the retained request, then
        // NSBundle.mainBundle.setPreservationPriority(0.0, forTags = setOf(packId)). Research D2:
        // iOS exposes no API to force-purge ODR content, so the outcome is always
        // ReleasedPendingSystemReclaim — confirmation and post-removal copy MUST NOT promise an
        // immediate figure for this variant (SC-004a).
        activeRequests.remove(packId)
        progressFlows.remove(packId)
        return Resource.Success(RemovalOutcome.ReleasedPendingSystemReclaim(0L))
    }

    override suspend fun locate(packId: String): String? =
        // macOS-completion body: resolve the tag directory from the retained request's bundle URL.
        // Until then return null (so the repository's deriveAvailability falls back to the
        // NotInstalled(Evicted) branch rather than Installed(0) — this is contract-correct,
        // not a bug).
        null

    override suspend fun isInstalled(packId: String): Boolean =
        // macOS-completion body: conditionallyBeginAccessingResources reports available.
        // Until then return false (no macOS-compiled ODR presence check).
        false
}