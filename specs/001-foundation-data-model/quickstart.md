# Phase 0 Quickstart: Validating the Foundation

This phase ships **no UI**. It is validated entirely through `commonTest` unit tests running
against an in-memory SQLDelight driver — no device, emulator, network, or real audio files.
This guide shows how to build the module and run the validation scenarios that prove the
foundation works end to end. Entity/schema/contract details are in `data-model.md` and
`contracts/`; this file is the run guide only.

## Prerequisites

- JDK 17+ and the repo's Gradle wrapper (`./gradlew` / `gradlew.bat`).
- Xcode + a configured iOS simulator only if you also want to run the iOS test target
  (`iosSimulatorArm64Test`). Not required to validate the shared logic.
- No network connection is required (that is part of what SC-005 verifies).

## Build the shared module

```bash
# from repo root
./gradlew :shared:assemble
```

A successful assemble confirms the KMP module (with the new SQLDelight schema, coroutines,
serialization, and Koin dependencies) compiles for all configured targets — satisfying the
constitution's "every phase is independently buildable" rule.

## Run the validation test suite

```bash
# Common shared logic (JVM host) — the primary gate
./gradlew :shared:testDebugUnitTest        # Android host tests, or:
./gradlew :shared:allTests                 # all configured test targets

# iOS simulator target (optional, macOS only)
./gradlew :shared:iosSimulatorArm64Test
```

All content-repository and seed-loader tests must pass.

## Validation scenarios (each maps to a spec success criterion)

Run the suite above; the following scenarios are the acceptance checks it must contain. See
`contracts/repositories.md` for the full traceability table.

| # | Scenario | Setup | Expected result | Requirement |
|---|----------|-------|-----------------|-------------|
| 1 | **Simple matn round-trip** | Load `simple_matn.json` | Matn retrievable by id; all N verses returned in `display_number` order; metadata correct | US1 / SC-001 |
| 2 | **Diacritics preserved** | Load a verse with heavy تَشْكِيل, read back | `arabicText` identical byte-for-byte | FR-007 / SC-003 |
| 3 | **Structured matn** | Load `structured_matn.json` | Chapters in order; each chapter's verses grouped & ordered; full sequence coherent | US2 / SC-002 |
| 4 | **Simple matn has no chapters** | Load simple matn, query chapters | Empty list, **not** an error | FR-012 |
| 5 | **Audio resolves 1:1** | For each verse, resolve audio | Exactly one asset; no two verses share a `fileRef` | US3 / SC-004 |
| 6 | **Multi-reciter ready** | Inspect audio model | A second `reciterId` maps to the same verse id with no schema change | FR-015 |
| 7 | **Atomic reject: duplicate order** | Load `invalid_duplicate_order.json` | `Failure(DuplicateDisplayNumber…)`; **zero** rows persisted for that matn | FR-019 / SC-008 |
| 8 | **Atomic reject: missing audio** | Load `invalid_missing_audio.json` | `Failure(MissingAudio…)`; **zero** rows persisted | FR-019 / SC-008 |
| 9 | **Reload dedup** | Load the same matn twice | No duplicate matn/chapter/verse rows; identical stable ids | FR-009 / SC-007 |
| 10 | **Offline reads** | Run entire suite | All reads succeed; the module has no network dependency to make a request | FR-004/FR-017 / SC-005 |
| 11 | **Not found** | Query an unloaded matn id | `Success(null)` — clear not-found, no partial object, no throw | Edge case |
| 12 | **Independent متون** | Load simple + structured into one store | Each retrievable independently, no interference | US2 scenario 4 |

## Performance check (SC-006)

A test (or a simple timed harness) loads a ~500-verse matn and reads its full verse list;
the read must complete well under **1 second** against the in-memory driver. The
`verse_by_matn_order` index backs this. On-device confirmation is a later, optional step —
the requirement is a mid-range-device budget, and the indexed query is the mechanism that
meets it.

## Done when

- `:shared` assembles for all targets.
- The full `commonTest` suite (scenarios 1–12) passes.
- No test requires a device, network, or real audio binary.
