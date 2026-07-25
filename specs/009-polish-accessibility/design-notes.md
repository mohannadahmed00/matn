# Design Notes: Polish & Accessibility (2026-07-25)

Following the Phase 6/7/8 precedent (`specs/008-storage-downloads/design-notes.md`): what was
fetched, what was derived, what was composed from tokens with no capture to reference, and every
deviation from the literal task text, recorded before merge.

## T037 — the derived dark palette

`presentation/theme/Color.kt`'s `MatnDarkColors` is **derived**, not fetched. The one registered
Phase 9 Stitch screen — *Splash Screen* (`6aba0b42e95d43e5b6f81928f3e3f7c6`) — was fetched during
planning and its `tailwind.config` declares `darkMode: "class"` but contains **zero `dark:` variant
classes**: the dark-mode hook exists, no dark values were ever authored behind it. This confirms
`docs/DESIGN-SOURCE.md` open issue #5 first-hand rather than taking the registry's word for it.

The tone-mapping rule (research D3) — light tone → dark tone per role family (e.g.
`primary`/`secondary`/`tertiary`: 40 → 80; `background`/`surface`: 98 → 6) — produced all 34 dark
roles from the existing 34 light roles. `ColorContrastTest` (T040) is the acceptance gate, not
visual inspection: all 48 assertions (24 pairs × 2 schemes) pass. Publishing the rule plus a
machine-checked gate is more honest than inventing 34 hex literals nobody measured.

## T099 — the fetched splash's 3000ms fake progress bar is not implemented

The *Splash Screen* capture's "Preparing your workspace…" treatment animates a fake determinate
progress bar over ~3 seconds. Matn has nothing to prepare — cold start reads one `SharedPreferences`
key and shows the first frame. Per Principle VIII ("designs are authoritative about appearance, not
behaviour"), the visual treatment (brand mark, tagline, background colour) was adopted for
onboarding panel 1; the fabricated delay was not. No loading indicator, no artificial `delay()`,
anywhere in the launch or onboarding path.

## T047/T049/T050 — the pinned-vs-system launch residual

The platform splash (`Theme.Matn.Splash` on Android, `UILaunchScreen` on iOS) is unavoidably
selected by the **device's** night-mode/appearance setting, before any Kotlin runs (research D9).
`MainActivity.applyMirroredAppearance()` (T049) then explicitly overrides the **post-splash** theme
to match the student's *pinned* appearance, read back from the mirror `AndroidAppearanceMirror`
last wrote.

This leaves one unavoidable residual: a student who has pinned Dark while their device is in Light
mode (or vice versa) sees the splash itself in the device's appearance for the ~100–300ms the
platform owns, before the post-splash theme (and then Compose) corrects it. This is structurally
unfixable without the OS exposing per-app appearance to the launch-window chooser, which it does
not. `MatnTheme` itself is always correct from the first Compose frame; only the platform-drawn
splash frame is subject to this gap. Recorded per T037's comment in `Color.kt`, which names this as
the reason `Surface`/`DarkSurface` are mirrored in `themes.xml` and `Assets.xcassets`.

## T050 — iOS launch surface: `UILaunchScreen` generation, not a hand-authored storyboard

The task list named `LaunchScreen.storyboard` as the artifact to create/modify. The actual iOS
project (`iosApp.xcodeproj`) already builds its launch screen via
`INFOPLIST_KEY_UILaunchScreen_Generation = YES` — the modern, storyboard-free mechanism. Rather than
introduce a parallel, unused storyboard (or risk a malformed hand-edit of `project.pbxproj`, which
this environment cannot open in Xcode to verify), the launch surface is customised the way Apple's
own build-setting mechanism is designed to be customised: a `LaunchBackground` colour asset
(`Assets.xcassets`, Any `#FCF9F8` / Dark `#121414`, matching `Color.kt`'s `Surface`/`DarkSurface`
exactly) referenced from an explicit `UILaunchScreen` dictionary added to `Info.plist`. Same
outcome — a static, colour-only, no-animation launch surface reflecting device appearance — reached
through the project's existing configuration surface instead of a second one.

## T056 — the locale-parity half of the label-catalogue test lives in `androidHostTest`

`A11yLabelCatalogTest` (totality over `A11yAction`, no duplicate `StringResource`) is pure Kotlin
and lives in `commonTest` as directed. The "both locale files declare an identical set of
`<string name=…>`" check needs real file I/O, and this project declares no cross-platform
file-reading dependency (no `kotlinx-io`/`okio`). Following the exact precedent
`db/MigrationTest.kt` already sets for JVM-only-verifiable logic, `A11yLabelLocaleParityTest` lives
in `androidHostTest` instead, using `java.io.File` to parse both XML files directly.

## T063 — traversal-order audit: no `traversalIndex` needed anywhere

Read all eight named screens' composition order against the forced RTL layout. Every place an
element is positioned outside normal `Row`/`Column` flow (`Modifier.align` inside a `Box`) is either
a single-child overlay (a loading spinner centred alone) or a badge whose composition order already
matches sensible reading order (`VerseRosette`'s verse number, then its optional A/B loop-boundary
badge). Compose's default traversal follows composition order, which for ordinary `Row`/`Column`
placement is *always* in sync with the RTL-mirrored visual order — a mismatch can only arise from
absolute positioning that decouples the two, and none of the eight screens has one. Matches the
task's own expectation ("expected: few or none").

## Onboarding — panels 2–3 and the permission rationale are original compositions

Only onboarding panel 1 (brand mark, "المتن", tagline) draws from the fetched *Splash Screen*
design. Panels 2 ("what the app is for") and 3 (the download-model explanation — the panel that
earns this story its place) have no Stitch capture; they are composed from the same
`presentation/theme` token set, following the Phase 8 precedent for Settings (also an original
composition, see `specs/008-storage-downloads/design-notes.md`). `PermissionRationaleSheet` follows
the already-fetched-and-implemented `InstallPromptSheet` idiom exactly (title, body, primary button,
text-button dismiss) rather than inventing a new sheet shape.

## `permission_rationale_not_now` — one string beyond the T021 fixed list

T021 enumerates the phase's string additions as a fixed table. Building `PermissionRationaleSheet`
(T076) surfaced a real gap: the rationale needs a "Not now" dismiss action, and no existing or
listed string covers it. Added `permission_rationale_not_now` ("ليس الآن" / "Not now") to both
locale files, immediately adjacent to `permission_rationale_body` — the smallest possible addition,
justified by the UI genuinely not being buildable without it (the same class of gap
`/speckit-analyze`'s C1 finding caught for `A11yAction` before implementation started).

## T098 — sheet motion: a content-level fade layered on the framework's own slide

`ModalBottomSheet` (Material3 1.11.0-alpha07, this project's resolved version) does not expose an
`animationSpec`/`enterTransition` parameter for its own slide-up — that timing is internal to
`SheetState`. Rather than reach into internal APIs, each of the four sheets
(`RepetitionSetupSheet`, `NoteEditorSheet`, `InstallPromptSheet`, `PermissionRationaleSheet`) fades
its content in at `MatnMotion.durationMedium` via a `remember { Animatable }` + `LaunchedEffect`,
layered on top of the framework's built-in slide — reaching the contract's "slide up + fade"
description through composition rather than a single controlled transition. `ConfirmRemovalDialog`
needed no equivalent treatment: Material3's `AlertDialog` already fades and scales in by default,
which is exactly the B3 table's "Dialogs: fade + subtle scale" — adding a second transition on top
would double-animate it.

## What implementation MUST NOT do (carried forward, re-verified this phase)

- No raw hex colour, `.dp`, or `.sp` literal outside `presentation/theme/Color.kt` /
  `presentation/theme/Motion.kt` (rule 2; T105 re-greps this).
- No animation call — nothing importing `androidx.compose.animation*`, no `Animatable`, no
  `delay()` — anywhere in `playback/PlaybackController.kt` or reachable from `moveToVerse`/
  `applyCursor` (rule 1; T101's regression tests assert the transition path never awaits time).
- No permission request during onboarding or at app launch (rule 5) — `OnboardingScreen.kt`
  requests nothing; the only `NotificationPermission.request()` call sites are
  `EnsureNotificationPermissionUseCase.onRationaleContinue()` (first playback, behind the
  rationale) and `SettingsViewModel.onRetryNotificationPermission()` (an explicit, student-initiated
  retry from Settings, not a cold-launch ask).
- The storage section stays at the top of `SettingsScreen` (rule 4) — Appearance, Permissions, and
  "Show the introduction again" were all added **below** it, in that order.
