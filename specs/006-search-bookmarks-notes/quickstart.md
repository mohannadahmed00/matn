# Quickstart: Validating Search, Bookmarks & Notes

**Feature**: Phase 6 — Search, Bookmarks & Notes | **Date**: 2026-07-24

How to prove this phase works. §A is automated and gates the PR. §B is visual/manual for what
`commonTest` cannot assert (RTL rendering, design fidelity, on-device navigation feel).

Contracts referenced here live in [contracts/](./contracts/); types in
[data-model.md](./data-model.md). Canonical designs: `docs/DESIGN-SOURCE.md` (*Search Matn*
`fa8b63b0…`, *Bookmarks & Notes* `bc99ab7f…`).

---

## Prerequisites

- Repo builds on `develop` (Phases 1–5 + 10 merged).
- Seeded content with: ≥2 متون; at least one **structured** matn (chapters with titles, for
  chapter-title search); verse text containing diacritics and hamza-carrier alef forms (e.g.
  `الإِسْلَام`) to exercise FR-002.
- For SC-001: the perf test seeds its own ≥1,000-verse corpus — no manual setup.
- No network required at any point (FR-004; offline-first).

---

## A. Automated — `commonTest`

```bash
./gradlew :shared:allTests
```

### A1. Normalization (normalization-contract.md)

`ArabicNormalizerTest` passes all 9 contract vectors — including the **negative** vector #6
(hamza forms not folded) and idempotence. SC-003 is asserted here: folded query ≡ exact-form
query over the same corpus.

### A2. Search behavior + performance (search-contract.md)

`SearchRepositoryTest`: all four corpus fields match; deterministic ordering (FR-006); blank
query → empty emission; chapter-without-verses fallback. **SC-001 guard**: 1,000+-verse seeded
corpus, query completes < 1 s (pattern: existing `data/PerformanceTest.kt`).
`SearchViewModelTest`: Idle / Searching / Results / NoResults transitions, debounce, stale-query
cancellation (FR-008/FR-009).

### A3. Annotations + migration (annotations-contract.md)

`BookmarkRepositoryTest` (toggle round-trips, rapid-toggle serialization, ordering),
`NoteRepositoryTest` (create/edit-in-place/delete, blank-save rejection per FR-019),
`MigrationV2Test` (v1 → v2 keeps existing content intact; SC-004's persistence claim rests on
rows in the same DB, verified across a reopened connection).

### A4. No-regression + literal guard

Existing suites (`PlaybackControllerTest`, `MatnDetailsViewModelTest`, `HomeViewModelTest`, …)
still pass with unchanged assertions. Token literal guard on files touched by this phase
(same greps as specs/010 quickstart §A3) — zero raw `Color(0x`/`.dp` literals in
`presentation/search`, `presentation/notes`, and modified files.

---

## B. Manual / on-device — required before merge

| # | Check | Expected |
|---|---|---|
| B1 | Home → tap top-bar search entry | Dedicated search screen opens (matches Stitch `fa8b63b0…`); idle prompt state, no bottom bar |
| B2 | Type a verse fragment **without diacritics** whose stored form is vocalized with أ/إ | Verse appears with matn title + verse number; tap → reading screen focused on that verse, playback NOT auto-started (SC-002: ≤3 interactions) |
| B3 | Type a chapter title fragment (فصل/باب) | Chapter result appears; tap → reading screen at chapter's first verse |
| B4 | Type a matn title fragment; type a verse number in Arabic-Indic digits (e.g. ٥) | Matn result → details screen; number result lists matching verses across متون (FR-003) |
| B5 | Type gibberish; then clear the field | "No matches" empty state; clearing returns to idle state, not "no results" (FR-008/FR-009) |
| B6 | Airplane mode on, repeat B2 | Identical results (FR-004) |
| B7 | In the reading screen, bookmark the active verse; un-bookmark; re-bookmark | Indicator appears/disappears/reappears within 1 s (SC-005); rapid tapping settles consistently |
| B8 | Add a note to a verse; reopen it; edit; save | Note indicator (distinct from bookmark's, FR-016) appears; editor prefills saved text; edit persists |
| B9 | Bookmark + note the same verse | Both indicators coexist (Edge Case) |
| B10 | Notes tab | Real bookmarks/notes surface (SC-007 — no "coming soon"); both sections list entries with matn/verse context; tap an entry → its verse. With a fresh install: purposeful empty states |
| B11 | Force-close the app, relaunch | All bookmarks and notes intact (SC-004); Notes tab unchanged |
| B12 | During active playback, navigate to a verse from search and from a bookmark | Reading screen opens on target verse; playback does not crash or corrupt (Edge Case — audio state remains coherent) |
| B13 | Editor: open on a verse with no note, save empty / dismiss | No note created (FR-019); delete action only visible for existing notes |
| B14 | Device RTL check on all three new surfaces + indicators | Native RTL layout, Arabic-first, token-driven styling matches Stitch designs (Constitution VII/VIII) |
| B15 | SC-006 discoverability walkthrough: hand the device to someone who hasn't seen the feature; ask them to bookmark a verse, then attach a note to it, with no instruction | Both tasks completed from the verse's own controls with at most one wrong tap each |

B2, B7, B10, B11, B14 are this phase's highest-risk surfaces and are **mandatory**, matching the
convention of prior phases' quickstarts.
