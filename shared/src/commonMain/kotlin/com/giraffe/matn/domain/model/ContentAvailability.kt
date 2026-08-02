package com.giraffe.matn.domain.model

/**
 * The atomic per-matn availability of a matn's content (FR-016, spec Clarifications 2026-08-02).
 * A download is atomic from the student's point of view: it is downloaded or it is not. There is no
 * partial-availability state and no per-verse presence tracking — the staging directory
 * (`downloads/.tmp-{matnId}/`) is invisible here by construction, so there is nothing to
 * mis-report (research D7).
 *
 * Computed per read from what is actually on disk (FR-022), **never persisted**: a `downloaded_matn`
 * row whose directory has vanished resolves to [NotDownloaded], which is what makes FR-045 a free
 * property rather than a reconciliation job.
 */
sealed interface ContentAvailability {

    /**
     * Not on the device. [failure] is non-null when the last download attempt failed; every value
     * of [DeliveryFailure] is retryable from the UI (FR-017, FR-043).
     *
     * Files disappearing outside the app (FR-045) resolve here with a **null** failure — the
     * student did nothing wrong, so it renders as plain "not downloaded" with the normal download
     * action.
     */
    data class NotDownloaded(val failure: DeliveryFailure? = null) : ContentAvailability

    /**
     * Accepted and waiting for the single active transfer slot (FR-015, Clarification 4).
     *
     * Carries no progress **deliberately**: nothing has transferred yet, and that is precisely what
     * distinguishes it on screen from `Downloading` sitting at 0% — a queued matn must not render a
     * progress bar that will not move.
     */
    data object Queued : ContentAvailability

    /** A transfer is in flight. Holds the live [DeliveryProgress]. */
    data class Downloading(val progress: DeliveryProgress) : ContentAvailability

    /**
     * Present and complete right now. [occupiedBytes] is the measured size of
     * `downloads/{matnId}/` — verse text and recitations only. Cached cover images live outside
     * that directory and are never counted (FR-012, Clarification 3).
     */
    data class Downloaded(val occupiedBytes: Long) : ContentAvailability
}
