# Matn — Design Source of Truth

The canonical visual reference for Matn's UI is the **Stitch** project below. Any model or
developer implementing a screen MUST pull that screen's design from Stitch rather than inventing
a layout — see Principle VIII in [`../.specify/memory/constitution.md`](../.specify/memory/constitution.md).

This file is the **registry** (which screen backs which phase). It deliberately holds no design
content — the designs themselves live in Stitch and are fetched on demand via MCP.

> **2026-07-24 audit note:** the registry below was originally written against the roadmap's old
> 0-indexed phase numbers, before the `docs/ROADMAP.md` renumber to 1-indexed. All phase numbers
> here have been corrected (+1) to match the live roadmap. The "Design System" screen entry that
> used to head this registry has been removed — it never existed as a real screen in the Stitch
> project (its ID was a stub, never fetched). There is no separate design-system screen; every
> screen embeds an identical token set in its own inline `tailwind.config`, extracted below.

## Project

| Field | Value |
|-------|-------|
| Title | Matn Memorization Platform |
| Project ID | `5201142409061412050` |
| Access | Stitch MCP server (`stitch`), configured in `.mcp.json` and `opencode.json` |

## Access setup

The MCP server is declared in the repo and is secret-free. Each developer authenticates once,
locally, using **either** method:

```bash
# Option A — guided OAuth wizard (bundles its own gcloud; no prior install needed)
npx -y @_davideast/stitch-mcp init

# Option B — API key from stitch.withgoogle.com/settings → API Keys
# Set STITCH_API_KEY in your user/system environment (NEVER commit it)
```

Verify with `npx -y @_davideast/stitch-mcp doctor`, then restart the agent so it picks up the
server.

## Design tokens

Extracted verbatim from the `tailwind.config` embedded in every fetched screen (Home / Library,
Repetition Setup, Matn Details — all identical). This is the canonical token set until
`specs/010-design-system-adoption` lands the equivalent Compose `Color.kt` / `Type.kt` /
`Shape.kt` / `Spacing.kt`.

- **Color roles** (Material 3 role names): `primary #425546`, `on-primary #ffffff`,
  `primary-container #5a6d5d`, `on-primary-container #d9eeda`, `secondary #6b5c41`,
  `secondary-container #f2ddba`, `background #fcf9f8`, `surface #fcf9f8`,
  `surface-container-low #f6f3f2`, `surface-container #f0eded`, `surface-container-high #eae7e7`,
  `surface-container-lowest #ffffff`, `on-surface #1b1c1c`, `on-surface-variant #434843`,
  `outline #737872`, `outline-variant #c3c8c1`, `error #ba1a1a`. (Full role set — including all
  `-fixed`/`-dim`/`inverse-*` roles — is in every screen's `<script id="tailwind-config">` block.)
- **Fonts**: `Amiri` (`body-ar` 24px/2.2 line-height, `display-ar` 40px/700/1.6 line-height) for
  Arabic verse text; `Source Serif 4` (`headline-lg` 32px/600, `headline-lg-mobile` 24px/600,
  `body-md` 16px/400) for headings and Latin UI copy; `Plus Jakarta Sans` (`label-sm`
  12px/600/uppercase-tracking) for buttons, chips, nav labels; `Material Symbols Outlined` for
  all icons.
- **Spacing**: `margin-mobile 20px`, `margin-desktop 64px`, `gutter 24px`, `unit 8px`,
  `container-max 1024px`.
- **Radii**: `DEFAULT 0.25rem`, `lg 0.5rem`, `xl 0.75rem`, `full 9999px`.

**Current-code gap**: `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/` has no
`Shape.kt`/`Dimens.kt`/`Spacing.kt`, uses a different ad-hoc "manuscript" color palette, and only
bundles the Amiri font (no Source Serif 4 / Plus Jakarta Sans). 91+ raw `.dp`/`.sp` literals exist
directly in screen composables — a standing Principle VIII violation independent of this redesign.

## Screen registry

Screen IDs are stable; use them with the `list_screens` / `get_screen` MCP tools (`stitch` server).

### Foundational

| Screen | ID | Notes |
|--------|----|-------|
| Matn Brand Logo | `d3e5ae25b9734ceaa4fdba8c04ed54af` | Brand asset (icon + wordmark lockup). |

### Phase-mapped screens

| Screen | ID | Phase |
|--------|----|-------|
| Home / Library | `618643f891144557b5a4ddf4bbad0c03` | **2** (library grid); its *Continue Learning* card is **5**; its daily-goal progress ring is **7**; its bottom nav shell (Library/Goals/Notes/Settings) is **10** |
| Matn Details (Refined) | `472161bbcdee47adb26d94a1fefcc3a8` | **2** — canonical, see *Duplicates* below |
| Matn Details | `106e8e5d4cf748898f12a2e554ca07e5` | **2** — superseded iteration |
| Reading & Playback (Updated) | `ac354abd54c246ed841f63b31a19fbf2` | **3** — canonical, see *Duplicates* below |
| Reading & Playback | `490d65e3499f4a0ca9d1f6c269eef91a` | **3** — superseded iteration |
| Reading & Playback | `e27cc8b9a4624fbabae6368cb9a98bfe` | **3** — superseded; see *Duplicates* below (this ID and Repetition Setup resolve to the identical HTML artifact) |
| Repetition Setup | `c17c4877123f403292a30c78ae685f6e` | **4** |
| Search Matn | `fa8b63b036c94510ba1be2d90e7a3417` | **6** |
| Bookmarks & Notes | `bc99ab7f8110479086326271d0aa0310` | **6** |
| Progress & Goals | `f059cccd2f634bc9ba2cf4d620e5df80` | **7** |
| Splash Screen | `6aba0b42e95d43e5b6f81928f3e3f7c6` | **9** (first-launch / onboarding) |

## Open issues in the design set

1. ~~**Duplicates need a canonical pick.**~~ **Resolved 2026-07-24** by visual/HTML comparison:
   - *Matn Details*: `472161bb…` "(Refined)" is canonical. Diff vs. the superseded `106e8e5d…` is
     small — Refined drops a redundant header `play_arrow` button and a stray `mic` icon that
     don't correspond to any spec'd control.
   - *Reading & Playback*: `ac354abd…` "(Updated)" is canonical (3-verse carousel with a floating
     glass control bar). `490d65e3…` is an earlier iteration of the same layout. `e27cc8b9…`
     (390×884, no screenshot) is a stranger case: its `htmlCode.downloadUrl` resolves to the
     **exact same generated HTML file** as *Repetition Setup* (`c17c4877…`) — i.e. in Stitch's
     backing store these two differently-titled/differently-sized catalog entries are the same
     artifact. Treat `e27cc8b9…` as a duplicate pointer, not a distinct design.
   - All three superseded/duplicate entries should be retired in Stitch to stop future agents from
     picking them up as guidance.

2. **`Upload Matn (Timestamp Map)` (`f55899af78974175b345f3c3cd048387`) contradicts a locked
   architectural decision.** The constitution and `PRODUCT-SPEC.md` lock the audio model to *one
   micro-audio file per verse*, and explicitly place a shared/continuous-file-with-timestamps
   model out of scope. This screen designs the rejected model. **Do not implement it.** It should
   be retired in Stitch to stop it being picked up as guidance.

3. **`Upload Matn (Per-Verse)` (`4f1bee3d7518487b986c7c63cb3c07ff`) has no home in the roadmap.**
   Content intake is a production-side concern in `PRODUCT-SPEC.md`, not a v1 app phase. Either
   add a roadmap phase for it or treat it as out of scope for v1.

4. **Phase 5 has no dedicated screen.** *Continue Learning* is a card on Home / Library rather
   than its own design. Phase 5 UI work should derive from the Home screen's card region.

5. **No dark-mode variants exist.** Principle VII makes dark mode contractual and Phase 9 owns it,
   but the Stitch set is light-theme only. Dark tokens will need to be derived rather than
   imported.

6. ~~**The bottom navigation shell (Library / Goals / Notes / Settings) is undocumented outside
   Stitch.**~~ **Resolved 2026-07-24**: documented in `docs/PRODUCT-SPEC.md` § Navigation & App
   Shell and implemented in `specs/010-design-system-adoption` (User Story 3) — a persistent
   `NavigationBar` on every top-level screen, with the Goals and Notes tabs routed to a shared
   "coming soon" placeholder until Phases 6–7 land.

7. **Phase 6 (Search, Bookmarks & Notes) design questions — resolved 2026-07-24.** Three open
   questions in `specs/006-search-bookmarks-notes/contracts/ui-contract.md` were resolved by
   fetching *Search Matn* (`fa8b63b0…`), *Bookmarks & Notes* (`bc99ab7f…`), and re-inspecting
   *Reading & Playback (Updated)* (`ac354abd…`) — see `specs/006-search-bookmarks-notes/design-notes.md`
   for the full write-up:
   - **Search scope**: the fetched design queries the whole library, matching the spec's
     library-wide requirement (FR-001) — no conflict to override.
   - **Notes-tab segmentation**: the design uses stacked sections (not tabs/segmented control);
     implemented that way.
   - **Verse-card bookmark/note affordance**: the design shows a bookmark icon in the active
     verse card's action row but **no dedicated note-taking affordance** — this is a real
     affordance gap in the Stitch set. Per the ui-contract.md fallback rule, the note action was
     added to the same action row alongside the bookmark toggle, with a visually distinct
     hand-drawn glyph (pencil-on-page vs. ribbon) rather than inventing a new screen region.
   - Also noted: the fetched *Search Matn* HTML bleeds in teacher/producer-portal navigation
     chrome (dashboard, upload-matn, "Ustadh Ahmed" identity) from a shared Stitch project shell —
     not part of the student search screen; ignored during implementation.

## Phase 10 — Design System Adoption

Phases 1–5's screens (Home/Library, Matn Details, Reading & Playback, Repetition Setup) have been
retrofitted to this registry's canonical designs — see `specs/010-design-system-adoption/`. The
"Design tokens" section above is now backed by real Compose token files
(`presentation/theme/{Color,Type,Shape,Spacing}.kt`), and the reading experience has moved from a
scrollable verse list to the focused 3-verse carousel described by the "Reading & Playback
(Updated)" screen. Repetition Setup's bottom sheet and the bottom navigation shell are both
implemented; dark mode (issue 5) remains open and owned by Phase 9.
