# Phase 0 — Research: Teacher Authoring Tool: Foundation & Upload

**Feature**: `specs/011-teacher-authoring-upload` | **Date**: 2026-07-26

Fourteen decisions. Each records what was chosen, why, and what was rejected. Findings marked
**verified** were confirmed against files in this repo or artifacts in the local Gradle cache
rather than recalled.

---

> **Backend superseded, 2026-07-31.** These decisions were taken against Firebase. The backend has
> since moved wholesale to Supabase — see `design-notes.md` § *T-backend-swap*. The decisions
> themselves still hold; what changed is the provider each one is discharged against:
>
> | Decision | Now discharged by |
> |----------|-------------------|
> | D1 Backend access with no desktop SDK | Supabase has no desktop-JVM SDK either; the Ktor REST client is unchanged |
> | D2 Document layout | `public.matns`, `chapters`/`verses` as `jsonb` |
> | D3 Ordering vs the cover image | Unchanged — upload first, then write the row |
> | D4 Conflict detection | Trigger-bumped `revision` column instead of `updateTime` |
> | D9 Rules testing | Local Supabase stack instead of the Firebase emulator |
> | D10 Value encoding | Withdrawn — PostgREST returns ordinary JSON, so no wrapper codec exists |
> | D13 Config without secrets | `supabase.local.properties` instead of `firebase.local.properties` |
> | D14 Provisioning the teacher | `public.teachers` row instead of a `teachers/{uid}` document |
>
> D5–D8 and D11–D12 are provider-independent and are untouched. The current wire contracts are
> [contracts/postgres-schema.md](./contracts/postgres-schema.md),
> [contracts/rls-policies.md](./contracts/rls-policies.md), and
> [contracts/rest-contract.md](./contracts/rest-contract.md).

---

## D1 — Backend access with no desktop Firebase SDK

**Decision**: One Ktor **3.2.3** HTTP client in `:shared/commonMain`, wrapping three REST surfaces:
Identity Toolkit (sign-in, token refresh), Firestore (documents), and Cloud Storage (cover images).
**One engine, no `expect`/`actual`**: CIO is published for JVM, Android, and all three iOS variants,
so a single `commonMain` `createHttpClient()` serves every target. An engine seam would have to
supply an `actual` in `androidMain` and `iosMain` on day one or fail to compile — cost with no
benefit while every target can share one engine.

**Rationale**: Firebase publishes no client SDK for desktop JVM, so a platform-SDK approach cannot
serve `:teacherApp` or `:desktopApp` at all, and would fracture backend access into three
implementations that then drift. One REST implementation in `commonMain` covers all five targets and
matches the repo's established "domain interface, one implementation" shape (`ContentDeliveryEngine`,
`AudioEngine`). **Verified**: every artifact needed is already in the local Gradle cache at 3.2.3 —
`ktor-client-core`, `-cio`, `-content-negotiation`, `-auth`, `-logging`, `-mock`, and
`ktor-serialization-kotlinx-json`, with JVM, iosArm64, iosSimulatorArm64, and iosX64 variants — so
the version needs no discovery and the build will resolve offline.

**Alternatives considered**:
- *Firebase Android/iOS SDKs + a hand-rolled desktop path*: three code paths, one of which is the
  REST client anyway. Strictly worse than writing that path once.
- *`java.net.http.HttpClient`*: JVM-only. Would re-orphan Android and iOS in Phase 13.
- *A third-party Firestore-REST wrapper (e.g. `dev.gitlive:firebase-*`)*: GitLive wraps the platform
  SDKs, so it inherits the desktop gap it was meant to close. Others are unmaintained.
- *Ktorfit*: **verified** present in the Gradle cache, but it generates typed clients from
  annotations, adding a codegen step for four endpoints. Not worth the build complexity.

---

## D2 — Firestore document layout

**Decision**: One document per matn at `matns/{matnId}`, with `chapters` and `verses` as arrays of
maps inside it. Catalog overview fields (`title`, `author`, `description`, `coverUrl`, `verseCount`,
`declaredSizeBytes`, `published`, `audioCompleteness`, timestamps) sit at the top level so a reader
can fetch them with a **field mask** and never download verse text.

**Rationale**: FR-007 specifies a single record, and it makes FR-033's all-or-nothing write free — a
single-document write is atomic in Firestore with no transaction needed. Size holds comfortably: a
500-verse matn at ~120 Arabic characters per verse is ≈240 bytes of text plus a 36-byte UUID and
field-name overhead, ~400 bytes per verse, ~200 KB total against the 1 MiB document ceiling. Phase 12
adds an audio reference and duration per verse — roughly another 60 KB — still under half the limit.
Field-mask reads (`documents.get?mask.fieldPaths=title&mask.fieldPaths=author&…`) are exactly what
FR-012's "list it without downloading it" asks for, and hand Phase 13 its catalog-sync mechanism.

**Alternatives considered**:
- *Verses as a subcollection*: unlimited size and per-verse writes, but a matn is then no longer
  atomically writable without a batched commit, the overview read becomes a second round-trip, and
  Phase 13's sync turns into pagination. Cost without a matching benefit at 500 verses.
- *Verse text in a Storage blob, metadata in Firestore*: cheap reads, but verse text stops being
  queryable or rule-gated per document and every edit rewrites the blob.
- *Document per verse in a flat collection*: worst of both — N writes per save, no atomicity.

**Note for the 1 MiB ceiling**: `ValidateMatn` should include a size estimate and refuse to publish a
document projected over ~900 KB, so the failure is a clear message rather than a REST rejection. This
is a guard, not a new requirement — no realistic matn approaches it.

---

## D3 — Atomicity across the document and the cover image

**Decision**: Upload the cover image to Storage **first**, then patch the Firestore document with its
path. Never the reverse.

**Rationale**: The document is the only thing anyone reads. If the image upload succeeds and the
document write then fails, the result is an unreferenced object in Storage — invisible, harmless, and
overwritten by the next attempt at the same deterministic path (`matns/{matnId}/cover.<ext>`). The
reverse order would leave a document pointing at an object that does not exist, which is exactly the
"half-written and unreadable" state the spec's edge case forbids. FR-033 is then satisfied by
Firestore's own single-document atomicity, with no transaction and no compensating delete.

**Alternatives considered**:
- *Document first, then image*: produces the broken state described above.
- *A two-phase commit with a `pendingCover` field*: real machinery to avoid an inert orphaned blob.
- *Cover image inline as base64 in the document*: burns the size budget D2 depends on and makes every
  overview read carry the image.

---

## D4 — Concurrent-edit detection (FR-037, FR-043)

**Decision**: Carry the document's `updateTime` from the read into editor state, and send it back as
the `currentDocument.updateTime` precondition on every write. Firestore rejects a stale write with
`FAILED_PRECONDITION`, which maps to `RemoteError.Conflict`.

**Rationale**: This is server-side optimistic concurrency with no extra field, no extra read, and no
clock trust — Firestore's REST commit/patch API takes the precondition natively. It satisfies "detect
and report rather than overwrite" precisely, and it covers the two-machines edge case in the spec
without any coordination between clients.

**Alternatives considered**:
- *A `version` integer maintained by the client*: reimplements what the precondition already gives,
  and a client that forgets to increment silently loses the protection.
- *Last-write-wins*: what FR-043 exists to forbid.
- *Document locking*: needs lease expiry and stale-lock recovery for a single-teacher tool.

---

## D5 — Runtime interface-language switching (FR-006a)

**Decision**: The teacher tool does **not** use Compose resources for its own chrome. It defines its
own string table — an interface `TeacherStrings` with `ArabicStrings` and `EnglishStrings`
implementations — provided through a `staticCompositionLocalOf`, selected from the persisted language
preference. The student app's `composeResources/values*/strings.xml` are untouched.

**Rationale**: **Verified against the dependency source, not recalled** — in
`components-resources-1.11.1-sources.jar`,
`org/jetbrains/compose/resources/ResourceEnvironment.kt` declares
`class ResourceEnvironment internal constructor(...)` and
`internal val LocalComposeEnvironment = staticCompositionLocalOf { DefaultComposeEnvironment }`. Both
are `internal`, and the only public entry points are `rememberResourceEnvironment()` and
`getSystemResourceEnvironment()`, which read the system locale. There is therefore **no public API in
Compose Multiplatform 1.11.1 to override the resource locale at runtime**, so a teacher-selectable
language cannot be built on `stringResource`. A plain Kotlin string table has no such limit, is
exhaustively checked by the compiler (a missing translation is a compile error, not a runtime
fallback), previews in either language by passing a value, and is testable in `commonTest`.
**Verified**: the existing `values/strings.xml` (Arabic) and `values-en/strings.xml` each hold 138
strings, and the teacher vocabulary barely overlaps them — "Save as Draft", "Publish Matn", "Library
Management" are all new — so nothing is duplicated by keeping the two tables separate.

**Alternatives considered**:
- *`Locale.setDefault()` before composition*: JVM-only, and `rememberResourceEnvironment` will not
  recompose on it without remounting the whole UI behind a `key()`. A hack whose failure mode is
  half-translated screens.
- *Two `strings.xml` locale folders and system-locale-only chrome*: this is what the student app does,
  and it cannot satisfy "the teacher MUST be able to switch".
- *Waiting for a public locale-override API*: blocks the phase on an upstream release.

---

## D6 — Bidirectional layout without forking the theme (FR-006b)

**Decision**: Add an optional `layoutDirection: LayoutDirection = LayoutDirection.Rtl` parameter to
the existing `MatnTheme`, and provide it into `LocalLayoutDirection` instead of the current hard-coded
`Rtl`. `:teacherApp` passes the direction derived from its language preference.

**Rationale**: **Verified** — `presentation/theme/MatnTheme.kt:58` currently does
`LocalLayoutDirection provides LayoutDirection.Rtl` unconditionally, and its KDoc records that
`themeMode` and `reduceMotion` are both defaulted specifically so the 88 existing `@Preview`s keep
working untouched. A third defaulted parameter follows that established pattern exactly: every
existing call site and preview is unchanged, every student screen still forces RTL, and the teacher
tool gets LTR by passing one argument. Principle VIII forbids a second token layer, and this avoids
one.

**Alternatives considered**:
- *A separate `TeacherTheme` wrapping `MaterialTheme` directly*: duplicates the colour/type/shape
  wiring, which drifts the moment a token changes — the exact failure Principle VIII exists to
  prevent.
- *Overriding `LocalLayoutDirection` inside `:teacherApp` beneath `MatnTheme`*: works, but leaves two
  competing direction providers in one tree and confuses anything reading direction between them.
- *Mirroring by hand with `Modifier.scale(-1f, 1f)`*: reverses text too. Not a real option.

---

## D7 — Session credential at rest (FR-003a)

**Decision**: A domain `SecretStore` interface (`put`/`get`/`clear`, keyed by a name) with a JVM
implementation that dispatches per operating system:

| OS | Mechanism |
|----|-----------|
| Windows | DPAPI via `com.sun.jna.platform.win32.Crypt32Util.cryptProtectData/cryptUnprotectData`, ciphertext in the app data directory |
| macOS | `security add-generic-password` / `find-generic-password` / `delete-generic-password` |
| Linux | `secret-tool store` / `lookup` / `clear` when present on `PATH` |
| Any, no store available | File in the app data directory with owner-only permissions, and a visible in-app warning that persistence is unprotected |

**Rationale**: FR-003a names the OS credential store and permits a restricted-file fallback only
where none exists. DPAPI is the Windows credential store's own primitive, user-scoped by default, and
reachable in ~20 lines through `jna-platform`, which is **verified** already resolved at 5.6.0 in the
local Gradle cache from the existing desktop graph — so this declares an existing dependency rather
than introducing a new supply chain. macOS and Linux ship their tools with the OS, so those paths
need no dependency at all. The interface keeps all of it behind a seam that fakes trivially in tests
(Principle V) and keeps `commonMain` free of platform code (Principle IV).

**Alternatives considered**:
- *`java-keyring` or `credential-secure-storage`*: each pulls JNA anyway plus a wrapper layer, for
  code this phase needs ~60 lines of.
- *`java.util.prefs`*: plaintext in the registry or a plist. Fails FR-003a outright.
- *Encrypting the token file with a key derived from a passphrase*: reintroduces the per-launch prompt
  FR-003 exists to remove.
- *No persistence at all*: contradicts FR-003.

**Note**: only the **refresh** token is persisted. The one-hour ID token stays in memory, so a stolen
file yields a credential that the server can also be made to revoke.

---

## D8 — Reusing the integrity rules without inverting a layer (FR-026)

**Decision**: Extract the rule set from `ContentSeedLoaderImpl.validate()` into
`domain/catalog/ContentIntegrityValidator`, operating on the **domain** `MatnDraft` and returning the
existing `List<ContentIntegrityError>`. `ContentSeedLoaderImpl` keeps its signature and behaviour, and
its `validate()` becomes: map `SeedMatn → MatnDraft`, delegate, return.

**Rationale**: **Verified** — `validate()` is currently `private` inside
`data/seed/ContentSeedLoaderImpl.kt` and enforces eight rules (`InvalidId`, `DuplicateId`,
`DuplicateDisplayNumber`, `DuplicateChapterOrder`, `MissingAudio`, `DuplicateAudioRef`,
`OrphanChapterRef`, `StructureMismatch`), and `ContentIntegrityError` already lives in
`domain/error/`. FR-026 demands one rule set, not two, which means the teacher tool must call this
code, not copy it. It cannot stay in the data layer, because a domain use case (`ValidateMatnUseCase`)
would then depend on data-layer code and invert Principle I's dependency arrow — and it cannot keep
taking `SeedMatn`, a `data.seed` DTO, for the same reason. Moving the rules to the domain over a
domain model fixes both, and satisfies Principle III as a side effect.

**Behaviour-preservation contract**: the extraction must land with the existing
`ContentSeedLoader` tests **unchanged and passing**. That is the whole proof, and it is why
`/speckit-tasks` should sequence this first, as a standalone reviewable refactor before any new
feature code depends on it.

**Alternatives considered**:
- *Make `validate()` internal and call it from the teacher tool*: the layer inversion above, plus it
  couples the tool to `SeedMatn`'s Phase 8 delivery fields (`packId`, `isStarter`) which mean nothing
  to an authoring tool.
- *A second rule set in the teacher tool*: exactly what FR-026 forbids, and guarantees the two drift.
- *Validate only server-side in security rules*: rules cannot produce per-verse diagnostics, and
  FR-028 requires naming the offending verse.

---

## D9 — Testing the security rules (FR-036)

**Decision**: Firestore/Storage rule tests run against the **Firebase Local Emulator Suite**, driven
from `jvmTest` through this project's own Ktor client — the same client the app uses, pointed at the
emulator host. Tests read `FIREBASE_EMULATOR_HOST` and skip when it is unset, so ordinary
`./gradlew test` stays green on a machine without the emulator. `firebase emulators:exec` is the
documented way to run them, and CI needs one job that does.

**Rationale**: FR-036 requires the rules be *tested*, not merely written, and calls them
load-bearing — they are the only gate on student visibility. Driving them with the project's own
client means the test exercises the real request shape the app sends, so a rule that passes the test
cannot fail in production for a request-shape reason. Keeping it in `jvmTest` avoids adding a Node
toolchain and a second test runner to a Kotlin repo. The env gate honours the constitution's "CI
expectation: `commonTest` must pass before merge" without making every developer install the Firebase
CLI.

**Alternatives considered**:
- *`@firebase/rules-unit-testing` (Node)*: the official path and genuinely good, but it means npm,
  a second language toolchain, and rule tests written in a language nothing else in the repo uses.
- *Manual verification in the console*: not automated; FR-036 explicitly rules it out.
- *Testing against the real project*: writes real data, costs money, and cannot test refusals safely.

---

## D10 — Firestore Value encoding

**Decision**: A hand-rolled sealed `FirestoreValue` type (`StringValue`, `IntegerValue`,
`BooleanValue`, `TimestampValue`, `NullValue`, `ArrayValue`, `MapValue`) with encode/decode helpers
and per-type unit tests, in `data/remote/firestore/`.

**Rationale**: Firestore's REST API does not take plain JSON — every field is a typed wrapper
(`{"stringValue":"..."}`), integers are JSON **strings**, and timestamps are RFC 3339. That
encoding is a real translation layer whatever produces it. Hand-rolling is ~150 lines, reads exactly
like the wire format it mirrors, fails loudly on an unexpected type, and is the easiest thing in this
phase to test exhaustively — which matters because a silent mis-encode corrupts content. One codec
serves every document type (Principle III).

**Alternatives considered**:
- *A kotlinx-serialization custom serializer / `JsonTransformingSerializer`*: fewer lines, but the
  integer-as-string and timestamp rules end up spread across annotations and transformers, and
  debugging a mis-shaped document means reading generated behaviour instead of a function.
- *`Map<String, Any?>` with casts*: no compile-time safety at the one place in the phase where a
  wrong type silently corrupts a student's content.

---

## D11 — Drag-to-reorder on Compose Desktop (FR-020)

**Decision**: Hand-rolled: a drag handle per row (the design already shows one) using
`Modifier.pointerInput` over a `LazyColumn`, with all index arithmetic in a pure `commonMain`
function — `VerseOrdering.move(verses, from, to)` — that also renumbers display numbers.

**Rationale**: Compose has no first-party reorderable-list API, and the part that can actually be
wrong is not the gesture but the index and renumbering arithmetic after a move, an add, or a delete
(FR-020). Extracting that into a pure function makes the risky half unit-testable with no UI at all,
which is Principle V applied where it pays. The gesture layer left over is small and its failure mode
is visible immediately.

**Alternatives considered**:
- *`sh.calvin.reorderable`*: KMP and well-regarded, but it is a new dependency needing constitutional
  justification for a gesture, and it would not remove the arithmetic that needs testing anyway.
- *Up/down arrow buttons instead of dragging*: simpler, but FR-020 says drag and the design shows
  handles. Keep it as a fallback affordance if the gesture proves fiddly on one platform — it also
  happens to be more accessible, so shipping both is defensible.

---

## D12 — Autosave scheduling (FR-031a–c)

**Decision**: A `commonMain` `DraftAutosaveScheduler` taking an injected clock, a coroutine scope, and
a save lambda. Two triggers: 5 seconds of edit inactivity, and a 60-second hard ceiling since the last
successful save while changes are pending. Saves are coalesced — one in flight at a time, with the
latest state saved on completion. **Drafts only**: published matns are never autosaved (FR-031b).

**Rationale**: FR-031a sets 60 seconds as the outer bound and SC-014 measures it, so the ceiling is
the contract and the idle debounce is what makes it feel immediate without a write per keystroke. An
injected clock plus `kotlinx-coroutines-test` virtual time makes "at most 60 seconds is lost" an
actual assertion rather than a hope. The published-matn exclusion is what keeps FR-033's
never-partially-visible guarantee true while the teacher is mid-correction.

**Alternatives considered**:
- *Save every mutation*: a write per keystroke; also makes every intermediate state of a published
  matn visible to readers.
- *Idle debounce only*: a teacher typing continuously for ten minutes would never save.
- *Ceiling only*: up to 60 seconds of exposure on work that has been sitting idle and could have been
  saved instantly.

---

## D13 — Firebase configuration without secrets in the repo

**Decision**: `firebase/firebase.local.properties` (gitignored) holds `projectId`, `apiKey`, and
`storageBucket`; `firebase.local.properties.template` is tracked with the keys and empty values;
environment variables override the file for CI. The rule files themselves —
`firebase/firestore.rules`, `firebase/storage.rules`, `firebase/firebase.json` — **are** tracked,
because they are code and FR-036 makes them testable code.

**Rationale**: The constitution's tracked-config carve-out permits shared capability declarations on
the condition they hold no credentials, and requires anything that would embed a token to stay
untracked. A web API key is not a secret in the same sense — it identifies the project and is gated by
security rules — but keeping it out of the repo costs nothing and avoids arguing the distinction. No
service-account key exists anywhere in this design: the tool authenticates as the teacher, not as an
administrator, so there is no privileged credential to leak.

**Alternatives considered**:
- *Config constants in Kotlin source*: tracked, and forces a rebuild to point at the emulator.
- *A service account for the tool*: an administrator credential on a teacher's laptop, which would
  also bypass the security rules that FR-033 makes the only visibility gate.

---

## D14 — Provisioning the single teacher without an Admin SDK

**Decision**: The teacher's account is created by hand in the Firebase console (Email/Password
provider). Authorization is a marker document at `teachers/{uid}`, also created by hand, and the rules
check `exists(/databases/$(database)/documents/teachers/$(request.auth.uid))`.

**Rationale**: The spec assumes manual, out-of-band provisioning and no self-registration, so no
programmatic user creation is needed at all — which is what lets this phase avoid the Admin SDK and
its service-account key entirely. A marker document is checkable from both Firestore and Storage
rules, needs no custom-claim tooling, and makes FR-013's "must not block multiple teachers" true in
the cheapest possible way: a second teacher is a second document. Custom claims would need an Admin
SDK invocation per teacher and a token refresh before they take effect.

**Alternatives considered**:
- *Custom claim `role: teacher`*: marginally cheaper at read time (no rule lookup), but needs an Admin
  SDK path this phase otherwise doesn't have, and claims are invisible in the console.
- *Hard-coding the teacher's UID in the rules*: works today, but a rules edit and deploy per teacher —
  and it makes FR-013 false in the one place that enforces access.
- *Any authenticated user may write*: since only the teacher has an account it is technically
  equivalent today, and it silently becomes wrong the moment student accounts arrive in V2.

---

## Open items carried into implementation

| Item | Owner | Note |
|------|-------|------|
| Fetch Stitch screen `4f1bee3d7518487b986c7c63cb3c07ff` via the `stitch` MCP server before writing editor UI | Principle VIII, blocking | Verse-list/text regions only; the audio column is Phase 12 |
| Record the Arabic/mirrored portal chrome as a design deviation in `design-notes.md` | Principle VIII | Not in the captured design; original work |
| `docs/ROADMAP.md:122` says "bulk CSV import"; FR-023a is plain text | Docs | One-word correction, same PR |
| Add a CI job running the emulator-gated rule tests | Ops | Without it FR-036's tests exist but never run |
| Confirm the constitution's Stack list needs no edit | Governance | It already names `teacherApp` as the producer client |
