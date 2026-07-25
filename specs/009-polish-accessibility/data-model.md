# Data Model: Polish & Accessibility

**Feature**: `specs/009-polish-accessibility` | **Date**: 2026-07-25

This phase adds **no table, no migration, and no schema-version bump** (research D2). It introduces
three scalar preferences persisted in the existing `app_setting` key/value table, plus five
render-layer value types that never reach storage. The database stays at schema v5 as Phase 8 left
it.

---

## 1. Persisted state

All three keys live in `app_setting` (`Content.sq:56`) and are read/written through the existing
`selectSetting` / `upsertSetting` queries. Every read falls back to a default on an absent or
unrecognized value and **never throws**, following `ReadingPreferencesRepositoryImpl`'s established
pattern.

| Key | Values | Default when absent/invalid | Written when |
|---|---|---|---|
| `theme_mode` | `SYSTEM` \| `LIGHT` \| `DARK` | `SYSTEM` | The student changes the appearance setting |
| `onboarding_completed` | `"true"` \| `"false"` | `"false"` | Onboarding is completed **or** skipped |
| `notification_permission_asked` | `"true"` \| `"false"` | `"false"` | The rationale has been shown and the system prompt dispatched, whatever the outcome |

### 1.1 Why no step counter for onboarding

Interrupted onboarding restarts rather than resumes (research D10), so `onboarding_completed` is the
complete persisted state. FR-025 is satisfied because a restart is by definition not stuck.

### 1.2 Mirrored platform value (not in the database)

The **effective appearance** (`light` / `dark`, already resolved from mode + system) is additionally
mirrored to a synchronously-readable platform store — `SharedPreferences` on Android,
`NSUserDefaults` on iOS — on every change. This is not a second source of truth: the database
remains authoritative, and the mirror exists solely so the platform launch window can be themed
before any Kotlin runs (research D9). A stale or missing mirror degrades to the system appearance,
never to an error.

---

## 2. Domain types

### 2.1 `ThemeMode`

```
enum class ThemeMode { SYSTEM, LIGHT, DARK }
```

- `DEFAULT = SYSTEM`.
- `fromStorageOrDefault(value: String?): ThemeMode` — mirrors `ReadingFontSize`'s accessor exactly.
- **Resolution rule** (pure, unit-tested — the one piece of theming logic worth a test):
  `effectiveAppearance(mode, systemIsDark) = when (mode) { LIGHT -> Light; DARK -> Dark; SYSTEM -> if (systemIsDark) Dark else Light }`.
  FR-006 depends on this being the *only* place the decision is made.

### 2.2 `Appearance`

```
enum class Appearance { LIGHT, DARK }
```

The resolved outcome of §2.1. Distinct from `ThemeMode` on purpose: `SYSTEM` is a *preference*, never
an appearance, and conflating them is how "pinned theme still follows the device" bugs happen
(FR-006's failure mode).

### 2.3 `OnboardingStatus`

```
enum class OnboardingStatus { NOT_COMPLETED, COMPLETED }
```

Derived from the `onboarding_completed` key. Drives the navigation start destination (FR-017,
FR-019). Skipping and finishing produce the same value — FR-018 requires skipping to leave nothing
unset.

### 2.4 `PermissionStatus`

```
enum class PermissionStatus { NOT_DETERMINED, GRANTED, DENIED, PERMANENTLY_DENIED }
```

Reported by the platform seam (research D11). `PERMANENTLY_DENIED` is what FR-023 keys the "go to
device settings" affordance off, since the platform will no longer surface its own prompt.

**Relationship to `notification_permission_asked`**: the persisted flag and this status answer
different questions. The flag answers "have *we* asked?" (FR-022's no-re-prompt guarantee); the
status answers "what does the OS currently allow?" (FR-023's display). Neither is derivable from the
other, which is why both exist.

### 2.5 `WindowWidthClass`

```
enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }
```

| Class | Available width | Applies to |
|---|---|---|
| `COMPACT` | `< 600.dp` | Phones; narrow split-screen panes on any device |
| `MEDIUM` | `600.dp ..< 840.dp` | Large phones landscape, small tablets, half-screen tablet panes |
| `EXPANDED` | `>= 840.dp` | Tablets, unfolded foldables, wide desktop-class windows |

Derived from **available** width, never device type (FR-026, research D8). Recomputed on every
resize, so rotation and multi-window transitions are ordinary recompositions rather than special
cases.

---

## 3. Render-layer token additions

None of these are persisted; they are constants and composition-locals consumed by the render layer.

### 3.1 `MatnDarkColors`

A second `ColorScheme` beside `MatnLightColors` in `theme/Color.kt`, covering the **identical 34
roles** — not a subset. Values follow the tone-mapping rule in research D3 and are accepted by
`ColorContrastTest` (see `contracts/accessibility-contract.md`), which is the gate that makes them
correct. Adding a role to one scheme without the other is a compile error, since both are built from
the same role list.

### 3.2 `MatnMotion`

```
object MatnMotion {
    val durationShort / durationMedium / durationLong: Int   // ms
    val easingStandard / easingEmphasized / easingExit: Easing
    val screenEnter / screenExit / sheetEnter / sheetExit: <transition specs>
}
```

One vocabulary, defined once, consumed everywhere (FR-032). Screens never name a duration or easing
of their own.

### 3.3 Composition-locals

| Local | Type | Provided by | Default |
|---|---|---|---|
| `LocalAppearance` | `Appearance` | `MatnTheme` | Resolved from `ThemeMode.SYSTEM` |
| `LocalReduceMotion` | `Boolean` | `MatnTheme` | `false` |
| `LocalWindowWidthClass` | `WindowWidthClass` | App root, from `BoxWithConstraints` | `COMPACT` |

All three default to a safe value so the 88 existing `@Preview`s keep working untouched (research
D12) and any preview can override one to render a specific state.

### 3.4 Spacing, widened for width class

`MatnSpacing` gains width-aware accessors rather than new bare constants, so no screen branches on a
breakpoint itself:

- `horizontalMargin(widthClass)` → `marginMobile` (20dp) for `COMPACT`, `marginDesktop` (64dp) for
  `MEDIUM`/`EXPANDED`. Both values already exist in the token set.
- `readingMaxWidth` → the bounded measure for verse and text-heavy content (FR-028).
- `surfaceMaxWidth` → the bound for control bars, sheets, and dialogs (FR-029). Deliberately
  narrower than `readingMaxWidth`: a comfortable *reading* measure is too wide for a control bar,
  which pushes its controls uncomfortably far apart.
- `libraryColumns(widthClass)` → the grid's column count (FR-027; SC-011 requires `EXPANDED` to be at
  least double `COMPACT`).

---

## 4. What this phase deliberately does not model

- **No new entity carries a UUID**, because none is an entity — Principle VI's stable-identity rule
  applies to persisted domain objects, and these are device-local display preferences. They are
  intentionally *not* sync candidates: a student's phone and tablet should be free to differ in
  theme and text size.
- **No reminder, schedule, or notification-content model** — reminders are out of scope
  (clarified 2026-07-25).
- **No two-pane navigation state** — responsive refinement only (clarified 2026-07-25), so
  `WindowWidthClass` influences layout within a screen and never the navigation graph.
