# Implementation Plan: Phase 11 — Teacher Authoring Tool: Foundation & Upload

**Branch**: `011-teacher-authoring-upload` (git: `feature/011-teacher-authoring-upload`) | **Date**: 2026-07-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/011-teacher-authoring-upload/spec.md`

## Summary

Stand up `:teacherApp` — a JVM/Compose Desktop authoring client — plus the shared backend client
every later phase depends on, so the teacher can create, correct, publish, and withdraw the
**textual** corpus without a developer or a release. Three pieces of work:

1. **Shared backend client** (`:shared/commonMain`): one Ktor-based REST layer over Supabase's
   PostgREST, Storage, and Auth surfaces, behind domain repository interfaces. No platform SDK,
   because none covers desktop JVM.
2. **Shared catalog domain** (`:shared/commonMain`): a `MatnDraft` domain aggregate, the Postgres
   schema that mirrors `SeedMatn` field-for-field, and `ContentIntegrityValidator` —
   extracted out of `ContentSeedLoaderImpl.validate()` so the tool and the app's ingestion path
   enforce one rule set rather than two.
3. **Teacher client** (`:teacherApp`): sign-in, portal shell, catalog list, matn editor
   (metadata + chapters + verse text with drag reorder), bulk import, validation report, and
   publish/unpublish — bilingual Arabic/English with mirrored layout, on the shared Phase 10 tokens.

No audio, and no student-client change: `:androidApp`, `:iosApp`, and `:desktopApp` compile and
behave exactly as before.

## Technical Context

**Language/Version**: Kotlin 2.4.10; JVM 11 bytecode target (matches `:shared`'s Android target),
Compose Multiplatform 1.11.1 / Material 3 1.11.0-alpha07

**Primary Dependencies**:
- **New**: Ktor **3.2.3** client — `client-core`, `client-content-negotiation`,
  `serialization-kotlinx-json`, the `client-cio` engine, and `client-mock` in test source sets.
  **One engine for every target**: CIO is published for JVM, Android, and all three iOS variants, so
  there is no `expect`/`actual` engine seam — a single `commonMain` `HttpClientFactory`. All of these
  are already resolved in the local Gradle cache at 3.2.3, so no version hunting at implementation
  time.
- **New**: `net.java.dev.jna:jna-platform:5.6.0` — Windows DPAPI (`Crypt32Util`) for FR-003a's
  credential-at-rest requirement. Already present transitively in the desktop graph; this phase
  declares it explicitly.
- **Reused**: Koin 4.2.1 + Koin Annotations, kotlinx-serialization-json 1.7.3,
  kotlinx-coroutines 1.9.0, kotlinx-datetime, Compose Multiplatform, the Phase 10 token set
  (`presentation/theme/*`), `BaseViewModel`, `UseCase`/`FlowUseCase`, `Resource`/`AppError`.
- **Not used**: any Firebase SDK, any Supabase client SDK, any third-party PostgREST wrapper.

**Storage**:
- **Remote, authoritative**: Postgres table `public.matns` — one row per matn holding metadata,
  chapters, and verses; Supabase Storage `matns/{matnId}/cover.<ext>` for cover images.
- **Local**: none for content. The only thing on the teacher's disk is the session credential
  (OS credential store, FR-003a) and the interface-language preference. `:teacherApp` does **not**
  open the SQLDelight `ContentDatabase` — that is the student app's local source of truth.

**Testing**:
- `commonTest` (`kotlin.test` + `kotlinx-coroutines-test`): `ContentIntegrityValidator` rule-by-rule,
  row encode/decode round-trips, `SeedMatn` ↔ `MatnDraft` projection, verse
  reorder/renumber pure functions, the import parser, and the autosave scheduler on virtual time.
- `jvmTest`: REST clients against Ktor `MockEngine` (request shape, error mapping, token refresh);
  `JvmSecretStore` round-trip.
- `jvmTest`, stack-gated: **row-level-security policy** tests (FR-036) driven through the same Ktor
  client against a local `supabase start` stack, skipped when `SUPABASE_TEST_URL` is unset so
  ordinary CI stays green.
- `@Preview` per Principle II, and per FR-006a **two** previews for every teacher screen — one
  Arabic/RTL, one English/LTR.

**Target Platform**: `:teacherApp` — desktop JVM only (Windows/macOS/Linux via Compose Desktop
`TargetFormat.Msi/Dmg/Deb`). The shared REST layer is written for all five targets so Phase 13 can
use it unchanged on Android and iOS.

**Project Type**: Kotlin Multiplatform monorepo; adds a second JVM client module alongside
`:desktopApp`.

**Performance Goals**: 500-verse editor stays interactive — typing, scrolling, and drag-reorder with
no dropped-frame stutter (FR-025, SC-009); autosave never blocks input (FR-031c); sign-in and
save round-trips complete inside 2 s on a normal connection.

**Constraints**:
- **Online-only by design** — the teacher tool has no offline mode; drafts live remotely (FR-029).
  This is a deliberate, recorded deviation from Principle VI's local-source-of-truth default (see
  Complexity Tracking).
- **Bidirectional layout** — the tool mirrors both ways (FR-006b), unlike the student app, whose
  `MatnTheme` hard-forces RTL.
- **No secrets in the repo** — Supabase project config comes from a gitignored local file with a
  tracked template; no service-role key is used anywhere outside the test environment.
- **No student-visible change** — `:shared`'s existing public surface and behaviour are preserved;
  the only edit to shipped student code is the validator extraction, which must be behaviour-identical.

**Scale/Scope**: 1 new Gradle module; ~7 teacher screens + portal shell; 3 new domain aggregates
(`MatnDraft`, `CatalogEntry`, `TeacherSession`); 3 new domain interfaces (`CatalogRepository`,
`TeacherAuthRepository`, `SecretStore`); 10 new use cases; 2 Postgres tables; 1 migration set;
1 extraction refactor inside existing shipped code.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Clean Architecture & Layer Boundaries** — PASS with one design rule. Teacher domain models,
  repository **interfaces**, the validator, and use cases go in `:shared/commonMain/domain`; the
  Ktor REST implementations in `:shared/commonMain/data/remote`; UI and platform edges in
  `:teacherApp`. **The validator must operate on the domain `MatnDraft`, not on
  `data.seed.SeedMatn`** — a domain-layer validator referencing a data-layer DTO would invert the
  dependency arrow. `ContentSeedLoaderImpl` becomes the adapter that maps `SeedMatn → MatnDraft` and
  calls the shared validator.
  **A second design rule, on DI**: teacher-side classes added to `:shared` MUST NOT carry Koin
  annotations. `di/ContentModule.kt` declares `@ComponentScan("com.giraffe.matn")`, and the student
  apps use that module — so an annotated `SupabaseCatalogRepository` or `SupabaseAuthClient` would
  make every student client's graph demand a `SupabaseConfig` and an `HttpClient` it cannot supply.
  The Koin compiler plugin reports that as a hard `KOIN-D001` **build** error, not a runtime one, per
  `ContentModule`'s own KDoc. `:teacherApp`'s module constructs them with explicit provider functions
  — the pattern `PlatformModule` already uses for platform singletons. This is the most likely way to
  accidentally violate FR-044, so `tasks.md` carries it as Ground Rule 11 plus a build gate (T030a).
- **II. MVVM Presentation (NON-NEGOTIABLE)** — PASS, with attention at design time. Every teacher
  screen is a stateless content composable over an immutable state `data class` plus a thin holder
  that collects the `StateFlow`. The editor is the risk: 500 verse rows must not put mutable text
  state in the composable. State stays in the ViewModel; rows receive `(verse, onTextChange)`.
  Previews: loaded / empty / error, each in both languages.
- **III. DRY via Base Abstractions** — PASS, and this phase *reduces* duplication. `BaseViewModel`,
  `UseCase`/`FlowUseCase`, and `Resource`/`AppError` are reused unchanged; the validator extraction
  removes the only place where a second copy of the integrity rules would otherwise have been
  written. One `MatnRow` mapping serves both the full read and the overview projection rather than two parsers.
- **IV. Shared-First Multiplatform** — PASS. REST clients, DTOs, the row mapping, validator,
  import parser, reorder/renumber arithmetic, autosave policy, and every use case live in
  `commonMain`. `:teacherApp`'s JVM code is confined to genuine platform edges: HTTP engine,
  credential store, file chooser, file bytes, window/main. No business logic there.
- **V. Test-First & Testable Design (NON-NEGOTIABLE)** — PASS. Every rule, projection, parser, and
  scheduler is a pure function or takes injected collaborators (clock, HTTP engine, secret store),
  so all of it is testable in `commonTest` with no network, emulator, or device. The validator
  extraction must land with the existing loader tests passing **unchanged** — that is the proof the
  refactor is behaviour-preserving.
- **VI. Offline-First & Future-Proof Data** — PARTIAL, justified. UUID identity (FR-008) and
  schema future-proofing (FR-013) hold. The principle's "local persistence is the source of truth /
  must function fully with no network" is scoped to student content on a student device; the
  producer tool inverts it deliberately. Recorded in Complexity Tracking.
- **VII. Experience Fidelity: Audio, RTL & Accessibility** — PASS. No audio is touched (FR-045).
  RTL is *extended*, not weakened: `MatnTheme` gains an optional `layoutDirection` parameter
  defaulting to `LayoutDirection.Rtl`, so all 88 existing previews and every student screen are
  byte-identical in behaviour while `:teacherApp` can pass `Ltr` for English. Accessibility labels
  follow the existing `A11yLabels` pattern.
- **VIII. Design Fidelity & Reusable Composables (NON-NEGOTIABLE)** — PASS with two obligations.
  (a) The Stitch screen *Upload Matn (Per-Verse)* (`4f1bee3d7518487b986c7c63cb3c07ff`) MUST be
  fetched through the `stitch` MCP server before any editor UI is written; its verse-list/text
  regions are this phase's, its audio column is Phase 12's. The producer chrome visible in *Search
  Matn* is the portal nav reference, per `docs/DESIGN-SOURCE.md` open issue #7. (b) The Arabic,
  mirrored form of that chrome is **not** in the captured design and is original work — it must be
  recorded in `design-notes.md` as a deviation, as Phases 6–9 did. Tokens only: no raw hex, no magic
  `.dp`/`.sp` in `:teacherApp`.

**Technology & Architecture Constraints**
- **Stack list** — adding `:teacherApp` requires updating the constitution's client-module list in
  the same PR. The constitution already names `teacherApp` under *Producer client*, so this is a
  no-op confirmation rather than an edit.
- **Per-verse audio lock** — untouched; FR-045 forbids storing audio or any timestamp offset.
- **Dependency justification** — Ktor and `jna-platform` are new. Both are itemized in Complexity
  Tracking, discharging the constitution's outstanding deferred TODO (a) for Phase 11.

**Gate result: PASS.** One partial (Principle VI) with a recorded justification; no unjustified
violations.

## Project Structure

### Documentation (this feature)

```text
specs/011-teacher-authoring-upload/
├── plan.md              # This file
├── research.md          # Phase 0 output — 14 decisions
├── data-model.md        # Phase 1 output — domain aggregates + Postgres schema
├── quickstart.md        # Phase 1 output — runnable validation walkthrough
├── contracts/           # Phase 1 output
│   ├── postgres-schema.md       # table shape, overview columns, indexes
│   ├── rls-policies.md          # policy text + the FR-036 test matrix
│   ├── rest-contract.md         # the three REST surfaces + error mapping
│   ├── validation-contract.md   # rule set, blocking vs deferred
│   └── teacher-ui-contract.md   # screens, states, bilingual/mirroring rules
├── checklists/
│   └── requirements.md  # spec quality checklist (16/16)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
settings.gradle.kts                       # + include(":teacherApp")
gradle/libs.versions.toml                 # + ktor 3.2.3 libs, jna-platform

shared/src/commonMain/kotlin/com/giraffe/matn/
├── domain/
│   ├── catalog/                          # NEW
│   │   ├── MatnDraft.kt                  #   draft aggregate (+ DraftChapter, DraftVerse)
│   │   ├── PublicationState.kt           #   DRAFT | PUBLISHED
│   │   ├── AudioCompleteness.kt          #   NONE | PARTIAL | COMPLETE (derived)
│   │   ├── CatalogEntry.kt               #   overview projection (FR-012)
│   │   ├── ContentIntegrityValidator.kt  #   EXTRACTED from ContentSeedLoaderImpl.validate()
│   │   ├── ValidationReport.kt           #   blocking vs deferred split (FR-027/FR-028)
│   │   ├── VerseOrdering.kt              #   pure move/renumber arithmetic (FR-020)
│   │   ├── VerseTextImport.kt            #   pure parser (FR-023a/FR-023b)
│   │   └── CatalogRepository.kt          #   interface
│   ├── auth/                             # NEW
│   │   ├── TeacherSession.kt
│   │   └── TeacherAuthRepository.kt      #   interface
│   ├── secret/SecretStore.kt             # NEW interface (FR-003a)
│   ├── error/RemoteError.kt              # NEW — Network/Unauthorized/Conflict/Quota/Server/Decode
│   ├── catalog/MatnDraftFactory.kt       #   (also NEW) the only place a matn is created
│   └── usecase/                          # NEW: SignIn, SignOut, RestoreSession,
│                                         #   ListAuthoredMatns, LoadMatnForEdit, SaveDraft,
│                                         #   ValidateMatn, PublishMatn, UnpublishMatn,
│                                         #   ImportVerseText, UploadCoverImage
├── data/
│   ├── remote/                           # NEW
│   │   ├── HttpClientFactory.kt          #   commonMain only — CIO on every target
│   │   ├── SupabaseConfig.kt
│   │   ├── postgrest/                    #   PostgrestClient, MatnRow
│   │   ├── storage/StorageRestClient.kt
│   │   ├── auth/                         #   SupabaseAuthClient, TokenRefresher
│   │   └── RemoteErrorMapper.kt
│   ├── repository/
│   │   ├── SupabaseCatalogRepository.kt  # NEW — implements CatalogRepository
│   │   └── SupabaseTeacherAuthRepository.kt # NEW
│   └── seed/
│       ├── ContentSeedLoaderImpl.kt      # CHANGED — validate() delegates to shared validator
│       └── SeedMatnProjection.kt          # NEW — MatnDraft ↔ SeedMatn (Phase 13 seam)
└── presentation/theme/MatnTheme.kt       # CHANGED — optional layoutDirection param, default Rtl

teacherApp/                               # NEW module (JVM, Compose Desktop)
├── build.gradle.kts
└── src/
    ├── main/kotlin/com/giraffe/matn/teacher/
    │   ├── TeacherMain.kt                # main(), Koin boot, window
    │   ├── di/TeacherModule.kt
    │   ├── platform/
    │   │   ├── JvmSecretStore.kt         # DPAPI / security(1) / secret-tool / 0600 fallback
    │   │   ├── JvmFileChooser.kt         # cover image + import file
    │   │   └── JvmLanguagePreference.kt  # persisted interface-language choice
    │   └── presentation/
    │       ├── strings/                  # TeacherStrings (ar/en) + LocalTeacherStrings
    │       ├── shell/                    # portal nav, identity, storage row, language switch
    │       ├── signin/                   # US1
    │       ├── library/                  # US5 — catalog list
    │       ├── editor/                   # US2 + US3 — metadata, chapters, verse list
    │       ├── importer/                 # US6 — preview dialog
    │       └── publish/                  # US4 — validation report, publish/unpublish
    └── test/kotlin/com/giraffe/matn/teacher/   # JVM-edge tests

supabase/                                 # NEW — schema and policies live in the repo, secret-free
├── migrations/                            #   tables, RLS policies, storage bucket constraints
├── config.toml                            #   local stack for the policy tests
└── supabase.local.properties.template     # url/anonKey/bucket; real file gitignored
```

**Structure Decision**: `:teacherApp` is a plain `kotlinJvm` + Compose Desktop module mirroring the
existing `:desktopApp`, not a KMP module — it has exactly one target. Everything reusable is pushed
down into `:shared/commonMain` so Phase 13's student consumer inherits the REST layer, the schema,
and the validator without a rewrite; `:teacherApp` keeps only its own UI and the four platform
edges. Teacher code does not enter `:shared`'s presentation layer, which keeps it out of every
student binary and satisfies the constitution's "MUST NOT be reachable from any student client".

Because `:shared` declares its Compose and lifecycle dependencies as `implementation`, they are not
exposed transitively. `:teacherApp` therefore declares `compose.desktop.currentOs`,
`androidx-lifecycle-viewmodelCompose`, `androidx-lifecycle-runtimeCompose`, `koin-core`, and
`koin-annotations` itself — same as `:desktopApp` does today. `:shared`'s dependency exposure is
left untouched.

## Phase 0 — Research

See [research.md](./research.md). Fourteen decisions, the load-bearing ones being:

| # | Question | Decision |
|---|----------|----------|
| D1 | Backend access without a desktop SDK | Ktor 3.2.3 REST client in `commonMain`, one implementation for all five targets |
| D2 | Row layout | One row per matn; chapters and verses as `jsonb` arrays; overview reads select scalar columns only |
| D3 | Atomic writes across row + cover image | Upload image first, then write the row; an orphaned image is inert |
| D4 | Conflict detection (FR-037/FR-043) | Trigger-bumped `revision` column as an update filter; zero rows matched → `RemoteError.Conflict` |
| D5 | Runtime language switching | **Cannot** use compose-resources: `LocalComposeEnvironment` is `internal` and `ResourceEnvironment`'s constructor is `internal` in 1.11.1. Own `TeacherStrings` table via `CompositionLocal` instead |
| D6 | Bidirectional layout without forking the theme | Add `layoutDirection` param to `MatnTheme`, default `Rtl` |
| D7 | Credential at rest on JVM | `SecretStore` interface; Windows DPAPI via `jna-platform`, macOS `security(1)`, Linux `secret-tool`, permission-restricted file fallback with a visible warning |
| D8 | Validation reuse without a layer inversion | Extract to a domain validator over `MatnDraft`; loader becomes an adapter |
| D9 | Policy testing (FR-036) | Local Supabase stack + the project's own Ktor client from `jvmTest`; env-gated |
| D10 | Row encoding | Plain kotlinx-serialization DTOs — PostgREST returns ordinary JSON, so no wrapper codec is needed |
| D11 | Drag-reorder on Desktop | Hand-rolled handle drag over `LazyColumn`; the index arithmetic is a pure `commonMain` function |
| D12 | Autosave | Debounce 5 s idle + 60 s hard ceiling, injected clock, virtual-time tested; drafts only |
| D13 | Config without secrets in the repo | Gitignored `supabase.local.properties` + tracked template + env override |
| D14 | Provisioning the teacher without an Admin SDK | `public.teachers` marker row created by hand in the dashboard; policies check existence |

## Phase 1 — Design & Contracts

Artifacts produced:

- **[data-model.md](./data-model.md)** — `MatnDraft` / `DraftChapter` / `DraftVerse` /
  `CatalogEntry` / `TeacherSession`, their invariants, the derived `AudioCompleteness`, the
  publication-state machine, and the field-for-field `SeedMatn` correspondence table that discharges
  FR-009.
- **[contracts/postgres-schema.md](./contracts/postgres-schema.md)** — the `public.matns` table,
  the `public.teachers` marker, the column subset for overview reads, indexes, and the size budget
  for a 500-verse matn.
- **[contracts/rls-policies.md](./contracts/rls-policies.md)** — the row-level-security policy text
  plus the FR-036 test matrix: every permitted and refused case, named.
- **[contracts/rest-contract.md](./contracts/rest-contract.md)** — the three REST surfaces
  (sign-in, token refresh, PostgREST rows, Storage upload), request/response shapes, and the
  HTTP-status → `RemoteError` mapping that FR-005's retryable/not-retryable messaging depends on.
- **[contracts/validation-contract.md](./contracts/validation-contract.md)** — the rule set with
  each rule marked blocking or deferred, the exact behaviour-preservation contract for the
  `ContentSeedLoaderImpl` extraction, and the mapping from error type to teacher-facing message.
- **[contracts/teacher-ui-contract.md](./contracts/teacher-ui-contract.md)** — screen inventory,
  per-screen states, the stateless/holder split, the bilingual + mirroring rules, and the required
  preview matrix.

**Agent context update**: `.specify/scripts/powershell/` provides no agent-context script in this
repo (only `check-prerequisites`, `common`, `create-new-feature`, `setup-plan`, `setup-tasks`), so
that step is a no-op here. `CLAUDE.md` is not present at the repo root; project guidance lives in
`.specify/memory/constitution.md` and `docs/`, both already current.

### Post-design Constitution re-check

Re-evaluated after the artifacts above were written; result unchanged — **PASS**, same single
justified partial. Two design decisions were specifically chosen to keep it that way:

- **D8** moves the validator to the domain layer over a domain model, avoiding the Principle I
  inversion that a `SeedMatn`-shaped validator in `domain/` would have caused.
- **D6** parameterizes the existing theme rather than forking a teacher token set, which would have
  violated Principle VIII's single-token-layer rule.

One post-design note for `/speckit-tasks`: the validator extraction (D8) touches shipped student
code, so it must be sequenced **first**, land with the existing loader tests untouched and passing,
and be reviewable as a standalone behaviour-preserving refactor.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| **New dependency: Ktor 3.2.3 client** (core, content-negotiation, kotlinx-json, CIO engine, mock for tests) | The project's first HTTP client. Every backend operation in Phases 11–13 needs one, and it must work on desktop JVM, Android, and iOS from a single `commonMain` implementation. | Firebase and Supabase both ship nothing for desktop JVM, so they would leave `:teacherApp` and `:desktopApp` unserved and split backend access into three divergent paths. Hand-rolling on `java.net.http` would be JVM-only and re-orphan Android/iOS for Phase 13. Ktor is the only KMP HTTP client with first-party engines for all five targets, and 3.2.3 is already resolved in the local Gradle cache. |
| **New dependency: `net.java.dev.jna:jna-platform:5.6.0`** | FR-003a requires the session credential in the OS credential store. On Windows that means DPAPI, reached through `Crypt32Util`. | A dedicated keyring library (e.g. `java-keyring`) pulls the same JNA transitively plus an unmaintained wrapper. Shelling out to three per-OS CLIs for all platforms means three fragile subprocess paths, including on Windows where no equivalent CLI exists. JNA is already in the desktop dependency graph at this exact version; this only declares it. macOS and Linux still use their native CLIs, where they are first-class and dependency-free. |
| **Principle VI partial: remote source of truth, online-only tool** | Drafts must be durable, machine-independent, and reachable from wherever the teacher works; a local-first producer would need sync and merge, which the spec explicitly excludes. FR-029 requires remote drafts. | A local-first teacher tool with background sync means conflict-resolution machinery for a single-user tool — the very complexity the "single teacher" scope exists to avoid. Principle VI's offline guarantee protects the *student's* downloaded library on a *student's* device (constitution 2.0.0 restates this explicitly); it was never a claim about the producer client. The student-facing guarantee is untouched by this phase. |
| **Second JVM client module (`:teacherApp`) alongside `:desktopApp`** | The constitution's Stack section already designates `teacherApp` as the producer client and requires it be unreachable from any student client. | Adding teacher screens to `:desktopApp` would ship authoring UI inside a student binary and make "unreachable from any student client" unenforceable. Putting teacher presentation code in `:shared` would compile it into every student app on every platform. |
