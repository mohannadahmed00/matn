# Implementation Plan: Phase 0 — Foundation & Data Model

**Branch**: `001-foundation-data-model` | **Date**: 2026-07-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-foundation-data-model/spec.md`

## Summary

Phase 0 establishes the offline content foundation for the Matn app: a pure-Kotlin
**domain model** (Matn, Chapter/Section, Verse, Audio Asset) with content-authored UUID
identities, a **SQLDelight** local store that persists and reads that content fully offline,
and **repository interfaces + implementations** that seed content atomically and read it back
in stable matn-global verse order. No UI, playback, or later-phase behavior is built. The
work lands entirely in `shared/commonMain` (with a thin `expect`/`actual` SQLDelight driver
per platform) and is validated by `commonTest` unit tests using an in-memory driver — no
device, emulator, or real audio files required.

## Technical Context

**Language/Version**: Kotlin 2.4.10 (Kotlin Multiplatform), JVM target 11 for Android

**Primary Dependencies**: SQLDelight 2.x (local database + coroutines extension),
kotlinx-coroutines-core (Flow-based observation), kotlinx-serialization-json (seed-content
parsing), Koin 4.x (dependency injection). Compose Multiplatform is already present for later
UI phases but is **not** used by this phase's domain/data code.

**Storage**: SQLDelight (SQLite) local database — the single source of truth for content in
this phase. Platform drivers via `expect`/`actual`: `AndroidSqliteDriver` (Android),
`NativeSqliteDriver` (iOS), `JdbcSqliteDriver`/in-memory (commonTest).

**Testing**: `kotlin.test` in `commonTest`, executed against an in-memory SQLDelight driver.
No device, emulator, or audio binaries. Optional host-test run on Android JVM.

**Target Platform**: Android (minSdk 24, compileSdk 36) + iOS (iosArm64, iosSimulatorArm64),
single shared source in `commonMain`.

**Project Type**: Mobile — Kotlin Multiplatform shared library (`:shared`) consumed by
`:androidApp` and `iosApp`. This phase touches only `:shared`.

**Performance Goals**: Read the full verse list of a matn of up to 500 verses in under 1
second on a typical mid-range device (SC-006). All reads succeed with zero network requests
(SC-005).

**Constraints**: Fully offline; Arabic text (including all diacritics تَشْكِيل) must
round-trip byte-for-byte with no normalization loss (FR-007/SC-003); content-authored UUIDs
persisted as-is (FR-002); atomic-reject load on any integrity violation (FR-019/SC-008);
per-verse micro-audio-file model locked (FR-013).

**Scale/Scope**: Domain + data layers only. ~4 domain entities, ~1 SQLDelight schema, a small
set of repository interfaces/impls, a seed-loader with integrity validation, and unit tests.
Content scale target: matn up to ~500 verses; a handful of seeded متون.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies to Phase 0? | How this plan complies |
|-----------|---------------------|------------------------|
| **I. Clean Architecture & Layer Boundaries** | Yes (domain + data) | Domain layer = pure Kotlin entities + repository **interfaces** + Result type in `commonMain`, zero framework/platform imports. Data layer = SQLDelight-backed repository implementations + seed loader, depending inward on the interfaces. No presentation layer in this phase. Dependency direction domain ← data upheld. |
| **II. MVVM Presentation (NON-NEGOTIABLE)** | No | Phase 0 has **no UI** (FR-018). No ViewModels/Composables are created. No violation possible; MVVM is enforced in Phase 1+. |
| **III. DRY via Base Abstractions** | Yes | A shared `Result`/`Resource` sealed type wraps all fallible ops (load, read). Row→entity mapping logic is single-sourced in one `mapper/` file (no copy-paste). A dedicated base *repository* contract is **deferred**: with only three small read repositories in this phase, Principle III's "where applicable" latitude applies; a base type is introduced when a fourth repository or a shared CRUD/observe surface first justifies it. |
| **IV. Shared-First Multiplatform** | Yes | All domain + data logic lives in `commonMain`. `expect`/`actual` is used **only** for the SQLDelight `SqlDriver` factory (a genuine platform capability); the `actual`s contain no business logic. |
| **V. Test-First & Testable Design (NON-NEGOTIABLE)** | Yes | Repository/seed-loader behavior specified by the spec's acceptance scenarios is covered by `commonTest` unit tests against an in-memory driver. All collaborators injected via interfaces (Koin). No device/audio needed. Tests land in the same change as the code. |
| **VI. Offline-First & Future-Proof Data** | Yes (**core of this phase**) | Every entity carries a stable content-authored `UUID` independent of row/display order. Local store is the sole source of truth; no network. Schema reserves room for multi-reciter audio, sync, accounts, and later-phase entities (bookmarks/notes/progress) without blocking assumptions (FR-016). |
| **VII. Experience Fidelity: Audio, RTL & Accessibility** | Partially | Playback, RTL, and accessibility are UI/audio concerns of later phases and are out of scope (FR-018). The one binding invariant this phase owns — the **per-verse micro-audio-file asset model** — is locked into the schema and the Audio Asset entity (FR-013/FR-015). |

**Additional constraints**
- **Stack** matches the constitution: KMP + SQLDelight, base package `com.giraffe.matn`, DI required.
- **DI**: Koin wires repositories, the seed loader, and the driver factory across layers — no manual singletons reached across boundaries.
- **New dependencies** (SQLDelight, coroutines, serialization, Koin) are each justified in `research.md` against simpler alternatives.

**Result**: ✅ PASS — no violations. Complexity Tracking table below is empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-foundation-data-model/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output — tech decisions & rationale
├── data-model.md        # Phase 1 output — entities, schema, relationships
├── quickstart.md        # Phase 1 output — how to validate the foundation
├── contracts/           # Phase 1 output — repository + seed contracts
│   ├── repositories.md   # MatnRepository / VerseRepository / AudioAssetRepository contracts
│   └── seed-content.md    # Seed JSON schema + integrity rules
├── checklists/          # (pre-existing)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

Existing KMP layout (`:shared` consumed by `:androidApp` + `iosApp`). This phase adds a
domain/data package tree under the existing `com.giraffe.matn` base package and a SQLDelight
schema. No presentation code.

```text
shared/
├── build.gradle.kts                      # + SQLDelight, coroutines, serialization, Koin
├── src/
│   ├── commonMain/
│   │   ├── sqldelight/com/giraffe/matn/db/
│   │   │   └── Content.sq                 # Matn/Chapter/Verse/AudioAsset tables + queries
│   │   └── kotlin/com/giraffe/matn/
│   │       ├── core/
│   │       │   └── Resource.kt            # shared Result/Resource sealed type + AppError (DRY)
│   │       ├── domain/
│   │       │   ├── model/                 # Matn, Chapter, Verse, AudioAsset, StructureKind
│   │       │   ├── repository/            # MatnRepository, VerseRepository,
│   │       │   │                          #   AudioAssetRepository (interfaces)
│   │       │   └── error/                 # ContentIntegrityError (duplicate order / missing audio)
│   │       ├── data/
│   │       │   ├── db/                    # DatabaseDriverFactory (expect), DatabaseBuilder
│   │       │   ├── mapper/                # row → domain mappers
│   │       │   ├── repository/            # SQLDelight-backed repository implementations
│   │       │   └── seed/                  # SeedContentLoader (atomic validate + upsert),
│   │       │                              #   kotlinx.serialization DTOs
│   │       └── di/
│   │           └── ContentModule.kt       # Koin module wiring
│   ├── androidMain/kotlin/com/giraffe/matn/data/db/
│   │   └── DatabaseDriverFactory.android.kt  # actual — AndroidSqliteDriver (no business logic)
│   ├── iosMain/kotlin/com/giraffe/matn/data/db/
│   │   └── DatabaseDriverFactory.ios.kt      # actual — NativeSqliteDriver (no business logic)
│   └── commonTest/kotlin/com/giraffe/matn/
│       ├── db/TestDatabase.kt                 # inMemoryDriver() expect for tests
│       ├── data/repository/                    # round-trip, ordering, offline read tests
│       ├── data/seed/                          # atomic-reject, reload-dedup, integrity tests
│       └── fixtures/                           # sample simple + structured matn JSON
```

**Structure Decision**: Kotlin Multiplatform shared library. All new domain/data code lives in
`shared/src/commonMain/kotlin/com/giraffe/matn/{core,domain,data,di}`, the SQLDelight schema in
`shared/src/commonMain/sqldelight/...`, platform driver `actual`s in `androidMain`/`iosMain`,
and tests in `commonTest`. The existing sample files (`Greeting*.kt`, `App.kt`) are untouched
by this phase; no new module is introduced.

## Complexity Tracking

> No Constitution Check violations — this table is intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
