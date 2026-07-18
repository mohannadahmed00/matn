# Phase 0 Research: Foundation & Data Model

This document resolves the technical unknowns for the foundation phase. The stack is largely
fixed by the constitution (KMP + SQLDelight, base package `com.giraffe.matn`, DI required);
the open decisions are the supporting libraries and a few modeling choices. No `NEEDS
CLARIFICATION` markers remain after this document.

---

## Decision 1 — Local persistence: SQLDelight

- **Decision**: Use **SQLDelight 2.x** with typed `.sq` schema files and the coroutines
  extension for `Flow`-based observation. Platform drivers via `expect`/`actual`:
  `AndroidSqliteDriver` (Android), `NativeSqliteDriver` (iOS), and the in-memory JDBC driver
  in `commonTest`.
- **Rationale**: Named explicitly by the constitution (Technology & Architecture Constraints
  and the Phase 0 roadmap entry). Compile-time-checked SQL fits the "testable by design"
  principle; the coroutines extension gives `Flow` observation to satisfy FR-008 (later phases
  react to loaded content). SQLite reliably stores fully-formed Unicode (Arabic + diacritics)
  with no normalization, satisfying FR-007/SC-003. In-memory driver enables device-free unit
  tests (Principle V).
- **Alternatives considered**:
  - *Room (KMP)* — viable, but the constitution specifies SQLDelight; SQLDelight's generated
    typed queries and mature multiplatform driver story fit better.
  - *Realm/Realm-Kotlin* — object DB with sync features, but heavier, less transparent SQL,
    and its identity/migration model conflicts with content-authored UUIDs as primary keys.
  - *Hand-rolled file storage (JSON on disk)* — rejected: no query/ordering guarantees, no
    transactional atomic-reject, poor performance at 500 verses (SC-006).

## Decision 2 — Async & observation: kotlinx-coroutines + Flow

- **Decision**: Add `kotlinx-coroutines-core` and use `Flow` for observable reads
  (`observeMatnLibrary()`, `observeVerses(matnId)`), with `suspend` functions for one-shot
  reads and the atomic seed load.
- **Rationale**: The constitution mandates coroutines/Flow for async and state. SQLDelight's
  coroutines extension maps queries to `Flow` directly, satisfying FR-008 without custom
  plumbing. `suspend` + a single DB transaction gives the atomic-reject semantics of FR-019.
- **Alternatives considered**: *Callbacks / manual listeners* — rejected as non-idiomatic and
  hard to test. *StateFlow at the data layer* — deferred; state exposure is a presentation
  concern (Phase 1+), not a data-layer one.

## Decision 3 — Dependency injection: Koin

- **Decision**: Use **Koin 4.x** to wire the driver factory, database, repository
  implementations, and seed loader into a single `ContentModule`.
- **Rationale**: The constitution requires DI with no manual singletons/service locators
  across layer boundaries. Koin is the most widely used pure-Kotlin KMP DI framework, works in
  `commonMain`, needs no annotation processor (keeping iOS/native builds simple), and lets
  tests swap the driver factory for the in-memory one via a test module (Principle V).
- **Alternatives considered**:
  - *Kotlin-inject / kotlin-inject-anvil* — compile-time-safe and excellent, but adds KSP to
    the KMP build and is heavier than this phase needs; can be revisited if runtime DI proves
    limiting.
  - *Manual DI (hand-written factory)* — rejected: the constitution forbids service locators
    reached across boundaries and expects a DI framework; manual wiring would grow unmanaged
    as later phases add use cases/ViewModels.

## Decision 4 — Seed content format: kotlinx-serialization JSON

- **Decision**: Represent seed content as **JSON** parsed with `kotlinx-serialization-json`
  into DTOs, which the `SeedContentLoader` validates and upserts. Seed fixtures live in
  `commonTest` for tests; production seed provisioning (bundled asset location) is a
  content-prep concern and is left as a thin, replaceable input to the loader.
- **Rationale**: JSON is the natural interchange format for content-authored data carrying
  pre-assigned UUIDs (FR-002, Assumptions). kotlinx-serialization is the standard KMP,
  reflection-free serializer and round-trips Arabic + diacritics losslessly. Keeping the DTO
  layer separate from domain models preserves Clean Architecture boundaries (mapping happens
  in the data layer).
- **Alternatives considered**: *SQL insert scripts as the seed source* — rejected: opaque to
  content authors, no validation hook, and couples content to schema internals. *Protobuf* —
  unnecessary complexity for human-authored content at this scale.

## Decision 5 — UUID representation & identity strategy

- **Decision**: Store each entity's content-authored UUID as its **primary key**, persisted as
  a canonical lowercase string (`TEXT`) exactly as authored. Reload dedup/upsert is keyed on
  this UUID (`INSERT ... ON CONFLICT(id) DO UPDATE`). The store never mints its own IDs.
- **Rationale**: Directly implements FR-002 and FR-009 (content-authored, globally stable,
  upsert-in-place on reload). String storage keeps IDs human-readable, portable across devices
  and future sync, and avoids endian/binary pitfalls between platforms. Using the UUID as the
  PK makes reload dedup a natural `ON CONFLICT` upsert.
- **Alternatives considered**: *Auto-increment INTEGER PK + UUID as a side column* — rejected:
  reintroduces order-dependent local IDs the constitution explicitly forbids and complicates
  cross-device identity. *Binary(16) UUID storage* — marginal space savings not worth the
  reduced debuggability and cross-platform conversion risk at this scale.

## Decision 6 — Verse ordering model (matn-global number)

- **Decision**: Every verse carries a **matn-global sequential display number** (1..N, unique
  within its matn) as the single source of truth for reading order (FR-003). Chapter grouping
  is an overlay: a verse links to an owning chapter (nullable for simple متون), and a chapter's
  verses are a contiguous slice of the global sequence ordered by that number (FR-011). Reads
  `ORDER BY display_number` — never by row id or UUID.
- **Rationale**: Encodes the clarification from Session 2026-07-18 (matn-global number;
  chapter grouping derived). A single global order avoids composite-key ambiguity, makes the
  full-sequence read trivial and stable (SC-001/FR-006), and lets a TOC be derived without a
  separate within-chapter counter. A `UNIQUE(matn_id, display_number)` constraint lets the DB
  itself surface the duplicate-order integrity failure (FR-019).
- **Alternatives considered**: *Composite (chapter order, within-chapter position)* — rejected
  by clarification; more complex, harder to render a flat reading list, redundant numbering.
  *Sort by insertion order* — rejected: violates FR-006 (order must not depend on row order).

## Decision 7 — Atomic-reject load with integrity validation

- **Decision**: `SeedContentLoader` validates a matn's content **before** any write, then
  performs all inserts/upserts for that matn inside a **single SQLDelight transaction**. Any
  integrity violation — duplicate matn-global display number, or a verse missing its audio
  asset reference — aborts the whole load for that matn, persists nothing, and returns a
  typed `ContentIntegrityError` naming the specific problem(s). Validation runs both in
  application code (to produce precise, aggregated error messages) and is backstopped by DB
  constraints (`UNIQUE(matn_id, display_number)`, `NOT NULL`/FK on the audio link).
- **Rationale**: Implements FR-019/SC-008 and the edge cases (ordering collisions, missing
  audio) with a guarantee of zero partial persistence. A transaction gives all-or-nothing
  atomicity for free; app-level pre-validation gives good error reporting; DB constraints are
  the safety net.
- **Alternatives considered**: *Partial load + flag* and *store-then-validate* — both rejected
  by the clarification (atomic reject chosen). *DB constraints only* — rejected: yields cryptic
  errors and can't aggregate multiple problems into one clear report.

## Decision 8 — Audio asset model (per-verse, multi-reciter-ready)

- **Decision**: Model the **Audio Asset** as a separate entity mapping `(verse_id, reciter_id)`
  → `(file_reference, duration)`, with a uniqueness rule of **exactly one asset per verse per
  reciter**. Phase 0 uses a single default reciter. Resolving a verse's audio is a lookup by
  `verse_id` (+ default `reciter_id`).
- **Rationale**: Locks the per-verse micro-file boundary (FR-013, constitution) while the
  `reciter_id` dimension leaves room for alternate reciters with no schema change and no verse
  redefinition (FR-015/SC-004). Keeping it a distinct table (rather than columns on Verse)
  makes the future one-verse-to-many-reciters relationship natural.
- **Alternatives considered**: *Audio fields inline on Verse* — rejected: would require schema
  change to add a second reciter, violating FR-015. *A single continuous audio file with
  offsets* — explicitly forbidden by the constitution and FR-013.

---

## Resolved unknowns summary

| Unknown | Resolution |
|---------|-----------|
| Persistence engine | SQLDelight 2.x (+ coroutines extension) |
| Async/observation | kotlinx-coroutines + Flow |
| DI framework | Koin 4.x |
| Seed format | kotlinx-serialization JSON DTOs |
| UUID storage | content-authored string, used as primary key |
| Verse ordering | matn-global display number; chapter overlay via nullable FK |
| Load integrity | pre-validate + single transaction, atomic reject, typed error |
| Audio model | separate `(verse, reciter)` asset table; one file per verse per reciter |

All Technical Context items are resolved; no open clarifications block Phase 1 design.
