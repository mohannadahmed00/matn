# Phase 0 Research: Design System Adoption

Technical Context in `plan.md` had no unresolved `NEEDS CLARIFICATION` markers — the stack,
testing approach, and target platforms are already fixed by Phases 1–5. The decisions below cover
the implementation-approach questions that *do* need a documented choice before design.

## 1. How should the previous/active/next verse carousel be driven?

**Decision**: The carousel is a plain derived-state render (`Column`/`Box` with
`AnimatedContent`/`animate*AsState` for the highlight/scale/opacity transitions between states),
indexed directly off the existing playback-position `StateFlow` already exposed by
`PlayerBarViewModel`/`MatnDetailsViewModel`. It is not a swipeable `HorizontalPager`.

**Rationale**: The design shows the carousel advancing in lock-step with playback (previous
verse fades back, active verse becomes the just-finished one, next verse becomes active) — it has
exactly one source of truth: playback position. A gesture-driven `Pager` would introduce a second
index (the page the user swiped to) that has to be reconciled with playback position on every
transport action, and Compose's `Pager` scroll-then-settle semantics fight with the "always
show exactly index-1/index/index+1" requirement instead of simplifying it.

**Alternatives considered**: `HorizontalPager` with `animateScrollToPage` synced to playback —
rejected for the reconciliation problem above and because the design has no swipe-to-seek
affordance (seeking is via the scrub bar, not swiping verses).

## 2. What component backs the repetition-setup bottom sheet?

**Decision**: Compose Material 3 `ModalBottomSheet` (already available transitively via
`compose.material3`, no new dependency).

**Rationale**: Matches the design's glass-panel bottom-sheet presentation, is the standard M3
primitive for this pattern, and needs no new library — consistent with the constitution's "new
dependency requires justification" rule (III/Technology constraints): there is a built-in option,
so no justification would pass review anyway.

**Alternatives considered**: A custom `Dialog`-based sheet — rejected as unnecessary
reinvention of `ModalBottomSheet`'s drag-to-dismiss, scrim, and inset handling.

## 3. How are the two new fonts (Source Serif 4, Plus Jakarta Sans) sourced and licensed?

**Decision**: Bundle both as `composeResources` font files, loaded into `FontFamily`s the same
way `Type.kt` already bundles Amiri.

**Rationale**: Both are Google Fonts distributed under the SIL Open Font License — the same
license already governing the bundled Amiri font, so this introduces no new licensing review
burden and stays consistent with the project's offline-first constraint (Principle VI: the app
MUST function with no network, which rules out loading fonts from Google Fonts' CDN at runtime).

**Alternatives considered**: Runtime `google.fonts` composable API — rejected, requires network
and violates offline-first.

## 4. How are the new design tokens structured in code?

**Decision**: Extend the existing `Color.kt`/`Type.kt` in place with the new value sets; add two
new plain Kotlin `object`s, `Shape.kt` (named `RoundedCornerShape` constants: `lg`, `xl`, `full`)
and `Spacing.kt` (named `Dp` constants: `unit`, `gutter`, `marginMobile`, `marginDesktop`), both
wired into `MatnTheme.kt` alongside the existing `MaterialTheme` wrap.

**Rationale**: Matches the project's existing lightweight style (no `CompositionLocal` machinery
beyond what `MaterialTheme` already provides) and the constitution's anti-overengineering
guidance — a `MaterialTheme.colorScheme`/`typography` extension plus two small token objects is
the minimum structure that eliminates every raw literal without inventing new plumbing.

**Alternatives considered**: A single monolithic `MatnDesignTokens` object bundling color/type/
shape/spacing — rejected as an unnecessary indirection layer; `MaterialTheme` already is that
aggregation point for color/type, so only shape and spacing (which M3 doesn't standardize the way
this design needs) get their own objects.

## 5. How does the bottom nav coexist with the existing 2-route `MatnNavHost`?

**Decision**: Wrap the existing `NavHost` in a `Scaffold` with a `bottomBar = { NavigationBar {…} }`
at the app root; add three new routes (`Routes.GOALS`, `Routes.NOTES`, `Routes.SETTINGS`) that all
point at one new shared `ComingSoonScreen` composable, parameterized by which tab it was reached
from (for the placeholder's copy/icon only — no behavioral difference).

**Rationale**: Smallest change that satisfies FR-006/FR-007 without inventing a second navigation
system; `navigation-compose` (already a dependency) supports this directly.

**Alternatives considered**: A dedicated `Voyager`/`PreCompose` navigator — rejected, would be a
new dependency for something the existing `navigation-compose` already does, and the constitution
requires justifying new dependencies against a simpler existing alternative.

## 6. Does repetition-setup UI rework touch `RepetitionPlanner`/`RepeatCount`?

**Decision**: No. `RepetitionSetupSheet` is purely presentational — it collects the same
`RepetitionSettings` shape the existing `DrillPanel` already produces and forwards it through the
same intent lambda into the existing ViewModel, which still drives `RepetitionPlanner` exactly as
specs/004 tests it.

**Rationale**: FR-005 and the Constitution Check both require this phase to change presentation
only; re-verified by confirming `RepetitionPlanner.kt`/`RepeatCount.kt` need no edits — only their
call site (which composable constructs the settings object and when) changes.

**Alternatives considered**: N/A — changing the domain layer here would violate Principle I and
was never on the table per the spec's own scope boundary.
