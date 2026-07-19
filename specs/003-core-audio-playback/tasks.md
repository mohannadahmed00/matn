---
description: "Task list for Phase 2 — Core Audio Playback"
---

# Tasks: Phase 2 — Core Audio Playback

**Input**: Design documents from `/specs/003-core-audio-playback/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Included. Constitution Principle V (Test-First & Testable Design, NON-NEGOTIABLE)
requires `commonTest` unit tests for all changed domain/data/controller/ViewModel logic in the
same change. Platform-only behaviour (gapless, background service, wake lock, real interruptions)
is validated on-device per [quickstart.md](./quickstart.md).

**Organization**: Grouped by user story (US1–US3) so each is independently implementable and
testable. Priority order from spec.md: **US1 (P1, MVP)** → US2 (P2) → US3 (P3).

---

## Conventions for the implementer (READ FIRST)

- **Base package / root**: all Kotlin below lives under
  `shared/src/commonMain/kotlin/com/giraffe/matn/` unless a path says `androidMain`, `iosMain`,
  or `commonTest`. Android app entry lives in `androidApp/src/main/...`; iOS entry in `iosApp/`.
- **Reuse Phase 0/1, do NOT redefine it**. These already exist and must not be re-created:
  - Models: `Verse(id,matnId,chapterId,displayNumber,arabicText,durationMs)`,
    `AudioAsset(id,verseId,reciterId,fileRef,durationMs)`, `Matn`, `Chapter`.
  - Core: `Resource<T>` = `com.giraffe.matn.core.Resource` (`Success(data)` / `Failure(error)`);
    `AppError.NotFound`, `AppError.Storage(msg)`.
  - Contracts: `UseCase<P,R>` (`suspend operator fun invoke(params): Resource<R>`),
    `FlowUseCase<P,R>` (`operator fun invoke(params): Flow<R>`), `BaseViewModel<S>`
    (`state: StateFlow<S>`, `setState { }`, `runUseCase(...)`, `viewModelScope`).
  - Data: `ContentDatabase` (SQLDelight, `db.contentQueries.*`), `AudioAssetRepository`
    (`getAudioForVerse`, `DEFAULT_RECITER = "reciter-default-v1"`), `VerseRepository`
    (`observeVerses(matnId): Flow<List<Verse>>`), `AudioAssetRepositoryImpl` (uses `storageCall{}`),
    `ContentMappers.kt` (`toDomain()`), `contentModule()` (Koin), `initMatnKoin(driverFactory)` +
    `MatnKoinHolder.koin`.
  - UI: `MatnDetailsScreen.kt` / `MatnDetailsContent` / `VerseRowItem` / `MatnDetailsUiState`,
    `MatnDetailsViewModel`, `MatnNavHost.kt`, `MatnTheme`, `verseFontFamily()`.
- **Platform audio edge is delivered by DI injection, NOT `expect`/`actual`** (simpler, always
  compiles). `AudioEngine` and `WakeLock` are plain `commonMain` **interfaces**. Their concrete
  implementations live in `androidMain` / `iosMain` as ordinary classes and are handed to the
  shared Koin graph through an **extended `initMatnKoin(...)`**, exactly like the existing
  `DatabaseDriverFactory` is passed in from `MainActivity`. `commonMain` never references a
  platform class. (This supersedes the `expect AudioEngineFactory` sketch in the contracts.)
- **All playback DECISIONS live in `PlaybackController` (`commonMain`)**. The platform engines
  only translate calls to ExoPlayer/AVQueuePlayer and translate native callbacks into
  `AudioEngineEvent`s — **no business logic** (Principle IV).
- **Terminology**: the spec's "persistent control bar" is implemented as the **player bar**
  (`presentation/player/PlayerBar.kt` / `PlayerBarContent`). "player bar", "control bar", and
  "persistent control bar" all refer to the same component.
- **RTL & typography**: never use `left`/`right`; only `start`/`end`. Verse text uses
  `verseFontFamily()` and `Verse.arabicText` **verbatim**.
- **DI retrieval in Composables**: `MatnKoinHolder.koin.get<TheType>()`, then build a ViewModel
  with `androidx.lifecycle.viewmodel.compose.viewModel { TheViewModel(...) }` (see
  `MatnNavHost.kt` for the exact pattern already used).
- **Every phase must leave the app building and `commonTest` green** (Constitution: no phase may
  leave the app non-building). Commit after each task or logical group.
- Type shapes and state transitions are specified in [data-model.md](./data-model.md); engine and
  controller APIs in [contracts/audio-engine.md](./contracts/audio-engine.md) and
  [contracts/playback-contract.md](./contracts/playback-contract.md). When in doubt, follow those.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the Media3 dependency, bundle the sample audio, and add the one additive query
that every playback story needs.

- [X] T001 Add the Media3 dependency (Android only). In `gradle/libs.versions.toml` add version
  `media3 = "1.4.1"` under `[versions]`, and under `[libraries]` add:
  `media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }`,
  `media3-session = { module = "androidx.media3:media3-session", version.ref = "media3" }`,
  `media3-common = { module = "androidx.media3:media3-common", version.ref = "media3" }`.
  Then in `shared/build.gradle.kts` inside `sourceSets { androidMain.dependencies { ... } }` add
  `implementation(libs.media3.exoplayer)`, `implementation(libs.media3.session)`,
  `implementation(libs.media3.common)`. Do a Gradle sync / run `./gradlew :shared:assembleDebug`
  to confirm it resolves. (iOS uses AVFoundation — no dependency.)
- [X] T002 [P] Bundle sample per-verse audio. Create the folder
  `shared/src/commonMain/composeResources/files/audio/` and place short, correctly-trimmed `.mp3`
  clips named **exactly** like the `fileRef`s already in the Phase 0 seed
  (`shared/src/commonMain/kotlin/com/giraffe/matn/di/MatnKoinStarter.kt`), e.g.
  `ajurrumiyya_verse_001.mp3` … `ajurrumiyya_verse_004.mp3` and `structured_verse_001.mp3` …
  `structured_verse_005.mp3`. **Intentionally omit ONE file** (e.g. leave `structured_verse_003.mp3`
  out) so the skip-on-missing path (FR-020) can be exercised on-device; note which one in a short
  `README.txt` in that folder.
- [X] T003 [P] Add the additive ordered-audio query. In
  `shared/src/commonMain/sqldelight/com/giraffe/matn/db/Content.sq` append (no schema change):
  ```sql
  selectAudioForMatnOrdered:
  SELECT audio_asset.*
  FROM audio_asset
  JOIN verse ON verse.id = audio_asset.verse_id
  WHERE verse.matn_id = ? AND audio_asset.reciter_id = ?
  ORDER BY verse.display_number;
  ```
  Run `./gradlew :shared:generateSqlDelightInterface` (or a build) to confirm the generated query
  compiles.

**Checkpoint**: Media3 resolves, audio assets bundled, ordered query generated. App still builds.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared models, interfaces, queue use case, and test fakes that ALL stories build
on. Everything here is pure `commonMain` / `commonTest` — the app stays green.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T004 [P] Create `PlaybackSpeed` enum in `domain/model/PlaybackSpeed.kt` with steps
  `X0_5(0.5f), X0_75(0.75f), X1(1.0f), X1_25(1.25f), X1_5(1.5f)` each carrying a `val multiplier: Float`,
  and a `companion object { val DEFAULT = X1 }`. Add `fun next(): PlaybackSpeed` that cycles to the
  next step (X1_5 → X0_5) for the speed button (data-model.md §2.1).
- [X] T005 [P] Create `domain/model/AudioTrack.kt` with
  `data class AudioTrack(val verseId: String, val displayNumber: Int, val uri: String, val durationMs: Long)`
  and `data class PlaybackQueue(val matnId: String, val tracks: List<AudioTrack>, val startIndex: Int)`
  (data-model.md §2.2–2.3).
- [X] T006 [P] Create `domain/model/PlaybackStatus.kt` with
  `enum class PlaybackStatus { IDLE, LOADING, PLAYING, PAUSED, ENDED }`,
  `enum class PauseReason { USER, TRANSIENT_INTERRUPTION, NON_TRANSIENT_INTERRUPTION }`, and
  `sealed interface PlaybackNotice { data class SkippedMissingVerse(val verseId: String): PlaybackNotice; data object ReachedEnd: PlaybackNotice; data object NoPlayableAudio: PlaybackNotice }`
  (data-model.md §2.4–2.5, §2.7).
- [X] T007 Create `domain/model/PlaybackState.kt` — the single session snapshot data class exactly
  as in data-model.md §2.6 (fields: `status`, `matnId`, `activeVerseId`, `activeIndex`,
  `positionMs`, `durationMs`, `speed`, `pauseReason`, `notice`, plus computed `isPlaying` /
  `hasSession`). Depends on T004, T006.
- [X] T008 [P] Create the engine interface in `domain/audio/AudioEngine.kt` exactly as in
  [contracts/audio-engine.md](./contracts/audio-engine.md): the `AudioEngine` interface
  (`playbackInfo: StateFlow<EnginePlaybackInfo>`, `events: SharedFlow<AudioEngineEvent>`,
  `setQueue`, `play`, `pause`, `stop`, `seekTo`, `seekToTrack`, `setSpeed`, `release`), the
  `EnginePlaybackInfo` data class, and the `AudioEngineEvent` sealed interface
  (`TrackTransition`, `QueueEnded`, `TrackError`, `InterruptionBegan`, `InterruptionEnded`,
  `Ready`). Uses `com.giraffe.matn.domain.model.AudioTrack`.
- [X] T009 [P] Create `domain/audio/WakeLock.kt` — plain interface
  `interface WakeLock { fun acquire(); fun release() }` **and** a `commonMain` no-op fallback
  `object NoOpWakeLock : WakeLock { override fun acquire() {}; override fun release() {} }` in the
  same file. `NoOpWakeLock` is the default binding until US3 supplies the real platform wake locks
  (keeps US1/US2 compiling and running without any `expect`/`actual`). No platform code here.
- [X] T010 [P] Create the audio-source resolver: interface `domain/audio/AudioSourceResolver.kt`
  (`suspend fun resolve(fileRef: String): String`) and impl
  `data/audio/AudioSourceResolverImpl.kt` returning
  `org.jetbrains.compose.resources.Res.getUri("files/audio/$fileRef")` (research.md D7). Keep it an
  interface so it is faked in tests.
- [X] T011 Extend audio reads for a whole matn. In
  `domain/repository/AudioAssetRepository.kt` add
  `suspend fun getAudioForMatn(matnId: String, reciterId: String = DEFAULT_RECITER): Resource<List<AudioAsset>>`.
  Implement it in `data/repository/AudioAssetRepositoryImpl.kt` using
  `db.contentQueries.selectAudioForMatnOrdered(matnId, reciterId).executeAsList().map { it.toDomain() }`
  wrapped in the existing `storageCall({ "Failed to read audio for matn $matnId" }) { ... }` helper.
  Reuse the existing `AudioAsset` mapper in `data/mapper/ContentMappers.kt` (add an overload if the
  generated row type differs). Depends on T003.
- [X] T012 Create `domain/usecase/BuildPlaybackQueueUseCase.kt` implementing
  `UseCase<BuildPlaybackQueueUseCase.Params, PlaybackQueue>` per data-model.md §4.2. `Params(matnId,
  startVerseId: String?, reciterId = DEFAULT_RECITER)`. Body: get the ordered verses
  (`verseRepository.observeVerses(matnId).first()`), get ordered audio
  (`audioRepository.getAudioForMatn(...)`), build one `AudioTrack` per verse **in displayNumber
  order** (a verse with no audio row is a defensive case only — Phase 0 V4 validation guarantees
  every verse has audio — so just omit it; no notice), resolve each `fileRef` via
  `AudioSourceResolver.resolve(...)`, compute `startIndex` from `startVerseId` (null → 0), return
  `Resource.Success(PlaybackQueue(...))`, or `Resource.Failure(AppError.NotFound)` if zero tracks.
  (The FR-020 skip+notice is handled at runtime by the controller on `TrackError`, not here.)
  Depends on T005, T010, T011.
- [X] T013 [P] Create `commonTest/.../playback/FakeAudioEngine.kt` implementing `AudioEngine`
  in-memory: record calls (`lastQueue`, `playCalled`, `paused`, `speed`, `seekedToTrack`,
  `seekedToMs`) and expose test helpers `emit(event: AudioEngineEvent)` (pushes into the `events`
  `MutableSharedFlow`) and `setInfo(...)`. No real audio. Depends on T008.
- [X] T014 [P] Create `commonTest/.../playback/FakeWakeLock.kt` implementing `WakeLock` recording
  `acquired: Boolean` / counts. Depends on T009.
- [X] T015 Create `commonTest/.../playback/BuildPlaybackQueueUseCaseTest.kt`: with a faked
  `VerseRepository` + `AudioAssetRepository` + `AudioSourceResolver`, assert tracks come back in
  ascending `displayNumber`, `startIndex` resolves from `startVerseId`, a verse with no audio row is
  defensively omitted from the queue, and an empty matn → `Resource.Failure(NotFound)`. Depends on
  T012, T013.
- [X] T016 Register the foundational providers in `di/ContentModule.kt` (`contentModule()`):
  `single<AudioSourceResolver> { AudioSourceResolverImpl() }` and
  `factory { BuildPlaybackQueueUseCase(get(), get(), get()) }`. (The existing
  `single<AudioAssetRepository> { AudioAssetRepositoryImpl(get()) }` now also serves
  `getAudioForMatn` — no change needed.) Depends on T010, T012.

**Checkpoint**: `./gradlew :shared:allTests` passes (queue test green); no engine/controller yet.

---

## Phase 3: User Story 1 — Listen to a matn read aloud (Priority: P1) 🎯 MVP

**Goal**: Tap play on a verse → recitation plays from there, advances **gaplessly** verse to
verse, the active verse is highlighted and auto-scrolled into view, and play / pause / resume /
stop work; at the end of the matn playback stops.

**Independent Test**: Open a seeded matn, start from a verse, and verify audio plays, the verse
highlights, the list follows it, advances with no audible gap, stops at the end, and pause /
resume / stop behave — offline.

### Controller + tests (commonMain / commonTest)

- [X] T017 [US1] Create `playback/PlaybackController.kt` (app-scoped) per
  [contracts/playback-contract.md](./contracts/playback-contract.md), **core subset only**:
  ctor `(engine: AudioEngine, buildQueue: BuildPlaybackQueueUseCase, wakeLock: WakeLock, scope: CoroutineScope)`;
  `val state: StateFlow<PlaybackState>`. Implement `playFromVerse(matnId, verseId)` and
  `playFromStart(matnId)` (set LOADING, `buildQueue.invoke(...)`, `engine.setQueue(tracks, startIndex)`,
  `engine.play()`), `pause()` (PauseReason.USER), `resume()`, `stop()` (→ IDLE, clear
  `activeVerseId`/position). Collect `engine.events`: `Ready`→PLAYING; `TrackTransition(i)`→set
  `activeIndex=i`, `activeVerseId=queue.tracks[i].verseId`; `QueueEnded`→ENDED + `notice=ReachedEnd`
  + clear highlight; `TrackError(i)`→`engine.seekToTrack(i+1)` + `notice=SkippedMissingVerse`, or if
  none left ENDED + `notice=NoPlayableAudio`. Wire `wakeLock.acquire()` on entering PLAYING /
  `release()` on PAUSED/ENDED/IDLE. Add a throttled position ticker updating `positionMs` from
  `engine.playbackInfo`. Add `consumeNotice()`. (next/previous/seek/speed and interruptions are
  added in US2/US3.) Depends on T007, T008, T012.
- [X] T018 [P] [US1] Create `commonTest/.../playback/PlaybackControllerTest.kt` covering US1 with
  `FakeAudioEngine` + `FakeWakeLock` + faked queue use case: start-from-verse sets correct
  `startIndex` and PLAYING after `Ready`; `TrackTransition` moves `activeVerseId`; `QueueEnded`→
  ENDED + `ReachedEnd`, highlight cleared, **no** restart; `TrackError` skips one + notice, none
  left → `NoPlayableAudio`; `pause`/`resume` hold+continue; `stop`→IDLE cleared; wake lock acquired
  on PLAYING, released on PAUSED/ENDED. Depends on T017, T013, T014.

### Platform engines (androidMain / iosMain) — no business logic

- [X] T019 [P] [US1] Create `androidMain/.../audio/Media3AudioEngine.kt` implementing `AudioEngine`
  with Media3 `ExoPlayer` (ctor takes `android.content.Context`). `setQueue` →
  `player.setMediaItems(tracks.map { MediaItem.fromUri(it.uri) }); player.seekTo(startIndex, 0);
  player.prepare()`; `play/pause/stop/seekTo/seekToTrack(→seekTo(index,0))/setSpeed(→later)/release`.
  Add a `Player.Listener`: `onMediaItemTransition`→emit `TrackTransition(player.currentMediaItemIndex)`;
  `onPlaybackStateChanged(STATE_READY)` first time→`Ready`; `onPlaybackStateChanged(STATE_ENDED)`→
  `QueueEnded`; `onPlayerError`→`TrackError(player.currentMediaItemIndex)`. Push
  `EnginePlaybackInfo` from `isPlaying`/`currentPosition`/`duration`. (Gapless is automatic with a
  playlist — do not stop/recreate the player per verse.) Depends on T008.
- [X] T020 [P] [US1] Create `iosMain/.../audio/AvQueueAudioEngine.kt` implementing `AudioEngine`
  with `AVQueuePlayer`. `setQueue` → build `AVPlayerItem`s from `NSURL(string = uri)` and enqueue
  all (removeAllItems + insertItem for gapless), seek to `startIndex`; `play/pause/stop/seekTo/
  seekToTrack(advanceToNextItem or rebuild)/release`. Observe `currentItem` (KVO)→`TrackTransition`;
  add `NSNotificationCenter` observer for `AVPlayerItemDidPlayToEndTimeNotification` on the last
  item→`QueueEnded`; item `.status == .failed` / `AVPlayerItemFailedToPlayToEndTime`→`TrackError`.
  Push `EnginePlaybackInfo`. Depends on T008.

### DI + platform wiring

- [X] T021 [US1] Extend the Koin seam to accept the platform engine. In `di/MatnKoinStarter.kt`
  change `initMatnKoin(driverFactory)` to `initMatnKoin(driverFactory, audioEngine: AudioEngine)`
  and register `single<AudioEngine> { audioEngine }` in that startup module (same pattern as
  `single { driverFactory }`). **Do NOT add a `wakeLock` param yet** — that lands in US3/T039. In
  `di/ContentModule.kt` add: `single<WakeLock> { NoOpWakeLock }` (the US1/US2 default from T009),
  `single { PlaybackController(get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Main)) }`,
  and `factory { PlayerBarViewModel(get()) }`. Depends on T017, T019, T020.
- [X] T022 [US1] Pass the platform engine in from each shell. Android:
  `androidApp/src/main/kotlin/com/giraffe/matn/MainActivity.kt` → construct
  `Media3AudioEngine(applicationContext)` and pass it into `initMatnKoin(DatabaseDriverFactory(this), Media3AudioEngine(applicationContext))`.
  iOS: in `shared/src/iosMain/.../MainViewController.kt` (or the iOS bootstrap that calls
  `initMatnKoin`) construct `AvQueueAudioEngine()` and pass it. Depends on T021.

### Reading-screen integration + minimal player bar

- [X] T023 [US1] Extend `presentation/details/MatnDetailsUiState.kt`: add `val activeVerseId: String? = null`
  and `val isPlaying: Boolean = false` (data-model.md §5.1). Extend
  `presentation/details/MatnDetailsViewModel.kt` to take the `PlaybackController`, collect its
  `state` and fold `activeVerseId`/`isPlaying` into `MatnDetailsUiState` via `setState`, and add
  intents `onVersePlayClicked(verseId: String)` → `controller.playFromVerse(matnId, verseId)` and
  `onGlobalPlayClicked()` → `controller.playFromStart(matnId)`. Update the `viewModel { }` builder
  in `MatnNavHost.kt` to pass `MatnKoinHolder.koin.get<PlaybackController>()`. Depends on T017, T021.
- [X] T024 [US1] Add highlight + auto-scroll + play affordances in
  `presentation/details/MatnDetailsScreen.kt`. In `VerseRowItem`, highlight the row when
  `row.id == state.activeVerseId` (e.g. a tinted background / stronger rosette) and add a small
  **play** `IconButton` calling `onVersePlayClicked(row.id)`. In `VerseList`, add a
  `LaunchedEffect(state.activeVerseId)` that `animateScrollToItem` to the active verse using the
  existing `firstVerseIndex + verseOffset` math already in that file. Add a global play control in
  the header (`onGlobalPlayClicked`). Keep everything `start`/`end` (RTL). Depends on T023.
- [X] T025 [US1] Create the minimal player bar: `presentation/player/PlayerBarUiState.kt`
  (data-model.md §5.2, but for US1 only the fields used now: `visible`, `status`,
  `activeVerseNumber`, `speed` unused yet), `presentation/player/PlayerBarViewModel.kt`
  (`BaseViewModel<PlayerBarUiState>`, maps `PlaybackController.state`, intents `onPlayPause`,
  `onStop`), and stateless `presentation/player/PlayerBar.kt` (`PlayerBarContent(state, onPlayPause,
  onStop)`) rendering play/pause + stop, shown only when `state.visible`. Host `PlayerBarContent`
  at the bottom of the reading screen (a `Box`/`Column` wrapping the existing content + the bar).
  Add `@Preview`s for **playing**, **paused**, and **hidden** states with hand-built state (no
  ViewModel). Depends on T021, T017.

### Test + validation

- [X] T026 [P] [US1] Add a `MatnDetailsViewModel` highlight test in
  `commonTest/.../presentation/` (extend the existing `MatnDetailsViewModelTest.kt`): feeding a
  `PlaybackController` state (via a fake engine) with an `activeVerseId` sets
  `MatnDetailsUiState.activeVerseId`. Depends on T023.
- [ ] T027 [US1] Run the on-device US1 checks from [quickstart.md](./quickstart.md) §B rows 1–4 and
  13 (play-from-verse, gapless advance, highlight+auto-scroll, stop-at-end, pause/resume, offline)
  on Android and iOS. Depends on T022, T024, T025.

**Checkpoint**: MVP — a matn plays gaplessly with highlight/auto-scroll and pause/resume/stop.
Independently demoable.

---

## Phase 4: User Story 2 — Control and fine-tune playback (Priority: P2)

**Goal**: Next / previous verse, scrub within the active verse, and change playback speed
(0.5×–1.5×, pitch preserved), applied live and persisting across verse transitions.

**Independent Test**: During playback, use next/previous to move verses, scrub within the active
verse, and change speed — each takes effect immediately and speed carries to the next verse.

- [X] T028 [US2] Extend `playback/PlaybackController.kt` with `next()` (advance one index, or ENDED
  at the last verse — FR-012), `previous()` (if `positionMs > 2000` or `activeIndex == 0` restart
  the current verse via `engine.seekTo(0)`, else `engine.seekToTrack(activeIndex-1)`),
  `seekTo(positionMs)` (`engine.seekTo`), and `setSpeed(speed: PlaybackSpeed)` (store in state,
  `engine.setSpeed(speed.multiplier)`, and re-apply on each `TrackTransition` so it persists).
  Depends on T017.
- [X] T029 [P] [US2] Extend `PlaybackControllerTest.kt` with US2 scenarios: `next` advances / at
  last → ENDED; `previous` after >2s restarts current, at index 0 restarts, else steps back;
  `seekTo` updates position; `setSpeed` then a `TrackTransition` keeps the speed and re-applies it
  to the fake engine. Depends on T028, T013.
- [X] T030 [P] [US2] Extend `androidMain/.../audio/Media3AudioEngine.kt`: implement `seekTo` →
  `player.seekTo(positionMs)`, `seekToTrack(i)` → `player.seekTo(i, 0)`, `setSpeed(m)` →
  `player.setPlaybackParameters(PlaybackParameters(m, 1.0f))` (pitch preserved). Depends on T019.
- [X] T031 [P] [US2] Extend `iosMain/.../audio/AvQueueAudioEngine.kt`: `seekTo` →
  `currentItem.seek(toTime)`, `seekToTrack` → `advanceToNextItem()` / rebuild to index, `setSpeed(m)`
  → set `player.rate = m` and each item's `audioTimePitchAlgorithm = AVAudioTimePitchAlgorithmTimeDomain`
  (pitch preserved). Depends on T020.
- [X] T032 [US2] Enrich the player bar. Extend `PlayerBarUiState` with `positionMs`, `durationMs`,
  `speed`, `canNext`, `canPrevious` (data-model.md §5.2); extend `PlayerBarViewModel` with intents
  `onNext`, `onPrevious`, `onSeek(pos)`, `onSpeedSelected` (→ controller); extend
  `PlayerBar.kt` with previous/next buttons, a scrub `Slider` bound to `positionMs`/`durationMs`
  (FR-013), and a speed button showing `state.speed` and calling `onSpeedSelected(state.speed.next())`.
  Add `@Preview`s for the enriched **playing** and **loading** states. Depends on T025, T028.
- [ ] T033 [US2] Run on-device US2 checks from [quickstart.md](./quickstart.md) §B rows 5–7
  (next/previous, scrub, speed) on Android and iOS. Depends on T032, T030, T031.

**Checkpoint**: US1 + US2 work — full in-session transport control.

---

## Phase 5: User Story 3 — Hands-free & interruptions (Priority: P3)

**Goal**: Background playback with media-notification / lock-screen controls, screen wake lock
while playing, and graceful audio-interruption handling (transient → auto-resume; non-transient →
manual resume).

**Independent Test**: Start playback, background/lock the device (audio continues, controllable
from the notification), confirm the screen stays awake while playing, then trigger a call /
focus loss / Bluetooth disconnect and confirm playback pauses and resumes per policy.

- [X] T034 [US3] Extend `playback/PlaybackController.kt` with interruption handling: on
  `InterruptionBegan(transient)` → pause with `PauseReason.TRANSIENT_INTERRUPTION` or
  `NON_TRANSIENT_INTERRUPTION`; on `InterruptionEnded(shouldResume)` → auto-`resume()` **only** if
  the current `pauseReason == TRANSIENT_INTERRUPTION` and `shouldResume`; never auto-resume a
  `USER` pause. Confirm wake-lock gating (acquire on PLAYING / release otherwise) from T017 covers
  FR-017. Depends on T017, T009.
- [X] T035 [P] [US3] Extend `PlaybackControllerTest.kt` with US3 scenarios: transient interruption
  → PAUSED(TRANSIENT) then auto-resume on end; non-transient → PAUSED(NON_TRANSIENT) stays paused;
  a `USER` pause is not auto-resumed by a later `InterruptionEnded`; wake lock acquired on PLAYING
  and released on PAUSED/ENDED (via `FakeWakeLock`). Depends on T034, T013, T014.
- [X] T036 [US3] Android background + focus + wake lock. Create
  `androidMain/.../audio/MatnMediaSessionService.kt` (a `MediaSessionService` holding a
  `MediaSession` wrapping the ExoPlayer) so playback runs as a foreground service with a
  media-style notification (play/pause + next/previous) mirrored to the lock screen. **The
  notification/lock-screen next/previous and play/pause MUST be routed through
  `PlaybackController` (`next()`/`previous()`/`pause()`/`resume()` from T017/T028), not straight to
  the raw ExoPlayer**, so `PlaybackState` (active verse, highlight) stays consistent whether the
  user taps the in-app bar or the notification — implement the `MediaSession` command callbacks to
  call the controller. In `Media3AudioEngine`, configure
  `setAudioAttributes(AudioAttributes.Builder().setUsage(USAGE_MEDIA)
  .setContentType(CONTENT_TYPE_SPEECH).build(), handleAudioFocus = true)` and map focus loss /
  `ACTION_AUDIO_BECOMING_NOISY` to `InterruptionBegan/Ended` events. Create
  `androidMain/.../audio/AndroidWakeLock.kt` implementing `WakeLock` by toggling
  `FLAG_KEEP_SCREEN_ON` on the provided Activity's window (`acquire`/`release` on the main thread).
  Depends on T019, T009, **T028** (needs the controller's `next()`/`previous()`).
- [X] T037 [US3] Android manifest. In `androidApp/src/main/AndroidManifest.xml` add
  `<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>`,
  `...FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `...POST_NOTIFICATIONS`, `...WAKE_LOCK`, and declare the
  service `<service android:name="com.giraffe.matn.audio.MatnMediaSessionService"
  android:exported="false" android:foregroundServiceType="mediaPlayback">` with the
  `<intent-filter><action android:name="androidx.media3.session.MediaSessionService"/></intent-filter>`.
  Request `POST_NOTIFICATIONS` at runtime on Android 13+ in `MainActivity`. Depends on T036.
- [X] T038 [US3] iOS background + focus + wake lock. In `AvQueueAudioEngine` configure
  `AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback)` +
  `setActive(true)`, observe interruption notifications (`AVAudioSessionInterruptionNotification` →
  `InterruptionBegan/Ended(shouldResume)`) and route changes
  (`AVAudioSessionRouteChangeNotification`, reason old-device-unavailable → `InterruptionBegan(false)`),
  and publish `MPNowPlayingInfoCenter` + wire `MPRemoteCommandCenter` play/pause/next/previous —
  **routed through `PlaybackController` (`next()`/`previous()`/`pause()`/`resume()` from T017/T028),
  not the raw player**, so `PlaybackState` stays consistent (same rule as Android/T036). Create
  `iosMain/.../audio/IosWakeLock.kt` implementing `WakeLock` via
  `UIApplication.sharedApplication.idleTimerDisabled = true/false`. In `iosApp/iosApp/Info.plist`
  add `<key>UIBackgroundModes</key><array><string>audio</string></array>`. Depends on T020, T009,
  **T028**.
- [X] T039 [US3] Swap the no-op wake lock for the real platform ones — a **binding change only, no
  ctor/signature churn to `PlaybackController`**. In `di/MatnKoinStarter.kt` add a third param
  `initMatnKoin(driverFactory, audioEngine, wakeLock: WakeLock)` and register
  `single<WakeLock> { wakeLock }` in the startup module; then **remove** the
  `single<WakeLock> { NoOpWakeLock }` line added to `di/ContentModule.kt` in T021 (so the injected
  platform binding wins with no duplicate). Update both call sites from T022: pass
  `AndroidWakeLock(this)` from `MainActivity` and `IosWakeLock()` from `MainViewController.kt`.
  Depends on T036, T038, T021, T022.
- [ ] T040 [US3] Run on-device US3 checks from [quickstart.md](./quickstart.md) §B rows 8–12
  (background + notification, wake lock, interruption/resume, Bluetooth unplug, missing-audio skip)
  on Android and iOS. Depends on T037, T038, T039.

**Checkpoint**: All three stories work — full Phase 2 audio experience.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T041 [P] Preview-coverage audit (Principle II, blocking): confirm `PlayerBarContent` has
  `@Preview`s for **playing / paused / loading / ended-or-hidden**, and the reading screen has a
  preview variant with a non-null `activeVerseId` (highlighted row). Add any missing preview in the
  relevant `presentation/player/` or `presentation/details/` file.
- [X] T042 Run the full automated gate: `./gradlew :shared:allTests` — every `commonTest` scenario
  in [quickstart.md](./quickstart.md) §A green — then execute the §B walkthrough end-to-end on both
  Android and iOS.
- [X] T043 [P] Confirm offline + no-leak hygiene: no network URI is ever constructed (all audio
  from `files/audio/…`), the `CoroutineScope` in `PlaybackController` and the engines are released
  on teardown (`engine.release()`), and the Android Activity reference in `AndroidWakeLock` is not
  retained past the session.
- [X] T044 Constitution + scope compliance pass. Verify: (a) layer direction
  (presentation → domain ← data / platform engine); (b) all playback DECISIONS live in `commonMain`
  (`PlaybackController`), platform engines hold no business logic; (c) every changed
  domain/data/controller/ViewModel behaviour has a `commonTest`; (d) **FR-022 invariant** — the
  engines feed one `MediaItem`/`AVPlayerItem` **per verse** and never concatenate or seek across a
  shared file (grep the engines for a single playlist-per-verse construction, no merged source);
  (e) **FR-023 exclusion** — confirm no repetition/counter/loop logic, no cross-session persistence
  of playback state, and no search/bookmark/notes/download/dark-mode code leaked into this phase
  (session state stays in-memory in `PlaybackState`). Fix any violation before marking Phase 2 done.

---

## Dependencies & Execution Order

### Phase dependencies
- **Setup (Phase 1)**: no dependencies — start immediately.
- **Foundational (Phase 2)**: depends on Setup — **BLOCKS all user stories**.
- **US1 (Phase 3)**: depends on Foundational. This is the MVP.
- **US2 (Phase 4)**: depends on US1 (extends the controller, engines, and player bar built in US1).
- **US3 (Phase 5)**: depends on US1 (extends the controller + engines; adds background/wake lock)
  **and has a soft dependency on US2**: the media-notification / lock-screen next/previous transport
  routes through the controller's `next()`/`previous()` introduced in US2 (T028), so build US2
  before US3. (Everything else in US3 — background service, focus, wake lock, interruptions — is
  independent of US2.)
- **Polish (Phase 6)**: depends on all desired stories.

### Key within-story order
- Models/interfaces (Phase 2) before the controller (T017) before its extensions (T028, T034).
- Controller (T017) before DI wiring (T021) before ViewModel/UI (T023–T025).
- Engine impls (T019/T020) can be built in parallel with the controller, but DI wiring (T021/T022)
  needs both.
- Tests for a story land in the same phase as that story's code (Principle V).

### Parallel opportunities
- Setup: T002, T003 in parallel (T001 touches Gradle alone).
- Foundational: T004, T005, T006, T008, T009, T010 in parallel (distinct files); T013, T014 in
  parallel after T008/T009. T007 after T004+T006; T011 after T003; T012 after T005+T010+T011.
- US1: T019 (Android engine) and T020 (iOS engine) in parallel; T018 (controller test) parallel
  with the engine tasks; T026 parallel once T023 lands.
- US2: T030 (Android) and T031 (iOS) in parallel; T029 parallel with them.
- US3: T036 (Android) and T038 (iOS) largely in parallel; T035 parallel with them.

---

## Parallel Example: Foundational models

```bash
# Distinct files, no interdependencies — safe to do together:
Task: T004 PlaybackSpeed enum in domain/model/PlaybackSpeed.kt
Task: T005 AudioTrack + PlaybackQueue in domain/model/AudioTrack.kt
Task: T006 PlaybackStatus/PauseReason/PlaybackNotice in domain/model/PlaybackStatus.kt
Task: T008 AudioEngine interface in domain/audio/AudioEngine.kt
Task: T009 WakeLock interface in domain/audio/WakeLock.kt
Task: T010 AudioSourceResolver (+Impl) in domain/audio/ + data/audio/
```

## Parallel Example: US1 platform engines

```bash
# Two platform source sets, independent files:
Task: T019 Media3AudioEngine in shared/src/androidMain/.../audio/Media3AudioEngine.kt
Task: T020 AvQueueAudioEngine in shared/src/iosMain/.../audio/AvQueueAudioEngine.kt
```

---

## Implementation Strategy

### MVP first (US1 only)
1. Phase 1 Setup → 2. Phase 2 Foundational (green `:shared:allTests`) → 3. Phase 3 US1 →
4. **STOP & VALIDATE**: quickstart §A US1 tests + §B rows 1–4/13 on device → 5. demo the MVP.

### Incremental delivery
- Foundation ready → US1 (MVP: gapless read-along + pause/stop) → US2 (transport controls) →
  US3 (background + wake lock + interruptions). Each story adds value without breaking the prior.

### Notes
- `[P]` = different files, no dependency on an incomplete task.
- Keep the build green after every phase; commit per task or logical group.
- Never put playback decisions in the platform engines — they translate calls/events only.
- Do not persist session state this phase — `PlaybackState` holds it in memory; persistence is
  Phase 4 (the fields are already the right shape).
