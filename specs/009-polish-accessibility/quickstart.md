# Quickstart: Validating Polish & Accessibility

**Feature**: `specs/009-polish-accessibility` | **Date**: 2026-07-25

How to prove this phase works. Most of it runs with no device; the parts that cannot are listed
explicitly rather than glossed over.

---

## Prerequisites

- The repo builds on `main`'s toolchain — Kotlin 2.4.10, AGP 9.0.1, JDK 17+.
- Android: a device or emulator on API 33+ to exercise the runtime notification permission (below
  API 33 there is no prompt and the seam returns `GRANTED`).
- iOS: Xcode with a simulator, for the Dynamic Type and VoiceOver passes.
- Branch: `feature/009-polish-accessibility`, based on `de89128` (Phase 8, PR #17). The working tree
  should be clean apart from this phase's own changes before you start.

### Reference device (SC-013)

SC-013 is judged on a **mid-range Android device from the last three years — 6–8 GB RAM, a mid-tier
SoC, running a release build**. A Pixel 6a, Galaxy A54, or equivalent. Not a flagship, and not an
emulator: emulators mask jank on the fast side and invent it on the slow side, so neither direction
is informative.

"Without visible stutter" means judged by eye at arm's length, in a release build with debugging
detached. This is deliberately a qualitative check — a frame-timing harness would be a
disproportionate build for a phase that adds no rendering-heavy surface. If a transition looks wrong,
profile it then; do not profile every transition on principle.

---

## 1. Automated — the part that gates CI

```bash
./gradlew :shared:allTests
```

New suites and what a failure means:

| Suite | Failure means |
|---|---|
| `ColorContrastTest` | A colour role pair misses its WCAG ratio. The message names the pair, the scheme, and the measured ratio. **This is the gate on the derived dark palette** — fix the token, not the test. |
| `A11yLabelCatalogTest` | An `A11yAction` has no label, a blank one, a duplicate, or is missing from `values-en/`. |
| `ThemeModeTest` | Mode → appearance resolution is wrong, or the storage fallback throws. |
| `AppearancePreferencesRepositoryTest` | Theme choice does not round-trip, or the platform mirror was not written. |
| `WindowWidthClassTest` / `MatnSpacingTest` | A breakpoint is off by one dp, or `EXPANDED` columns are under 2× `COMPACT` (SC-011). |
| `OnboardingRepositoryTest` / `OnboardingViewModelTest` | Onboarding does not persist, or skip leaves state unset. |
| `NotificationPermissionGateTest` | The permission is asked more than once, or a denied permission blocks playback. |
| `ReducedMotionSpecTest` | A transition has no reduced form. |
| `PlaybackControllerTest` | **Animation entered the playback path** — see FR-035. Treat as a release blocker. |

Migration tests are unchanged: this phase adds no schema change, so `MigrationV4Test` and its
predecessors should pass untouched. If a migration test fails, something outside this phase's scope
has been altered.

---

## 2. Preview-driven — no device

Open the previews in Android Studio. Per `contracts/accessibility-contract.md` §7 and
`adaptive-motion-contract.md` §A5, every screen-level content composable now has:

- its existing state previews (loaded / empty / error / …),
- a **dark** preview,
- a **largest-scale** preview (`fontScale = 2.0f`, 320dp width),
- an **`EXPANDED`-width** preview (840dp+).

What to look for:

| Check | Requirement |
|---|---|
| No light surface in any dark preview; no invisible icon or divider | SC-001 |
| Nothing clipped, overlapping, or pushed off-screen at 2.0× / 320dp | SC-007 |
| Verse text bounded and centred at 840dp, not stretched | FR-028 |
| Library grid shows ≥ 2× the compact column count at 840dp | SC-011 |
| Active verse still the highest-emphasis element in dark | US1 scenario 3 |

---

## 3. Manual device passes — what automation cannot cover

### 3.1 Dark mode, live (US1)

1. Launch with the device in **light** mode. Confirm no dark flash.
2. Kill the app, set the device to **dark**, relaunch. **No white flash at any point** (SC-003) —
   this is the check that catches a missed `AndroidManifest` launch theme.
3. With the app open and **audio playing**, toggle the device theme. Confirm: theme switches, audio
   does not stutter, the screen keeps its state, any in-flight install keeps running (FR-003).
4. Settings → pin **always light** on a dark device. Confirm the app stays light, and stays light
   after a restart (FR-006, SC-003).
5. Note the known residual: the pre-Activity system splash follows the *device*, so a pinned-opposite
   student sees a brief tone difference. Expected — research D9.

### 3.2 Screen reader (US2) — the pass automation cannot replace

Android TalkBack / iOS VoiceOver. Walk every primary journey (FR-011):

browse library → open a matn → play → pause → next/previous verse → change speed → open repetition
setup → bookmark a verse → add a note → mark memorized → search → set a daily goal → install a matn →
remove it.

| Check | Requirement |
|---|---|
| Every control announces a meaningful name **and** its state | FR-009 |
| Focus order follows the visual **right-to-left** order on every screen | FR-012 |
| Cover art and decorative surfaces are skipped, not announced | FR-010 |
| Progress ticks are **not** announced during playback | Spec edge case |
| Nothing is unreachable by swipe navigation | FR-011 |

### 3.3 Font scale (US2)

- **Android**: Settings → Display → Font size → maximum (2.0×). Walk every screen at that scale on a
  320dp-wide device or emulator.
- **iOS**: Settings → Accessibility → Display & Text Size → Larger Text → enable accessibility sizes
  → **AX5**. Note that Compose receives this as `fontScale = 1.8`, not 3.1× (research D4) — so iOS
  is *less* demanding than Android here, and Android's 2.0× is the binding case.
- Set the scale **before** launching on iOS: live reaction to a mid-session Dynamic Type change is
  not guaranteed by Compose (research D4) and is not required by any FR.
- Also set the app's own reading size to **XLARGE** simultaneously — the combined worst case.

### 3.4 First launch and permissions (US3)

On a **freshly installed** app (uninstall first — clearing data also works):

1. Onboarding appears; panel 3 explains the download model (FR-017).
2. **No permission prompt anywhere in the flow** (FR-021) — this is the clarified behaviour and the
   most likely regression.
3. Skip from panel 1. Land in the library, everything working (FR-018).
4. Relaunch. Onboarding does not reappear (FR-019).
5. Settings → re-open onboarding. It shows, and completing it does not reset anything (FR-019).
6. Press play for the first time. The rationale appears **before** the system prompt (FR-020).
7. **Decline.** Confirm playback still works, the media notification is simply absent, and the prompt
   never returns on later plays (FR-022, SC-010).
8. Settings shows the permission as declined with a route to device settings (FR-023).
9. Repeat steps 1–2 with the device in **airplane mode** (FR-024).

### 3.5 Adaptive layout (US4)

On a tablet or a resizable emulator:

1. Portrait, then landscape: column counts and margins change; verse text stays bounded.
2. **Rotate while playing** — active verse, scroll position, and playback survive (FR-030).
3. Rotate with a bottom sheet open and with text typed into the note editor — both survive (FR-030).
4. Enter split-screen and narrow the pane below 600dp — the layout becomes compact (FR-026).

### 3.6 Motion (US5)

1. Walk every transition. Smooth, consistent, and sliding toward the **RTL** edge (FR-033).
2. **The gapless check (FR-035)**: play a matn straight through 10+ verses and listen. Any click,
   gap, or hesitation at a verse boundary is a release blocker — compare against a build with
   transitions disabled if in doubt.
3. Enable reduce motion — Android: Settings → Accessibility → Remove animations; iOS: Accessibility
   → Motion → Reduce Motion. Confirm animation stops, and that **every** progress value, the active
   verse, and the goal ring are still readable (FR-036).
4. Tap through a transition without waiting for it — input is accepted (FR-037).

---

## 4. Definition of done

- `./gradlew :shared:allTests` green, including every suite in §1.
- Every preview in §2 inspected; no clipping, no light-in-dark, no stretched column.
- All six manual passes in §3 completed on both platforms.
- `design-notes.md` written, recording: the derived dark palette (values + the tone rule that
  produced them), the rejection of the fetched splash's 3000 ms fake progress bar, and the
  pinned-vs-system launch residual.
- `docs/DESIGN-SOURCE.md` open issue #5 (dark-mode tokens) closed out, and a Phase 9 entry added
  alongside the existing Phase 6/7/8 entries.
