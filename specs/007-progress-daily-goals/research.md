# Research: Progress & Daily Goals

Phase 0 decisions. Each resolves an unknown in the plan's Technical Context or a design choice the
spec/clarifications left to implementation. Format: Decision / Rationale / Alternatives.

## D1 — Local calendar day source

**Decision**: Represent "today" as a **local epoch-day** (`Long`, days since 1970-01-01 in the
device's current time zone), supplied through an injected `today: () -> Long` provider — mirroring
the existing injected `clock: () -> Long` millis provider used by `BookmarkRepositoryImpl`/
`NoteRepositoryImpl`. The production provider is a `commonMain` lambda over a new `kotlinx-datetime`
dependency: `Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays()`. Millisecond
timestamps (`practiced_at`, `memorized_at`) keep using the existing `clock` lambda.

**Rationale**: The daily count and its midnight reset (FR-013) hinge on the *local* calendar day, not
UTC; hand-rolling day math from epoch-millis is wrong at time-zone and DST boundaries. Injection
keeps domain/data pure and device-free (Principle V) — tests pass a fake `today` to simulate a day
rollover deterministically. `kotlinx-datetime` is the canonical KMP date library and adds no
platform code.

**Alternatives**: (a) Derive local-day = `floor(epochMillis / 86_400_000)` — rejected: ignores the
time-zone offset and DST, mis-attributing evening practice to the wrong day. (b) `expect`/`actual`
`currentEpochDay()` — rejected: duplicates platform date code (Constitution IV) and still needs an
injected seam for tests, so it buys nothing over the lambda. (c) A wall-clock `LocalDate` string key
— rejected: an integer epoch-day is cheaper to compare/range and sorts naturally.

## D2 — Practice-signal detection ("completed a full playthrough in Memorization/A–B Loop mode")

**Decision**: `PlaybackController` publishes a **natural-completion marker** on `PlaybackState` —
the id of the verse that just finished plus a monotonically increasing tick — set **only** at the
two points where the controller already knows a track finished on its own: the engine
`AudioEngineEvent.TrackTransition` handler (the *outgoing* window entry's verse) and the
`AudioEngineEvent.QueueEnded` handler (the final active verse). User transport (`next()`/
`previous()`/`stop()`) does **not** set it. A new pure-observer `PracticeSignalRecorder` (built like
`SessionStateRecorder`) subscribes to `PlaybackController.state`; on each new completion tick it
checks whether the session `settings` are a **recall mode** and, if so, records a practice event for
the completed verse at `today()`'s epoch-day via `ProgressRepository.recordPractice`. Duplicate
credits are absorbed by the DB `UNIQUE(day_epoch, verse_id)` constraint.

**Recall-mode predicate** (implements clarified FR-011):
`settings.loopRange != null || settings.verseRepeat != RepeatCount.ONE || settings.matnRepeat != RepeatCount.ONE`.
Normal continuous playback (`Vr = 1`, `Mr = 1`, no loop) never credits a completion.

**Rationale**: Whether a transition is a genuine completion (vs. a user skip) and whether a
repetition *pass* finished are known only inside the controller — inferring them from outside is
fragile. Publishing a marker at the two natural boundaries keeps `PracticeSignalRecorder` a **pure
observer** (read state, write the repository — the exact `SessionStateRecorder` shape the codebase
already trusts) and adds no new decision branch to playback. In Memorization mode with `Vr > 1` the
planner materializes each pass as its own window track, so a pass boundary is itself a
`TrackTransition` — crediting on the first completion (then dedup) is correct regardless of pass
count.

**Alternatives**: (a) Fully pure observer that infers completion from `activeVerseId` changes +
`positionMs` reaching `durationMs` — rejected: `applyCursor` resets `positionMs` to 0 at the
transition, so the observer never sees the outgoing verse's final position, and it cannot tell a
natural transition from a user `next()`. (b) A dedicated completion `SharedFlow`/`Channel` on the
controller — rejected: the codebase models playback as a single `StateFlow`; a state marker fits
that model and needs no extra subscription plumbing or replay handling. (c) Crediting on *entry* to
a verse in a recall session — rejected: credits verses skipped past without being heard, violating
"completed a full playthrough."

## D3 — "Mark as memorized" also credits the daily goal; un-mark never decrements

**Decision**: `ToggleVerseMemorizedUseCase` sets the memorized state. Turning it **on** writes the
`memorization` row **and** records a practice event for `today()` (FR-011 names marking as a
qualifying practice action). Turning it **off** deletes only the `memorization` row and leaves every
`daily_practice` row intact (append-only, FR-014 / clarification Q2). `MarkChapterMemorizedUseCase`
applies the same rule in bulk, crediting each *newly* memorized verse at most once.

**Rationale**: Directly encodes the two clarifications — marking is practice (Q1), and the daily
record is append-only so un-marking corrects only the percentage, never the ring (Q2). Keeping the
daily-practice write inside the same use case (not the UI) means both entry points (carousel toggle
and chapter bulk) credit consistently.

**Alternatives**: Recording practice from the ViewModel — rejected: business rule belongs in the
domain layer (Principle I) and would otherwise be duplicated across the two call sites.

## D4 — Per-matn progress percentage derivation

**Decision**: Derive progress with a **single reactive SQL aggregate** that `LEFT JOIN`s
`matn → verse → memorization` and groups by matn, yielding `(matn_id, total_verses,
memorized_verses)` per matn. `ObserveMatnProgressUseCase` (one matn) and
`ObserveLibraryProgressUseCase` (all متون) read from this one source; the details header, the
library card, and the Goals dashboard all consume it, guaranteeing they agree (SC-002). The
percentage/fraction is computed in the domain model (`MatnProgress.fraction`), not in SQL, so a
zero-verse matn yields `0f` with no divide-by-zero (FR-008).

**Rationale**: One query = one source of truth = automatic cross-surface agreement; it mirrors the
existing `selectLibrarySummaries` LEFT-JOIN aggregate precedent (a zero-verse matn still appears with
count 0). Reactive SQLDelight queries re-emit on any memorization change, so all surfaces update
within one round-trip (SC-001/SC-005).

**Alternatives**: Each ViewModel computing % from independently fetched counts — rejected: invites
drift between surfaces and violates SC-002.

## D5 — Daily goal storage

**Decision**: Store the goal in the existing Phase-1 `app_setting` key/value table under key
`daily_goal` (value = the integer as a string), reusing the reading-font-size precedent. Absent or
unparseable value resolves to the **default 10** (FR-009 / clarification Q3). `SetDailyGoalUseCase`
coerces the written value to `≥ 1` (FR-009 "positive whole number"; FR-014 boundary). Observation
is a reactive `app_setting` query mapped to `Int`.

**Rationale**: A single scalar setting does not warrant its own table; the additive key/value table
was designed for exactly this ("forward-compatible for later settings"), and the default-on-absent
pattern already exists for font size (never crashes on a bad value).

**Alternatives**: A dedicated `daily_goal` table — rejected: overkill for one integer. A compiled
default with no persistence — rejected: the goal must survive restart (SC-004).

## D6 — Schema migration

**Decision**: Add `shared/src/commonMain/sqldelight/com/giraffe/matn/db/migrations/3.sqm`
(migrates **from** schema v3, bumping `ContentDatabase.Schema.version` to **4**) creating the
`memorization` and `daily_practice` tables. The `CREATE TABLE` text in `3.sqm` is byte-identical to
the definitions added to `Content.sq`. A new `MigrationV3Test` (mirroring the existing
`MigrationV2Test`) proves existing v3 data (متون, verses, bookmarks, notes, sessions) survives the
upgrade and the new tables are queryable.

**Rationale**: Matches the established migration discipline documented in `2.sqm` (named for the
version it migrates *from*; CREATE text kept identical to `Content.sq`; guarded by a migration
test). Purely additive — no existing table is touched, so the upgrade is non-destructive.

**Alternatives**: Recreating the DB on version bump — rejected: destroys the user's bookmarks/notes/
sessions. Editing existing tables — rejected: unnecessary; progress is orthogonal.

## D7 — Entity identity (Constitution VI)

**Decision**: Both new tables carry their **own** UUID `id` primary key, independent of the verse
they reference:
- `memorization`: `id` (UUID) + `verse_id UNIQUE REFERENCES verse(id) ON DELETE CASCADE` +
  `memorized_at`. `UNIQUE(verse_id)` enforces one status per verse and doubles as the per-verse
  lookup index (the `bookmark` precedent exactly).
- `daily_practice`: `id` (UUID) + `day_epoch` + `verse_id REFERENCES verse(id) ON DELETE CASCADE` +
  `practiced_at`, with `UNIQUE(day_epoch, verse_id)` enforcing at-most-once-per-verse-per-day
  (FR-010) and serving as the per-day count/dedup index.

**Rationale**: Constitution VI names the "progress record" as an entity that MUST carry a stable
UUID identity independent of local row ids — so a future sync layer can address it directly. `CASCADE`
from `verse` (text identity), never from `audio_asset`, guarantees audio removal cannot delete
progress (FR-004).

**Alternatives**: `verse_id` as the sole primary key of `memorization` — rejected: violates
Constitution VI's stable-UUID-identity rule for progress records (and diverges from the bookmark
shape the codebase already uses).

## D8 — Shared ring / progress-bar components

**Decision**: Extract two stateless, parameterized components into `presentation/common`:
`DailyGoalRing` (used by the Home top bar **and** the Goals dashboard) and `MatnProgressBar` (used by
the matn details header **and** each Goals dashboard matn row). Each is driven entirely by its
parameters, carries at least one `@Preview`, and uses only Phase 10 tokens. The memorized
indicator/action is a new `MemorizedGlyph` added to the existing `AnnotationGlyphs.kt`, a distinct
hand-drawn shape (not a fill/color variant of the bookmark/note glyphs).

**Rationale**: The ring and the bar each appear on two surfaces — the constitution's Principle VIII
"extract at the second use, not the third" makes extraction mandatory, and stateless components
preview in isolation (Principle II).

**Alternatives**: Inlining the ring separately on Home and Goals — rejected: a direct Principle VIII
violation and a drift risk (two rings that render the same data differently).

## Resolved unknowns

- Local-day source → D1 (`kotlinx-datetime` behind injected `today`).
- Practice completion detection → D2 (controller marker + pure-observer recorder, recall-mode gate).
- Marking-as-practice + append-only semantics → D3.
- Progress % single-source derivation and zero-verse handling → D4.
- Goal persistence + default + bounds → D5.
- Migration path and identity → D6/D7.
- UI reuse → D8.

No open [NEEDS CLARIFICATION] remain; the three spec clarifications (practice signal, append-only
count, default goal) are encoded above.
