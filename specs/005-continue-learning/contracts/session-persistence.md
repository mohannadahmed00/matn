# Contract: Session Persistence

**Feature**: Phase 4 — Continue Learning & State Persistence | **Date**: 2026-07-24

The interfaces this phase introduces, and the behavioral contract `commonTest` holds them to.
Types are defined in [data-model.md](../data-model.md).

---

## 1. `SessionStateRepository` (domain interface, data implementation)

```kotlin
interface SessionStateRepository {
    /** Null when no row exists. MUST NOT throw on unreadable/undecodable rows — returns null. */
    suspend fun getSession(matnId: String): SavedMatnSession?

    /** Upsert. Failures surface as Resource.Error(AppError.Storage), never a thrown exception. */
    suspend fun putSession(session: SavedMatnSession): Resource<Unit>

    /** Null when unset or when the referenced matn no longer exists. */
    suspend fun getLastListenedMatnId(): String?

    suspend fun setLastListenedMatnId(matnId: String): Resource<Unit>

    /** Dismiss. Clears ONLY the pointer; every matn_session row is left intact (FR-017a). */
    suspend fun clearLastListenedMatnId(): Resource<Unit>

    /** Cold flow for the Home card; re-emits when the pointer or its session row changes. */
    fun observeContinueLearning(): Flow<ContinueLearningEntry?>
}
```

**Guarantees**

| # | Rule | Req |
|---|---|---|
| P1 | No method throws. Decode failures degrade to `null`; write failures return `Resource.Error`. | FR-011, FR-029 |
| P2 | `clearLastListenedMatnId` MUST NOT delete or modify any `matn_session` row. | FR-017a |
| P3 | `putSession` is an upsert keyed by `matn_id` — never duplicates a matn. | FR-001 |
| P4 | `observeContinueLearning` emits `null` (not an error) when state is absent or unhonourable. | FR-015, FR-025 |
| P5 | All operations work with no network. | FR-030, SC-010 |

---

## 2. `RepetitionSettingsStore` — persistent implementation

**No interface change.** Phase 3 declared this type "the Phase 4 persistence seam — a
SQLDelight-backed implementation substitutes in with no change to any caller". This phase honours
that: `InMemoryRepetitionSettingsStore` is replaced in `contentModule()` by a persistent
implementation, and `PlaybackController` is not modified for it.

```kotlin
interface RepetitionSettingsStore {          // unchanged
    fun get(matnId: String): RepetitionSettings
    fun put(matnId: String, settings: RepetitionSettings)
}
```

| # | Rule | Req |
|---|---|---|
| S1 | `get` on an unknown matn returns `RepetitionSettings()` defaults — never null, never throws. | FR-024 |
| S2 | `put` persists across process death. | FR-010, FR-019 |
| S3 | `put` for matn A never affects matn B. | FR-023, SC-009 |
| S4 | `RepeatCount.Unlimited` survives a round trip as `Unlimited`, distinct from any finite value. | FR-007 |
| S5 | `put` with no prior row creates one — a configured-but-unplayed drill survives (FR-002b). | FR-002b |

> The synchronous signature is retained deliberately: changing it would ripple into
> `PlaybackController.updateSettings`, contradicting Phase 3's promise of substitution "without
> touching a single caller". The implementation writes through a scope internally.

---

## 3. `SessionStateRecorder`

```kotlin
class SessionStateRecorder(
    private val state: StateFlow<PlaybackState>,
    private val repository: SessionStateRepository,
    private val scope: CoroutineScope,
) {
    fun start()
    suspend fun flush()   // forced write: pause, stop, background
}
```

| # | Rule | Req |
|---|---|---|
| W1 | A change to `matnId`, `activeVerseId`, or `settings` writes within 1 s. | FR-008, SC-004 |
| W2 | `positionMs` alone writes no more often than the throttle interval. | FR-009, FR-011 |
| W3 | Changes to `status`, `speed`, `notice`, `pauseReason`, `cursor` alone produce **zero** writes. | FR-011, SC-008 |
| W4 | The first playback in a matn writes both the session row and the pointer. | FR-002a |
| W5 | A settings change with no playback writes the row but **not** the pointer. | FR-002b |
| W6 | `flush()` is idempotent and safe to call when there is no session. | FR-010 |
| W7 | A write failure is swallowed (logged, not surfaced) — playback continues unaffected. | FR-011, SC-008 |

The recorder holds **no clock**: nothing persisted is time-derived (see data-model §1.1), so tests
need only a virtual-time coroutine scheduler to exercise the throttle in W2 — no time source to
fake, and no new dependency.

---

## 4. `ResumeTargetResolver` (pure)

```kotlin
object ResumeTargetResolver {
    fun resolve(
        session: SavedMatnSession?,
        orderedVerses: List<VerseRef>,   // (id, displayNumber), ascending
    ): ResumeTarget
}
```

No coroutines, no I/O, no fakes required — the table-test target, mirroring Phase 3's
`RepetitionPlanner` (Principle V).

Implements rules **R1–R9** in [data-model.md §3](../data-model.md#3-resolution-rules-pure--resumetargetresolver). Required table coverage:

| Case | Expected |
|---|---|
| Verse present | `Resolved`, exact `positionMs`, `substituted = false` |
| Verse gone, predecessor exists | `Resolved` at predecessor, `positionMs = 0`, `substituted = true` |
| Verse gone, first verse deleted | `Resolved` at nearest follower |
| Verse gone, matn now empty | `None` |
| Loop endpoint gone | Loop cleared, counters retained |
| Resolved verse outside its loop | Loop cleared |
| `Unlimited` counters | Round-trip preserved |
| Null session | `None` |

---

## 5. Use cases

```kotlin
class ObserveContinueLearningUseCase(...)  // Flow<ContinueLearningEntry?> → HomeUiState
class ResolveResumeTargetUseCase(...)      // suspend (matnId) -> ResumeTarget
class DismissContinueLearningUseCase(...)  // suspend () -> Resource<Unit>
```

All extend the existing `UseCase`/`FlowUseCase` base contracts (Principle III). None contains
resolution logic — they fetch, delegate to `ResumeTargetResolver`, and return.

---

## 6. Resume entry point

`PlaybackController.playFromVerse` gains one optional parameter:

```kotlin
fun playFromVerse(matnId: String, verseId: String, startPositionMs: Long = 0)
```

Defaulted, so **every existing Phase 2/3 caller and test is source-compatible and unchanged.**

| # | Rule | Req |
|---|---|---|
| E1 | Resume starts playing automatically — no second user action. | FR-021 |
| E2 | Playback begins at `startPositionMs` within the verse. | FR-022 |
| E3 | If audio focus is unavailable, the session restores fully but sits paused with the existing Phase 2 `PauseReason` — it never plays over another app and never silently fails. | FR-022a |
| E4 | A substituted verse (FR-026) is entered at position 0. | FR-026 |

---

## 7. Zero-regression guard

The Phase 2 and Phase 3 `commonTest` suites MUST pass **unchanged**. This phase adds a
persistent implementation behind an existing interface, one defaulted parameter, and one nullable
UI-state field — no existing behavioral assertion may need editing. An assertion that does need
editing indicates an unintended behavior change and is a blocking review failure.
