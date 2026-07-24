# Phase 1 Data Model: Continue Learning & State Persistence

**Feature**: Phase 4 — Continue Learning & State Persistence | **Date**: 2026-07-24

This phase adds the project's **first persisted playback state**. Phases 2 and 3 shaped that state
deliberately for this moment, so the job is storage and resolution, not redesign.

---

## 1. Persisted schema

### 1.1 `matn_session` (new table)

One row per matn the student has listened to or configured (FR-002b).

```sql
CREATE TABLE matn_session (
    matn_id                    TEXT NOT NULL PRIMARY KEY,
    last_verse_id              TEXT NOT NULL,
    last_verse_display_number  INTEGER NOT NULL,
    position_ms                INTEGER NOT NULL,
    verse_repeat               TEXT NOT NULL,
    matn_repeat                TEXT NOT NULL,
    loop_start_verse_id        TEXT,
    loop_end_verse_id          TEXT,
    FOREIGN KEY (matn_id) REFERENCES matn(id) ON DELETE CASCADE
);
```

| Column | Why it exists | Requirement |
|---|---|---|
| `matn_id` | Per-matn scoping, matching Phase 3 FR-007 | FR-001, FR-023 |
| `last_verse_id` | Stable UUID, never a list index | FR-003 |
| `last_verse_display_number` | Ordinal anchor so "nearest surviving verse" stays computable after the verse row is deleted | FR-026 (research D4) |
| `position_ms` | Millisecond resume point | FR-004, FR-022 |
| `verse_repeat`, `matn_repeat` | Encoded `RepeatCount` — see §1.3 | FR-001, FR-007 |
| `loop_start_verse_id`, `loop_end_verse_id` | Nullable pair; both null = no loop | FR-001, FR-020 |

> **No timestamp column.** An `updated_at_ms` was considered for recency and future sync-conflict
> resolution and **rejected**: no requirement needs it (the single pointer already determines which
> session is current), and a wall clock in `commonMain` would force either a new dependency or a new
> `expect`/`actual` — real cost for speculative benefit. Principle VI asks that the shape not
> *block* sync, not that it pre-build for it; a timestamp can be added by a later migration at the
> point sync actually exists.

**Deliberately absent**: playback **mode** (FR-005 — derived; `RepetitionSettings.mode` is a
computed property with no backing field, so it is *structurally* impossible to persist),
**in-flight repetition/pass indices** (FR-006), and **playing-vs-paused** (resume always plays,
per the auto-play clarification).

`ON DELETE CASCADE` gives FR-025 for free at the storage layer: deleting a matn removes its session
row, so a dangling per-matn record cannot outlive its content.

### 1.2 `last_listened_matn_id` (new `app_setting` row)

The single pointer (FR-002), stored in the existing key/value table rather than a new one
(research D2). Key: `last_listened_matn_id`. Value: the matn UUID.

Absent key = no Continue Learning entry. Dismiss deletes this row and nothing else (FR-017a).

### 1.3 `RepeatCount` encoding

`RepeatCount` is a sealed type — `Finite(1..99)` or `Unlimited` — and FR-007 requires unlimited to
stay distinguishable from both a finite value and an unset one.

**Encoding**: `TEXT`, either `"UNLIMITED"` or the decimal digits of the finite value.

A `TEXT` column with an explicit `"UNLIMITED"` sentinel keeps the domain's central invariant intact
in storage: `RepeatCount`'s own KDoc says unlimited is "a **distinct type**, never a sentinel
`0`/`null`, so 'unlimited' can never be confused with 'unset'". Encoding it as an integer with a
magic `0` or `NULL` would reintroduce in the database exactly the ambiguity the domain type was
built to eliminate.

Decoding is total and never throws: unparseable or out-of-range input falls back to
`RepeatCount.of(...)`'s clamp or to the `ONE` default, matching the `ReadingFontSize`
`fromStorageOrDefault` precedent and satisfying FR-029.

### 1.4 Migration

| Version | Contents |
|---|---|
| 1 | Phase 0/1 schema as it stands today (implicit; no migration files exist) |
| 2 | `1.sqm` — `CREATE TABLE matn_session` (no extra index; `matn_id` is the PRIMARY KEY) |

`Content.sq` carries the table for fresh installs (`Schema.create`); `1.sqm` carries it for existing
installs (`Schema.migrate`). See research D3 for why this is introduced now.

---

## 2. Domain types (`commonMain`)

### 2.1 `SavedMatnSession`

```kotlin
data class SavedMatnSession(
    val matnId: String,
    val lastVerseId: String,
    val lastVerseDisplayNumber: Int,
    val positionMs: Long,
    val settings: RepetitionSettings,
)
```

Reuses Phase 3's `RepetitionSettings` wholesale rather than re-declaring counters and range
(Principle III). Because `mode` is computed on that type, a restored session cannot display a mode
contradicting its counters (FR-005).

### 2.2 `ResumeTarget`

The resolved, validated outcome of "what should tapping Continue Learning actually do?"

```kotlin
sealed interface ResumeTarget {
    data class Resolved(
        val matnId: String,
        val verseId: String,
        val positionMs: Long,          // 0 when the verse was substituted (FR-026)
        val settings: RepetitionSettings,
        val substituted: Boolean,      // true when the saved verse no longer existed
    ) : ResumeTarget
    data object None : ResumeTarget    // no state, or state unhonourable (FR-025, FR-029)
}
```

### 2.3 `ContinueLearningEntry`

Derived, never stored (FR-015) — exists only when resolution succeeds.

```kotlin
data class ContinueLearningEntry(
    val matnId: String,
    val matnTitle: String,
    val verseDisplayNumber: Int,
    val verseId: String,
)
```

---

## 3. Resolution rules (pure — `ResumeTargetResolver`)

Given the saved session, the pointer, and the matn's current ordered verses:

| # | Condition | Outcome | Req |
|---|---|---|---|
| R1 | No pointer, or no session row for it | `None` | FR-015 |
| R2 | Matn no longer exists | `None` | FR-025 |
| R3 | Saved verse exists | `Resolved` at `positionMs` | FR-018, FR-022 |
| R4 | Saved verse gone → nearest **preceding** by display number | `Resolved`, `positionMs = 0`, `substituted = true` | FR-026 |
| R5 | Saved verse gone, none preceding → nearest **following** | as R4 | FR-026 |
| R6 | Matn has no verses at all | `None` | FR-026 |
| R7 | Loop endpoint missing | Clear the loop; keep counters | FR-027 |
| R8 | Resolved verse outside its own loop range | Clear the loop | FR-028 |
| R9 | Row unreadable / undecodable | Treat as absent → `None` | FR-029 |

R4/R5 are why `last_verse_display_number` is persisted (research D4): once the verse row is gone,
its ID carries no ordering information.

R7 and R8 both prefer **clearing the loop over discarding the session** — the counters remain valid
configuration, and dropping them would lose more of the student's work than the corruption warrants.

---

## 4. Write path

`SessionStateRecorder` observes `PlaybackController.state` and projects a durable subset:

```
PlaybackState → DurableSnapshot(matnId, verseId, displayNumber, positionMs, settings)
```

| Signal | Cadence | Req |
|---|---|---|
| `matnId`, `verseId`, or `settings` changed (`distinctUntilChanged`) | Immediate | FR-008, SC-004 |
| `positionMs` | Throttled ~5 s | FR-009 |
| Pause / stop / background | Forced flush | FR-009, FR-010 |
| First playback in a matn | Writes row **and** pointer | FR-002a, FR-002b |
| Settings change with no playback | Writes row, **not** pointer | FR-002b |

Because the projection drops `status`, `speed`, `notice`, `pauseReason`, and `cursor`, ordinary
pause/resume and speed changes produce **no** writes at all.

All writes go through the existing `storageCall` helper, surfacing failures as
`AppError.Storage` rather than throwing — a failed write must never disturb playback (FR-011,
SC-008).

---

## 5. UI state additions

`HomeUiState` gains one nullable field; the grid contract is untouched.

```kotlin
data class HomeUiState(
    val isLoading: Boolean = true,
    val items: List<MatnSummary> = emptyList(),
    val isEmpty: Boolean = false,
    val error: AppError? = null,
    val continueLearning: ContinueLearningEntry? = null,   // null → card omitted (FR-015)
)
```

`null` renders nothing at all — not an empty or placeholder card.
