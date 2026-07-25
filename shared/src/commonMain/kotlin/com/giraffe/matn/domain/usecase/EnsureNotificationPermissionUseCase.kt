package com.giraffe.matn.domain.usecase

import com.giraffe.matn.domain.model.PermissionStatus
import com.giraffe.matn.domain.permission.NotificationPermission
import com.giraffe.matn.domain.repository.NotificationPermissionAskedRepository

/**
 * T074 (US3, onboarding-permissions-contract.md §4) — the first-playback notification-permission
 * gate. Implements the five-step sequence exactly:
 *
 * 1. Already asked → proceed, no prompt, ever again.
 * 2. Else already `GRANTED` → set the flag, proceed.
 * 3. Else → [invoke] returns [Result.ShowRationale] so the caller renders the in-app rationale
 *    (FR-020). Playback is never blocked on this — the caller decides only whether to show UI.
 * 4. Rationale "Continue" → [onRationaleContinue] requests the system prompt, then sets the flag
 *    whatever the result.
 * 5. Rationale dismissed/"Not now" → [onRationaleDismissed] sets the flag; never asks again.
 *
 * **Never blocks or fails playback** (FR-021/FR-022) — [PlaybackController.startSession] invokes
 * this once per session start, off the queue-building path, and only reacts to
 * [Result.ShowRationale] by surfacing UI.
 */
class EnsureNotificationPermissionUseCase(
    private val notificationPermission: NotificationPermission,
    private val askedRepository: NotificationPermissionAskedRepository,
) {
    sealed interface Result {
        /** Steps 1 and 2 — nothing for the caller to do. */
        data object Proceed : Result

        /** Step 3 — the caller should present the rationale sheet. */
        data object ShowRationale : Result
    }

    suspend operator fun invoke(): Result {
        if (askedRepository.askedNow()) return Result.Proceed
        return if (notificationPermission.status() == PermissionStatus.GRANTED) {
            askedRepository.markAsked()
            Result.Proceed
        } else {
            Result.ShowRationale
        }
    }

    /** Step 4 — the rationale's "Continue" action. */
    suspend fun onRationaleContinue(): PermissionStatus {
        val result = notificationPermission.request()
        askedRepository.markAsked()
        return result
    }

    /** Step 5 — the rationale dismissed without continuing ("Not now" or an outside tap). */
    suspend fun onRationaleDismissed() {
        askedRepository.markAsked()
    }
}
