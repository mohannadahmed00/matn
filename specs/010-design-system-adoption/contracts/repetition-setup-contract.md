# Contract: Repetition Setup Sheet

**Feature**: Phase 10 — Design System Adoption | **Date**: 2026-07-24

Presentation-only contract. The behavioral contract for `RepetitionPlanner`/`PlaybackController`
itself is unchanged and already covered by `specs/004-repetition-engine/contracts/*` — this
document covers only the new UI surface's responsibility to construct the same inputs those
contracts already expect.

## 1. Composable surface

```kotlin
@Composable
fun RepetitionSetupSheet(
    state: RepetitionSetupUiState,
    onAbLoopToggled: (Boolean) -> Unit,
    onStartVerseChanged: (Int) -> Unit,
    onEndVerseChanged: (Int) -> Unit,
    onVerseRepeatChanged: (RepeatCount) -> Unit,
    onSegmentRepeatChanged: (RepeatCount) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
)
```

Stateless content composable per Principle II; a thin stateful holder in `player/` collects the
owning ViewModel's `StateFlow<RepetitionSetupUiState>` and forwards these intents — it does not
call `RepetitionPlanner` or any use case directly.

## 2. `onStart` guarantee (spec FR-004, FR-005)

Calling `onStart` MUST result in the ViewModel invoking the **existing**
`PlaybackController.setVerseRepeat` / `setMatnRepeat` / `setLoopStart` / `setLoopEnd` intents
(contract: `specs/004-repetition-engine/contracts/repetition-contract.md` § 1) with values derived
one-to-one from `RepetitionSetupUiState` — no new domain call is introduced, no existing one is
bypassed. `onStart` is the only intent in this composable that mutates playback; every other
callback only updates the sheet's own local/ViewModel-held draft state.

## 3. Dismiss-without-start guarantee (Edge Case)

Calling `onDismiss` before `onStart` MUST NOT call any `PlaybackController` intent — the draft
state in `RepetitionSetupUiState` is discarded, and whatever repetition settings were active
before the sheet opened remain active (spec Edge Cases: "does any partial configuration leak into
the next session?" → no).

## 4. `canStart` guarantee

`onStart` MUST be wired to a disabled/no-op state whenever `RepetitionSetupUiState.canStart` is
`false` (e.g. A-B enabled but `endVerse` not yet chosen) — the sheet cannot invoke playback with
an invalid configuration.
