# Phase 0 Research: Polish & Accessibility

**Feature**: `specs/009-polish-accessibility` | **Date**: 2026-07-25

Every decision below was verified against the code and the resolved dependency artifacts in this
repository rather than from memory. Where a claim came from reading a library's own sources, the
artifact path is cited so it can be re-checked.

---

## D1 — Dark-theme detection needs no new dependency, and it is live on both platforms

**Decision**: Use `androidx.compose.foundation.isSystemInDarkTheme()` from `commonMain` as the
system signal, resolved once at the root in `MatnTheme`.

**Rationale**: Verified in the resolved artifact
(`~/.gradle/caches/.../org.jetbrains.compose.foundation/foundation/1.11.1/…-sources.jar`):
`commonMain/androidx/compose/foundation/DarkTheme.kt` declares
`@Composable @ReadOnlyComposable fun isSystemInDarkTheme()` delegating to an internal `expect`, and
`skikoMain/…/DarkTheme.skiko.kt` implements it as `LocalSystemTheme.current == SystemTheme.Dark`.
Because the iOS side reads a `CompositionLocal` that the platform updates, an appearance change
recomposes rather than requiring a restart — which is exactly FR-003's "update live … without
restarting". The function's own KDoc recommends the shape this plan uses: call it once at the top
level and provide the resolved value downward, so user overrides can win.

**Alternatives considered**: An injected `SystemAppearance` platform seam (the codebase's usual
pattern for platform capabilities). Rejected — it would duplicate a working common API, and unlike
audio or storage there is no business logic to test here; the override logic that *does* deserve a
test (mode → effective appearance) is pure and lives in the domain layer regardless.

---

## D2 — The theme preference and the onboarding flag need **no schema migration**

**Decision**: Persist both in the existing `app_setting` key/value table under the keys
`theme_mode`, `onboarding_completed`, and `notification_permission_asked`. No `.sqm` file, no
schema-version bump.

**Rationale**: `Content.sq:56` already defines `app_setting(key TEXT PRIMARY KEY, value TEXT)`, with
`selectSetting` / `upsertSetting` / `deleteSetting` queries at lines 148–152 and 211.
`ReadingPreferencesRepositoryImpl` is a working precedent for exactly this pattern, including the
`fromStorageOrDefault` fallback that keeps an absent or corrupt value from ever throwing. Phase 9
adds no entity with identity, relationships, or lifecycle — it adds three scalar preferences — so a
dedicated table would be pure ceremony. This also means Phase 9 ships with **zero migration risk**,
unlike Phases 7 and 8.

**Alternatives considered**: A new `app_preferences` table with typed columns. Rejected — three
scalars do not justify a migration, and the key/value table is already the established home for
exactly this class of value.

---

## D3 — Dark palette derivation: tone-mapping rule plus an automated gate, not hand-picked hex

**Decision**: Derive `MatnDarkColors` from the existing light roles in `theme/Color.kt` by the
standard Material tonal role-flip, and let the contrast test (D6) be the acceptance gate on the
resulting values rather than asserting a hand-authored palette is correct by inspection.

The derivation rule, applied per hue family (primary/secondary/tertiary keep their light-scheme hue
and chroma; neutrals keep theirs):

| Role family | Light tone | Dark tone |
|---|---|---|
| `primary` / `secondary` / `tertiary` | 40 | 80 |
| `onPrimary` / `onSecondary` / `onTertiary` | 100 | 20 |
| `primaryContainer` etc. | 90 | 30 |
| `onPrimaryContainer` etc. | 10 | 90 |
| `background` / `surface` | 98 | 6 |
| `onBackground` / `onSurface` | 10 | 90 |
| `surfaceVariant` | 90 | 30 |
| `onSurfaceVariant` | 30 | 80 |
| `outline` | 50 | 60 |
| `outlineVariant` | 80 | 30 |
| `surfaceContainerLowest → Highest` | 100/96/94/92/90 | 4/10/12/17/22 |
| `error` | 40 | 80 |
| `inverseSurface` / `inverseOnSurface` | 20 / 95 | 90 / 20 |

**Rationale**: The clarification session settled that dark tokens are derived rather than authored
in Stitch, and `docs/DESIGN-SOURCE.md` open issue #5 pre-authorizes exactly that. Fetching the one
registered Phase 9 screen confirmed the gap first-hand rather than taking the registry's word for
it: the *Splash Screen* HTML declares `darkMode: "class"` in its `tailwind.config` but contains
**zero `dark:` variant classes** — the dark mode hook exists, no dark values were ever authored
behind it.

Publishing the *rule* plus a machine-checked gate is more honest than this plan inventing 34 hex
literals that nobody has measured. The gate is what makes the palette correct; the rule is what
makes it coherent.

**Alternatives considered**: Hand-authoring each role from the light value by eye — rejected, it is
unreproducible and the failure mode (a role pair that misses 4.5:1 by a hair) is invisible without
measurement. Generating the palette at runtime from a seed colour via a dynamic-colour utility —
rejected, it adds a dependency and makes the shipped palette a function of library internals rather
than a reviewable constant.

---

## D4 — On iOS, Compose caps `fontScale` at 1.8 — the spec's "above 200%" carve-out is unreachable

**Decision**: Implement a hard no-clipping guarantee across the **entire** reachable font-scale
range on both platforms. Do not build a separate degraded mode for scales above 200%, because
neither platform can produce one.

**Rationale**: This is the research finding that most changes the shape of the work, and it
contradicts an assumption made during clarification. Reading
`iosMain/androidx/compose/ui/uikit/Extensions.ios.kt` in the resolved
`org.jetbrains.compose.ui:ui:1.11.1` sources shows `UIView.density` building
`Density(density = screen.scale, fontScale = uiContentSizeCategoryToFontScaleMap[...] ?: 1.0f)`
against this table:

| `UIContentSizeCategory` | Compose `fontScale` |
|---|---|
| ExtraSmall … Large (default) | 0.8 – 1.0 |
| ExtraLarge / XXL / XXXL | 1.1 / 1.2 / 1.3 |
| AccessibilityMedium … AccessibilityXXXL (AX1–AX5) | 1.4 / 1.5 / 1.6 / 1.7 / **1.8** |

The library's own comment notes these deliberately do **not** match the native percentages, because
"iOS uses non-linear scaling calculated by `UIFontMetrics`, while Compose uses linear". So AX5,
which iOS renders natively at roughly 310%, reaches Compose as **1.8×**. Android's system font
scale tops out at 2.0×. **No reachable configuration on either platform exceeds 2.0×.**

**Consequence for the spec**: FR-014's and SC-007's "above 200% … the no-clipping guarantee does not
extend there" clause describes a state that cannot occur. The hedge was reasonable when written —
it was reasoning from iOS's native 310% figure — but Compose never sees that number. The
implementation therefore targets the stricter guarantee across the full range at no additional
cost, which is what Q4's rejected Option B asked for. The spec text is left as the clarification
recorded it; this decision documents that the implementation exceeds it and that the carve-out is
dead text. **Recommend tightening FR-014/SC-007 to drop the carve-out.**

**Also noted**: `UIView.density` derives from `traitCollection.preferredContentSizeCategory` and
carries an upstream `TODO` about how the density is retrieved. Live reaction to a Dynamic Type
change *while the app is foregrounded* is therefore not guaranteed on iOS the way the dark-mode
signal is (D1). This affects no functional requirement — FR-014 is about surviving a scale, not
about reacting to a mid-session change — but the quickstart's iOS pass sets the scale before launch
rather than during, so the check tests what is actually specified.

---

## D5 — Touch-target enforcement is structural and compile-time, not a unit test

**Decision**: Route every icon-only action through one new shared `IconActionButton` composable that
(a) applies `Modifier.minimumInteractiveComponentSize()` and (b) takes its accessibility label as a
**required, non-nullable** parameter. Migrate the icon-only `Modifier.clickable` call sites in
`presentation/` onto it.

**Call-site count**: there are **11** `Modifier.clickable` call sites, of which **3 are icon-only**
and must move to `IconActionButton` (`ContinueLearningCard.kt:91` dismiss,
`MatnDetailsScreen.kt:620` play, `PlayerBar.kt:296` transport). The remaining 8 are content rows or
cards whose accessible name comes from the text they contain; they need an `onClickLabel`, not a 48dp
icon target. (An earlier draft of this plan said "18 sites" — that number counted the seven
`import androidx.compose.foundation.clickable` lines the grep also matched.)

**Rationale**: Verified in the resolved `org.jetbrains.compose.material3:material3:1.11.0-alpha07`
sources: `commonMain/androidx/compose/material3/InteractiveComponentSize.kt` provides
`Modifier.minimumInteractiveComponentSize()` and `LocalMinimumInteractiveComponentSize`, whose
default is already `48.dp`. So the 32 existing `IconButton` sites are compliant today for free; the
exposure is the 18 hand-rolled `clickable` sites, several of which are `Canvas` glyphs sized 22.dp.

A required non-nullable label parameter is a **stronger guarantee than any test** — an unlabeled
icon action stops compiling. This is deliberately chosen over a runtime assertion. Correspondingly,
this plan does **not** claim an automated touch-target test: the clarification session promised "a
label and touch-target inventory check is likewise automated", and only half of that survives
contact with the code. What is genuinely automated is the label *catalogue* totality check (D6);
touch-target compliance is enforced by construction and confirmed by preview and review. Saying so
is better than shipping a test that asserts a constant equals 48.

**Alternatives considered**: `runComposeUiTest` semantics assertions over each screen. Rejected for
this phase — it needs a new test dependency and a host target that can render, and on the Android
side `commonTest` runs as a plain JVM unit test that cannot compose. It would buy a weaker guarantee
than the compile-time one at meaningfully higher cost.

---

## D6 — What the automated accessibility checks actually assert

**Decision**: Two pure-Kotlin `commonTest` suites, no Compose runtime, no device:

1. **`ColorContrastTest`** — implements the WCAG 2.1 relative-luminance and contrast-ratio formulas
   over a declared table of (foreground role, background role, minimum ratio) pairings, run against
   both `MatnLightColors` and `MatnDarkColors`. This is the gate that makes D3's derived palette
   correct, and it is a standing regression guard: a later token edit that breaks AA fails CI.
2. **`A11yLabelCatalogTest`** — asserts the new `A11yLabels` map from every icon-only action to its
   `StringResource` is **total** (one entry per action, none blank, no duplicates), in both the
   Arabic default and the `values-en` locale.

**Rationale**: Both are arithmetic and structure over values that already live in `commonMain`, so
they need nothing the project does not already have (`kotlin.test` is wired into `commonTest`
today). This delivers the clarified intent — automate what is genuinely automatable — while D5
records honestly what is not.

The pairing table is part of the contract, not an implementation detail, because "100% of text meets
4.5:1" is only meaningful once the set of pairs is enumerated. It lives in
`contracts/accessibility-contract.md`.

**Alternatives considered**: Asserting contrast only for the roles currently used on screen.
Rejected — the point of the gate is that it holds for the whole token set, including roles a future
screen reaches for.

---

## D7 — Reduce-motion requires a platform seam; Compose exposes nothing

**Decision**: Add a `MotionPreferences` interface in `commonMain/domain`, injected through Koin
exactly like `AudioEngine`, `WakeLock`, `ContentDeliveryEngine`, and `DeviceStorage`. Surface it to
the render layer through a `LocalReduceMotion` CompositionLocal provided once in `MatnTheme`.

**Rationale**: Compose's own `AccessibilityManager` interface
(`commonMain/androidx/compose/ui/platform/AccessibilityManager.kt` in the resolved `ui` artifact)
exposes exactly one member — `calculateRecommendedTimeoutMillis` — and the skiko implementation
overrides only that. There is no common reduce-motion signal, so a seam is unavoidable. Android
reads `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`; iOS reads
`UIAccessibility.isReduceMotionEnabled` plus its change notification.

The interface-plus-Koin shape (rather than `expect`/`actual`) is the codebase's established
precedent for platform capabilities — only `DatabaseDriverFactory` and `getPlatform()` use
`expect`/`actual` — and Phase 8's plan articulated the reason: an interface can be faked, so every
motion path is testable and previewable with no device.

**Alternatives considered**: `expect fun isReduceMotionEnabled()`. Rejected — untestable and
un-previewable, and inconsistent with four existing seams.

---

## D8 — Window size comes from available width via `BoxWithConstraints`, not a size-class library

**Decision**: Derive a `WindowWidthClass` (`COMPACT` < 600dp ≤ `MEDIUM` < 840dp ≤ `EXPANDED`) from
`BoxWithConstraints` at the app root and publish it as `LocalWindowWidthClass`.

**Rationale**: FR-026 requires adapting to *available window width*, explicitly not to a device
category — "a narrow split-screen pane on a tablet gets the compact layout". `BoxWithConstraints`
measures precisely that, comes from `foundation-layout` which is already a dependency, and is
correct for free in split-screen, floating windows, and foldables. The
`material3-window-size-class` artifact is not in the resolved dependency graph (only `material3`
itself is cached), so it would be a new dependency bought for a weaker semantic — it classifies the
window, and this plan needs the same numbers without the extra module.

**Alternatives considered**: `androidx.compose.material3.windowsizeclass` /
`org.jetbrains.compose.material3:material3-window-size-class`. Rejected on the dependency-
justification rule in the constitution's Technology Constraints: it adds an artifact to obtain
breakpoints this plan can state in four lines against an API already present.

---

## D9 — Splash: what the platform can actually guarantee, and the residual flash

**Decision**: Three distinct surfaces, deliberately separated:

1. **Platform launch window** — Android `androidx.core:core-splashscreen` (needed because
   `minSdk = 24`, below the API 31 built-in) with a `values-night` variant; iOS launch storyboard
   backed by an asset-catalog colour carrying Any/Dark appearances. Static: a background colour and
   the brand mark. This is all either platform permits.
2. **Onboarding opening panel** — a Compose screen that may carry the fetched design's `logo-reveal`
   motion, subject to `LocalReduceMotion`.
3. **The `Theme.Material.Light.NoActionBar` in `AndroidManifest.xml:15` is replaced.** It is a
   hard-coded light theme and is the concrete cause of the white flash FR-005 forbids.

**Residual limitation, stated rather than hidden**: the pre-Activity launch window is selected by
the OS from a manifest/plist value before any app code runs, so it can follow the *system*
appearance but cannot know a *pinned* override. A student who pins light on a dark device (or the
reverse) sees the system-matched launch surface for its duration before Compose applies their
choice. Mitigations applied: (a) the effective appearance is mirrored on every change into a
synchronously-readable platform store (`SharedPreferences` / `NSUserDefaults`) so `MainActivity`
can `setTheme()` before its first frame, shrinking the window to the system-splash duration alone;
(b) the launch surface uses the brand `surface` role, which the fetched design already specifies
(`<body class="bg-surface text-on-surface …">`), so the mismatch is a background-tone difference
rather than a jarring white flash. Fully eliminating it is not possible within the platforms'
launch model.

**On the fetched design's loading theatre**: the *Splash Screen* HTML animates a progress line over
`duration-[3000ms]` under the caption "Preparing your workspace…". Matn has nothing to prepare — the
database is local and the starter matn is bundled. Implementing that would be a fabricated three-
second delay on every cold start. Per Principle VIII ("the constitution outranks the design", and
designs are authoritative about *appearance*, not behaviour), the visual treatment is adopted and
the artificial wait is not. Recorded for `design-notes.md`.

---

## D10 — Interrupted onboarding restarts; no step is persisted

**Decision**: Persist a single `onboarding_completed` boolean. An interrupted flow restarts from the
first panel on next launch.

**Rationale**: FR-025 requires only that the student "is not left stuck — the flow either resumes or
restarts cleanly". Restart satisfies it outright. The flow is a handful of explanatory panels that
can be skipped in one tap, so resuming mid-way optimises a path measured in seconds at the cost of a
persisted step counter, a migration-free-but-still-real state machine, and a class of "resumed into
a panel that no longer exists after an update" bugs. This is the spec's one Outstanding item from
clarification, resolved here as the planning phase is entitled to.

**Alternatives considered**: Persisting the panel index. Rejected as above.

---

## D11 — Permission seam and the "asked once" guarantee

**Decision**: A `NotificationPermission` interface in `commonMain/domain` (`status()`, `request()`,
`openSystemSettings()`) with Android and iOS implementations, injected via Koin. The
"has been asked" fact is a persisted `app_setting` flag, **not** inferred from platform status.

**Rationale**: FR-022 and SC-010 require prompting "no more than once per permission per install".
Platform status alone cannot distinguish "never asked" from "asked and denied" reliably across both
platforms and OS versions, so the app must own that fact. Storing it beside the other preferences
(D2) keeps it migration-free.

The trigger point is first playback (clarified), so the check belongs where a session starts —
`PlaybackController.startSession`, which Phase 8 already established as the single gate for
per-session preconditions. Reusing that seam keeps the rationale out of the per-verse path, exactly
as Phase 8's availability gate does. `MainActivity.requestPostNotificationsIfNeeded()` and its
launcher call at `MainActivity.kt:41` are deleted.

---

## D12 — `MatnTheme`'s new parameters must be defaulted, or 88 previews break

**Decision**: `MatnTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, reduceMotion: Boolean = false,
content: …)`. Every new parameter is defaulted.

**Rationale**: `MatnTheme` is called by `App()` and, transitively, by all 88 `@Preview`s across 56
presentation files. A required parameter would be a mechanical 88-site edit with no benefit. With
defaults, existing previews keep compiling and rendering light, and dark previews are added
deliberately where the contract calls for them (`contracts/accessibility-contract.md` enumerates
which). The theme mode reaches `App()` from a thin `AppViewModel` collecting
`ObserveThemeModeUseCase`.

**First-frame correctness**: a `Flow`'s first emission is asynchronous, which would render one frame
of the default before the stored value arrives — the flash FR-005 forbids. The repository therefore
also exposes a synchronous `themeModeNow()` reading `selectSetting(...).executeAsOneOrNull()`
directly (SQLDelight queries are synchronous), used as the `StateFlow`'s initial value at
construction. The flow remains the source of live updates.

---

## D13 — Scope boundary: the 74 remaining raw `.dp` literals

**Decision**: Out of scope. Not fixed by this phase beyond files it already edits for another
reason.

**Rationale**: `presentation/` still contains 74 raw `.dp` literals — a standing Principle VIII
violation inherited from before Phase 10, which `docs/DESIGN-SOURCE.md` records as pre-existing
("91+ raw `.dp`/`.sp` literals … a standing Principle VIII violation independent of this
redesign"). Phase 9 touches most of these files, so the temptation to sweep them is real, but doing
so would balloon the diff and mix a token-hygiene cleanup into an accessibility phase, making both
harder to review. New code in this phase adds zero literals. Flagged for a dedicated cleanup.
