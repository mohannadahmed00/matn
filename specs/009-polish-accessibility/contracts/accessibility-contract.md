# Contract: Accessibility

**Feature**: `specs/009-polish-accessibility` | Covers **US2** (FR-007 – FR-016)

---

## 1. Contrast gate — `ColorContrastTest`

Pure `commonTest`, no Compose runtime, no device (research D6).

### 1.1 Formulas (WCAG 2.1)

```
channel(c)  = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
luminance(color) = 0.2126*channel(r) + 0.7152*channel(g) + 0.0722*channel(b)
ratio(a, b) = (max(La, Lb) + 0.05) / (min(La, Lb) + 0.05)
```

Implemented once as a test helper. Colours come straight from the two `ColorScheme` objects.

### 1.2 Pairing table — asserted against **both** schemes

The requirement "100% of text meets 4.5:1" is only testable once the pairs are enumerated. This table
*is* the contract.

| Foreground | Background | Min ratio | Why |
|---|---|---|---|
| `onSurface` | `surface` | 4.5 | Body and verse text |
| `onSurface` | `surfaceContainerLowest` … `surfaceContainerHighest` | 4.5 | Cards, rows, sheets (5 pairs) |
| `onSurfaceVariant` | `surface` | 4.5 | Secondary text, inactive nav labels |
| `onSurfaceVariant` | `surfaceContainerLow` | 4.5 | Secondary text on cards |
| `onBackground` | `background` | 4.5 | Screen-level text |
| `onPrimary` | `primary` | 4.5 | Primary button labels |
| `onPrimaryContainer` | `primaryContainer` | 4.5 | Active-verse card, selected chips |
| `onSecondary` | `secondary` | 4.5 | Secondary actions |
| `onSecondaryContainer` | `secondaryContainer` | 4.5 | Highlight surfaces |
| `onTertiary` / `onTertiaryContainer` | matching container | 4.5 | Tertiary surfaces (2 pairs) |
| `onError` | `error` | 4.5 | Error text |
| `onErrorContainer` | `errorContainer` | 4.5 | Error surfaces |
| `inverseOnSurface` | `inverseSurface` | 4.5 | Snackbars |
| `primary` | `surface` | 4.5 | Text-only buttons, links, the splash wordmark |
| `error` | `surface` | 4.5 | Inline error text |
| `outline` | `surface` | 3.0 | Control boundaries — non-text (FR-008) |
| `primary` | `surfaceContainerLow` | 3.0 | Progress fills, goal ring, active nav icon |
| `primary` | `outlineVariant` | 3.0 | **Progress fill vs. track** — the pair that actually communicates progress |
| `outline` | `surfaceContainerHigh` | 3.0 | Borders on raised surfaces (e.g. the speed pill) |

24 pairs × 2 schemes = 48 assertions, **all verified passing** against the existing light scheme and
the derived dark palette before this contract was written (values in `tasks.md` T00-series). A
failure names the pair, the scheme, and the measured ratio.

### 1.2a `outlineVariant` is decorative and is deliberately NOT gated

`outlineVariant` against `surface` measures **1.62 : 1** in the *existing shipped light scheme* and
1.98 : 1 in the derived dark one. This is not a defect to fix — Material 3 defines `outlineVariant`
as a decorative role, and WCAG 1.4.11 requires 3:1 only for visual information *needed to identify*
a component or its state. A list divider carries no such information: delete it and nothing becomes
unusable. Gating it would force the app to darken every hairline divider into a heavy rule, making
the UI worse in the name of a rule that does not apply.

What **is** binding, and is enforced instead:

> **Rule**: any element whose boundary is the only thing identifying an interactive control MUST use
> `outline`, never `outlineVariant`.

Audited across all 14 `outlineVariant` call sites. Thirteen are genuinely decorative (dividers in
`MatnDetailsScreen`, `TableOfContents`, `NotesTabScreen`, `SearchScreen`; the `CoverImage` frame; the
alpha-reduced card borders in `ReadingCarousel` and `PlayerBar`; the carousel's 32dp rules). Two need
attention:

- `PlayerBar.kt:126` — `LinearProgressIndicator(color = primary, trackColor = outlineVariant)`. The
  meaningful pair is fill-vs-track, measured at 4.72 (light) / 5.49 (dark). **Already compliant; no
  change.**
- `PlayerBar.kt:330` — `SpeedPill`'s 1dp border is the *only* affordance identifying a clickable
  control. **Must move to `outline`** (3.67 light / 4.53 dark). This is the one token fix the audit
  produced.

### 1.3 Disabled and pressed states

Material derives these by alpha-compositing the role over its background. The test composites at the
alpha the theme actually uses and asserts the result, rather than testing the opaque role and
assuming (FR-007 covers "every state including … disabled").

## 2. Label catalogue — `A11yLabels` + `A11yLabelCatalogTest`

### 2.1 The catalogue

```kotlin
// presentation/common/A11yLabels.kt
enum class A11yAction { … }                       // 30 members — see the table below
val A11yLabels: Map<A11yAction, StringResource>   // total over A11yAction
```

Every icon-only control resolves its label from here. No inline literal, no `null`.

**The mapping is fixed and every member resolves to a resource that already exists**, except the four
marked *new* (added by tasks.md T021). This table is the contract — the catalogue must match it
exactly, because §2.2's totality test has no other definition to check against.

| `A11yAction` | `StringResource` | |
|---|---|---|
| `PLAY` | `player_play` | |
| `PAUSE` | `player_pause` | |
| `STOP` | `player_stop` | |
| `NEXT_VERSE` | `player_next` | |
| `PREVIOUS_VERSE` | `player_previous` | |
| `SPEED` | `player_speed` | |
| `REPETITION_SETUP` | `repetition_setup_open` | |
| `TOGGLE_BOOKMARK` | `toggle_bookmark` | |
| `TOGGLE_NOTE` | `toggle_note` | |
| `TOGGLE_MEMORIZED` | `toggle_memorized` | |
| `NOTE_SAVE` | `note_editor_save` | |
| `NOTE_DELETE` | `note_editor_delete` | |
| `MARK_CHAPTER_MEMORIZED` | `mark_chapter_memorized` | |
| `UNMARK_CHAPTER_MEMORIZED` | `unmark_chapter_memorized` | |
| `INSTALL` | `content_action_install` | |
| `INSTALL_CANCEL` | `content_action_cancel` | |
| `REMOVE` | `content_action_remove` | |
| `REMOVE_ALL` | `settings_remove_all` | |
| `SEARCH` | `search_open` | |
| `SEARCH_CLEAR` | `search_clear` | |
| `BACK` | `back` | |
| `TAB_LIBRARY` | `nav_library` | |
| `TAB_GOALS` | `nav_goals` | |
| `TAB_NOTES` | `nav_notes` | |
| `TAB_SETTINGS` | `nav_settings` | |
| `FONT_SIZE` | `font_size` | |
| `CLOSE` | `a11y_close` | *new* |
| `RETRY` | `a11y_retry` | *new* |
| `SKIP` | `onboarding_skip` | *new* |
| `CONTINUE` | `onboarding_continue` | *new* |

### 2.1a Toggles are one action, not an add/remove pair

Bookmark, note, and memorized are **single toggle controls** in this codebase — `toggle_bookmark`,
`toggle_note`, and `toggle_memorized` already exist and are already wired at their call sites. The
catalogue keeps that shape rather than splitting each into an ADD and a REMOVE member.

This is also the more correct screen-reader model: the **label names the control** and does not
change, while the **`stateDescription` reports its condition** and does change. A control whose name
flips between "Add bookmark" and "Remove bookmark" makes a screen-reader user re-learn the control
every time they toggle it. The add/remove distinction is carried entirely by the `a11y_state_*`
strings applied in §4, which is what §4 already specifies.

### 2.2 Test

| Assertion | Guards |
|---|---|
| Catalogue is **total** — one entry per `A11yAction`, matching §2.1's table exactly | FR-009: no unlabeled action can be added silently |
| No duplicate `StringResource` across two distinct actions | Two controls announcing identically |
| Every referenced string exists and is non-blank in **both** `values/` (Arabic) and `values-en/` | FR-009's "localised" |
| Both locale files declare an identical set of `<string name=…>` | A label that silently falls back to Arabic in the English locale |

Adding an enum entry without a mapping fails the test; using a control without a label fails to
compile (§3).

## 3. `IconActionButton` — the structural guarantee

```kotlin
@Composable
fun IconActionButton(
    action: A11yAction,                 // required — resolves the label; no nullable escape hatch
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,   // for toggles: "bookmarked" / "not bookmarked"
    enabled: Boolean = true,
    glyph: @Composable (Color) -> Unit,
)
```

- Applies `Modifier.minimumInteractiveComponentSize()` (verified present in
  material3 1.11.0-alpha07, default 48.dp — research D5).
- Sets `contentDescription` from `A11yLabels[action]` and `stateDescription` when supplied.
- **Migration**: of the 11 `Modifier.clickable` sites in `presentation/`, the **3 icon-only** ones
  move onto this component — `ContinueLearningCard.kt:91` (dismiss), `MatnDetailsScreen.kt:620`
  (play), `PlayerBar.kt:296` (transport). The other 8 are content rows/cards whose accessible name
  comes from their own text and need only an `onClickLabel`. The 32 existing `IconButton` sites
  already meet the 48dp floor and need only their labels audited.

This is why touch-target and label compliance is enforced by construction rather than asserted at
runtime: an unlabeled icon action does not compile.

## 4. Screen-reader semantics

| Requirement | Implementation |
|---|---|
| FR-009 state announcement | `stateDescription` on every toggle: play/pause, bookmark, memorized, availability |
| FR-010 decorative imagery skipped | Cover art and the grain overlay set `contentDescription = null`; existing `CoverImage` keeps its meaningful `cover_placeholder_desc` |
| FR-012 RTL focus order | Traversal follows the forced RTL layout; audited per screen, no manual `traversalIndex` unless a screen proves to need one |
| FR-011 primary journeys reachable | Manual device walkthrough — the one item automation cannot cover (research D5/D6) |

**Playback announcement discipline**: progress ticks must not be announced. Only discrete state
changes (verse changed, playback started/stopped/paused) carry semantics; the scrub position does
not. Otherwise a screen reader talks over the recitation — the edge case the spec names.

## 5. Font scaling

- Interface text uses `sp` and inherits `LocalDensity.fontScale` — automatic on Android, and
  confirmed automatic on iOS via the `UIContentSizeCategory → fontScale` map (research D4).
- **The app never clamps the scale** (FR-014).
- The app's own `ReadingFontSize` (`theme/FontScale.kt`) is unchanged and multiplies with the system
  scale. Combined worst case at 320dp: `XLARGE` (30sp) × 2.0 = 60sp of Arabic verse text — the
  binding constraint for the reading carousel.
- **No degraded mode above 200%**: research D4 established that neither platform can produce a
  Compose `fontScale` above 2.0 — iOS caps at 1.8 even at AX5 — so the hard no-clipping guarantee
  holds across the whole reachable range. FR-014 and SC-007 were tightened accordingly on 2026-07-25;
  there is no scrollable-but-clipping fallback to build.
- **Both ends of the range** (FR-015): the minimum combination — `ReadingFontSize.SMALL` at a 0.8
  scale, ~14.4sp — must also stay legible, not just the 60sp maximum.

## 6. Colour is never the only signal (FR-016)

| Surface | Colour today | Added non-colour signal |
|---|---|---|
| Availability badge | Tint | Already carries an icon + text — verify, don't add |
| Install progress | Fill | Percentage text |
| Memorized state | Tint | Distinct glyph, not just a colour change |
| Daily goal ring | Fill | Already renders "4/10" inside |
| Selected nav tab | Tint | Selected state exposed to the screen reader; icon weight differs |
| Error vs. normal text | `error` role | Icon + wording |

## 7. Previews required

Every screen-level content composable gains a **dark** preview and a **largest-scale** preview
(`fontScale = 2.0f`, 320dp width) alongside its existing states. That is 2 new previews per screen
across Home, Matn Details, Reading Carousel, Player Bar, Search, Notes, Goals, Settings, Onboarding —
plus one dark preview per shared component that carries colour.

## 8. Out of scope

Alternate high-contrast or colour-blind-specific themes (clarified). FR-016 is the mitigation.
