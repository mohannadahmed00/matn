package com.giraffe.matn.domain.model

/**
 * Whether first-launch onboarding has completed (Phase 9, data-model §2.3). Drives the navigation
 * start destination (FR-017/FR-019). Skipping and finishing produce the same value — FR-018
 * requires skipping to leave nothing unset.
 */
enum class OnboardingStatus {
    NOT_COMPLETED,
    COMPLETED,
}