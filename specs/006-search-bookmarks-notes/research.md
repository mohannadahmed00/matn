# Research: Search, Bookmarks & Notes (Phase 6)

**Date**: 2026-07-24 | **Plan**: [plan.md](./plan.md)

No `NEEDS CLARIFICATION` markers remained in the Technical Context; the decisions below resolve
the open *design* choices the spec leaves to planning.

## D1 — Arabic matching: shared pure-Kotlin normalizer, not SQL

**Decision**: Implement FR-002/FR-003 as a single pure function set `ArabicNormalizer` in
`commonMain/domain/search`: strip the Arabic diacritic range (fatha, damma, kasra, sukun,
shadda, tanwin, superscript alef — U+064B–U+0655, U+0670), fold أ/إ/آ/ٱ → ا, ى → ي, ة → ه,
map Arabic-Indic digits ٠–٩ → 0–9, remove tatweel (U+0640), collapse/trim whitespace, and
lowercase (for any Latin content in titles). Matching = substring containment of
`normalize(query)` in `normalize(text)`.

**Rationale**: SQLite (via SQLDelight) has no Arabic-aware collation or diacritic folding;
`LIKE` is ASCII-case-insensitive only. A Kotlin normalizer is trivially unit-testable in
`commonTest` (Principle V), lives once in `commonMain` (Principles III/IV), and is reusable by
any future consumer (e.g., Phase 7+ or note search later).

**Alternatives considered**: SQLite `ICU` extension (not bundled in the Android/native SQLite
builds SQLDelight uses; platform-divergent); storing a pre-normalized shadow column and matching
with `LIKE` (rejected — see D2); Unicode NFKD + mark-stripping via a library (no KMP-ready
dependency; the needed character set is small and fixed, a hand-rolled table is simpler and
exactly matches the clarified FR-002 scope).

## D2 — Search execution: in-memory filter over repository reads, no FTS, no schema change

**Decision**: `SearchRepositoryImpl` loads the searchable corpus (all متون with their chapters
and verses, via existing/new plain SELECTs), normalizes once per content change (cached,
invalidated by SQLDelight's reactive queries), and filters in memory per query. Results are
assembled grouped by matn in library order, verses in `display_number` order (FR-006).

**Rationale**: The corpus is small (low thousands of short rows — SC-001 names 1,000 verses).
A full in-memory scan with pre-normalized text is microseconds-scale, comfortably inside the
1-second budget, and keeps *all* matching logic in testable common Kotlin. `LIKE '%…%'` cannot
use an index anyway, so a DB-side scan has no performance advantage — it would only push
normalization into a stored column.

**Alternatives considered**:
- **SQLite FTS5**: requires a custom tokenizer for diacritic/letter-form folding — not
  expressible portably through SQLDelight on both Android and iOS; massive overkill at this
  scale. Rejected.
- **Pre-normalized `text_normalized` columns + LIKE**: forces a v2 migration *with backfill*
  (Kotlin normalization cannot run inside a `.sqm` migration, so backfill would need app-start
  logic), duplicates data, and splits matching semantics between Kotlin (query side) and SQL
  (text side). Rejected as more moving parts for zero measurable gain.
- **Normalize-on-every-keystroke without caching**: simplest, and acceptable at current scale;
  the cache is a one-class refinement — keep it only if the perf test demands it. The contract
  fixes behavior, not the caching strategy.

## D3 — Bookmarks & notes: two UUID-keyed tables, first SQLDelight migration (v1 → v2)

**Decision**: Add `bookmark(id TEXT PK, verse_id TEXT NOT NULL UNIQUE REFERENCES verse ON
DELETE CASCADE, created_at INTEGER NOT NULL)` and `note(id TEXT PK, verse_id TEXT NOT NULL
UNIQUE REFERENCES verse ON DELETE CASCADE, text TEXT NOT NULL, updated_at INTEGER NOT NULL)`
via `migrations/1.sqm`; bump the SQLDelight schema version to 2.

**Rationale**: Constitution VI explicitly requires bookmarks and notes to carry their own stable
UUID identity (sync-safe for v2 cloud backup), so `verse_id` alone as PK is insufficient —
hence `id` UUID PK + `UNIQUE(verse_id)` to enforce the spec's at-most-one-per-verse rule at the
schema level. `ON DELETE CASCADE` ties annotation lifetime to the *verse row* (text content),
not to audio assets — deleting downloaded audio (`deleteAudioByMatn`) never touches these
tables, satisfying FR-014/FR-018. Epoch-millis timestamps order the global lists.

**Alternatives considered**: key-value rows in `app_setting` (unqueryable, violates entity
modeling); one combined `annotation` table with a type column (bookmarks and notes have
different columns and lifecycles; JOIN-heavy for no benefit). Both rejected.

## D4 — Timestamps via injected clock

**Decision**: Repositories take a `clock: () -> Long` (epoch millis) constructor parameter,
defaulted at the Koin wiring site; tests inject a deterministic clock.

**Rationale**: Principle V requires injected time sources; matches the store/recorder patterns
already used in Phases 4–5. No kotlinx-datetime dependency needed for a single epoch-millis
read.

**Alternatives considered**: `kotlinx-datetime` `Clock` (new dependency for one call site —
constitution requires justifying new dependencies; not warranted); reading time inside SQL
(`strftime`) — moves logic into the DB and out of test control. Rejected.

## D5 — Navigation to a specific verse: optional `focusVerseId` argument on the existing matn route

**Decision**: Extend `Routes.MATN_DETAILS` with an optional `focusVerseId` query-style argument
(`matn/{matnId}?focusVerseId={id}`); `MatnDetailsViewModel` treats it as the initial carousel
focus (without starting playback). Search verse-results, chapter-results (first verse of the
chapter), bookmarks, and notes all navigate through this one path. Matn-title results navigate
to the plain matn route.

**Rationale**: Reuses the existing, playback-safe entry path (the details screen already owns
carousel focus state and the PlayerBar), satisfying the spec's "navigation during playback must
not corrupt playback state" edge case with zero new playback API surface. One mechanism serves
all four navigation sources (DRY).

**Alternatives considered**: navigating then imperatively scrolling via a shared side-channel
(fragile, violates unidirectional data flow); a distinct `verse/{id}` route (duplicates the
details screen registration). Rejected.

## D6 — Notes tab composition: one screen, two sections, replacing the stub

**Decision**: `NotesTabScreen` (route `notes`, already in `NavigationTab`) renders bookmarks
and notes as two sections/segments of one screen per the canonical *Bookmarks & Notes* Stitch
design (`bc99ab7f8110479086326271d0aa0310`), each with its own empty state; the
`ComingSoonScreen` registration for `Routes.NOTES` is replaced. Exact segmentation (tabs vs.
sections) follows the fetched Stitch design at implementation time — the design is authoritative
about appearance (Principle VIII), this plan only fixes the data contract.

**Rationale**: The tab, route, glyph, and string resource already exist from Phase 10; the spec
(FR-020) requires both collections in one coherent tab.

**Alternatives considered**: separate top-level destinations for bookmarks vs. notes (violates
the locked 4-tab shell). Rejected.

## D7 — Reading-screen annotation actions

**Decision**: Bookmark toggle and note actions attach to the **active verse** of the existing
3-verse `ReadingCarousel` (Phase 10 layout), with indicator glyphs rendered on any verse card
that is bookmarked/noted (`ObserveVerseAnnotationsUseCase` provides a per-verse annotation map
for the whole matn as a `Flow`). The note editor is a bottom sheet
(`NoteEditorSheet`) hosted by the details screen, consistent with the existing
`RepetitionSetupSheet` pattern. Exact control placement follows the fetched Stitch designs;
where the design shows no explicit affordance, controls join the active-verse card's action row
— raised as an open design question in ui-contract.md rather than silently invented
(Principle VIII).

**Rationale**: The carousel's active-verse card is the only per-verse surface in the current
reading UI; a sheet matches the app's established modal-edit idiom and keeps the editor
stateless-content + holder testable.

**Alternatives considered**: long-press context menu (undiscoverable, conflicts with SC-006's
"no instruction" discoverability bar); a separate full-screen note editor (heavier than the
established sheet idiom for a single text field). Rejected.

## D8 — Search-as-you-type mechanics

**Decision**: `SearchViewModel` debounces input (~250 ms) and cancels stale queries
(`flatMapLatest`); blank/whitespace-only input maps to the `Idle` state (FR-008), zero matches
to `NoResults` (FR-009). No minimum query length — single-character queries are allowed and
must stay responsive (spec edge case); result assembly is bounded by the corpus size, and the
UI renders lazily.

**Rationale**: Standard, testable coroutine pattern (`kotlinx-coroutines-test` controls the
debounce clock); distinguishing Idle vs. NoResults is a spec requirement, so it belongs in the
UI-state type, not in string copy.

**Alternatives considered**: explicit submit-button search (contradicts FR-008 "update results
as the student refines the query"); min-length ≥ 2 gating (spec explicitly requires
single-character queries to work). Rejected.
