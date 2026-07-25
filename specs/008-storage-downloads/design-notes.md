# Design Notes: Storage & Downloads (fetched via Stitch MCP, 2026-07-25)

## T008 — Home / Library (`618643f891144557b5a4ddf4bbad0c03`) + Matn Details (Refined)
(`472161bbcdee47adb26d94a1fefcc3a8`)

Both screens were fetched via the `stitch` MCP server's `get_screen` tool. The retrieved HTML
(summarized below) confirms the structural facts implementation should rely on. The retrieved
designs are also cached locally under `stitch-designs/` (`01-Home-Library.html`,
`10-Matn-Details-Refined.html`) for cross-reference.

### Home / Library card — status-icon slot

- The fetched Home/Library grid card displays: cover image, title (Arabic, right-aligned),
  author line, a `"240 بيت"` verse-count line, and a **sync-status icon** (`cloud_done` /
  `download`) as the per-card availability affordance. No per-card progress bar was present in the
  original capture (Phase 7 design-notes gap #1 already records that the progress affordance is an
  addition, not a fetched element).
- The status-icon slot is the natural home for `ContentAvailabilityBadge`: it sits at the trailing
  edge of the meta line (verse-count + duration), where `cloud_done` / `download` already lived in
  the fetched design. T046 puts `ContentAvailabilityBadge` in exactly that slot — reusing the
  fetched region rather than inventing a new one (Constitution VIII / contract §1).
- The current `MatnCard.kt` renders the meta line as a single `Text` (`"$verses_count · $duration"`)
  and an optional `MatnProgressBar`. There is **no literal `Icon` slot** in the current code, so
  T046 turns the meta line into a `Row`: meta-text on the leading edge, the availability badge on
  the trailing edge. This preserves the fetched card's geometry (status icon at the trailing edge of
  the meta line) without introducing a new region.

### Matn Details (Refined) — header action area

- The fetched refined details header carries: cover image, title, author, structure meta, and a
  single **`Play` button** in the header action area. No install/remove/delete action is present in
  the capture — the design predates this phase.
- T049 therefore layers `ContentActionButton` (install / cancel / remove) on the **header action
  row** that already hosts the `Play` button. When the matn is installed, the existing `Play`
  affordance remains; when not installed, the install affordance takes the same slot (and the play
  surface elsewhere gets the install-prompt treatment per T051, so the details header and the
  playback surface stay consistent).

### Settings — no captured design (original composition)

- The Stitch set registered in `docs/DESIGN-SOURCE.md` has **no Settings screen** and **no
  captured install/remove/progress states**. The Settings screen, the availability affordance
  variants beyond the card's status slot, the progress indicator's transferring/waiting states, the
  removal confirmation dialog, and the install prompt sheet are all **original compositions built
  from the Phase 10 token set** (`presentation/theme/{Color,Type,Shape,Spacing}.kt`). This mirrors
  the Phase 6 (Notes) and Phase 7 (Goals zero state) fallback precedent: when no Stitch capture
  exists for a state, compose it from the same tokens and record the gap here.
- T072/T073 will record the same gap in `docs/DESIGN-SOURCE.md` (Phase 8 note + open issue 6 update).

### Design-fidelity gaps recorded (for T072 / T074 / `docs/DESIGN-SOURCE.md`)

1. **No Settings screen** in the Stitch set — composed from Phase 10 tokens (storage section header
   pair, breakdown list, "remove all downloaded content" action; loading / zero / populated /
   confirmation / released-pending-reclaim states each get a `@Preview`).
2. **No install / progress / remove states** in the Stitch set — every state of
   `ContentAvailabilityBadge`, `InstallProgressIndicator`, `ContentActionButton`,
   `ConfirmRemovalDialog`, and `InstallPromptSheet` is an original composition from the same tokens.
3. The Home/Library card's status-icon slot is reused (not invented) for the availability badge —
   it sits at the trailing edge of the existing meta line, matching the fetched `cloud_done` /
   `download` placement.
4. The Matn Details (Refined) header action row is reused (not invented) for `ContentActionButton` —
   it layers onto the row that already hosts the fetched `Play` button.
5. RTL: the fetched details header and Home meta line are Arabic-RTL; size figures,
   `formatBytes` output, and the progress direction must lay out natively RTL (FR-031) the same way
   the rest of the app already handles mixed Arabic/numeral text (existing verse-number treatment).

### What implementation MUST NOT do

- Do **not** invent a new status region on the Home card or a new action region on the details
  header — reuse the fetched slots as described above.
- Do **not** introduce raw hex colors, magic `.dp` / `.sp` literals in any new screen or component
  (Constitution VIII blocking review item; T074 audits this).
- Do **not** name a `packId`, `AssetPack`, `NSBundleResourceRequest`, or `asset-delivery` anywhere
  in `presentation/`, `domain/usecase/`, or `playback/` — only `ContentPackRepositoryImpl` and the
  two platform adapters may name a pack id or platform delivery type (FR-032 forward-compatibility;
  T074 greps for this).

## T072 — Post-implementation notes (2026-07-25)

Recorded after all three user stories landed, per the Phase 6/7 precedent of closing the design
gap log with what was actually built vs. what the fetched captures showed.

### T074 audit findings, fixed in place

- `ContentAvailabilityBadge.kt`'s `Installing` branch originally sized the progress indicator with
  a bare `96.dp`. Replaced with `MatnSpacing.unit * 12` (Constitution VIII — every layout
  dimension must derive from the token set, not a literal).
- `ContentGlyphs.kt`'s preview-only `Row` used a bare `8.dp` gap; replaced with `MatnSpacing.unit`
  for consistency, even though preview scaffolding isn't itself a shipped screen.
- FR-032 grep (`packId`, `AssetPack`, `NSBundleResourceRequest`, `asset-delivery`) across
  `presentation/`, `domain/usecase/`, and `playback/`: **zero hits** — confirmed clean.
- Constitution IV re-read of `ANDROID/delivery/*` and `IOS/delivery/*`: both adapters are pure
  translation (status-code mapping only); no free-space checks, connectivity policy, ordering, or
  formatting found in either.

### T051 deviation — no reachable play affordance in `ReadingCarousel.kt` / `PlayerBar.kt`

The task list named these two files for the "gate the play affordances" requirement, but neither
renders a play action reachable for a not-installed matn in the actual codebase:
`ReadingCarousel.kt` has no play control at all (it only renders once a session is already
active — bookmark/note/memorize toggles only); `PlayerBar` is `visible` only while
`PlaybackController.state.hasSession` is true, and `PlayerBarViewModel.onPlayPause` only
resumes/pauses an *existing* session — it never starts one, so it can never reach a matn that
hasn't already passed the T041 gate. The actual reachable play affordances — the details header's
global Play button (`Frontispiece`) and each row's per-verse play button (`VerseRowItem`) in
`MatnDetailsScreen.kt` — are the ones gated: the header swaps to `ContentActionButton` when not
installed (never a dead Play button), and a per-verse tap on not-installed content opens
`InstallPromptSheet` instead of silently failing (SC-007).

### T058/T057 simplification — confirmation-dialog copy defaults to the immediate-reclaim wording

`ConfirmRemovalDialog` supports both copy variants per its component contract row, but the actual
`RemovalOutcome` variant a removal will produce is platform-determined (Android's engine always
returns `Reclaimed`; iOS's always returns `ReleasedPendingSystemReclaim`) and is only knowable
*after* a removal completes — no signal in the frozen `ContentDeliveryEngine` /
`ContentPackRepository` contracts exposes it ahead of time. `MatnDetailsScreen`'s and
`SettingsScreen`'s confirmation dialogs currently always pass
`isReleasedPendingSystemReclaim = false` (the immediate-reclaim wording). The **honest, outcome-
specific** copy still renders correctly after the fact, from `lastRemovalOutcome` /
`SettingsUiState.lastOutcome`, which carry the real `RemovalOutcome` the repository/engine
returned. Flagged for the PR description (T079) — the fix, if wanted, needs a small addition to
`ContentDeliveryEngine` (e.g. a "removal is immediate" capability flag) that the current contract
text doesn't include.

### RTL (FR-031)

No new left/right-anchored APIs were introduced anywhere in this phase — every new composable uses
`Row`/`Column` with `Arrangement`/`horizontal/vertical` padding and `Alignment.CenterStart` /
`CenterEnd`, all of which mirror automatically under `MatnTheme`'s forced RTL layout direction.
`formatBytes` keeps digits LTR-ordered inside the RTL flow, mirroring `formatDuration`'s existing
convention. Verified by source audit (grep for `.Left`/`.Right`/hardcoded `left =`/`right =`);
no live on-device RTL pass was possible in this environment (see T077/T078 — no Android emulator
or Xcode/macOS available here).