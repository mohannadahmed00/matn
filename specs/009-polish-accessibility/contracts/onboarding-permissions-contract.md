# Contract: Onboarding & Permissions

**Feature**: `specs/009-polish-accessibility` | Covers **US3** (FR-017 – FR-025)

---

## 1. Onboarding state

```kotlin
// domain/repository/OnboardingRepository.kt
interface OnboardingRepository {
    fun observeStatus(): Flow<OnboardingStatus>
    fun statusNow(): OnboardingStatus          // synchronous — decides the start destination
    suspend fun markCompleted(): Resource<Unit> // called on both finish and skip
}
```

Backed by the `onboarding_completed` key in `app_setting` (data-model §1). No step index is persisted
— an interrupted flow restarts (research D10), which satisfies FR-025 by construction.

`statusNow()` is synchronous for the same reason `themeModeNow()` is: the navigation start
destination must be right on the first composition, not one frame later.

### 1.1 Use cases

| Use case | Base | Contract |
|---|---|---|
| `ObserveOnboardingStatusUseCase` | `FlowUseCase` | Emits the current status |
| `GetOnboardingStatusNowUseCase` | plain `operator fun invoke(): OnboardingStatus` | The synchronous read that picks the navigation start destination |
| `CompleteOnboardingUseCase` | `UseCase` | Persists completion; called by both finish and skip |

`GetOnboardingStatusNowUseCase` exists for the same reason as `GetThemeModeNowUseCase`
(`theming-contract.md` §2): `MatnNavHost` must not inject `OnboardingRepository` directly, because
Principle I routes presentation to domain through use cases. Not `suspend`, no `Resource` — the read
falls back to `NOT_COMPLETED`, which is the safe default (onboarding shows again rather than a first
launch silently skipping it).

## 2. Screen

`presentation/onboarding/` — `OnboardingScreen.kt`, `OnboardingUiState.kt`, `OnboardingViewModel.kt`,
following the stateless-content + thin-holder split (Principle II).

### 2.1 Panels

| # | Content | Requirement |
|---|---|---|
| 1 | Brand mark, "المتن", tagline — the fetched *Splash Screen* treatment | FR-017 |
| 2 | What the app is for: read, listen, repeat, memorize | FR-017 |
| 3 | **How content works** — a starter matn is included; other متون install on demand; installed content then works offline forever | FR-017, FR-024 |

Panel 3 is the panel that earns this story its place: it is the only chance to explain the download
model before a student meets an uninstalled matn.

### 2.2 Rules

- **Skip is available on every panel** (FR-018) and reaches the library with nothing unset and no
  permission requested.
- Finishing and skipping both call `markCompleted()` — identical resulting state.
- **No permission is requested anywhere in this flow** (FR-021, clarified 2026-07-25).
- **No network access** (FR-024): all copy is a bundled string resource, the brand mark a bundled
  drawable.
- Panel motion honours `LocalReduceMotion`; the fetched design's `logo-reveal` is permitted here (it
  is a real reveal, unlike the rejected fake progress bar — research D9).

### 2.3 Navigation

- `Routes.ONBOARDING = "onboarding"`.
- `MatnNavHost`'s `startDestination` is `ONBOARDING` when `statusNow() == NOT_COMPLETED`, else `HOME`.
- No bottom navigation bar on this route — it is not a `NavigationTab`, so the existing
  `showBottomBar` predicate in `MatnNavHost.kt:76` already excludes it with no change.
- Completing pops onboarding off the back stack so system back from Home never returns to it.
- Re-opened from Settings as an ordinary forward navigation (FR-019), which does **not** clear the
  completed flag.

## 3. Permission seam

```kotlin
// domain/permission/NotificationPermission.kt
interface NotificationPermission {
    suspend fun status(): PermissionStatus
    suspend fun request(): PermissionStatus   // shows the system prompt; returns the outcome
    fun openSystemSettings()                  // for PERMANENTLY_DENIED (FR-023)
}
```

Injected via Koin like `AudioEngine` / `WakeLock` / `ContentDeliveryEngine` (research D7/D11), so a
fake scripts every path with no device.

| Platform | Implementation |
|---|---|
| Android | Wraps the `ActivityResultContracts.RequestPermission` launcher for `POST_NOTIFICATIONS`; returns `GRANTED` unconditionally below API 33, where no runtime permission exists |
| iOS | `UNUserNotificationCenter.requestAuthorization` / `getNotificationSettings` |

### 3.1 The "asked once" flag

`notification_permission_asked` in `app_setting` (data-model §1). Set once the rationale has been
shown and the system prompt dispatched, **regardless of outcome**. Platform status alone cannot
distinguish "never asked" from "asked and denied" across both platforms, so the app owns this fact
(research D11). This is what makes SC-010's "no more than once per permission per install" true.

## 4. Trigger point — first playback

Checked where a playback session starts: `PlaybackController.startSession`, the same single gate
Phase 8 uses for its availability precondition. **Never** in the per-verse path.

Sequence on the first `startSession` of an install:

1. `notification_permission_asked == true` → proceed, no prompt, ever again.
2. Else `status() == GRANTED` → set the flag, proceed.
3. Else → show the in-app rationale (FR-020) stating what the media-playback notification does and
   what is lost by declining. **Playback is not blocked on the answer** — the notification is a
   convenience, not a prerequisite.
4. On continue → `request()`, then set the flag whatever the result.
5. On dismiss → set the flag; do not ask again (FR-022).

**Deleted**: `MainActivity.requestPostNotificationsIfNeeded()` and its call at `MainActivity.kt:41`,
plus the `requestPermissionLauncher` field whose result is currently discarded. This is the cold
launch-time request FR-021 forbids.

## 5. Settings — permission state

A row per permission the app uses (FR-023), placed with the appearance setting **below** the storage
section so SC-008 still holds:

| Status | Row shows |
|---|---|
| `NOT_DETERMINED` | "Not requested yet" — no action, it will be asked in context |
| `GRANTED` | Granted — a pointer to device settings to revoke |
| `DENIED` | Declined, with what that costs, and a way to retry |
| `PERMANENTLY_DENIED` | Declined, with a button opening device settings (`openSystemSettings()`) |

Status is re-read when Settings resumes, so a change made outside the app is reflected (the spec's
"granted, then revoked outside the app" edge case).

## 6. Previews required

| Preview | Asserts |
|---|---|
| Each of the 3 panels, light + dark | FR-017, dark coverage |
| Panel with skip focused | FR-018 |
| Permission rationale sheet, light + dark | FR-020 |
| Settings permission row — all 4 statuses | FR-023 |

## 7. Tests (`commonTest`, no device)

| Test | Asserts |
|---|---|
| `OnboardingRepositoryTest` | Default `NOT_COMPLETED`; `markCompleted` persists; `statusNow` agrees with the flow |
| `OnboardingViewModelTest` | Panel advance; skip from any panel completes; completing is idempotent |
| `NotificationPermissionGateTest` | All five branches of §4 against a fake; the flag is set on every terminal path including dismissal; **playback proceeds regardless of outcome** |
| `PlaybackControllerTest` (extend) | The gate runs once per install, never per verse; a denied permission does not block a session |
| `NavigationStartDestinationTest` | `NOT_COMPLETED` → onboarding; `COMPLETED` → home |
