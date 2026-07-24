# Phase 0 Research: Continue Learning & State Persistence

**Feature**: Phase 4 — Continue Learning & State Persistence | **Date**: 2026-07-24

Decisions taken before design. Each records what was chosen, why, and what was rejected.

---

## D1 — Where persistence hooks into playback

**Decision**: A new `SessionStateRecorder` in `commonMain` that **observes**
`PlaybackController.state` and writes derived snapshots. `PlaybackController` gains **no** new
dependency and no write calls.

**Rationale**: Phase 2 already made `PlaybackState` the single authoritative session snapshot, and
its own KDoc states it "carries exactly the fields Phase 4 must persist … so Phase 4 persists this
snapshot without reshaping it". Observing that flow uses the seam as designed. It also keeps the
write policy (immediate vs throttled, D5) expressible as ordinary Flow operators in one file, and
makes the whole feature testable by feeding a `MutableStateFlow<PlaybackState>` with no controller,
no engine, and no database.

**Alternatives considered**:

- *Write calls inside `PlaybackController`* — rejected. The controller is already 602 lines with
  10+ `_state.value =` mutation sites (`startSession`, `next`, `previous`, `moveToVerse`,
  `updateSettings`, `stop`, …). Every one would need a write call, and each is a place to forget
  one. It also spreads throttling policy across the class.
- *Persist from the ViewModel layer* — rejected outright: it would put data-layer access in
  presentation and invert the dependency direction (Principle I).

---

## D2 — Storage shape

**Decision**: One new table `matn_session` (one row per matn), plus **one `app_setting` row** keyed
`last_listened_matn_id` for the pointer.

**Rationale**: The two entities have genuinely different cardinalities — per-matn records are many,
the pointer is exactly one. `app_setting` already exists for exactly this kind of singleton and has
a precedent in `ReadingPreferencesRepositoryImpl` (`reading_font_size`), including the
absent-or-unrecognized → default fallback that FR-029 needs. Reusing it costs no schema surface and
inherits a tested pattern (Principle III).

**Alternatives considered**:

- *Single-row "session" table* — rejected; cannot hold per-matn settings, which FR-023 requires.
- *Everything in `app_setting` as serialized JSON* — rejected. It would make per-matn lookup a
  full-scan-and-parse, and put a schema inside a string where SQLite cannot enforce it.

---

## D3 — Schema migration discipline *(gap found in existing code)*

**Decision**: Introduce SQLDelight **migration files** with this change: add `1.sqm` containing the
new table, alongside the `CREATE TABLE` in `Content.sq`. Database version becomes 2.

**Rationale**: The project currently has **no `.sqm` files at all**. Both platform drivers pass
`ContentDatabase.Schema`, so a fresh install calls `create()` and gets the current schema — but an
**existing** install stays at version 1 and no migration ever runs, so a newly added table simply
would not exist and the first query against it would fail at runtime. Phase 1 added `app_setting`
this way without incident only because the app is unreleased and developers reinstall.

Phase 4 is the phase whose entire purpose is *not losing the student's state*. Establishing the
migration path here — before first release, while the cost is one file — is the cheap moment. After
release it is a data-loss bug.

**Alternatives considered**:

- *Continue without migrations* — rejected. It works only for as long as every install is
  disposable, and nothing warns you when that stops being true.
- *Retrofit migrations for the whole Phase 0/1 history* — rejected as unnecessary archaeology;
  version 1 is defined perfectly well by the current `.sq` files.

---

## D4 — Persist the verse's display number, not only its ID

**Decision**: `matn_session` stores **both** `last_verse_id` and `last_verse_display_number`.

**Rationale**: This falls directly out of the FR-026 clarification. When the saved verse has been
deleted, "resume at the nearest surviving verse in reading order" requires knowing *where in the
order the missing verse was* — but its ID no longer resolves to anything, so ordering information
is unrecoverable from the ID alone. The display number is a stable ordinal (`verse.display_number`,
already `UNIQUE (matn_id, display_number)` and indexed by `verse_by_matn_order`) that survives the
row's deletion as a plain integer, letting the resolver find the nearest neighbour on either side.

Without this field FR-026 is unimplementable as specified, and the only available fallback would be
"restart the matn" — the option explicitly rejected during clarification.

**Alternatives considered**:

- *Store the ID only* — rejected; makes the chosen fallback impossible (above).
- *Store a full ordered ID snapshot per session* — rejected as unbounded and redundant; one integer
  answers the only question actually asked.

---

## D5 — Write cadence

**Decision**: Two triggers off the same observed flow.

| Trigger | Cadence | Serves |
|---|---|---|
| Structural change — matn, verse, or repetition settings | Immediate, on `distinctUntilChanged` | FR-008, SC-004 (≤1 s) |
| Position within the active verse | Throttled (~5 s) **plus** forced on pause / stop / background | FR-009, SC-003 |

**Rationale**: FR-008's "every verse transition" and FR-009's "at most the current verse's partial
position" are different guarantees and need different mechanisms. Structural changes are rare and
cheap, so write them at once. Position changes continuously and must never cause I/O per frame, so
throttle it and force a flush at the moments the app is about to lose control. Deriving a small
durable snapshot and applying `distinctUntilChanged` also means pause/resume, speed changes, and
notice updates — none of which are persisted — trigger no writes at all.

**Alternatives considered**:

- *Debounce everything* — rejected; would delay verse-transition writes and fail SC-004's 1 s bound
  under continuous playback, which is exactly when transitions happen.
- *Write on every state emission* — rejected; the position field updates at player tick frequency,
  which would mean sustained database writes throughout every listening session (violates FR-011,
  SC-008).

---

## D6 — Resume orchestration and the nearest-verse resolver

**Decision**: A pure `ResumeTargetResolver` (no coroutines, no I/O) plus two use cases —
`ObserveContinueLearningUseCase` (Home card) and `ResolveResumeTargetUseCase` (the tap).

**Rationale**: Every FR-025→FR-028 rule is a *decision about data*, not an I/O concern: matn
missing, verse missing, range partly missing, verse outside its own range. Isolating them in a pure
function makes them table-testable with no fakes — the same shape Phase 3 used for
`RepetitionPlanner`, which the constitution singles out as the right treatment for core correctness
logic (Principle V). The use cases stay thin: fetch, delegate, return.

**Alternatives considered**:

- *Resolve inside the repository* — rejected; buries branching business rules in the data layer and
  makes them reachable only through a database.
- *Resolve in the ViewModel* — rejected; business logic in presentation (Principle II).

---

## D7 — Auto-play on resume, gated by audio focus

**Decision**: `ResolveResumeTargetUseCase` returns a target; the existing
`PlaybackController.playFromVerse(matnId, verseId)` starts it, extended to accept a start position.
FR-022a's "restore paused if focus is unavailable" reuses Phase 2's existing `PauseReason`
machinery rather than adding a parallel path.

**Rationale**: Phase 2 already owns audio-focus acquisition and `PauseReason`. Resume is not a new
audio concern — it is an ordinary session start with a non-zero start position and an existing
focus rule. Adding a second focus path would duplicate the interruption contract the constitution
calls contractual (Principle VII).

---

## D8 — Dismiss clears only the pointer

**Decision**: Dismiss deletes the `last_listened_matn_id` setting row. No `matn_session` row is
touched.

**Rationale**: Directly implements the FR-017/FR-017a clarification. It also makes the operation
trivially safe: one key delete, no cascade, nothing to get wrong, and re-establishing the entry is
just the next playback writing the key again.

---

## Resolved unknowns

No `NEEDS CLARIFICATION` items remain from the spec. Two items the spec listed as *Outstanding
(low impact)* are dispositioned here rather than left open:

- **Saved-record growth** — no cap or pruning. One row per matn with a bounded column set; even a
  library of thousands of متون is kilobytes. Revisit only if Phase 7 introduces bulk content.
- **Exclusion from OS cloud backup** — not addressed in this phase. The data is study position, not
  credentials or personal content. Deliberately deferred to the v2 sync work that will own backup
  semantics generally.
