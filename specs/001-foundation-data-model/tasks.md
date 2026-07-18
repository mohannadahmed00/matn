---
description: "Task list for Phase 0 — Foundation & Data Model"
---

# Tasks: Phase 0 — Foundation & Data Model

**Input**: Design documents from `/specs/001-foundation-data-model/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/repositories.md, contracts/seed-content.md, quickstart.md

**Tests**: INCLUDED and ordered **test-first** (constitution Principle V is NON-NEGOTIABLE). In each user story the test tasks are listed and authored **before** the implementation tasks. Kotlin note: a test file that references a not-yet-created implementation class will not compile — that "red" state is expected; complete the story's implementation tasks and the tests must then pass. Never weaken a test to make it pass.

**Organization**: Tasks are grouped by user story (US1 → US2 → US3) so each can be implemented and tested independently.

## Conventions for the implementer (READ FIRST)

- **Language**: Kotlin 2.4.10, Kotlin Multiplatform. Base package: `com.giraffe.matn`.
- **Module**: everything lives in the existing `:shared` module. Do NOT create a new module.
- **Do not touch** the existing sample files: `App.kt`, `Greeting.kt`, `GreetingUtil.kt`, `Platform*.kt`, `MainViewController.kt`, and their tests. Leave them exactly as-is.
- **Path roots** used below:
  - `COMMON = shared/src/commonMain/kotlin/com/giraffe/matn`
  - `SQLDIR = shared/src/commonMain/sqldelight/com/giraffe/matn/db`
  - `ANDROID = shared/src/androidMain/kotlin/com/giraffe/matn`
  - `IOS = shared/src/iosMain/kotlin/com/giraffe/matn`
  - `TEST = shared/src/commonTest/kotlin/com/giraffe/matn`
  - `HOSTTEST = shared/src/androidHostTest/kotlin/com/giraffe/matn` (JVM host actuals)
  - `IOSTEST = shared/src/iosTest/kotlin/com/giraffe/matn` (iOS test actuals)
- **After each task**: the module MUST still compile (`gradlew :shared:assemble`), except a story's test file may be intentionally red until that story's implementation tasks are done.
- **UUID note**: identities are content-authored strings. NEVER generate your own UUIDs anywhere. Store strings verbatim.
- **Arabic text**: store and read `String` verbatim. NEVER call `.trim()`, `.normalize()`, or any transformation on `arabicText`.
- **Reciter id (IMPORTANT)**: the single v1 reciter id constant is `AudioAssetRepository.DEFAULT_RECITER = "reciter-default-v1"`. Every fixture's `defaultReciterId` MUST be this exact string so that `getAudioForVerse(verseId)` (default reciter) resolves. This is enforced repeatedly below.
- **Fixtures are embedded Kotlin string constants**, NOT files under `resources/`. Reading classpath resources from `commonTest` is not portable to the iOS/native test target; embedding JSON as `String` constants works on every target. Do not create any `.json` resource files.
- **Format checklist**: `- [ ] T### [P?] [US#?] Description with file path`. `[P]` = safe to run in parallel (different file, no dependency on an unfinished task).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the four new libraries and the SQLDelight plugin, then create the package folders.

- [X] T001 Add new versions, libraries, and the SQLDelight plugin to `gradle/libs.versions.toml`. Under `[versions]` add: `sqldelight = "2.0.2"`, `coroutines = "1.9.0"`, `serialization = "1.7.3"`, `koin = "4.0.0"`. Under `[libraries]` add: `sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }`, `sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }`, `sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }`, `sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }`, `sqldelight-sqlite-driver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }`, `kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }`, `kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }`, `kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }`, `koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }`, `koin-test = { module = "io.insert-koin:koin-test", version.ref = "koin" }`. Under `[plugins]` add: `sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }`, `kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`.
- [X] T002 Edit `shared/build.gradle.kts`. (a) In the `plugins { }` block add `alias(libs.plugins.sqldelight)` and `alias(libs.plugins.kotlinSerialization)`. (b) At the top level of the file (after the `kotlin { }` block) add a SQLDelight config block:
  ```kotlin
  sqldelight {
      databases {
          create("ContentDatabase") {
              packageName.set("com.giraffe.matn.db")
          }
      }
  }
  ```
  (c) In `sourceSets`, add to `commonMain.dependencies { }`: `implementation(libs.sqldelight.runtime)`, `implementation(libs.sqldelight.coroutines)`, `implementation(libs.kotlinx.coroutines.core)`, `implementation(libs.kotlinx.serialization.json)`, `implementation(libs.koin.core)`. Add to `androidMain.dependencies { }`: `implementation(libs.sqldelight.android.driver)`. Add a new `iosMain.dependencies { }` block with `implementation(libs.sqldelight.native.driver)`. Add to `commonTest.dependencies { }`: `implementation(libs.kotlinx.coroutines.test)`, `implementation(libs.koin.test)`. Add the JVM host-test JDBC driver by adding, inside `sourceSets { }`, `val androidHostTest by getting { dependencies { implementation(libs.sqldelight.sqlite.driver) } }`. **If `by getting` fails to resolve** (source set not yet created by the AGP KMP-library plugin), use `getByName("androidHostTest").dependencies { implementation(libs.sqldelight.sqlite.driver) }` instead. Do NOT remove any existing dependency.
- [X] T003 [P] Verify the build resolves the new dependencies by running `gradlew :shared:dependencies` (or `gradlew :shared:help`). Fix any typo in T001/T002 before proceeding. No source files are created in this task.

**Checkpoint**: `gradlew :shared:assemble` still succeeds with the new libraries on the classpath.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, generated DB types, core Result type, ALL domain models, ALL repository interfaces, error types, seed DTOs, platform drivers, and the in-memory test driver. Every user story depends on this phase.

**⚠️ CRITICAL**: No user-story work may begin until this phase is complete and `gradlew :shared:assemble` passes.

### Database schema & generated types

- [X] T004 Create the SQLDelight schema at `SQLDIR/Content.sq`. Copy the four `CREATE TABLE` statements (`matn`, `chapter`, `verse`, `audio_asset`) and the three `CREATE INDEX` statements EXACTLY as written in `data-model.md` (section "SQLDelight schema"). Then add these named queries below the tables (labelled SQL statements SQLDelight compiles into functions):
  - `selectMatnById: SELECT * FROM matn WHERE id = ?;`
  - `selectAllMatn: SELECT * FROM matn ORDER BY title;`
  - `selectChaptersByMatn: SELECT * FROM chapter WHERE matn_id = ? ORDER BY display_order;`
  - `selectVersesByMatn: SELECT * FROM verse WHERE matn_id = ? ORDER BY display_number;`
  - `selectVersesByChapter: SELECT * FROM verse WHERE chapter_id = ? ORDER BY display_number;`
  - `selectVerseById: SELECT * FROM verse WHERE id = ?;`
  - `selectAudioForVerse: SELECT * FROM audio_asset WHERE verse_id = ? AND reciter_id = ?;`
  - `selectVerseIdsByMatn: SELECT id FROM verse WHERE matn_id = ?;`
  - `upsertMatn: INSERT INTO matn(id,title,author,description,cover_image_ref,structure_kind) VALUES (?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET title=excluded.title, author=excluded.author, description=excluded.description, cover_image_ref=excluded.cover_image_ref, structure_kind=excluded.structure_kind;`
  - `upsertChapter: INSERT INTO chapter(id,matn_id,title,display_order) VALUES (?,?,?,?) ON CONFLICT(id) DO UPDATE SET matn_id=excluded.matn_id, title=excluded.title, display_order=excluded.display_order;`
  - `upsertVerse: INSERT INTO verse(id,matn_id,chapter_id,display_number,arabic_text,duration_ms) VALUES (?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET matn_id=excluded.matn_id, chapter_id=excluded.chapter_id, display_number=excluded.display_number, arabic_text=excluded.arabic_text, duration_ms=excluded.duration_ms;`
  - `upsertAudioAsset: INSERT INTO audio_asset(id,verse_id,reciter_id,file_ref,duration_ms) VALUES (?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET verse_id=excluded.verse_id, reciter_id=excluded.reciter_id, file_ref=excluded.file_ref, duration_ms=excluded.duration_ms;`
  - `deleteAudioByMatn: DELETE FROM audio_asset WHERE verse_id IN (SELECT id FROM verse WHERE matn_id = ?);`
  - `deleteVersesByMatn: DELETE FROM verse WHERE matn_id = ?;`
  - `deleteChaptersByMatn: DELETE FROM chapter WHERE matn_id = ?;`
  - `deleteMatnById: DELETE FROM matn WHERE id = ?;`
- [X] T005 Run `gradlew :shared:generateCommonMainContentDatabaseInterface` (or `gradlew :shared:assemble`) to generate the SQLDelight types. Confirm `ContentDatabase`, `MatnQueries`, `ChapterQueries`, `VerseQueries`, `AudioAssetQueries`, and the row data classes (`Matn`, `Chapter`, `Verse`, `Audio_asset`) are generated under package `com.giraffe.matn.db`. If generation fails, fix `Content.sq` before continuing. NOTE for later mapper tasks: the generated row types share simple names with domain models — always import generated types with an alias, e.g. `import com.giraffe.matn.db.Matn as MatnRow`.

### Core & domain types (all pure Kotlin, no framework imports)

- [X] T006 [P] Create the shared result + error types at `COMMON/core/Resource.kt`. Define exactly:
  ```kotlin
  package com.giraffe.matn.core

  sealed interface Resource<out T> {
      data class Success<T>(val data: T) : Resource<T>
      data class Failure(val error: AppError) : Resource<Nothing>
  }

  sealed interface AppError {
      data class Storage(val message: String) : AppError
      data object NotFound : AppError
  }
  ```
  (`ContentIntegrityError` will also implement `AppError` in T012.)
- [X] T007 [P] Create `COMMON/domain/model/StructureKind.kt`: `enum class StructureKind { SIMPLE, STRUCTURED }` in package `com.giraffe.matn.domain.model`.
- [X] T008 [P] Create `COMMON/domain/model/Matn.kt` — `data class Matn` in package `com.giraffe.matn.domain.model` with fields exactly as in data-model.md: `id: String, title: String, author: String, description: String, coverImageRef: String?, structureKind: StructureKind`.
- [X] T009 [P] Create `COMMON/domain/model/Chapter.kt` — `data class Chapter` with `id: String, matnId: String, title: String, order: Int`.
- [X] T010 [P] Create `COMMON/domain/model/Verse.kt` — `data class Verse` with `id: String, matnId: String, chapterId: String?, displayNumber: Int, arabicText: String, durationMs: Long`.
- [X] T011 [P] Create `COMMON/domain/model/AudioAsset.kt` — `data class AudioAsset` with `id: String, verseId: String, reciterId: String, fileRef: String, durationMs: Long`.
- [X] T012 Create `COMMON/domain/error/ContentIntegrityError.kt` in package `com.giraffe.matn.domain.error`. Copy the sealed interface EXACTLY from `contracts/repositories.md` (cases: `DuplicateDisplayNumber(matnId, number)`, `MissingAudio(verseId)`, `DuplicateAudioRef(fileRef)`, `OrphanChapterRef(verseId)`, `StructureMismatch(matnId, detail)`, `InvalidId(detail)`, `Aggregate(problems: List<ContentIntegrityError>)`). It MUST `import com.giraffe.matn.core.AppError` and be declared `sealed interface ContentIntegrityError : AppError`.

### Repository interfaces & seed contract (domain layer)

- [X] T013 [P] Create `COMMON/domain/repository/MatnRepository.kt` (package `com.giraffe.matn.domain.repository`) with the three signatures from contracts/repositories.md: `suspend fun getMatn(id: String): Resource<Matn?>`, `fun observeLibrary(): Flow<List<Matn>>`, `suspend fun getChapters(matnId: String): Resource<List<Chapter>>`. Import `kotlinx.coroutines.flow.Flow`, `com.giraffe.matn.core.Resource`, and the models.
- [X] T014 [P] Create `COMMON/domain/repository/VerseRepository.kt` with: `fun observeVerses(matnId: String): Flow<List<Verse>>`, `suspend fun getVersesByChapter(chapterId: String): Resource<List<Verse>>`, `suspend fun getVerse(id: String): Resource<Verse?>`.
- [X] T015 [P] Create `COMMON/domain/repository/AudioAssetRepository.kt` with a companion `const val DEFAULT_RECITER = "reciter-default-v1"` and `suspend fun getAudioForVerse(verseId: String, reciterId: String = DEFAULT_RECITER): Resource<AudioAsset?>`. This exact `DEFAULT_RECITER` string is the value every fixture's `defaultReciterId` must match.
- [X] T016 [P] Create the seed DTOs at `COMMON/data/seed/SeedContent.kt` (package `com.giraffe.matn.data.seed`), matching contracts/seed-content.md JSON shape. All classes annotated `@Serializable` (import `kotlinx.serialization.Serializable`):
  ```kotlin
  @Serializable data class SeedMatn(
      val id: String, val title: String, val author: String, val description: String,
      val coverImageRef: String? = null, val structureKind: String, val defaultReciterId: String,
      val chapters: List<SeedChapter> = emptyList(), val verses: List<SeedVerse>)
  @Serializable data class SeedChapter(val id: String, val title: String, val order: Int)
  @Serializable data class SeedVerse(
      val id: String, val chapterId: String? = null, val displayNumber: Int,
      val arabicText: String, val durationMs: Long, val audio: SeedAudio? = null)
  @Serializable data class SeedAudio(val id: String, val fileRef: String, val durationMs: Long)
  ```
- [X] T017 [P] Create the loader interface at `COMMON/data/seed/ContentSeedLoader.kt`: `interface ContentSeedLoader { suspend fun load(payload: SeedMatn): Resource<com.giraffe.matn.domain.model.Matn> }`.

### Row → domain mappers (data layer)

- [X] T018 Create `COMMON/data/mapper/ContentMappers.kt` (package `com.giraffe.matn.data.mapper`). Add four `internal fun ...toDomain()` extension mappers converting each generated row type (import with `as ...Row` aliases per T005) to its domain model. For Matn map `structure_kind` string via `StructureKind.valueOf(...)`. Map `cover_image_ref`→`coverImageRef`, `matn_id`→`matnId`, `chapter_id`→`chapterId`, `display_number`→`displayNumber` (`.toInt()`), `display_order`→`order` (`.toInt()`), `arabic_text`→`arabicText`, `duration_ms`→`durationMs`, `verse_id`→`verseId`, `reciter_id`→`reciterId`, `file_ref`→`fileRef`. Do NOT transform `arabic_text`.

### Platform database drivers (expect/actual — no business logic)

- [X] T019 Create `COMMON/data/db/DatabaseDriverFactory.kt`: `expect class DatabaseDriverFactory { fun createDriver(): app.cash.sqldelight.db.SqlDriver }`. Also add `COMMON/data/db/DatabaseBuilder.kt` with `fun buildDatabase(factory: DatabaseDriverFactory): com.giraffe.matn.db.ContentDatabase = com.giraffe.matn.db.ContentDatabase(factory.createDriver())`.
- [X] T020 [P] Create `ANDROID/data/db/DatabaseDriverFactory.android.kt`: `actual class DatabaseDriverFactory(private val context: android.content.Context)` whose `createDriver()` returns `AndroidSqliteDriver(ContentDatabase.Schema, context, "content.db")` (import `app.cash.sqldelight.driver.android.AndroidSqliteDriver` and `com.giraffe.matn.db.ContentDatabase`). No other logic.
- [X] T021 [P] Create `IOS/data/db/DatabaseDriverFactory.ios.kt`: `actual class DatabaseDriverFactory` whose `createDriver()` returns `NativeSqliteDriver(ContentDatabase.Schema, "content.db")` (import `app.cash.sqldelight.driver.native.NativeSqliteDriver`). No other logic.

### In-memory test driver (so tests need no device)

- [X] T022 Create `TEST/db/TestDatabase.kt` in package `com.giraffe.matn` with `expect fun inMemoryDriver(): app.cash.sqldelight.db.SqlDriver`, plus a convenience `fun newTestDatabase(): com.giraffe.matn.db.ContentDatabase = com.giraffe.matn.db.ContentDatabase(inMemoryDriver())`. **Fan-out note**: this `expect` requires exactly one `actual` per test target that compiles `commonTest`. This project's host tests live in `androidHostTest` (T023) and iOS tests in `iosTest` (T024). After T023/T024, run `gradlew :shared:compileTestKotlinIosSimulatorArm64` and the Android host-test compile to confirm no target is left without an `actual`. If the Android **device**-test tree also inherits `commonTest`, either add an `androidDeviceTest` actual (using `AndroidSqliteDriver` with an in-memory config) or exclude it — Phase 0 requires no device tests.
- [X] T023 [P] Create the JVM host actual at `HOSTTEST/db/TestDatabase.jvm.kt`: `actual fun inMemoryDriver(): SqlDriver { val d = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY); ContentDatabase.Schema.create(d); return d }` (import `app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver` and `com.giraffe.matn.db.ContentDatabase`).
- [X] T024 [P] Create the iOS test actual at `IOSTEST/db/TestDatabase.ios.kt`: `actual fun inMemoryDriver(): SqlDriver = inMemoryDriver(ContentDatabase.Schema)` using `app.cash.sqldelight.driver.native.inMemoryDriver` (the native in-memory helper). Import `com.giraffe.matn.db.ContentDatabase`.

**Checkpoint**: `gradlew :shared:assemble` compiles. All types, interfaces, mappers, drivers, and the test driver exist. User stories can now begin.

---

## Phase 3: User Story 1 - Persist and read back a simple matn (Priority: P1) 🎯 MVP

**Goal**: Load one flat (no-chapter) matn into the store and read it back: metadata + every verse in `display_number` order, Arabic diacritics intact, offline. Loading is atomic — any integrity violation persists nothing.

**Independent Test**: Load the embedded simple-matn fixture, then read the matn by id and its verses; assert metadata correct, all verses returned in `displayNumber` order, `arabicText` byte-identical; assert invalid fixtures persist zero rows.

### Tests for User Story 1 (write FIRST — must be red until implementation below) ⚠️

- [X] T025 [P] [US1] Create `TEST/data/ContentFixtures.kt` (package `com.giraffe.matn`) holding fixtures as embedded triple-quoted JSON `String` constants plus a decode helper. Provide `private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }` and `fun parseSeed(raw: String): SeedMatn = json.decodeFromString(raw)`. Constants to define:
  - `SIMPLE_MATN_JSON`: `structureKind: "SIMPLE"`, no `chapters`, `defaultReciterId: "reciter-default-v1"` (**must equal `AudioAssetRepository.DEFAULT_RECITER`**), and 3–4 verses with ascending `displayNumber` 1..N, no `chapterId`, each with an `audio` object whose `fileRef` is UNIQUE. At least one verse's `arabicText` MUST contain heavy diacritics, e.g. `"الكَلامُ هُوَ اللَّفظُ المُرَكَّبُ المُفيدُ بِالوَضعِ"`. Use distinct authored UUID strings for every id.
  - `INVALID_DUPLICATE_ORDER_JSON`: like the simple one but two verses share `displayNumber: 1` (otherwise valid).
  - `INVALID_MISSING_AUDIO_JSON`: like the simple one but one verse has no `audio` object.
- [X] T026 [US1] Create `TEST/data/SimpleMatnTest.kt` using `kotlin.test` + `kotlinx.coroutines.test.runTest`. Each test builds a fresh DB with `newTestDatabase()` and wires `ContentSeedLoaderImpl`, `MatnRepositoryImpl`, `VerseRepositoryImpl` directly (these classes are created in T027–T030; the file is red until then). Tests (function names in parentheses trace to the contract's named scenarios):
  - `simpleMatnRoundTrip` → `SimpleMatnRoundTripTest` (SC-001): load `SIMPLE_MATN_JSON`; `getMatn(id)` returns correct title/author/description/structureKind; `observeVerses(id).first()` returns all N verses in ascending `displayNumber` order.
  - `diacriticsPreserved` → `DiacriticRoundTripTest` (SC-003): the loaded diacritic-heavy verse's `arabicText` equals the exact source string, character-for-character.
  - `atomicRejectDuplicateOrder` → `AtomicRejectDuplicateOrderTest` (SC-008): loading `INVALID_DUPLICATE_ORDER_JSON` returns `Resource.Failure` whose error is/contains `DuplicateDisplayNumber`, AND `getMatn(id)` afterward returns `Success(null)` (zero rows persisted).
  - `atomicRejectMissingAudio` → `AtomicRejectMissingAudioTest` (SC-008): loading `INVALID_MISSING_AUDIO_JSON` returns `Failure` containing `MissingAudio`, AND zero rows persisted.
  - `reloadDedup` → `ReloadDedupTest` (SC-007): load `SIMPLE_MATN_JSON` twice; verse count is unchanged and ids identical after the second load.
  - `notFound` → `NotFoundTest` (edge case): `getMatn("does-not-exist")` returns `Resource.Success(null)`, no throw.

### Implementation for User Story 1

- [X] T027 [US1] Implement `COMMON/data/repository/MatnRepositoryImpl.kt` (package `com.giraffe.matn.data.repository`), constructor `class MatnRepositoryImpl(private val db: ContentDatabase) : MatnRepository`. `getMatn` runs `selectMatnById(id).executeAsOneOrNull()`, maps via T018, and returns `Resource.Success(matnOrNull)` (null → `Success(null)`, never `Failure`). `observeLibrary` returns `db.matnQueries.selectAllMatn().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toDomain() } }` (import `app.cash.sqldelight.coroutines.asFlow`, `mapToList`). `getChapters` runs `selectChaptersByMatn(matnId)` and returns `Resource.Success(list)` — an **empty list for a simple matn is correct, not an error** (FR-012). Wrap DB exceptions in `Resource.Failure(AppError.Storage(...))`.
- [X] T028 [US1] Implement `COMMON/data/repository/VerseRepositoryImpl.kt`, `class VerseRepositoryImpl(private val db: ContentDatabase) : VerseRepository`. `observeVerses(matnId)` → `selectVersesByMatn(matnId).asFlow().mapToList(...)` mapped to domain, ordered by `display_number` (the query already orders). `getVersesByChapter(chapterId)` → `selectVersesByChapter(...)` wrapped in `Resource.Success`. `getVerse(id)` → `selectVerseById(id).executeAsOneOrNull()` → `Resource.Success(verseOrNull)`.
- [X] T029 [US1] Implement the atomic loader `COMMON/data/seed/ContentSeedLoaderImpl.kt`, `class ContentSeedLoaderImpl(private val db: ContentDatabase) : ContentSeedLoader`. Structure `load(payload)` in two strict steps:
  1. **Validate BEFORE any write** — call the private `validate(payload): List<ContentIntegrityError>` (T030). If the list is non-empty, return `Resource.Failure(ContentIntegrityError.Aggregate(problems))` and write NOTHING.
  2. **Persist atomically** — only if validation passed, open `db.transaction { }` and: first call the delete group (`deleteAudioByMatn`, `deleteVersesByMatn`, `deleteChaptersByMatn`) for `payload.id` to clear any stale children, then upsert matn, upsert every chapter, upsert every verse, upsert every verse's audio asset (using `payload.defaultReciterId` as `reciter_id`). Because the whole body is one `transaction`, any thrown error rolls back — nothing persists (FR-019). On success return `Resource.Success(payload.toMatnDomain())` (map the DTO's scalar fields + `StructureKind.valueOf(payload.structureKind)`).
  Note: convert `displayNumber`/`order` `Int`→`Long` when calling the generated upserts (SQLDelight uses `Long` for INTEGER).
- [X] T030 [US1] Implement the private `validate(payload: SeedMatn): List<ContentIntegrityError>` inside `ContentSeedLoaderImpl` (same file as T029). Collect ALL problems (do not stop at first). Rules from data-model.md V1–V7 & seed-content.md:
  - V1 `InvalidId`: any `id` (matn, chapter, verse, audio) that is blank.
  - V2 `DuplicateDisplayNumber(payload.id, number)`: any `displayNumber` value appearing on more than one verse.
  - V4 `MissingAudio(verseId)`: any verse whose `audio` is null.
  - V5 `DuplicateAudioRef(fileRef)`: any `audio.fileRef` shared by two verses.
  - V6 `OrphanChapterRef(verseId)`: a verse whose non-null `chapterId` is not one of `payload.chapters[].id`.
  - V7 `StructureMismatch(payload.id, detail)`: `structureKind == "STRUCTURED"` but `chapters` empty OR any verse `chapterId` null; OR `structureKind == "SIMPLE"` but `chapters` non-empty OR any verse `chapterId` non-null.
  (V3 non-contiguous sequence is SOFT per data-model.md note — do NOT enforce it; uniqueness V2 is enough.)
- [X] T031 [US1] Create the Koin module `COMMON/di/ContentModule.kt` (package `com.giraffe.matn.di`) exposing `fun contentModule() = org.koin.dsl.module { single { buildDatabase(get()) } single<MatnRepository> { MatnRepositoryImpl(get()) } single<VerseRepository> { VerseRepositoryImpl(get()) } single<ContentSeedLoader> { ContentSeedLoaderImpl(get()) } }`. Note: `DatabaseDriverFactory` is platform-constructed, so it is provided by the caller's platform module — add a KDoc comment saying the app must also register a `single { DatabaseDriverFactory(...) }` per platform. (The `AudioAssetRepository` binding is added in US3 / T035.)

**Checkpoint**: Run `gradlew :shared:testDebugUnitTest` (or `:shared:androidHostTest`) — all US1 tests (T026) now pass. This is a shippable MVP.

---

## Phase 4: User Story 2 - Persist and read back a structured matn with chapters (Priority: P2)

**Goal**: Load a matn whose verses are grouped into ordered chapters and read the hierarchy back: chapters in order, each chapter's verses grouped & ordered, full global sequence coherent, coexisting with a simple matn.

**Independent Test**: Load the embedded structured fixture; assert `getChapters` returns chapters by `order`; `getVersesByChapter` returns only that chapter's verses ordered by `displayNumber`; both a simple and a structured matn in the same store read back independently.

> US1's repositories and loader already support chapters — US2 adds no production code, only a fixture and verification tests. (Test-first "must be red" does not apply here since the exercised code already exists; these tests should pass as soon as they are written correctly.)

- [X] T032 [P] [US2] Add a `STRUCTURED_MATN_JSON` constant to `TEST/data/ContentFixtures.kt`: `structureKind: "STRUCTURED"`, 2 chapters with `order` 1 and 2, `defaultReciterId: "reciter-default-v1"`, and 4–6 verses. Every verse has a `chapterId` pointing at one of the two chapters, unique ascending matn-global `displayNumber` across the whole matn (chapter 1 verses are a contiguous low slice, chapter 2 the next slice), and each verse a unique-`fileRef` audio object. Distinct authored UUIDs throughout. This edits the same file as T025 — if US1 is done, append the constant.
- [X] T033 [US2] Create `TEST/data/StructuredMatnTest.kt` (`kotlin.test` + `runTest`, fresh `newTestDatabase()`):
  - `chaptersInOrder` → `StructuredMatnTest` (SC-002): after loading `STRUCTURED_MATN_JSON`, `getChapters(matnId)` returns 2 chapters ordered by `order`.
  - `versesGroupedByChapter` (SC-002): `getVersesByChapter(chapter1Id)` returns only chapter 1's verses in `displayNumber` order; same for chapter 2; no verse mis-assigned.
  - `fullSequenceCoherent`: `observeVerses(matnId).first()` returns the full set in global `displayNumber` order spanning both chapters.
  - `simpleMatnHasNoChapters` → `SimpleMatnNoChaptersTest` (FR-012): load `SIMPLE_MATN_JSON`, `getChapters(simpleId)` returns an EMPTY list wrapped in `Success`, not an error.
  - `independentMatns` (US2 scenario 4): load simple + structured into the SAME db; each `getMatn`/verse read returns its own content with no interference.

**Checkpoint**: US1 + US2 both pass independently.

---

## Phase 5: User Story 3 - Resolve each verse to its dedicated audio asset (Priority: P3)

**Goal**: Every verse resolves to exactly one audio asset reference; no two verses share a `fileRef`; the model admits an alternate reciter for the same verse id with no schema change.

**Independent Test**: For a loaded matn, `getAudioForVerse(verseId)` returns exactly one asset for that verse; distinct verses yield distinct `fileRef`s; a second reciter's asset for the same verse id is independently resolvable.

### Tests for User Story 3 (write FIRST — red until T035) ⚠️

- [X] T034 [US3] Create `TEST/data/AudioResolutionTest.kt` (`kotlin.test` + `runTest`, fresh db, load `SIMPLE_MATN_JSON`, wire `AudioAssetRepositoryImpl` from T035 — red until then):
  - `oneAssetPerVerse` → `AudioResolutionTest` (SC-004): for each verse, `getAudioForVerse(verseId)` (default reciter) returns a non-null asset whose `verseId` matches. **This depends on the fixture's `defaultReciterId` being `"reciter-default-v1"` (= `DEFAULT_RECITER`)**; otherwise the default lookup returns null.
  - `uniqueFileRefs` (SC-004): the set of resolved `fileRef`s has no duplicates across verses.
  - `unknownVerseNull`: `getAudioForVerse("nope")` returns `Success(null)`.
  - `multiReciterModel` → `MultiReciterModelTest` (FR-015): directly `upsertAudioAsset` a SECOND asset for an existing `verseId` with a different `reciterId` and `fileRef`; assert `getAudioForVerse(verseId, "reciter-default-v1")` and `getAudioForVerse(verseId, secondReciterId)` each resolve to their own distinct asset — proving alternate reciters need no schema change.

### Implementation for User Story 3

- [X] T035 [US3] Implement `COMMON/data/repository/AudioAssetRepositoryImpl.kt`, `class AudioAssetRepositoryImpl(private val db: ContentDatabase) : AudioAssetRepository`. `getAudioForVerse(verseId, reciterId)` runs `selectAudioForVerse(verseId, reciterId).executeAsOneOrNull()`, maps via T018, returns `Resource.Success(assetOrNull)` (unknown pair → `Success(null)`). Then edit `COMMON/di/ContentModule.kt` (from T031) to add one binding inside the module: `single<AudioAssetRepository> { AudioAssetRepositoryImpl(get()) }`.

**Checkpoint**: All three user stories pass independently.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Performance validation, full-suite/offline confirmation, structural-requirement sign-off, and a doc note. No new behavior.

- [X] T036 [P] Create `TEST/data/PerformanceTest.kt` (SC-006): programmatically build a `SeedMatn` with 500 verses (SIMPLE, `defaultReciterId = "reciter-default-v1"`, unique displayNumbers 1..500, unique audio fileRefs), load it, measure the time of `observeVerses(matnId).first()`; assert it returns 500 verses and completes in under 1000 ms against the in-memory driver. Use `kotlin.time` measurement.
- [X] T037 [P] Add a short "Phase 0 foundation" section to a `shared/README.md` (create if absent) describing: how to run `gradlew :shared:assemble` and `gradlew :shared:testDebugUnitTest`, and that the app must register a platform `DatabaseDriverFactory` in Koin. Do not document internals already in `specs/`.
- [X] T038 Run the full validation suite and sign off structural requirements:
  - Run `gradlew :shared:assemble`, then `gradlew :shared:testDebugUnitTest` (and, on macOS, `:shared:iosSimulatorArm64Test`); confirm all quickstart.md scenarios 1–12 are covered and green.
  - **FR-016** (no schema assumptions blocking the online/sync roadmap): visually confirm `Content.sq` has no download/file-size/order-coupled columns and that `audio_asset.reciter_id` is a first-class column — record this in the PR description.
  - **SC-005** (zero network): confirm the `:shared` module declares no networking dependency and no test performs any network call — the in-memory driver is the only I/O. Record this in the PR description.
  - Fix any failure at its source (never by weakening a test's assertion).

---

## Dependencies & Execution Order

### Phase order
- **Setup (P1: T001–T003)** → **Foundational (P2: T004–T024)** → **US1 (P3: T025–T031)** → **US2 (P4: T032–T033)** → **US3 (P5: T034–T035)** → **Polish (P6: T036–T038)**.
- Foundational BLOCKS all user stories. Do not start T025 until T024 is done and the module assembles.

### Test-first note (per story)
- **US1**: author T025 (fixtures) + T026 (tests) first; they stay red until T027–T031 exist, then pass.
- **US3**: author T034 (tests) first; red until T035, then passes.
- **US2**: T032/T033 exercise code already built in US1, so they pass once written correctly (no red phase).

### Key hard dependencies
- T004 (schema) → T005 (generate) → everything that imports `com.giraffe.matn.db.*` (T018 mappers, T019–T024 drivers, all repos).
- T006 (Resource/AppError) → T012 (`ContentIntegrityError`) and every repository/loader signature.
- T007–T011 (models) → T013–T018.
- T027–T030 (US1 repos + loader) make T026 pass and are reused unchanged by US2/US3.
- T035 edits the file created in T031 — do US1's T031 first.

### Story independence
- **US1** stands alone (MVP). **US2** reuses US1 code, adds only a fixture + tests. **US3** adds one new repo file + one Koin line + tests. Each story's tests run against a fresh in-memory DB, so all three are independently testable.

---

## Parallel Opportunities

- **Setup**: T003 is a check; T001 then T002 are sequential (T002 needs T001's aliases).
- **Foundational**: after T005, these are all `[P]` (different files): T006, T007–T011, T013–T017, T018, T020, T021, T023, T024. Sequential anchors: T004→T005; T019 before T020/T021; T022 before T023/T024.
- **US1**: fixtures T025 `[P]`; tests T026 after T025; impls T027/T028 `[P]` (different files); T029+T030 same file (sequential); T031 after impls.
- **US2**: T032 then T033.
- **US3**: T034 (tests) then T035 (impl).
- **Polish**: T036 and T037 `[P]`; T038 last.

### Example parallel batch (Foundational, after T005)
```
T006 Resource.kt · T007 StructureKind.kt · T008 Matn.kt · T009 Chapter.kt ·
T010 Verse.kt · T011 AudioAsset.kt   (all different files, no interdependencies)
```

---

## Implementation Strategy

1. **MVP**: Setup → Foundational → US1 (write tests T025/T026 first, then T027–T031), then STOP and run US1 tests. A single simple matn that loads atomically and reads back in order with diacritics intact is the demonstrable foundation.
2. **Increment**: add US2 (structure), then US3 (audio resolution) — each adds value without touching US1's passing tests.
3. **Finish**: Polish (performance + full-suite gate + FR-016/SC-005 sign-off).

---

## Notes
- `[P]` = different file, no unfinished dependency. `[US#]` maps a task to its story.
- Tests are authored before implementation within a story (Principle V); a story is not done until its tests pass.
- NEVER generate UUIDs; NEVER normalize/trim `arabicText`.
- Every fixture's `defaultReciterId` MUST be `"reciter-default-v1"` (= `AudioAssetRepository.DEFAULT_RECITER`) so default-reciter audio resolution works.
- Fixtures are embedded Kotlin `String` constants in `ContentFixtures.kt` — no `resources/` files (portable to iOS/native tests).
- Generated SQLDelight row types collide by name with domain models — always import them with an `as ...Row` alias.
- SQLDelight maps SQL `INTEGER` to Kotlin `Long`; convert `Int` display numbers/orders at the boundary.
- Do not weaken a test to make it pass — fix the code.
