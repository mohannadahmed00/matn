# Contract: Navigation Shell

**Feature**: Phase 10 — Design System Adoption | **Date**: 2026-07-24

## 1. Routes (additive to the existing `Routes` object)

| Route | Constant | Screen |
|---|---|---|
| `"home"` | `Routes.HOME` | `HomeScreen` (unchanged) |
| `"matn/{matnId}"` | `Routes.MATN_DETAILS` | `MatnDetailsScreen` (unchanged) |
| `"goals"` | `Routes.GOALS` **(new)** | `ComingSoonScreen(tab = GOALS)` |
| `"notes"` | `Routes.NOTES` **(new)** | `ComingSoonScreen(tab = NOTES)` |
| `"settings"` | `Routes.SETTINGS` **(new)** | `ComingSoonScreen(tab = SETTINGS)` |

## 2. Bottom nav visibility

The `NavigationBar` MUST be visible on `Routes.HOME`, `Routes.GOALS`, `Routes.NOTES`,
`Routes.SETTINGS` (the four top-level destinations) and MUST NOT be visible on
`Routes.MATN_DETAILS` or the reading/playback surface — matching the design, where the nav shell
wraps the library/dashboard level, not the focused reading experience.

## 3. State-preservation guarantee (FR-008, User Story 3 Acceptance Scenario 3)

Switching bottom-nav tabs MUST use `navigate(route) { launchSingleTop = true; restoreState = true }`
against a `popUpTo` of the graph's start destination with `saveState = true` — the standard
Compose Navigation bottom-nav pattern — so that:

- Audio playback in progress on `Routes.HOME`/`Routes.MATN_DETAILS` is untouched by switching to
  another tab and back (playback lives in the ViewModel/audio-engine layer, not the nav
  back-stack, so this is mostly automatic — the guarantee here is that navigation doesn't
  needlessly recreate the Home/Details ViewModel and lose its `StateFlow` state).
- specs/005's continue-learning cached state is unaffected — this phase adds no new persistence
  trigger points and removes none.

## 4. `ComingSoonScreen` contract

One shared composable, parameterized by which tab it was reached from (copy/icon only):

```kotlin
@Composable
fun ComingSoonScreen(tab: NavigationTab, modifier: Modifier = Modifier)
```

Stateless, no ViewModel — it renders a fixed message per tab and offers a way back to Library
(FR-007). Carries a `@Preview` for each of the three stubbed tabs.
