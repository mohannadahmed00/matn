# Contract: Adaptive Layout & Motion

**Feature**: `specs/009-polish-accessibility` | Covers **US4** (FR-026 – FR-031) and **US5** (FR-032 – FR-037)

---

# Part A — Adaptive layout

## A1. Width class

```kotlin
// presentation/theme/WindowSize.kt
enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

val LocalWindowWidthClass: ProvidableCompositionLocal<WindowWidthClass>  // default COMPACT

fun widthClassFor(width: Dp): WindowWidthClass   // pure — unit-tested at every boundary
```

Provided once at the app root:

```
App() → BoxWithConstraints { CompositionLocalProvider(LocalWindowWidthClass provides widthClassFor(maxWidth)) { … } }
```

Breakpoints per data-model §2.5. `BoxWithConstraints` comes from `foundation-layout`, already a
dependency — no new artifact (research D8).

**Invariants**

- No screen reads a raw `Dp` width or a device flag; every screen reads the class or a
  `MatnSpacing` accessor derived from it.
- Because it measures *available* width, a narrow tablet split-screen pane is `COMPACT` — which is
  exactly FR-026 and the spec's split-screen edge case, with no special handling.

## A2. Per-surface adaptation

| Surface | `COMPACT` | `MEDIUM` | `EXPANDED` | Requirement |
|---|---|---|---|---|
| Screen horizontal margin | 20dp (`marginMobile`) | 64dp (`marginDesktop`) | 64dp | FR-026 |
| Library grid columns | 2 | 3 | 4 | FR-027, SC-011 (≥ 2× compact) |
| Reading carousel / verse text | full width less margin | bounded to `readingMaxWidth`, centred | same | FR-028 |
| Player control bar | full width | bounded to `MatnSpacing.surfaceMaxWidth`, centred | same | FR-029 |
| Bottom sheets & dialogs | full width | bounded to `MatnSpacing.surfaceMaxWidth`, centred | same | FR-029 |
| Table of contents | full width | full width | full width | unchanged this phase |
| Settings / Goals / Notes lists | full width | bounded, centred | bounded, centred | FR-028 |

Grid column counts are the contract because SC-011 is measured against them.

**`surfaceMaxWidth` is a single token, not a per-surface number** (FR-029: "defined once in the shared
token layer, not per surface"). It sits in `MatnSpacing` beside `readingMaxWidth` and is deliberately
narrower — 480dp — because a control bar or dialog stretched to a comfortable *reading* measure puts
its controls uncomfortably far apart, which is the failure FR-029 names. The four surfaces it governs
are `RepetitionSetupSheet`, `NoteEditorSheet`, `InstallPromptSheet`, and `ConfirmRemovalDialog`, plus
the `PlayerBar` control bar.

## A3. State preservation across resize (FR-030)

Rotation and multi-window changes are ordinary recompositions, not process events, so state is
preserved by keeping it where it already lives:

- Screen state stays in `ViewModel`s, which survive configuration change.
- Scroll/carousel position uses `rememberSaveable`-backed state — **audit** each list and carousel;
  a plain `remember` is the failure mode for FR-030.
- Dialog and sheet visibility lives in `UiState`, not in a local `remember`, so an open sheet
  survives (the spec's "rotation with a dialog open" edge case).
- In-progress text input (note editor, search field) must be `rememberSaveable` or ViewModel-held.

Android additionally requires `MainActivity` to declare it handles the relevant configuration
changes, or to correctly restore across recreation. The audit covers whichever path is taken.

## A4. Tests

| Test | Asserts |
|---|---|
| `WindowWidthClassTest` | `widthClassFor` at 319/320/599/600/839/840/1280dp — every boundary, both sides |
| `MatnSpacingTest` | `horizontalMargin` / `libraryColumns` per class; `EXPANDED` columns ≥ 2 × `COMPACT` (SC-011) |

## A5. Previews required

Every screen-level content composable gains an `EXPANDED`-width preview (840dp+) alongside its
existing compact ones, so column counts and bounded widths are inspectable without a tablet.

## A6. Out of scope

A two-pane library-plus-reader layout (clarified 2026-07-25). `WindowWidthClass` never influences the
navigation graph — only layout within a screen.

---

# Part B — Motion

## B1. Motion tokens

```kotlin
// presentation/theme/Motion.kt
object MatnMotion {
    const val durationShort = 150    // state changes, toggles
    const val durationMedium = 250   // sheets, dialogs, screen transitions
    const val durationLong = 400     // the onboarding reveal
    val easingStandard: Easing       // most transitions
    val easingEmphasized: Easing     // entering surfaces
    val easingExit: Easing           // leaving surfaces
}
```

One vocabulary, defined once (FR-032). **No screen names its own duration or easing** — the same rule
Principle VIII applies to colour and spacing literals, extended to motion.

## B2. Reduce-motion seam

```kotlin
// domain/preferences/MotionPreferences.kt
interface MotionPreferences {
    fun observeReduceMotion(): Flow<Boolean>
}
```

Injected via Koin; surfaced as `LocalReduceMotion` by `MatnTheme`. Compose exposes no common signal
(research D7), so this seam is unavoidable.

| Platform | Source |
|---|---|
| Android | `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`, observed via `ContentObserver` |
| iOS | `UIAccessibility.isReduceMotionEnabled` + `UIAccessibilityReduceMotionStatusDidChangeNotification` |

### B2.1 Reduce-motion behaviour (FR-036)

| Normal | Reduced |
|---|---|
| Screen slide + fade | Fade only, `durationShort` |
| Sheet slide up | Fade only |
| Progress bar animated fill | Immediate value, no tween |
| Goal ring sweep | Immediate value |
| Carousel verse slide | Immediate position change |
| Onboarding logo reveal | No reveal; static |

**Never suppressed**: the *information*. A progress bar still shows its value, the active verse is
still indicated, the ring still reads "4/10" — only the interpolation stops. This is what separates
FR-036 from "turn animations off and lose state visibility".

## B3. Transitions

| Surface | Motion | Notes |
|---|---|---|
| Forward navigation (Home → Details, → Search) | Slide from the RTL start edge + fade | FR-033: direction must be RTL-correct — a left-to-right slide is a bug, not a preference |
| Back navigation | Reverse | |
| Bottom-nav tab change | Fade, no slide | Sibling destinations; sliding implies hierarchy |
| Sheets (repetition setup, note editor, install prompt) | Slide up + fade | |
| Dialogs (removal confirmation) | Fade + subtle scale | |
| Value changes (playback/install progress, progress bars, goal ring, badges) | `animateFloatAsState` / `animateColorAsState` at `durationShort` | FR-034: never display a value the state does not hold — animate *toward* the true value, never past it |
| Carousel verse advance | `durationMedium`, `easingStandard` | FR-035 below |

Wired in `MatnNavHost` via `enterTransition` / `exitTransition` on the `NavHost`, not per
`composable`, so every route shares one vocabulary.

## B4. The audio invariant (FR-035) — NON-NEGOTIABLE

Verse-to-verse audio remains gapless with all animation enabled.

- Animation MUST NOT be awaited anywhere on the playback path. The carousel animates **in response
  to** a verse change; the verse change never waits for the animation to finish.
- No `suspend`ing animation call in `PlaybackController`, `moveToVerse`, or `applyCursor`.
- Constitution Principle VII makes gapless playback contractual, and it outranks any transition.

This is the single most important constraint in Part B: a smooth carousel that inserts a 250 ms gap
between verses breaks the product's core promise to gain nothing.

## B5. Input responsiveness (FR-037)

Transitions never gate input. Concretely, and enforced at authoring time rather than discovered in
testing:

- **No blocking overlay, scrim, or `Modifier.pointerInput` consume-all may be introduced for the
  duration of a transition.** If a transition needs to prevent a double-navigation, use
  `launchSingleTop` on the navigation call — not an input blocker.
- Controls stay hittable while a transition plays; a tap landing mid-transition is honoured, not
  dropped.
- SC-013's "within a tenth of a second" is why `durationShort` is 150 ms and not 300 ms.

There is no unit test for this — it is a constraint on how B3's transitions are written, verified by
the manual pass in `quickstart.md` §3.6 step 4.

## B6. Tests

| Test | Asserts |
|---|---|
| `MotionPreferencesTest` | Fake emits both values; `LocalReduceMotion` reflects them |
| `ReducedMotionSpecTest` | Every entry in B2.1 resolves to its reduced form when the flag is set — a pure mapping test over the spec table, no rendering |
| `PlaybackControllerTest` (extend) | No animation call on the verse-transition path; timing unchanged with motion enabled (FR-035) |

## B7. Previews required

Reduce-motion is not visible in a static preview, so the guarantee is carried by `ReducedMotionSpecTest`
plus the manual pass in `quickstart.md`. Previews cover the *end states*, which they already do.
