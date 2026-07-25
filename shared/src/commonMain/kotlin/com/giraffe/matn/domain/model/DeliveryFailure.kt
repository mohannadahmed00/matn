package com.giraffe.matn.domain.model

/**
 * The retryable reason a transfer resolved to `NotInstalled` (FR-009, data-model §2.3). Every value
 * has a UI affordance that lets the student retry; `Evicted` in particular is **not** an error
 * state the student caused — it renders as plain "not installed" with the normal install action.
 */
sealed interface DeliveryFailure {

    /** No usable connectivity at the moment the install was attempted (FR-015). */
    data object NoConnectivity : DeliveryFailure

    /** Device free space below the install's required size (FR-007). Both figures are carried. */
    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : DeliveryFailure

    /** The student or the OS cancelled the transfer (FR-006). */
    data object Cancelled : DeliveryFailure

    /** The OS reclaimed the content after eviction; renders as plain "not installed". */
    data object Evicted : DeliveryFailure

    /** A platform-specific code the app does not decode; still retryable. */
    data class Unknown(val code: Int) : DeliveryFailure
}