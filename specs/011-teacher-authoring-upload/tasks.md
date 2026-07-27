---

description: "Task list for Phase 11 — Teacher Authoring Tool: Foundation & Upload"
---

# Tasks: Phase 11 — Teacher Authoring Tool: Foundation & Upload

**Input**: Design documents from `specs/011-teacher-authoring-upload/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`

**Tests**: REQUIRED. Constitution Principle V is NON-NEGOTIABLE — new domain/data behaviour must land
with tests in the same change.

**Organization**: Grouped by user story so each is independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel — different files, no dependency on an incomplete task
- **[Story]**: `[US1]`…`[US6]`, mapping to the user stories in `spec.md`
- Every task names its exact file path

---

## GROUND RULES — read before starting any task

These are not style advice. Violating any one of them produces a build failure or a blocking review
failure.

1. **Base package is `com.giraffe.matn`.** Directory path always mirrors the package.
2. **Never invent an API.** If a signature is not written in this file or in `contracts/`, open the
   named existing file and copy the real one. Files worth reading before you start:
   - `shared/src/commonMain/kotlin/com/giraffe/matn/core/Resource.kt` — `Resource<T>`, `AppError`
   - `shared/src/commonMain/kotlin/com/giraffe/matn/core/usecase/UseCase.kt` — `UseCase`, `FlowUseCase`
   - `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/base/BaseViewModel.kt`
   - `shared/src/commonMain/kotlin/com/giraffe/matn/domain/error/ContentIntegrityError.kt`
   - `shared/src/commonMain/kotlin/com/giraffe/matn/data/seed/SeedContent.kt`
   - `shared/src/commonMain/kotlin/com/giraffe/matn/data/seed/ContentSeedLoaderImpl.kt`
   - `desktopApp/build.gradle.kts` and `desktopApp/src/main/kotlin/com/giraffe/matn/main.kt` — the
     template for `:teacherApp`
3. **Dependencies are fixed.** Ktor **3.2.3** and `jna-platform` **5.6.0** only. Both are already in
   the local Gradle cache. Adding any other third-party library is out of scope for every task here.
4. **Tokens, never literals.** In `:teacherApp` no raw hex colour, no bare `.dp`/`.sp`. Use
   `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `MatnShapes.*`, `MatnSpacing.*` from
   `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/`.
5. **No user-visible string literals in composables.** Every label goes through `TeacherStrings`
   (T028). A hard-coded string is a review failure.
6. **MVVM split is mandatory.** Every screen is a stateless `XxxContent(state, onIntent…)` plus a
   thin `XxxScreen()` holder that only collects the `StateFlow` and forwards intents. No rendering
   logic in the holder. No use-case or repository call from a composable.
7. **Every state-rendering composable gets a `@Preview`** driven by hand-built sample state — no
   ViewModel, no Koin, no network. Screen-level composables preview each key state **in both
   languages**.
8. **Do not change student-app behaviour.** `:androidApp`, `:iosApp`, `:desktopApp` must build and
   behave identically. The only student-facing files touched by this phase are
   `ContentSeedLoaderImpl.kt` (T014, behaviour-preserving) and `MatnTheme.kt` (T027, additive
   defaulted parameter).
9. **Never log or display a token, password, or credential.** FR-003b.
10. **Verification after each phase**: run the command in that phase's checkpoint. If it fails, fix
    before moving on. Never proceed with a red build.
11. **Koin — read this twice; getting it wrong breaks the student apps' build.**
    `shared/src/commonMain/kotlin/com/giraffe/matn/di/ContentModule.kt` carries
    `@ComponentScan("com.giraffe.matn")`, which sweeps up **every** `@Single`/`@Factory` class in
    `:shared` — including any you add. The student apps (`:androidApp`, `:iosApp`, `:desktopApp`) use
    that module, and they have no `FirebaseConfig`, no `HttpClient`, and no `SecretStore`. So:
    - **Do NOT annotate any teacher-side class you create in `:shared`.** No `@Single`, no
      `@Factory`, no `@Module` on `IdentityToolkitClient`, `TokenRefresher`, `FirestoreRestClient`,
      `StorageRestClient`, `FirestoreCatalogRepository`, `IdentityTeacherAuthRepository`, any teacher
      use case, or anything else added by this phase. Plain constructors only.
    - **`:teacherApp` constructs them**, in its own `@Module` class (T030), with explicit provider
      functions that pass the dependencies in by hand — the pattern
      `di/MatnKoinStarter.kt`'s `PlatformModule` already uses for platform singletons.
    - The one exception is `ContentIntegrityValidator` and the other pure `domain/catalog` helpers:
      they are stateless and take no dependencies, so call them directly rather than injecting them.
    - **Why the compiler will not save you**: `ContentModule`'s own KDoc records that this Koin
      compiler plugin reports unresolved cross-bindings as hard `KOIN-D001` **errors**, so an
      annotated teacher class fails the whole build, not just `:teacherApp`. This is the fastest way
      to violate FR-044 ("MUST NOT change ... any student client").
12. **Commit after each task**, using the task ID in the message (e.g. `T011: extract validator`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Make the build know about the new module and dependencies.

- [X] T001 Add Ktor and JNA entries to `gradle/libs.versions.toml`: under `[versions]` add
      `ktor = "3.2.3"` and `jna = "5.6.0"`; under `[libraries]` add `ktor-client-core`
      (`io.ktor:ktor-client-core`), `ktor-client-cio` (`io.ktor:ktor-client-cio`),
      `ktor-client-contentNegotiation` (`io.ktor:ktor-client-content-negotiation`),
      `ktor-serialization-json` (`io.ktor:ktor-serialization-kotlinx-json`), `ktor-client-mock`
      (`io.ktor:ktor-client-mock`), all `version.ref = "ktor"`; and `jna-platform`
      (`net.java.dev.jna:jna-platform`, `version.ref = "jna"`).
- [X] T002 Add Ktor to `shared/build.gradle.kts`: `commonMain.dependencies` gets
      `libs.ktor.client.core`, `libs.ktor.client.cio`, `libs.ktor.client.contentNegotiation`,
      `libs.ktor.serialization.json`; `commonTest.dependencies` gets `libs.ktor.client.mock`.
      Do not add an engine to any platform source set — CIO covers every target.
- [X] T003 Add `include(":teacherApp")` to `settings.gradle.kts`, directly after
      `include(":desktopApp")`.
- [X] T004 Create `teacherApp/build.gradle.kts` by copying `desktopApp/build.gradle.kts` and
      changing: `mainClass = "com.giraffe.matn.teacher.TeacherMainKt"`,
      `packageName = "com.giraffe.matn.teacher"`. Add to `dependencies`: `project(":shared")`,
      `compose.desktop.currentOs`, `libs.kotlinx.coroutinesSwing`, `libs.compose.uiToolingPreview`,
      `libs.androidx.lifecycle.viewmodelCompose`, `libs.androidx.lifecycle.runtimeCompose`,
      `libs.koin.core`, `libs.koin.annotations`, and `libs.jna.platform`. Also apply
      `alias(libs.plugins.koinCompiler)`.
      Add test dependencies — `:teacherApp` has five test tasks and `desktopApp/build.gradle.kts`,
      which you are copying, has none: `testImplementation(libs.kotlin.test)`,
      `testImplementation(libs.junit)`, `testImplementation(libs.kotlinx.coroutines.test)`, plus
      `tasks.test { useJUnit() }` (the catalog's `junit` is JUnit **4**, so do not call
      `useJUnitPlatform()`).
      **Why the extra Compose/lifecycle/Koin lines**: `:shared` declares those as `implementation`,
      so they are not exposed transitively and `:teacherApp` will not compile without them.
      **Do not** add `kotlinx-serialization-json` — nothing in `:teacherApp` serializes JSON; config
      loading uses a plain properties file and all JSON handling lives in `:shared`.
      **Verify before moving on**: `alias(libs.plugins.koinCompiler)` is currently applied only to
      `:shared`, a KMP module. Run `./gradlew :teacherApp:compileKotlin` after adding it. If the
      plugin misbehaves on a JVM-only module, drop the plugin and write `TeacherModule` (T030) as a
      hand-written Koin DSL `module { single { … } }` instead — functionally identical here, since
      T030 uses explicit provider functions rather than a component scan.
- [X] T005 [P] Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/TeacherMain.kt` with
      `fun main() = application { Window(onCloseRequest = ::exitApplication, title = "Matn — Teacher") { Text("Teacher portal") } }`.
      Placeholder only; T030 replaces the body.
- [X] T006 [P] Create the `firebase/` directory at the repo root with: `firebase.json` (pointing
      `firestore.rules` → `firestore.rules`, `storage.rules` → `storage.rules`, and emulator ports
      auth 9099 / firestore 8080 / storage 9199), and `firebase.local.properties.template`
      containing empty `projectId=`, `apiKey=`, `storageBucket=`. Add
      `firebase/firebase.local.properties` to `.gitignore`.

**Checkpoint**: `./gradlew :teacherApp:run` opens a window. `./gradlew build` succeeds.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain model, the shared validator, the HTTP layer, theme and strings. Nothing in
Phases 3+ can start until this is done.

**⚠️ CRITICAL**: T007–T017 (including T013a/T013b) touch shipped student code. Do them first, in
order, and do not start
T018 until T017 is green.

### 2A — Domain model and the validator extraction

Read `contracts/validation-contract.md` before starting this group. Read
`data-model.md` §1–§6 for exact field lists.

- [X] T007 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/MatnDraft.kt`
      with four `data class`es — `MatnDraft`, `DraftChapter`, `DraftVerse`, `DraftAudio` — using
      exactly the fields in `data-model.md` §1–§3. Reuse the existing
      `com.giraffe.matn.domain.model.StructureKind` enum; do not declare a new one. Add computed
      properties `verseCount` (= `verses.size`) and `declaredSizeBytes`.
- [X] T008 [P] Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/PublicationState.kt`:
      `enum class PublicationState { DRAFT, PUBLISHED }`.
- [X] T009 [P] Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/AudioCompleteness.kt`:
      `enum class AudioCompleteness { NONE, PARTIAL, COMPLETE }` with
      `companion object { fun of(verses: List<DraftVerse>): AudioCompleteness }` per `data-model.md`
      §4. Empty list ⇒ `NONE`.
- [X] T010 Add two cases to the existing sealed interface in
      `shared/src/commonMain/kotlin/com/giraffe/matn/domain/error/ContentIntegrityError.kt`:
      `data class EmptyMatn(val matnId: String)` and
      `data class DocumentTooLarge(val matnId: String, val bytes: Long, val limitBytes: Long)`.
      Add only; change nothing existing.
- [X] T011 Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/ContentIntegrityValidator.kt`
      with `fun validate(draft: MatnDraft): ValidationReport`. **Move** — do not rewrite — the eight
      rule blocks currently in the `private fun validate(payload: SeedMatn)` of
      `shared/src/commonMain/kotlin/com/giraffe/matn/data/seed/ContentSeedLoaderImpl.kt`, keeping
      their **existing order** (V1 InvalidId, V1b DuplicateId, V2 DuplicateDisplayNumber, V2b
      DuplicateChapterOrder, V4 MissingAudio, V5 DuplicateAudioRef, V6 OrphanChapterRef, V7
      StructureMismatch), reading from `MatnDraft` instead of `SeedMatn`. Then append V8
      (`EmptyMatn` when `verses.isEmpty()`) and V9 (`DocumentTooLarge` when estimated size
      > 900_000 bytes). Route `MissingAudio` and `DuplicateAudioRef` into `deferred`; everything
      else into `blocking`.
- [X] T012 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/ValidationReport.kt`:
      `data class ValidationReport(val blocking: List<ContentIntegrityError>, val deferred: List<ContentIntegrityError>) { val canPublish: Boolean get() = blocking.isEmpty(); val all: List<ContentIntegrityError> get() = blocking + deferred }`.
- [X] T013 Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/seed/SeedMatnProjection.kt` with
      `fun SeedMatn.toDraft(): MatnDraft` and `fun MatnDraft.toSeedMatn(packId: String = "", declaredSizeBytes: Long = 0L): SeedMatn`.
      Map every field per `data-model.md` §11. `packId` and `isStarter` are not authored — leave at
      their defaults.
- [X] T013a Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/MatnDraftFactory.kt`
      — the **only** place a new matn comes into existence. Provide
      `const val INSTITUTIONAL_RECITER_ID` (a fixed UUID string, the FR-007a constant) and
      `fun newDraft(newId: () -> String, nowMillis: () -> Long, title: String = "", author: String = "", structureKind: StructureKind = StructureKind.SIMPLE): MatnDraft`
      which sets `id = newId()`, `defaultReciterId = INSTITUTIONAL_RECITER_ID`,
      `createdAt = updatedAt = nowMillis()`, `publicationState = DRAFT`, `remoteUpdateTime = null`,
      and empty chapter/verse lists. Add `fun newChapter(...)` and `fun newVerse(...)` doing the same
      for their ids. `newId`/`nowMillis` are **injected lambdas**, not `Uuid.random()` calls inline —
      that is what makes them testable, and it matches how `ContentModule.kt` already passes
      `newId`/`clock` into `BookmarkRepositoryImpl`. Callers in `:teacherApp` pass
      `{ Uuid.random().toString() }` and `{ Clock.System.now().toEpochMilliseconds() }`.
- [X] T013b [P] Create
      `shared/src/commonTest/kotlin/com/giraffe/matn/catalog/MatnDraftFactoryTest.kt`: two
      `newDraft()` calls produce **distinct** ids; `defaultReciterId` is never blank and is identical
      across calls (FR-007a); `createdAt == updatedAt` on a fresh draft; a fresh draft is `DRAFT` with
      `remoteUpdateTime == null` (FR-008, FR-010).
- [X] T014 Change the `private fun validate(payload: SeedMatn)` in
      `shared/src/commonMain/kotlin/com/giraffe/matn/data/seed/ContentSeedLoaderImpl.kt` to a
      three-line body: `payload.toDraft()`, call `ContentIntegrityValidator.validate(...)`, return
      `report.all`. **The loader treats every problem as blocking, exactly as today** — return
      `blocking + deferred`, not just `blocking`. Delete the moved rule code. Do not change the
      method's signature, its callers, or anything else in the file.
- [X] T015 [P] Create
      `shared/src/commonTest/kotlin/com/giraffe/matn/catalog/ContentIntegrityValidatorTest.kt` with
      one test per rule V1–V9 (nine tests), each asserting the specific error type lands in the
      correct bucket. Add a tenth: a valid text-only matn ⇒ `blocking` empty, `deferred` holds one
      `MissingAudio` per verse, `canPublish == true`. Add four more for `AudioCompleteness.of()` —
      empty list ⇒ `NONE`, no verse with audio ⇒ `NONE`, some ⇒ `PARTIAL`, all ⇒ `COMPLETE` — which
      is what SC-007 measures and what Phase 13 relies on to decide student visibility.
- [X] T016 [P] Create `shared/src/commonTest/kotlin/com/giraffe/matn/catalog/SeedMatnProjectionTest.kt`
      asserting `SeedMatn → MatnDraft → SeedMatn` round-trips every field, for both a `SIMPLE` and a
      `STRUCTURED` matn.
- [X] T017 **Regression gate.** Run `./gradlew :shared:jvmTest :shared:testDebugUnitTest` — **not**
      `:shared:allTests`, whose `iosArm64`/`iosSimulatorArm64` test tasks cannot execute on Windows and
      will either fail for an unrelated reason or silently skip. (`allTests` is the macOS/CI form; use
      it there.) The pre-existing files
      `shared/src/commonTest/kotlin/com/giraffe/matn/data/SimpleMatnTest.kt` and
      `shared/src/commonTest/kotlin/com/giraffe/matn/data/StructuredMatnTest.kt` must pass **without
      being edited**. If either needed a change, T011/T014 altered behaviour — revert and redo. Do
      not proceed past this task with those tests modified.

### 2B — Errors, config, HTTP, Firestore encoding

Read `contracts/rest-contract.md` §1–§4 and `contracts/firestore-schema.md` §2 first.

- [X] T018 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/error/RemoteError.kt`:
      a sealed interface extending `AppError` with the seven cases in `data-model.md` §10, each
      carrying `val retryable: Boolean`.
- [X] T019 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/secret/SecretStore.kt` —
      the interface exactly as written in `data-model.md` §9.
- [X] T020 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/FirebaseConfig.kt`:
      `data class FirebaseConfig(val projectId: String, val apiKey: String, val storageBucket: String, val emulatorHost: String? = null)`
      plus `val identityBaseUrl`, `val secureTokenBaseUrl`, `val firestoreBaseUrl`,
      `val storageBaseUrl` computed per `contracts/rest-contract.md` §2 (emulator host wins when set).
- [X] T021 Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/HttpClientFactory.kt`:
      `fun createHttpClient(engine: HttpClientEngine? = null): HttpClient` using `CIO`, installing
      `ContentNegotiation` with `Json { ignoreUnknownKeys = true; explicitNulls = false }` and
      `HttpTimeout` (30 s request, 15 s connect). The `engine` parameter exists so tests can inject
      `MockEngine`. **No `expect`/`actual`** — CIO is published for every target.
- [X] T022 Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/firestore/FirestoreValue.kt`:
      a sealed interface with `StringValue`, `IntegerValue`, `BooleanValue`, `TimestampValue`,
      `NullValue`, `ArrayValue(values: List<FirestoreValue>)`, `MapValue(fields: Map<String, FirestoreValue>)`,
      plus `toJson(): JsonElement` and `fun fromJson(element: JsonElement): FirestoreValue`.
      **Two wire quirks to get right**: `integerValue` is a JSON *string* (`"integerValue": "500"`),
      and `timestampValue` is RFC 3339.
- [X] T023 [P] Create
      `shared/src/commonTest/kotlin/com/giraffe/matn/remote/FirestoreValueTest.kt` — a round-trip
      test per type, plus explicit tests for the integer-as-string and RFC 3339 quirks.
- [X] T024 Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/firestore/FirestoreMatnMapper.kt`
      with `fun MatnDraft.toFirestoreFields(): Map<String, FirestoreValue>` and
      `fun matnDraftFromFields(id: String, fields: Map<String, FirestoreValue>, updateTime: String?): MatnDraft`.
      Field names and types come from `contracts/firestore-schema.md` §2 — follow that table exactly.
      `audio` is always `NullValue` in this phase.
- [X] T025 [P] Create
      `shared/src/commonTest/kotlin/com/giraffe/matn/remote/FirestoreMatnMapperTest.kt` asserting
      `MatnDraft → fields → MatnDraft` round-trips, for a `SIMPLE` matn and a `STRUCTURED` matn with
      chapters.
- [X] T026 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/RemoteErrorMapper.kt`
      with `fun mapHttpError(status: Int, body: String?): RemoteError` implementing the table in
      `contracts/rest-contract.md` §4.1, and `fun mapThrowable(t: Throwable): RemoteError` returning
      `RemoteError.Network` for connection failures. Rethrow `CancellationException` untouched.

### 2C — Theme, strings, module wiring

- [X] T027 Add a third defaulted parameter to `MatnTheme` in
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/MatnTheme.kt`:
      `layoutDirection: LayoutDirection = LayoutDirection.Rtl`, and change line 58's
      `LocalLayoutDirection provides LayoutDirection.Rtl` to provide the parameter. **Additive
      only** — every existing call site and all 88 existing previews must remain untouched and keep
      compiling.
- [X] T028 Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/strings/TeacherStrings.kt`:
      an `interface TeacherStrings` with one `val` per user-visible string, an
      `object ArabicStrings : TeacherStrings`, an `object EnglishStrings : TeacherStrings`, an
      `enum class TeacherLanguage { ARABIC, ENGLISH }` with `val layoutDirection`, and
      `val LocalTeacherStrings = staticCompositionLocalOf<TeacherStrings> { ArabicStrings }`.
      Start with the labels visible in `stitch-designs/11-Upload-Per-Verse.html`. Because it is an
      interface, a missing translation is a compile error rather than a silent fallback.
      Two later tasks add the remaining keys and nothing else may defer to "some later task":
      **T028a** (error messages) and **T073** (validation messages).
- [X] T028a Add the seven `RemoteError` message keys to `TeacherStrings`, `ArabicStrings`, and
      `EnglishStrings`, one per row of `contracts/teacher-ui-contract.md` §6: `Network`,
      `Unauthorized`, `Forbidden`, `Conflict`, `QuotaExceeded`, `Server`, `Decode`. Each needs **two**
      strings — the message and its action label ("Retry", "Sign in again", "Reload", or none). Add
      `fun TeacherStrings.messageFor(error: RemoteError): String` and
      `fun TeacherStrings.actionFor(error: RemoteError): String?` in the same file, so no screen maps
      an error itself. **No raw HTTP status, exception text, or error code may appear in any of these
      strings** (FR-005). Every screen that shows a failure — T045, T059, T086, T089 — uses these two
      functions.
- [X] T029 [P] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/platform/JvmLanguagePreference.kt` storing
      the chosen `TeacherLanguage` in a properties file under the OS app-data directory, with
      `load(): TeacherLanguage` (default `ARABIC`) and `save(language: TeacherLanguage)`.
- [X] T030 Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/di/TeacherModule.kt` — a Koin
      `@Module @ComponentScan("com.giraffe.matn.teacher")` class with explicit provider functions for
      `FirebaseConfig` (read from `firebase/firebase.local.properties`, overridden by environment
      variables), the `HttpClient`, `SecretStore`, and — because Ground Rule 11 forbids annotating
      them in `:shared` — **every teacher-side `:shared` class this phase adds**:
      `IdentityToolkitClient`, `TokenRefresher`, `FirestoreRestClient`, `StorageRestClient`,
      `IdentityTeacherAuthRepository`, `FirestoreCatalogRepository`, and each teacher use case. Later
      tasks add their provider function here as they create each class.
      Then rewrite `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/TeacherMain.kt` to start Koin
      with **only** `TeacherModule` — **not** `:shared`'s `ContentModule`, and never
      `initMatnKoin(...)`, which requires a `DatabaseDriverFactory`, an `AudioEngine`, a
      `ContentDeliveryEngine`, and six more student-only singletons, and which also seeds bundled
      matns. `:teacherApp` opens no SQLDelight database. Read the language preference and wrap the
      window content in `MatnTheme(layoutDirection = language.layoutDirection)` and a
      `CompositionLocalProvider(LocalTeacherStrings provides …)`.
- [X] T030a **Student-app regression gate.** Run `./gradlew :androidApp:assembleDebug` and
      `./gradlew :desktopApp:run`. Both must succeed with the remote layer present. A
      `KOIN-D001` error, or a `NoDefinitionFoundException` for `FirebaseConfig`/`HttpClient`/
      `SecretStore` at student-app startup, means a teacher-side class in `:shared` was annotated —
      remove the annotation and provide it in `TeacherModule` instead (Ground Rule 11). Re-run this
      task at the end of every later phase that adds a `:shared` class.

### 2D — Security rules

- [X] T031 [P] Create `firebase/firestore.rules` with the exact rule text from
      `contracts/security-rules.md` §1. Do not paraphrase it — the `teachers/{uid}` deny block is
      what stops an anonymous caller granting themselves write access.
- [X] T032 [P] Create `firebase/storage.rules` with the exact rule text from
      `contracts/security-rules.md` §2.
- [X] T033a **Fetch the canonical designs — blocking gate on all UI tasks (Principle VIII).** Using
      the configured `stitch` MCP server, fetch screen `4f1bee3d7518487b986c7c63cb3c07ff`
      (*Upload Matn (Per-Verse)*) and `fa8b63b0…` (*Search Matn*, for its producer/portal chrome —
      see `docs/DESIGN-SOURCE.md` open issue #7). Write what you find to
      `specs/011-teacher-authoring-upload/design-notes.md`: the layout regions, the control
      inventory, and anything the capture does **not** show. Local exports
      `stitch-designs/11-Upload-Per-Verse.html` and `stitch-designs/12-Upload-Timestamp-Map.html` are
      a reference, not a substitute — the MCP fetch is what the constitution requires.
      **T046, T059, T065, T075, T086, and T092 MUST NOT begin until this task is done.** Inventing a
      layout for a screen that has a design is a blocking review failure.
- [X] T033 [P] Create `shared/src/jvmTest/kotlin/com/giraffe/matn/remote/EmulatorTestSupport.kt` with
      `fun emulatorHostOrNull(): String? = System.getenv("FIREBASE_EMULATOR_HOST")` and a helper
      that skips a test when it is `null`. Every emulator test uses this so `./gradlew test` stays
      green on a machine with no Firebase CLI.

**Checkpoint**: `./gradlew build`, `./gradlew :shared:jvmTest :shared:testDebugUnitTest`, and
`./gradlew :teacherApp:compileKotlin` all green, with `SimpleMatnTest.kt` and `StructuredMatnTest.kt`
unmodified. T030a passes — the student apps still build with the remote layer present. T033a is done,
so UI work is unblocked. User story work can begin.

---

## Phase 3: User Story 1 — Sign in to the teacher portal (Priority: P1) 🎯 MVP

**Goal**: The teacher signs in, reaches the portal, stays signed in across restarts, and can sign out
and switch interface language.

**Independent Test**: Launch with no stored session → sign-in screen; correct credentials reach the
portal; wrong credentials show a clear error and store nothing; the session survives a restart;
sign-out revokes write access.

### Tests for User Story 1

- [X] T034 [P] [US1] Create `shared/src/jvmTest/kotlin/com/giraffe/matn/remote/IdentityToolkitClientTest.kt`
      using Ktor `MockEngine`: assert the `accounts:signInWithPassword` request shape (method, path,
      `key` query parameter, JSON body), and assert each `error.message` in
      `contracts/rest-contract.md` §3.2 maps to the right `RemoteError`.
- [X] T035 [P] [US1] Create `shared/src/jvmTest/kotlin/com/giraffe/matn/remote/TokenRefresherTest.kt`
      with an injected clock: refresh happens inside the 5-minute pre-expiry window; two concurrent
      callers cause exactly one refresh; `INVALID_REFRESH_TOKEN` clears the `SecretStore` and yields
      `RemoteError.Unauthorized`.
- [X] T036 [P] [US1] Create `teacherApp/src/test/kotlin/com/giraffe/matn/teacher/JvmSecretStoreTest.kt`:
      `put` → `get` returns the same value; `clear` → `get` returns `null`; the stored file does not
      contain the plaintext value.

### Implementation for User Story 1

- [X] T037 [P] [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/auth/TeacherSession.kt`
      — the `data class` in `data-model.md` §8.
- [X] T038 [P] [US1] Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/domain/auth/TeacherAuthRepository.kt` — the
      interface exactly as in `data-model.md` §9.
- [X] T039 [US1] Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/identity/IdentityToolkitClient.kt`
      with `suspend fun signInWithPassword(email: String, password: String): Resource<TeacherSession>`
      and `suspend fun refresh(refreshToken: String): Resource<TeacherSession>`, per
      `contracts/rest-contract.md` §3.1 and §3.3.
- [X] T040 [US1] Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/identity/TokenRefresher.kt` holding
      the current session in memory, refreshing when within 5 minutes of expiry, single-flight under
      concurrent callers (use a `Mutex`), and exposing `suspend fun currentIdToken(): Resource<String>`.
- [X] T041 [US1] Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/platform/JvmSecretStore.kt`
      implementing `SecretStore` per `research.md` D7: Windows via
      `com.sun.jna.platform.win32.Crypt32Util.cryptProtectData`/`cryptUnprotectData` writing
      ciphertext to the app-data directory; macOS via `security add-generic-password` /
      `find-generic-password` / `delete-generic-password`; Linux via `secret-tool` when on `PATH`;
      otherwise an owner-readable file with `isProtected = false`. Detect the OS from
      `System.getProperty("os.name")`.
- [X] T042 [US1] Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/data/repository/IdentityTeacherAuthRepository.kt`
      implementing `TeacherAuthRepository` over `IdentityToolkitClient`, `TokenRefresher`, and
      `SecretStore`. **Persist the refresh token only**, and only after a successful sign-in. Sign-out
      clears the store and emits `null` from `observeSession()`.
- [X] T043 [P] [US1] Create three use cases in
      `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/`: `SignInUseCase.kt`,
      `SignOutUseCase.kt`, `RestoreSessionUseCase.kt`, each implementing the `UseCase` contract from
      `core/usecase/UseCase.kt`.
- [X] T044 [US1] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/signin/SignInViewModel.kt`
      extending `BaseViewModel<SignInUiState>`, with `SignInUiState(email, password, isSubmitting, error: RemoteError?, secretStoreUnprotected)`
      and intents `onEmailChange`, `onPasswordChange`, `onSubmit`.
- [X] T045 [US1] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/signin/SignInScreen.kt` with a
      stateless `SignInContent(state, onEmailChange, onPasswordChange, onSubmit)` and a thin
      `SignInScreen(viewModel)` holder. Add `@Preview`s: idle, error, unprotected-store — each in
      Arabic/RTL and English/LTR (six previews).
- [X] T046 [US1] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/shell/PortalShell.kt` — the
      navigation chrome from `contracts/teacher-ui-contract.md` §3.2: destinations, teacher display
      name, storage-usage row (a placeholder value until T086 wires real usage from T052's
      `totalUsageBytes`), language switch, sign-out. Mirrors with the ambient layout direction.
      `@Preview` in both languages. Requires T033a.
- [X] T047 [US1] Wire routing in `TeacherMain.kt`: no session → `SignInScreen`; session →
      `PortalShell`. Call `RestoreSessionUseCase` on launch.
- [X] T048 [P] [US1] Create
      `teacherApp/src/test/kotlin/com/giraffe/matn/teacher/SignInViewModelTest.kt` with a fake
      `TeacherAuthRepository`: success sets the session; failure surfaces the error and leaves the
      typed email in state.
- [X] T048a [P] [US1] Create
      `shared/src/commonTest/kotlin/com/giraffe/matn/usecase/AuthUseCaseTest.kt` covering all three
      US1 use cases against a fake `TeacherAuthRepository` and a fake `SecretStore` (Principle V —
      new domain behaviour must land with tests): `SignInUseCase` returns the session on success and
      the mapped `RemoteError` on failure; `SignOutUseCase` clears the secret store; and
      `RestoreSessionUseCase` returns the session when a refresh token exists and `null` when it does
      not.

**Checkpoint**: User Story 1 fully functional. `./gradlew :teacherApp:run` → sign in → portal →
restart → still signed in → sign out → sign-in screen. Language switch mirrors the layout.

---

## Phase 4: User Story 2 — Create a matn and save it as a draft (Priority: P1)

**Goal**: The teacher creates a matn with metadata, cover image, and chapters, and saves it remotely
as a draft that survives a restart.

**Independent Test**: Sign in, create a matn with title/author/description/cover and two chapters,
save as draft, quit, reopen, confirm every field and the cover are intact.

### Tests for User Story 2

- [X] T049 [P] [US2] Create `shared/src/jvmTest/kotlin/com/giraffe/matn/remote/FirestoreRestClientTest.kt`
      with `MockEngine`: assert the `GET` document path; assert `PATCH` sends the full document and
      the `currentDocument.updateTime` query parameter; assert a 400 `FAILED_PRECONDITION` response
      maps to `RemoteError.Conflict`.
- [X] T050 [P] [US2] Create
      `shared/src/commonTest/kotlin/com/giraffe/matn/catalog/DraftAutosaveSchedulerTest.kt` using
      `kotlinx-coroutines-test` virtual time: saves after 5 s idle; saves at the 60 s ceiling under
      continuous editing; coalesces concurrent triggers into one save; **never** fires for a
      published matn.

### Implementation for User Story 2

- [X] T051 [US2] Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/firestore/FirestoreRestClient.kt`
      with `suspend fun getDocument(path: String, mask: List<String>? = null): Resource<FirestoreDocument>`
      and `suspend fun patchDocument(path: String, fields: Map<String, FirestoreValue>, updateTimePrecondition: String?, requireNotExists: Boolean = false): Resource<FirestoreDocument>`.
      `FirestoreDocument` carries `name`, `fields`, `updateTime`. Bearer token from `TokenRefresher`.
      Always send the **full** document — never a partial `updateMask`.
- [X] T052 [P] [US2] Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/storage/StorageRestClient.kt` with
      `suspend fun upload(objectPath: String, bytes: ByteArray, contentType: String): Resource<String>`
      and `suspend fun totalUsageBytes(prefix: String): Resource<Long>`, per
      `contracts/rest-contract.md` §5.
- [X] T053 [P] [US2] Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/CatalogRepository.kt` — the
      interface exactly as in `data-model.md` §9.
- [X] T054 [US2] Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/data/repository/FirestoreCatalogRepository.kt`
      implementing `load`, `save`, and `uploadCover`. `save` sends `remoteUpdateTime` as the
      precondition and returns the draft with the **new** `updateTime` from the response — omitting
      that makes every subsequent save report a false conflict. `uploadCover` uploads to Storage
      **before** the document is patched (`research.md` D3). Leave `publish`, `unpublish`, and
      `observeAuthored` as `TODO()` — Phases 6 and 7 fill them.
- [X] T055 [P] [US2] Create use cases in
      `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/`: `SaveDraftUseCase.kt`,
      `LoadMatnForEditUseCase.kt`, `UploadCoverImageUseCase.kt`.
- [X] T056 [US2] Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/DraftAutosaveScheduler.kt` per
      `research.md` D12: constructor takes a `CoroutineScope`, a clock lambda, and a
      `suspend (MatnDraft) -> Unit` save. `notifyChanged(draft)` schedules; 5 s idle debounce, 60 s
      hard ceiling, one save in flight. `notifyChanged` is a no-op when
      `draft.publicationState == PUBLISHED`.
- [X] T057 [P] [US2] Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/platform/JvmFileChooser.kt`
      with `fun pickImage(): ByteArray?` and `fun pickTextFile(): ByteArray?` using Swing
      `JFileChooser`. Reject images over 5 MB or outside png/jpeg/webp before returning.
- [X] T058 [US2] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/editor/EditorViewModel.kt`
      extending `BaseViewModel<EditorUiState>`. State per `contracts/teacher-ui-contract.md` §3.4.
      Intents for every metadata field, cover pick/remove, chapter add/edit/delete, and save. A save
      failure must leave `state.draft` untouched and set `saveState = Failed(error)` — losing typed
      work here is the single most damaging bug in this phase (FR-032, SC-010).
      **Required-field gate (FR-018)**: an explicit save draft check that `title`, `author`, and
      `structureKind` are present before calling the repository. When any is missing, flag those
      fields in state and make **no** repository call. This is a field-level gate, distinct from
      `ContentIntegrityValidator` — see `contracts/validation-contract.md` §5, which assigns
      required-field checking to draft-save and full validation to publish.
      Creating a new matn uses `MatnDraftFactory.newDraft(...)` from T013a — never construct a
      `MatnDraft` inline, or the reciter constant and UUIDs will drift.
- [X] T059 [US2] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/editor/EditorScreen.kt` with
      the stateless `EditorContent` covering the metadata form and chapter list (verses come in
      Phase 5), plus the thin holder. `@Preview`: new draft and loaded draft, both languages.
      **Requires T033a** — this screen has a canonical design; do not invent its layout.
- [X] T060 [P] [US2] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/common/SaveStateIndicator.kt`
      — a shared stateless component showing idle / autosaving / saved-at / failed. `@Preview` per
      state.
- [X] T061 [P] [US2] Create `teacherApp/src/test/kotlin/com/giraffe/matn/teacher/EditorViewModelTest.kt`
      with a fake `CatalogRepository`: a failed save preserves on-screen state and reports
      `retryable` correctly; a successful save updates `remoteUpdateTime`; and **saving with title,
      author, or structure kind missing is refused with those fields flagged and no repository call
      made** (FR-018).
- [X] T061a [P] [US2] Create
      `shared/src/commonTest/kotlin/com/giraffe/matn/usecase/DraftUseCaseTest.kt` covering the three
      US2 use cases against a fake `CatalogRepository` (Principle V): `SaveDraftUseCase` passes
      `remoteUpdateTime` through as the precondition and returns the refreshed value;
      `LoadMatnForEditUseCase` maps a `NotFound` failure without throwing; `UploadCoverImageUseCase`
      rejects an oversized or wrong-type image **before** any upload call is made.

**Checkpoint**: A draft can be created, saved, and reloaded after a restart with its cover intact.
Autosave visibly updates the last-saved time.

---

## Phase 5: User Story 3 — Enter verse text (Priority: P2)

**Goal**: An ordered verse list the teacher can add to, edit, reorder by dragging, delete from, and
assign to chapters — with numbering always correct.

**Independent Test**: Open a draft, add five verses, drag two, delete one, save and reload; order,
numbering, and text survive.

### Tests for User Story 3

- [X] T062 [P] [US3] Create `shared/src/commonTest/kotlin/com/giraffe/matn/catalog/VerseOrderingTest.kt`:
      move forward, move backward, move to first, move to last, delete from the middle, add at the
      end — each asserting display numbers are exactly `1..n` with no gaps afterwards.

### Implementation for User Story 3

- [X] T063 [US3] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/VerseOrdering.kt`
      with pure functions `move(verses: List<DraftVerse>, from: Int, to: Int): List<DraftVerse>`,
      `renumber(verses: List<DraftVerse>): List<DraftVerse>`,
      `removeAt(verses: List<DraftVerse>, index: Int): List<DraftVerse>`, and
      `append(verses: List<DraftVerse>, verse: DraftVerse): List<DraftVerse>`. No Compose, no
      Android, no I/O.
- [X] T064 [US3] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/common/VerseRow.kt` — a
      **stateless** row taking `(verse, isFlagged, onTextChange, onDelete, onMoveUp, onMoveDown, dragHandleModifier)`.
      Arabic text field renders RTL regardless of interface language (FR-021). `@Preview`: normal,
      focused, flagged — in both directions.
- [X] T065 [US3] Add the verse-list region to `EditorContent` in
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/editor/EditorScreen.kt`: a
      `LazyColumn` keyed by `verse.id`, an "add verse" action, a drag handle using
      `Modifier.pointerInput`, and move-up/move-down buttons as the keyboard-accessible equivalent.
      **All verse text state lives in the ViewModel** — the row must not hold its own mutable text
      state, or a 500-verse list will stutter (FR-025). **Requires T033a** — the verse-list region has
      a canonical design.
- [X] T066 [US3] Add verse intents to `EditorViewModel`: `onAddVerse`, `onVerseTextChange(id, text)`
      (debounced into state), `onDeleteVerse(id)`, `onMoveVerse(from, to)`,
      `onAssignChapter(verseId, chapterId)`. Every reorder/add/delete goes through `VerseOrdering`
      so numbering cannot drift.
- [X] T067 [P] [US3] Add tests to
      `teacherApp/src/test/kotlin/com/giraffe/matn/teacher/EditorViewModelTest.kt`: adding, editing,
      reordering, and deleting verses produce the expected state, and chapter assignment rejects a
      non-existent chapter id.

**Checkpoint**: Verses can be entered, reordered, and deleted; numbering stays `1..n` across a save
and reload.

---

## Phase 6: User Story 4 — Validate and publish (Priority: P2)

**Goal**: The teacher checks a matn, sees every problem named and clickable, and publishes when
nothing blocks. Only published matns are readable without an account.

**Independent Test**: A draft with two verses sharing a number is reported and refused; fixed, it
publishes; an anonymous reader can then read it, and cannot read a draft.

### Tests for User Story 4

- [X] T068 [P] [US4] Create
      `shared/src/commonTest/kotlin/com/giraffe/matn/catalog/ValidateMatnUseCaseTest.kt`: an empty
      matn cannot publish; an audio-less matn **can** publish with `deferred` populated; a duplicate
      display number blocks.
- [X] T069 [US4] Create
      `shared/src/jvmTest/kotlin/com/giraffe/matn/remote/SecurityRulesTest.kt` implementing all 23
      cases in `contracts/security-rules.md` §3 — 17 refusals, 6 permitted — driven through this
      project's own `FirestoreRestClient` and `StorageRestClient` against the emulator. Skip via
      `EmulatorTestSupport` when `FIREBASE_EMULATOR_HOST` is unset. Case **W9** (an anonymous caller
      writing `teachers/{uid}`) is the most important one — without it, self-promotion to teacher is
      possible.
      Add one non-rules case to the same file for **SC-011**: while a save of a large matn is in
      flight, read the same document repeatedly and assert every response is either the complete prior
      version or the complete new one — never a mixture. This is what verifies the atomicity FR-033
      designs for.

### Implementation for User Story 4

- [X] T070 [P] [US4] Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/ValidateMatnUseCase.kt` returning
      `ValidationReport`, including the V9 projected-size guard.
- [X] T071 [P] [US4] Create
      `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/PublishMatnUseCase.kt`: validate
      first, refuse on any `blocking` problem, then write with `published = true`.
- [X] T071a [P] [US4] Create
      `shared/src/commonTest/kotlin/com/giraffe/matn/usecase/PublishMatnUseCaseTest.kt` against a fake
      `CatalogRepository` (Principle V): a draft with a blocking problem is refused **and no
      repository call is made**; a text-only draft with only `deferred` problems publishes; the
      published result carries `audioCompleteness == NONE`; and an empty matn is refused (FR-030).
- [X] T072 [US4] Implement `publish` in
      `shared/src/commonMain/kotlin/com/giraffe/matn/data/repository/FirestoreCatalogRepository.kt`
      (replacing the `TODO()` from T054) — the same `patchDocument` call with `published` flipped, so
      it inherits the same atomicity and conflict check.
- [X] T073 [US4] Add the validation-message keys from `contracts/validation-contract.md` §4 to
      `TeacherStrings`, `ArabicStrings`, and `EnglishStrings`. Messages name the **verse number or
      chapter title**, never a UUID.
- [X] T074 [P] [US4] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/common/ProblemRow.kt` — a
      stateless row with a click callback that scrolls to the offending verse. `@Preview` per
      severity.
- [X] T075 [US4] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/publish/ValidationPanel.kt`
      with two sections — "must fix before publishing" and "still outstanding" (missing recordings,
      presented as work remaining, not failure). `@Preview`: blocking-only, deferred-only, both — in
      both languages. Requires T033a.
- [X] T076 [P] [US4] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/publish/PublishConfirmDialog.kt`
      stating plainly that the matn will publish with no recordings yet. `@Preview` in both languages.
- [X] T077 [US4] Wire check and publish into `EditorViewModel` and `EditorContent`: publish is
      disabled while `blocking` is non-empty; selecting a problem focuses its verse.
- [ ] T078 [US4] Deploy the rules with
      `firebase deploy --only firestore:rules,storage:rules`, then run T069 through
      `firebase emulators:exec` and confirm all 23 cases pass.

**Checkpoint**: A validated matn publishes; an anonymous read succeeds for published and fails for
drafts; all 23 rule cases pass.

---

## Phase 7: User Story 5 — Manage the catalog (Priority: P2)

**Goal**: List every matn with its state, reopen a draft, correct a published matn in place, and
withdraw one.

**Independent Test**: One draft and one published matn both list correctly; the draft reopens; a
published verse edit is immediately visible to an anonymous reader; unpublish hides it; republish
restores it with identifiers unchanged.

### Tests for User Story 5

- [X] T079 [P] [US5] Create `teacherApp/src/test/kotlin/com/giraffe/matn/teacher/LibraryViewModelTest.kt`
      with a fake repository: loaded, empty, and error states each render the expected state object.
- [X] T080 [P] [US5] Add a conflict test to
      `shared/src/jvmTest/kotlin/com/giraffe/matn/remote/FirestoreRestClientTest.kt`: a save with a
      stale `updateTime` yields `RemoteError.Conflict` and does **not** retry automatically.

### Implementation for User Story 5

- [X] T081 [P] [US5] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/CatalogEntry.kt`
      — the `data class` in `data-model.md` §7.
- [X] T082 [US5] Add `suspend fun listDocuments(collection: String, mask: List<String>, pageToken: String?)`
      to `FirestoreRestClient`, sending the field mask from `contracts/firestore-schema.md` §4.1 so
      verse arrays are never transferred.
- [X] T083 [US5] Implement `observeAuthored` and `unpublish` in `FirestoreCatalogRepository`
      (replacing the remaining `TODO()`s from T054).
- [X] T084 [P] [US5] Create use cases
      `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/ListAuthoredMatnsUseCase.kt` and
      `UnpublishMatnUseCase.kt`.
- [X] T084a [P] [US5] Create
      `shared/src/commonTest/kotlin/com/giraffe/matn/usecase/CatalogUseCaseTest.kt` against a fake
      `CatalogRepository` (Principle V): `ListAuthoredMatnsUseCase` emits entries with both
      publication states and an empty list without error; `UnpublishMatnUseCase` returns a draft-state
      matn whose `id` and every verse `id` are **unchanged** from the published input (FR-038).
- [X] T085 [P] [US5] Create two shared components in
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/common/`: `PublicationBadge.kt`
      and `AudioCompletenessBadge.kt`, each stateless with a `@Preview` per value.
- [X] T086 [US5] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/library/LibraryViewModel.kt`
      and `LibraryScreen.kt` (stateless `LibraryContent` + thin holder). `@Preview`: loaded, **empty
      first-run**, loading, error — in both languages. The empty state is required by
      `contracts/teacher-ui-contract.md` §3.3. Requires T033a. Also wire the real storage-usage figure
      into `PortalShell` here, using `StorageRestClient.totalUsageBytes("matns/")` from T052 and the
      existing `presentation/common/ByteFormatter.kt` for display, replacing T046's placeholder. A
      failed usage read shows nothing rather than an error — it is informational only.
- [X] T087 [P] [US5] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/publish/UnpublishConfirmDialog.kt`
      stating that students will no longer see the matn and that it can be republished unchanged.
      `@Preview` in both languages.
- [X] T088 [US5] Add the published-editing banner to `EditorContent` and enforce in `EditorViewModel`
      that `DraftAutosaveScheduler` is not notified while `publicationState == PUBLISHED` (FR-031b).
      Also add the **empty-published guard (FR-030, second clause)**: saving a matn whose
      `publicationState == PUBLISHED` with an empty verse list is refused, with the
      `EmptyMatn` message shown and no repository call made. Without this, deleting every verse from a
      published matn would leave students an empty published matn. Add a case to
      `teacherApp/src/test/kotlin/com/giraffe/matn/teacher/EditorViewModelTest.kt` covering it.
- [X] T089 [US5] Add conflict handling to `EditorViewModel`: on `RemoteError.Conflict`, show the
      message from `contracts/teacher-ui-contract.md` §6 with a reload action. Never overwrite
      silently.

**Checkpoint**: All five lifecycle operations work; a conflicting save is reported rather than
overwriting.

---

## Phase 8: User Story 6 — Import verse text in bulk (Priority: P3)

**Goal**: Import a prepared file of verse text with a preview, per-line problem reporting, and append
semantics.

**Independent Test**: Import 50 lines including two malformed ones; the preview reports exactly those
two; proceeding appends 48 editable verses in file order.

### Tests for User Story 6

- [X] T090 [P] [US6] Create `shared/src/commonTest/kotlin/com/giraffe/matn/catalog/VerseTextImportTest.kt`:
      blank lines skipped **without** being reported as problems; `\n` and `\r\n` both handled; a
      leading BOM stripped; non-UTF-8 bytes rejected; a line containing commas and quotation marks
      preserved verbatim and never split.

### Implementation for User Story 6

- [X] T091 [US6] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/VerseTextImport.kt`
      with `fun parse(bytes: ByteArray): Resource<ImportPreview>` where
      `ImportPreview(lines: List<String>, problemLineNumbers: List<Int>)`. Plain UTF-8 text, one verse
      per line. **No delimiter, no CSV, no quoting** — a line's entire content is the verse text
      (FR-023a).
- [X] T092 [P] [US6] Create
      `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/importer/ImportPreviewDialog.kt`
      showing the line count, the first ~10 lines, and problem lines **by line number**, with cancel
      and confirm. `@Preview`: clean and with problems, both languages. Requires T033a.
- [X] T093 [US6] Wire import into `EditorViewModel`: `onImportRequested` uses
      `JvmFileChooser.pickTextFile()`, parses, shows the preview; confirm appends via
      `VerseOrdering.append` so imported verses are indistinguishable from hand-entered ones
      (FR-024). Cancel leaves the list untouched.

**Checkpoint**: All six user stories functional.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T094 [P] Complete `specs/011-teacher-authoring-upload/design-notes.md` (started in T033a) with
      the two Principle VIII deviations from `contracts/teacher-ui-contract.md` §1: Category omitted
      (no `SeedMatn` field), and the Arabic mirrored portal chrome as original work. Add any further
      deviation the implemented screens turned out to need. Follow the format of
      `specs/008-storage-downloads/design-notes.md`.
- [X] T095 [P] Fix `docs/ROADMAP.md` line 122: "bulk CSV import" → "bulk text import", matching
      FR-023a.
- [X] T096 [P] Add the two Upload screens' Phase 11 status to `docs/DESIGN-SOURCE.md`'s phase-mapped
      registry, and close open issue #7 with a pointer to `design-notes.md`.
- [X] T097 [P] Add a CI job running
      `firebase emulators:exec --only firestore,storage,auth "./gradlew :shared:jvmTest --tests '*SecurityRules*'"`.
      Without it the T069 tests exist but never execute and FR-042 is satisfied on paper only.
- [X] T098 Audit every icon-only control in `:teacherApp` for an accessibility label in the active
      language, following the pattern in
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/common/A11yLabels.kt`.
- [X] T099 Audit previews against `contracts/teacher-ui-contract.md` §5: every state-rendering
      composable has one, and every screen-level composable has both languages.
- [X] T100 Grep `:teacherApp` for raw hex colours, bare `.dp`/`.sp` literals, and hard-coded
      user-visible strings. Any hit is a blocking review failure — replace with tokens or
      `TeacherStrings`.
- [~] T101 Run the performance check in `quickstart.md` §5: import 500 verses, then type, scroll, and
      drag from position 400 to 5. If typing stutters, verse text state has leaked into the row
      composable instead of the ViewModel. **Partially substituted** — no display in this
      environment for the real frame-timing HUD pass; see design-notes.md T101/T102 note for the
      automated data-layer proxy (correctness + timing at 500-verse scale) and the structural
      confirmation that `VerseRow` holds no text state of its own.
- [ ] T102 Run the full `quickstart.md` §3 manual walkthrough (all six user stories) and confirm each
      acceptance scenario in `spec.md`. **Not performable in this environment** — see design-notes.md
      T102 note (needs a live Firebase project + real teacher account + interactive GUI/display, none
      of which are available here). Automated coverage is the substitute; see the same note for the
      scenario-by-scenario mapping.
- [X] T103 Confirm the constitution's Stack section already lists `teacherApp` as the producer
      client, so no amendment is needed; if it does not, update it in this PR.

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)**: no dependencies.
- **Phase 2 (Foundational)**: needs Phase 1. **Blocks every user story.**
  - Inside Phase 2, **2A must complete before 2B–2D start** — it touches shipped student code and
    T017 is the gate that proves the refactor changed nothing.
  - **T033a (Stitch fetch) blocks every UI task**: T046, T059, T065, T075, T086, T092.
  - **T030a (student-app build gate) must be re-run** at the end of Phases 3, 4, 6, and 7 — each adds
    classes to `:shared`, and each is an opportunity to break the student apps' Koin graph.
- **Phase 3 (US1)**: needs Phase 2. No dependency on any other story.
- **Phase 4 (US2)**: needs Phase 2. Needs US1 in practice — a save requires a session.
- **Phase 5 (US3)**: needs Phase 4 (verses attach to a matn).
- **Phase 6 (US4)**: needs Phase 5 (nothing meaningful to validate without verses).
- **Phase 7 (US5)**: needs Phase 4; `unpublish` also needs Phase 6's `publish`.
- **Phase 8 (US6)**: needs Phase 5 (imports append to the verse list).
- **Phase 9 (Polish)**: needs every story you intend to ship.

### Parallel opportunities

- **Phase 1**: T005, T006 in parallel after T004.
- **Phase 2A**: T007, T008, T009 in parallel; then T010 → T011 → T012/T013 → T013a → T013b/T015/T016
  in parallel → T014 → T017.
- **Phase 2B**: T018, T019, T020 in parallel; T023 with T024; T025 with T026.
- **Phase 2C**: T028 → T028a; T029 in parallel with either; then T030 → T030a.
- **Phase 2D**: T031, T032, T033 all in parallel; T033a independent of all three.
- **Phase 3**: T034, T035, T036 (tests) in parallel; T037, T038 in parallel; T043 in parallel with
  T041; T048, T048a in parallel.
- **Phase 4**: T049, T050 in parallel; T052, T053 in parallel; T055 with T057; T060, T061, T061a in
  parallel.
- **Phase 6**: T068, T069 in parallel; T070, T071, T071a in parallel; T074, T076 in parallel.
- **Phase 7**: T079, T080 in parallel; T081, T084, T084a, T085, T087 in parallel.
- **Phase 9**: T094–T097 all in parallel.

### Parallel example — Phase 2A opening

```bash
# Three independent new files, no shared edits:
Task: "T007 Create MatnDraft.kt in shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/"
Task: "T008 Create PublicationState.kt in shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/"
Task: "T009 Create AudioCompleteness.kt in shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/"
```

---

## Implementation Strategy

### MVP scope

**Phase 1 + Phase 2 + Phase 3 (US1)** is the smallest shippable increment: a signing-in teacher
portal on a tested shared backend client, with the validator extraction already banked. It proves the
whole producer path — authenticate, persist a session securely, reach the portal — before any content
code exists.

Realistically the first *useful* milestone is **through Phase 4 (US2)**, which is the first point a
matn exists remotely. Phases 1–4 together are the recommended first PR.

### Incremental delivery

1. Phases 1–2 → foundation, student app provably unchanged (T017)
2. + Phase 3 → sign in and stay signed in **(MVP)**
3. + Phase 4 → create and save drafts remotely
4. + Phase 5 → full verse-text authoring
5. + Phase 6 → validate and publish; rules deployed and tested
6. + Phase 7 → full lifecycle: list, correct, withdraw
7. + Phase 8 → bulk import
8. + Phase 9 → docs, CI, audits

### Suggested PR split

| PR | Phases | Why it is reviewable alone |
|----|--------|----------------------------|
| 1 | 2A only (T007–T017, incl. T013a/T013b) | Behaviour-preserving refactor of shipped code. Reviewed on one question: do `SimpleMatnTest`/`StructuredMatnTest` pass unedited? |
| 2 | 1, 2B–2D, 3 | New module + backend client + sign-in. No student code touched except the additive `MatnTheme` parameter |
| 3 | 4, 5 | The editor |
| 4 | 6, 7 | Publishing, rules, lifecycle |
| 5 | 8, 9 | Import and polish |

---

## Notes

- **112 tasks.** US1: 16 · US2: 14 · US3: 6 · US4: 12 · US5: 12 · US6: 4 · Setup: 6 · Foundational:
  32 · Polish: 10.
- The four highest-risk tasks, worth extra care:
  - **T030/T030a** — annotating a teacher-side `:shared` class with `@Single` breaks the *student*
    apps' build via `ContentModule`'s component scan. Read Ground Rule 11 before writing either.
  - **T011/T014** — the extraction must be behaviour-preserving; `SimpleMatnTest`/`StructuredMatnTest`
    passing unedited is the only proof.
  - **T054** — return the fresh `updateTime` from the write response, or every later save falsely
    reports a conflict.
  - **T065** — verse text state belongs to the ViewModel; in the row composable it makes the
    500-verse list stutter.
- **T033a is a hard gate on all UI work** (T046, T059, T065, T075, T086, T092). Principle VIII makes
  inventing a layout for a screen that has a design a blocking review failure.
- `[P]` means a genuinely different file with no incomplete dependency. When in doubt, run
  sequentially — the phases are short.
- Commit per task, referencing the task ID.
- Stop at any checkpoint and validate before continuing.
