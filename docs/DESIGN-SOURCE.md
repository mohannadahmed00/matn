# Matn — Design Source of Truth

> ## ⚠️ Superseded for tokens — read this first (2026-08-05)
>
> The canonical design reference is now the **Matn Design System** in Claude Design:
> project `9902f252-a0d0-46c9-9e44-d7a7d7a8a0b2`, file `Matn Design System.dc.html`.
> Read it with the `DesignSync` tool (`get_file`), or open the checked-in copy at
> [`design/Matn Design System.dc.html`](./design/Matn%20Design%20System.dc.html). (`support.js`
> in the Claude Design project is the generated render runtime, not design content — ignore it.)
> The live project is authoritative if the two ever drift.
>
> It covers what Stitch never did: a **dark scheme**, stated contrast ratios, a **bilingual**
> type scale, semantic download/validation colours, a motion vocabulary, a logo, and a
> navigation consolidation for both clients.
>
> **The token tables further down this file are historical.** The live values are the Kotlin
> files themselves — `presentation/theme/{Color,Type,Shape,Spacing,Motion,FontScale}.kt` —
> which now carry the design-system values and a note on every place they diverge from it.
>
> ### Where the implementation deliberately diverges from the design
>
> 1. **Typography stays on Amiri for every M3 role.** The design assigns Source Serif 4 to
>    `bodyLarge`/`headlineSmall`/`titleLarge`, which is right for a stylesheet and wrong for
>    Compose: M3 roles are shared by both scripts, Compose does not fall back across
>    `FontFamily` boundaries, and Source Serif 4 has no Arabic glyphs — so that assignment
>    renders Arabic as tofu. Latin styles live in `MatnLatinType` as an explicit opt-in
>    instead. The failure mode is inverted on purpose: a missed Latin call site is merely
>    less pretty; a missed Arabic one would be unreadable.
> 2. **The Arabic scale ships 4 of its 5 stops.** Stops 1–4 map onto the persisted
>    `ReadingFontSize`. Stop 5 ("Accessible", 38sp/87sp) needs a new enum constant and a
>    settings-slider step, so it is not yet wired.
> 3. **Reduced motion now crossfades rather than cutting to 0ms** where a crossfade is
>    meaningful — see `MotionTransition`. This matches `adaptive-motion-contract.md` §B2.1's
>    "Fade only" wording, which the previous implementation did not.
>
> ### Navigation consolidation — landed 2026-08-08 (student app only)
>
> The student app is down from **7 routes / 4 tabs to 4 routes / 3 tabs**, one better than the
> design's own "5 or fewer" target because Details and Reader are still a single screen here (the
> design splits them; see the deviation below). The tabs are **Library · Saved · Settings**.
>
> - **Search** is an inline field on Library. A non-blank query swaps the grid for results in
>   place; the field is deliberately *not* autofocused, unlike the pushed screen it replaced —
>   arriving at that screen was an explicit act, whereas opening Library is not, and stealing focus
>   would raise the keyboard over the grid the student came to browse.
> - **Goals** is a bottom sheet opened by tapping Library's daily-goal ring. The sheet keeps the
>   per-matn progress list the old tab owned (FR-016) even though the design's sheet does not show
>   it — retiring the route must not retire a capability.
> - **Onboarding** is a dismissible overlay pager above the graph, re-opened from Settings.
> - **Saved** absorbs Bookmarks, Notes and Memorized. The design's three segments (All · Notes ·
>   Memorized) are implemented literally: bookmarks appear under *All* with a kind tag rather than
>   getting a segment of their own. Swipe-toward-end removes a row with a 4s Undo snackbar; the
>   removal is performed for real before Undo is offered, so the list is never showing a phantom row.
>   New data: `selectAllMemorizedWithContext` / `ProgressRepository.observeMemorized()`, shaped like
>   the existing bookmark and note context queries.
>
> Deviations recorded against the design's navigation section:
>
> 1. **Details and Reader remain one route.** The design lists them as two (`matn/{id}` and
>    `matn/{id}/read?v={n}`). They are one screen in this codebase and splitting them is a
>    reading-experience change, not a navigation one — it is tracked separately rather than smuggled
>    into the consolidation.
> 2. **The goal sheet has no reminder toggle.** The design draws "Remind me at 6:00 pm"; there is no
>    reminder scheduler in the app, so the control would be dead. Settings still owns the
>    notification permission.
>
> Still **not** implemented from the design: the Studio consolidation (4 destinations → 3), the
> component library, the remaining screen designs, and the logo.

The **Stitch** project below is the historical reference for screen layout. Any model or
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

~~**Current-code gap**~~ **Closed by Phase 10** — see the *Phase 10 — Design System Adoption*
section at the end of this file. The token set above is now backed by
`shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/{Color,Type,Shape,Spacing}.kt`.
New clients — including the `:teacherApp` of Phases 11–13 — consume those files rather than
re-deriving tokens from this table.

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
| Upload Matn (Per-Verse) | `4f1bee3d7518487b986c7c63cb3c07ff` | **11–12** — teacher client (`:teacherApp`); its verse list / text / reorder regions are **11** (implemented 2026-07-28), its per-row audio column is **12** |
| Upload Matn (Timestamp Map) | `f55899af78974175b345f3c3cd048387` | **12** — teacher client; appearance only, adopted as an authoring-time splitter. See *Open issues* #2 before implementing |

> The two Upload screens are the only entries here that belong to the **teacher** client rather
> than the student app. Their local exports are `stitch-designs/11-Upload-Per-Verse.*` and
> `stitch-designs/12-Upload-Timestamp-Map.*`.

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

2. **`Upload Matn (Timestamp Map)` (`f55899af78974175b345f3c3cd048387`) — resolved 2026-07-26,
   scoped to authoring-time use only.** This entry previously read "Do not implement it / retire it
   in Stitch." That still holds for the *architecture* the screen implies, but not for the screen
   itself. The constitution and `PRODUCT-SPEC.md` lock the runtime audio model to *one micro-audio
   file per verse*; a persisted shared/continuous file with a timestamp map remains out of scope
   and MUST NOT be built.

   Roadmap **Phase 12** adopts the screen's *appearance* — waveform, click-to-add verse markers,
   per-verse start/end (ms) fields, overlap/gap validation — as a **splitter**: the continuous file
   is an authoring *input*, and saving slices it into per-verse files. The marker list is transient
   and is never persisted, never exported, and never reaches the app. This is the
   Principle VIII distinction applied literally — designs are authoritative about appearance, not
   architecture. **Do not retire this screen in Stitch**; it is now live reference material.

   Reviewers: any implementation that stores start/end offsets against a shared audio asset, or
   that ships a matn whose verses point into one file, is the rejected model and is a blocking
   failure regardless of which screen it cites.

3. ~~**`Upload Matn (Per-Verse)` (`4f1bee3d7518487b986c7c63cb3c07ff`) has no home in the
   roadmap.**~~ **Resolved 2026-07-26** — content intake is still a production-side concern rather
   than a student-app feature, and it now has a home as such: `docs/ROADMAP.md` § *Content
   Delivery & Authoring* (Phases 11–13) builds a separate `:teacherApp` desktop client for it —
   and from Phase 13 it is the origin of *all* student-visible content. This screen is
   the canonical design for the per-verse upload path, split across both phases — see the registry
   table above.

4. **Phase 5 has no dedicated screen.** *Continue Learning* is a card on Home / Library rather
   than its own design. Phase 5 UI work should derive from the Home screen's card region.

5. ~~**No dark-mode variants exist.**~~ **Resolved 2026-07-25** — see issue 10 below. `MatnDarkColors`
   was derived per research D3's tone-mapping rule and is gated by `ColorContrastTest` (48
   assertions, all passing), not imported — confirmed via the fetched *Splash Screen* HTML, which
   declares `darkMode: "class"` but contains zero `dark:` variant classes.

6. ~~**The bottom navigation shell (Library / Goals / Notes / Settings) is undocumented outside
   Stitch.**~~ **Resolved 2026-07-24**: documented in `docs/PRODUCT-SPEC.md` § Navigation & App
   Shell and implemented in `specs/010-design-system-adoption` (User Story 3) — a persistent
   `NavigationBar` on every top-level screen. The Notes tab got its real screen in Phase 6; the
   Goals tab got its real screen in Phase 7 (see issue 8 below); **the Settings tab got its real
   screen in Phase 8 (see issue 9 below)** — no tab routes to the shared "coming soon" placeholder
   any more, and `ComingSoonScreen.kt` has been deleted as dead code.

   **Superseded 2026-08-08** by the design system's navigation consolidation: the bar is now
   Library · Saved · Settings, Goals is a sheet, and Notes was absorbed into Saved. See the banner
   at the top of this file.

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
     not part of the student search screen; ignored during implementation. **Reclassified
     2026-07-26:** with Phases 11–13 adding a real teacher client, that chrome is no longer noise —
     it is the closest thing the design set has to a `:teacherApp` navigation shell, and Phase 11
     should use it as reference rather than inventing one. **Closed 2026-07-28** — Phase 11 built
     `PortalShell` from exactly this chrome; see `specs/011-teacher-authoring-upload/design-notes.md`
     for the full write-up and its recorded deviations (no Category field, mirrored Arabic/English
     chrome as original work, no Material Icons dependency).

8. **Phase 7 (Progress & Daily Goals) — implemented 2026-07-24.** *Progress & Goals*
   (`f059cccd…`) and the daily-goal ring region of *Home / Library* (`618643f8…`) were fetched —
   see `specs/007-progress-daily-goals/design-notes.md` for the full write-up. Deviations from the
   fetched design:
   - **No per-card progress affordance existed** on the fetched Home/Library grid card (title,
     author, verse count, sync-status icon only). FR-006 requires one, so `MatnCard` gained a
     compact `MatnProgressBar`-style affordance not present in the captured design.
   - **No zero/empty state was captured** for the Goals dashboard (both fetches showed populated
     data). The Goals tab's empty state (SC-007) is an original composition built from the same
     ring/list tokens, not a captured design.
   - **Streak/day-of-week tracker row** (THU/WED/TUE/MON) appears below the daily-target stepper
     in the fetched Home screen but is out of scope for this phase's functional requirements —
     not implemented.
   - The ring's interior renders the practiced/goal figure (e.g. "4/10") rather than a bare
     percentage, per an explicit implementation-task instruction; the fetched design's caption
     line ("X out of Y verses") was folded into the same treatment rather than duplicated.

9. **Phase 8 (Storage & Downloads) — implemented 2026-07-25.** *Home / Library* (`618643f8…`) and
   *Matn Details (Refined)* (`472161bb…`) were re-fetched to place the content-delivery affordance —
   see `specs/008-storage-downloads/design-notes.md` for the full write-up. Deviations from the
   fetched design:
   - **No Settings screen exists in the Stitch set at all.** The entire Settings screen (storage
     header pair, size-ordered breakdown, "remove all downloaded content", every removal
     confirmation state) is an original composition built from the Phase 10 token set — there was
     no capture to retrofit, unlike Goals/Notes which had fetched screens to start from.
   - **No install/progress/remove states were captured** for the Home card or the details header.
     `ContentAvailabilityBadge`, `InstallProgressIndicator`, `ContentActionButton`,
     `ConfirmRemovalDialog`, and `InstallPromptSheet` are all original compositions from the same
     token set.
   - The Home/Library card's fetched `cloud_done`/`download` status-icon slot **was** reused (not
     invented) for the availability badge, and the Matn Details header's fetched `Play` button slot
     **was** reused for the install/cancel/remove action — both per the existing captured regions.

10. **Phase 9 (Polish & Accessibility) — implemented 2026-07-25.** *Splash Screen*
    (`6aba0b42e95d43e5b6f81928f3e3f7c6`) was fetched during planning — see
    `specs/009-polish-accessibility/design-notes.md` for the full write-up. Deviations from the
    fetched design:
    - **The fetched 3-second fake "Preparing your workspace…" progress bar was not implemented.**
      Matn has nothing to prepare; the visual treatment (brand mark, tagline, background colour)
      was adopted for onboarding panel 1, the fabricated delay was not (Principle VIII: designs are
      authoritative about appearance, not behaviour).
    - **The dark palette is derived, not fetched** (issue 5, now resolved) — the tone-mapping rule
      in research D3, gated by `ColorContrastTest`.
    - **Onboarding panels 2–3 and the permission rationale sheet have no Stitch capture** — both
      are original compositions from the Phase 10 token set, following the Settings-screen
      precedent Phase 8 set (issue 9 above) for phases with no design to retrofit.
    - **iOS's launch surface uses `UILaunchScreen` generation, not a hand-authored storyboard** —
      the project already builds its launch screen via
      `INFOPLIST_KEY_UILaunchScreen_Generation`; a `LaunchBackground` colour asset plus an explicit
      `UILaunchScreen` `Info.plist` entry reaches the same static, colour-only, no-animation result
      through the project's existing mechanism instead of introducing a second one.
    - One string beyond the phase's originally fixed list was required and added:
      `permission_rationale_not_now` (both locales) — the rationale sheet's dismiss action had no
      existing or listed string to use.

## Phase 10 — Design System Adoption

Phases 1–5's screens (Home/Library, Matn Details, Reading & Playback, Repetition Setup) have been
retrofitted to this registry's canonical designs — see `specs/010-design-system-adoption/`. The
"Design tokens" section above is now backed by real Compose token files
(`presentation/theme/{Color,Type,Shape,Spacing}.kt`), and the reading experience has moved from a
scrollable verse list to the focused 3-verse carousel described by the "Reading & Playback
(Updated)" screen. Repetition Setup's bottom sheet and the bottom navigation shell are both
implemented; dark mode (issue 5) was delivered by Phase 9 (issue 10 above).
