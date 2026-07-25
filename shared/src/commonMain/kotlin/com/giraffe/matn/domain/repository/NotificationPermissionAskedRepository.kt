package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource

/**
 * The `notification_permission_asked` flag (data-model §1, onboarding-permissions-contract.md
 * §3.1) — set once the rationale has been shown and the system prompt dispatched, **regardless of
 * outcome**. Platform status alone cannot distinguish "never asked" from "asked and denied" across
 * both platforms, so the app owns this fact. A dedicated single-flag repository, following the
 * same small-repository-per-preference shape as [OnboardingRepository] and
 * [AppearancePreferencesRepository] rather than reaching past domain into the database directly
 * (Principle I).
 */
interface NotificationPermissionAskedRepository {
    /** Synchronous read — the gate (`EnsureNotificationPermissionUseCase`) needs no Flow. */
    fun askedNow(): Boolean

    /** Persists `true`. Idempotent — asking twice is a no-op past the first call. */
    suspend fun markAsked(): Resource<Unit>
}
