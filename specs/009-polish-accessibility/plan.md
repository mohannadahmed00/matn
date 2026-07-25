# Implementation Plan: Polish & Accessibility

**Branch**: `009-polish-accessibility` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/009-polish-accessibility/spec.md`

## Summary

Phase 9 is cross-cutting: it changes how every already-shipped screen looks, adapts, announces
itself, and moves, without adding a feature. The work resolves to **one new colour scheme, three
scalar preferences, four platform seams, one new screen, and one shared component** — plus an audit
pass over 56 presentation files.

`MatnTheme` becomes the single resolution point for appearance: `ThemeMode` (SYSTEM/LIGHT/DARK) from
the student, `isSystemInDarkTheme()` from the platform, one `Appearance` out, one `ColorScheme`
applied. `MatnDarkColors` is **derived** from the existing light roles by a published tone rule and
gated by an automated contrast test — the test, not inspection, is what makes the palette correct.
Accessibility is enforced where possible at **compile time**: one new `IconActionButton` takes its
label as a required non-nullable parameter, so an unlabeled icon action stops compiling, and it
carries `minimumInteractiveComponentSize()` so the 48dp floor comes for free. Onboarding is three
explanatory panels that request **nothing**; the notification permission moves from its current cold
launch-time request to first playback, behind a rationale, gated once per install.
Adaptive layout reads *available width* through `BoxWithConstraints`, so a narrow tablet pane gets
the phone layout for free. Motion gets one shared vocabulary and a reduce-motion seam.

**Three findings shaped this plan more than the spec did:**

1. **No migration.** The `app_setting` key/value table already exists (`Content.sq:56`) with working
   queries and a working precedent in `ReadingPreferencesRepositoryImpl`. All three new preferences
   live there. Phase 9 ships with zero schema risk — unlike Phases 7 and 8.
2. **The spec's "above 200%" carve-out is unreachable.** Compose caps iOS `fontScale` at **1.8** even
   at AX5, and Android tops out at 2.0. The hedge added during clarification described a state
   neither platform can produce, so the implementation targets the *stricter* guarantee across the
   whole range at no extra cost. See research D4 — **this warrants a small spec edit**.
3. **The registered splash design animates a fake 3-second progress bar** under "Preparing your
   workspace…". Matn has nothing to prepare. The visual treatment is adopted; the fabricated delay is
   not (Principle VIII: designs are authoritative about appearance, not behaviour).

**One invariant outranks everything else here**: verse-to-verse audio stays gapless with all
animation enabled (FR-035, Principle VII). A carousel that inserts 250ms between verses to look
smooth breaks the product's core promise to gain nothing.

## Technical Context

**Language/Version**: Kotlin 2.4.10, Compose Multiplatform 1.11.1, Material 3 1.11.0-alpha07,
coroutines/Flow.

**Primary Dependencies**: No new *runtime* dependency in `shared`. Everything this phase needs is
already resolved — `isSystemInDarkTheme()` from `compose.foundation` (verified in the 1.11.1
sources), `minimumInteractiveComponentSize()` / `LocalMinimumInteractiveComponentSize` from
`material3` (verified in the 1.11.0-alpha07 sources, default 48.dp), `BoxWithConstraints` from
`foundation-layout`. **One new Android-app dependency**: `androidx.core:core-splashscreen`, required
because `minSdk = 24` predates the API 31 built-in splash (justified in Complexity Tracking).

**Storage**: SQLDelight, **unchanged**. No new table, no `.sqm`, no schema-version bump — the
database stays at v5 as Phase 8 left it. Three scalar preferences go into the existing `app_setting`
table (research D2). One non-authoritative mirror of the resolved appearance is written to
`SharedPreferences` / `NSUserDefaults` so the platform launch window can be themed before any Kotlin
runs.

**Testing**: `kotlin.test` + `kotlinx-coroutines-test` in `commonTest`, driven by fakes for the four
platform seams. Two new pure-Kotlin suites carry the accessibility guarantees: `ColorContrastTest`
(WCAG arithmetic over ~24 role pairs × 2 schemes) and `A11yLabelCatalogTest` (label-map totality in
both locales). Every screen-level content composable gains a dark preview, a 2.0×-fontScale/320dp
preview, and an 840dp preview. Screen-reader journeys and the gapless-audio check are manual — see
[quickstart.md](./quickstart.md) §3.

**Target Platform**: Android (minSdk 24) + iOS via the KMP `shared` module. No distribution change.

**Project Type**: Mobile app (KMP) — single `shared` module.

**Performance Goals**: SC-013 — no dropped frames on a mid-range device and no input delayed beyond
100ms, which is why `durationShort` is 150ms. SC-002 — a system theme change is reflected within one
second; in practice it is one recomposition, since `isSystemInDarkTheme()` reads a
`CompositionLocal` on iOS and the configuration on Android. **SC-015 — animation adds exactly zero
measurable delay to playback**, enforced structurally by keeping every animation off the playback
path rather than by tuning a duration.

**Constraints**: Gapless audio is untouchable (FR-035, Principle VII). RTL is already forced app-wide
and every new surface and animation direction must honour it. Zero new raw hex / `.dp` / `.sp`
literals. `MatnTheme`'s new parameters must be defaulted or all 88 existing previews break
(research D12). Onboarding must work with no network (FR-024). The app must never clamp the
student's font scale (FR-014).

**Scale/Scope**: 0 tables, 0 migrations; 1 new `ColorScheme` (34 roles); 3 preferences; 4 platform
seams (`AppearanceMirror`, `NotificationPermission`, `MotionPreferences`, plus the launch-theme
plumbing) with 2 implementations each; 2 new repositories; 5 use cases; 1 new screen (Onboarding);
1 new shared component (`IconActionButton`) absorbing the 3 icon-only `clickable` sites (of 11 total);
1 label catalogue;
motion + width-class token files; ~3 new previews × 9 screens; an audit pass over 56 presentation
files; Android manifest launch-theme replacement and the deletion of
`MainActivity.requestPostNotificationsIfNeeded()`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Clean Architecture & Layer Boundaries** — PASS. `ThemeMode`, `Appearance`, `OnboardingStatus`,
  `PermissionStatus`, the two repository interfaces, the four platform-seam interfaces and the five
  use cases are pure Kotlin in `commonMain/domain`. Persistence composition sits in `data`. Screens
  reach domain only through use cases injected via Koin. Platform code (splash theme, permission
  launcher, reduce-motion observers) sits at the outermost edge behind interfaces, exactly like
  `Media3AudioEngine` and `PlayAssetDeliveryEngine` today.
- **II. MVVM Presentation** — PASS by construction. The new `OnboardingViewModel` and `AppViewModel`
  extend `BaseViewModel` and expose one immutable state each via `StateFlow`; `OnboardingScreen`
  ships a stateless content composable plus a thin holder. Preview coverage *expands* rather than
  merely holding: every screen-level content composable gains dark, max-font-scale, and expanded-
  width previews (`accessibility-contract.md` §7, `adaptive-motion-contract.md` §A5).
- **III. DRY via Base Abstractions** — PASS, and this phase is largely *an application* of the
  principle. `IconActionButton` exists precisely so 18 hand-rolled clickable targets stop being 18
  independent accessibility decisions; `A11yLabels` is one catalogue rather than a literal per call
  site; `MatnMotion` is one vocabulary rather than a duration per screen; `MatnSpacing` gains
  width-aware accessors so no screen branches on a breakpoint itself. New use cases implement the
  existing `UseCase`/`FlowUseCase` contracts with `Resource`.
- **IV. Shared-First Multiplatform** — PASS. Every decision — mode→appearance resolution, the dark
  palette, the contrast table, breakpoints, column counts, the reduced-motion mapping, the
  permission gate sequence, onboarding flow control — lives in `commonMain`. The platform
  implementations are thin adapters translating one platform value into one domain value, with no
  business logic. Nothing is duplicated between `androidMain` and `iosMain`.
- **V. Test-First & Testable Design** — PASS. All four platform seams are interfaces, not
  `expect`/`actual`, so fakes script every path with no device (the precedent Phase 8 set and its
  stated reason). The two accessibility gates are pure arithmetic and structure over values already
  in `commonMain`, so they run in CI as permanent regression guards. Where automation genuinely
  cannot reach — rendered screen-reader semantics, the gapless-audio listen — research D5 says so
  plainly instead of shipping a test that asserts a constant.
- **VI. Offline-First & Future-Proof Data** — PASS, trivially. This phase adds no network access of
  any kind, and FR-024 makes onboarding explicitly offline. No new persisted entity needs a UUID
  because none is an entity: these are device-local display preferences, deliberately *not* sync
  candidates (a phone and a tablet should be free to differ in theme and text size). No existing
  schema is touched.
- **VII. Experience Fidelity: Audio, RTL & Accessibility** — PASS, and this phase is where the
  principle's accessibility clause is finally discharged: dark mode, adjustable Arabic font size
  (already present, now combined correctly with the system scale), tablet-adaptive layouts, and
  contrast compliance are all delivered here. **Gapless audio is protected by an explicit structural
  rule** (`adaptive-motion-contract.md` §B4): no animation call may appear on the playback path, and
  `PlaybackControllerTest` is extended to assert it. RTL correctness is extended from layout to
  motion direction (FR-033) and screen-reader focus order (FR-012).
- **VIII. Design Fidelity & Reusable Composables** — PASS with two recorded deviations, both
  pre-authorized rather than invented. The single registered Phase 9 screen — *Splash Screen*
  (`6aba0b42e95d43e5b6f81928f3e3f7c6`) — **was fetched during planning**, not deferred to the
  implementer, and its token set was confirmed identical to `docs/DESIGN-SOURCE.md`. Two deviations:
  (a) **the dark palette is derived, not fetched** — the fetched HTML declares `darkMode: "class"`
  but contains zero `dark:` classes, confirming registry issue #5 first-hand, and issue #5 already
  pre-authorizes derivation; (b) **the fetched 3000ms fake progress bar is not implemented** —
  designs are authoritative about appearance, not behaviour, and the app has nothing to prepare.
  Onboarding panels 2–3, the permission rationale, the appearance setting, and every adaptive and
  dark variant are original compositions from the existing token set, as Phase 8 did for Settings.
  All of this must be recorded in `design-notes.md` before merge (the Phase 6/7/8 precedent). Zero
  new raw literals.

**Post-design re-check (after Phase 1)**: PASS on all eight principles. Phase 1 introduced no
violations and, notably, *removed* expected complexity — the schema migration this phase was assumed
to need turned out to be unnecessary (research D2). One item sits in Complexity Tracking (the
`core-splashscreen` dependency), which is the constitution's standard new-dependency justification
rather than a principle conflict. Two items are flagged for the spec owner rather than tracked as
deviations: the unreachable font-scale carve-out (research D4) and the pre-existing raw-literal debt
this phase deliberately does not sweep (research D13).

## Project Structure

### Documentation (this feature)

```text
specs/009-polish-accessibility/
├── plan.md              # This file
├── research.md          # Phase 0 output — D1–D13
├── data-model.md        # Phase 1 output — preferences + render-layer types (no schema change)
├── quickstart.md        # Phase 1 output — automated / preview / manual validation passes
├── contracts/           # Phase 1 output
│   ├── theming-contract.md              # US1 — theme resolution, dark scheme, launch surface, settings
│   ├── accessibility-contract.md        # US2 — contrast table, label catalogue, IconActionButton, scaling
│   ├── onboarding-permissions-contract.md # US3 — onboarding flow, permission seam, first-playback gate
│   └── adaptive-motion-contract.md      # US4 + US5 — width class, per-surface adaptation, motion tokens
├── checklists/
│   └── requirements.md  # spec quality checklist (16/16)
└── tasks.md             # Phase 2 output (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
androidApp/src/main/
├── AndroidManifest.xml                      # MODIFY — replace the hard-coded Light launch theme (line 15)
├── kotlin/com/giraffe/matn/MainActivity.kt  # MODIFY — install splash; setTheme() from the mirror before
│                                            #          super.onCreate(); DELETE requestPostNotificationsIfNeeded()
│                                            #          and its discarded launcher; provide the new seams to Koin
└── res/values{,-night}/themes.xml           # NEW — light/dark launch themes
androidApp/build.gradle.kts                  # MODIFY — androidx.core:core-splashscreen
iosApp/iosApp/
├── Assets.xcassets                          # MODIFY — launch background colour with Any/Dark appearances
└── LaunchScreen.storyboard                  # NEW/MODIFY — static brand mark on that colour

shared/src/commonMain/kotlin/com/giraffe/matn/
├── App.kt                                   # MODIFY — AppViewModel + BoxWithConstraints width class;
│                                            #          pass themeMode/reduceMotion into MatnTheme
├── domain/
│   ├── model/{ThemeMode,Appearance,OnboardingStatus,PermissionStatus,WindowWidthClass}.kt   # NEW
│   ├── appearance/AppearanceMirror.kt       # NEW — platform seam (resolved appearance → platform store)
│   ├── permission/NotificationPermission.kt # NEW — platform seam (status/request/openSystemSettings)
│   ├── preferences/MotionPreferences.kt     # NEW — platform seam (observeReduceMotion)
│   ├── repository/
│   │   ├── AppearancePreferencesRepository.kt   # NEW — observe + synchronous read + set
│   │   └── OnboardingRepository.kt              # NEW
│   └── usecase/
│       ├── ObserveThemeModeUseCase.kt       # NEW
│       ├── SetThemeModeUseCase.kt           # NEW
│       ├── ObserveOnboardingStatusUseCase.kt # NEW
│       ├── CompleteOnboardingUseCase.kt     # NEW
│       └── EnsureNotificationPermissionUseCase.kt # NEW — the first-playback gate
├── data/repository/
│   ├── AppearancePreferencesRepositoryImpl.kt   # NEW — app_setting, mirrors ReadingPreferences… exactly
│   └── OnboardingRepositoryImpl.kt              # NEW — app_setting
├── playback/PlaybackController.kt           # MODIFY — permission gate in startSession only; never per verse
├── di/{ContentModule,MatnKoinStarter}.kt    # MODIFY — register repos, use cases, and the three seams
└── presentation/
    ├── theme/
    │   ├── Color.kt                         # MODIFY — MatnDarkColors, same 34 roles, one shared role list
    │   ├── MatnTheme.kt                     # MODIFY — themeMode/reduceMotion params (defaulted);
    │   │                                    #          provides LocalAppearance + LocalReduceMotion
    │   ├── Motion.kt                        # NEW — MatnMotion tokens + LocalReduceMotion
    │   ├── WindowSize.kt                    # NEW — WindowWidthClass, widthClassFor, LocalWindowWidthClass
    │   └── Spacing.kt                       # MODIFY — horizontalMargin/readingMaxWidth/libraryColumns
    ├── common/
    │   ├── A11yLabels.kt                    # NEW — A11yAction enum → StringResource catalogue
    │   ├── IconActionButton.kt              # NEW — required label + minimumInteractiveComponentSize()
    │   └── {MatnCard,DailyGoalRing,MatnProgressBar,InstallProgressIndicator,
    │       ContentAvailabilityBadge,ContentActionButton,StorageUsageRow,
    │       ConfirmRemovalDialog,InstallPromptSheet,*Glyphs}.kt   # MODIFY — labels, non-colour signals,
    │                                        #          animated value changes, dark previews
    ├── onboarding/{OnboardingScreen,OnboardingUiState,OnboardingViewModel}.kt   # NEW
    ├── settings/{SettingsScreen,SettingsUiState,SettingsViewModel}.kt  # MODIFY — appearance + permission
    │                                        #          rows BELOW storage (SC-008 must still hold)
    ├── navigation/MatnNavHost.kt            # MODIFY — onboarding start destination + route; shared
    │                                        #          enter/exit transitions on the NavHost
    └── {home,details,player,search,notes,goals}/*.kt   # MODIFY — width-class layout, labels via
                                             #          IconActionButton, rememberSaveable audit,
                                             #          dark + max-scale + expanded previews

shared/src/androidMain/kotlin/com/giraffe/matn/
├── appearance/AndroidAppearanceMirror.kt    # NEW — SharedPreferences
├── permission/AndroidNotificationPermission.kt # NEW — POST_NOTIFICATIONS launcher; GRANTED below API 33
└── preferences/AndroidMotionPreferences.kt  # NEW — ANIMATOR_DURATION_SCALE + ContentObserver

shared/src/iosMain/kotlin/com/giraffe/matn/
├── appearance/IosAppearanceMirror.kt        # NEW — NSUserDefaults
├── permission/IosNotificationPermission.kt  # NEW — UNUserNotificationCenter
└── preferences/IosMotionPreferences.kt      # NEW — UIAccessibility.isReduceMotionEnabled + notification

shared/src/commonTest/kotlin/com/giraffe/matn/
├── theme/
│   ├── ContrastRatio.kt                     # NEW — WCAG luminance/ratio helper (test-only)
│   ├── ColorContrastTest.kt                 # NEW — the gate on the derived dark palette
│   ├── ThemeModeTest.kt                     # NEW — resolution + storage fallback
│   └── WindowWidthClassTest.kt              # NEW — every breakpoint boundary, both sides
├── presentation/
│   ├── A11yLabelCatalogTest.kt              # NEW — catalogue totality, both locales
│   ├── MatnSpacingTest.kt                   # NEW — margins/columns per class; SC-011's 2× rule
│   ├── OnboardingViewModelTest.kt           # NEW
│   ├── ReducedMotionSpecTest.kt             # NEW — every transition has a reduced form
│   └── SettingsViewModelTest.kt             # MODIFY — appearance + permission rows
├── data/
│   ├── AppearancePreferencesRepositoryTest.kt # NEW — round-trip, default, sync/flow agreement, mirror
│   └── OnboardingRepositoryTest.kt          # NEW
├── permission/
│   ├── FakeNotificationPermission.kt        # NEW
│   └── NotificationPermissionGateTest.kt    # NEW — asked once; denial never blocks playback
├── preferences/FakeMotionPreferences.kt     # NEW
├── appearance/FakeAppearanceMirror.kt       # NEW
└── playback/PlaybackControllerTest.kt       # MODIFY — gate runs once per install; NO animation on the
                                             #          verse-transition path (FR-035 regression guard)
```

**Structure Decision**: Single `shared` KMP module, following the layering already in place. Three
new `domain` sub-packages (`appearance`, `permission`, `preferences`) sit beside the existing
`audio` and `delivery` seam packages — the closest precedent. A new `presentation/onboarding`
package sits beside `home`/`details`/`settings`. Theme additions extend the existing
`presentation/theme` package rather than starting a new one. The only work outside `shared` is the
platform launch surface, which by definition cannot live in shared code.

## Complexity Tracking

> One item requires justification. No principle deviation remains, and this phase turned out
> *simpler* than assumed: the schema migration it was expected to need is unnecessary (research D2).

| Item | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| **New dependency** — `androidx.core:core-splashscreen` (androidApp only) | `minSdk = 24`, and the platform splash-screen API arrived in API 31. Without the backport the app cannot present a theme-aware launch surface on the majority of its supported range, and FR-005/SC-003 ("no incorrect theme visible at any point during launch") cannot be met. It is a Google-maintained AndroidX artifact with no transitive weight. | Raising `minSdk` to 31 to use the built-in API would drop real devices for a cosmetic gain — not a trade this phase gets to make. Hand-rolling a first-Activity "splash screen" is the anti-pattern the platform API exists to replace: it renders *after* the system window, so the white flash it is meant to fix still happens. Doing nothing leaves `AndroidManifest.xml:15`'s hard-coded `Theme.Material.Light.NoActionBar` flashing white on every dark-mode cold launch, which is the exact defect FR-005 names. |

### Flagged for the spec owner

| Item | Status |
|---|---|
| **FR-014 / SC-007's "above 200%" carve-out was unreachable** | **Resolved 2026-07-25.** Compose caps iOS `fontScale` at 1.8 (AX5) and Android at 2.0, so nothing above 200% exists (research D4, verified in the CMP 1.11.1 iOS sources). FR-014 and SC-007 were tightened to a hard guarantee across the whole reachable range, the clarification bullet carries a "superseded during planning" note, and a *Font-scale range* assumption records why. Spec, plan, and tasks now agree. |
| **74 raw `.dp` literals remain in `presentation/`** | **Open, deliberately out of scope.** Pre-existing Principle VIII debt inherited from before Phase 10 and recorded in `docs/DESIGN-SOURCE.md`. Phase 9 touches most of these files but does not sweep them (research D13): mixing a token-hygiene cleanup into an accessibility phase makes both harder to review. New code here adds zero literals. Worth its own small chore. |

### Post-analysis remediation (2026-07-25)

`/speckit-analyze` raised 13 findings, 0 critical. All 13 were resolved before implementation:

- **C1 (HIGH)** — `A11yAction` had 29 members but only 25 resolvable string resources, so the
  totality test could not pass. The enum is now a fixed 30-member table in
  `contracts/accessibility-contract.md` §2.1 with every member mapped to a real resource (26 existing
  + 4 new). Bookmark/note/memorized collapsed from ADD/REMOVE pairs to single toggles matching the
  codebase's existing `toggle_*` strings — which is also the better screen-reader model, since a
  control's *name* should not change when its *state* does (§2.1a).
- **C2 / A1 (MEDIUM)** — FR-029's sheets and dialogs had no task and no number. Added
  `MatnSpacing.surfaceMaxWidth` (480dp, deliberately narrower than `readingMaxWidth`) and task T088
  bounding the four sheet/dialog surfaces; `MatnSpacingTest` now asserts the invariant.
- **N1 (MEDIUM)** — presentation reached past use cases to a repository for the synchronous read.
  Added `GetThemeModeNowUseCase` and `GetOnboardingStatusNowUseCase`; T041 and T072 now inject those.
- **D1 (MEDIUM)** — the surface colour is written in three places the Kotlin layer cannot unify.
  T037 now requires a comment naming both mirrors, and T105 re-checks all three agree.
- **A2 (MEDIUM)** — SC-013 was unfalsifiable. Restated qualitatively, with a reference device and an
  explicit rationale for *not* building a frame-timing harness, in `quickstart.md`.
- **G1 (MEDIUM)** — FR-012 had no implementation task. Added T063, a per-screen traversal-order audit
  that applies `traversalIndex` only where the RTL order is genuinely wrong.
- **G2, G3, G4, T1, D2 (LOW)** — FR-037's no-blocking-overlay rule stated in T094; FR-025's
  interrupted-restart path asserted in T079; FR-015's minimum font combination previewed in T067;
  "Window Size Category" renamed to Window Width Class; FR-031 reworded as an explicit cross-check
  rather than a restatement of other requirements.

Task count moved from 105 to 107.
