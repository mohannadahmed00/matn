# Phase 1 Design — Data Model (Core Audio Playback)

Phase 2 introduces **no new persisted entity** and **no schema change** (FR-023 — session state is
in-memory; persistence is Phase 4). It adds: (1) in-memory **domain models** for the playback
session, (2) one **additive read query** + repository method to fetch a matn's per-verse audio in
order, and (3) presentation **UI-state** additions. Arabic verse text and stable UUIDs come
unchanged from Phase 0/1.

---

## 1. Reused Phase 0/1 entities (read-only)

| Entity | Used for |
|--------|----------|
| `Verse(id, matnId, chapterId, displayNumber, arabicText, durationMs)` | ordered verse sequence; `id` is the highlight/active key (FR-008) |
| `AudioAsset(id, verseId, reciterId, fileRef, durationMs)` | the single per-verse file to play (FR-022) |
| `Matn`, `Chapter` | matn/queue boundaries; the reading surface the player attaches to |
| `app_setting(key,value)` | **not** used for playback this phase (session not persisted, FR-023) |

Default reciter is the existing `AudioAssetRepository.DEFAULT_RECITER` (`"reciter-default-v1"`).

---

## 2. New in-memory domain models (`commonMain/domain/model`)

### 2.1 `PlaybackSpeed` (enum) — D6, FR-014

```
enum class PlaybackSpeed(val multiplier: Float) {
    X0_5(0.5f), X0_75(0.75f), X1(1.0f), X1_25(1.25f), X1_5(1.5f);
    companion object { val DEFAULT = X1 }
}
```
- Exactly the five clarified steps; `DEFAULT = X1`. Applied pitch-preserved (D6). No other value is
  representable (invalid speeds unconstructable).

### 2.2 `AudioTrack` — one per verse (D2/D7)

```
data class AudioTrack(
    val verseId: String,       // stable Phase 0 UUID — the highlight key
    val displayNumber: Int,    // for notification/scrub labelling
    val uri: String,           // resolved playable URI (Res.getUri("files/audio/<fileRef>"))
    val durationMs: Long,      // from AudioAsset (fallback: Verse.durationMs)
)
```
- Built by `BuildPlaybackQueueUseCase` (§4). `uri` is produced by `AudioSourceResolver` (contracts).

### 2.3 `PlaybackQueue`

```
data class PlaybackQueue(
    val matnId: String,
    val tracks: List<AudioTrack>,   // matn-global display order (FR-005/FR-006)
    val startIndex: Int,            // where playback begins (FR-001)
)
```
- Ordering matches `verse.display_number`. `startIndex` derived from the tapped verse (per-verse
  play) or `0`/last-selected (global play).

### 2.4 `PlaybackStatus` (enum)

```
enum class PlaybackStatus { IDLE, LOADING, PLAYING, PAUSED, ENDED }
```
- `IDLE` no session; `LOADING` queue/track preparing; `ENDED` finished the last verse and stopped
  (FR-007). `STOPPED` collapses to `IDLE` (session cleared, highlight cleared — FR-004).

### 2.5 `PauseReason` (enum) — D5, FR-019

```
enum class PauseReason { USER, TRANSIENT_INTERRUPTION, NON_TRANSIENT_INTERRUPTION }
```
- Drives resume policy: only `TRANSIENT_INTERRUPTION` auto-resumes when focus returns; `USER` and
  `NON_TRANSIENT_INTERRUPTION` require a manual resume.

### 2.6 `PlaybackState` — the single session snapshot (D8; Phase 4 persistence target)

```
data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val matnId: String? = null,
    val activeVerseId: String? = null,     // highlight + auto-scroll key (FR-008/FR-009)
    val activeIndex: Int = -1,             // index into the queue
    val positionMs: Long = 0,              // within the active verse (scrub bar) (FR-013)
    val durationMs: Long = 0,              // active verse length
    val speed: PlaybackSpeed = PlaybackSpeed.DEFAULT,   // persists across transitions (FR-014)
    val pauseReason: PauseReason? = null,
    val notice: PlaybackNotice? = null,    // transient one-shot (e.g. skipped missing verse, FR-020)
) {
    val isPlaying get() = status == PlaybackStatus.PLAYING
    val hasSession get() = status != PlaybackStatus.IDLE
}
```
- **Phase 4 seam (Principle VI)**: `matnId`, `activeVerseId`, `positionMs`, and `speed` are exactly
  the "Continue Learning" fields the constitution requires persisted on each transition — Phase 2
  keeps them in memory; Phase 4 persists this snapshot without reshaping it.
- `notice` is consumed-once (cleared after the UI shows it) — used for the skip-on-error message
  (FR-020) and end-of-matn.

### 2.7 `PlaybackNotice` (sealed)

```
sealed interface PlaybackNotice {
    data class SkippedMissingVerse(val verseId: String) : PlaybackNotice   // FR-020
    data object ReachedEnd : PlaybackNotice                                // FR-007
    data object NoPlayableAudio : PlaybackNotice                           // FR-020 (none playable)
}
```

---

## 3. Additive persistence query (no schema change)

Add to `Content.sq` (read-only; reuses existing tables + `audio_by_verse` index):

```sql
-- Phase 2: a matn's per-verse audio in matn-global reading order (FR-005/FR-006), for one reciter.
-- INNER JOIN drops verses with no audio row for this reciter — a defensive case only, since Phase 0
-- V4 validation guarantees every verse has audio (see note below). Order is authoritative
-- verse.display_number, not storage order.
selectAudioForMatnOrdered:
SELECT audio_asset.*
FROM audio_asset
JOIN verse ON verse.id = audio_asset.verse_id
WHERE verse.matn_id = ? AND audio_asset.reciter_id = ?
ORDER BY verse.display_number;
```

> **FR-020 realization (one path in practice).** Phase 0's `ContentSeedLoader` validation rejects a
> matn if **any** verse lacks an audio row (`ContentIntegrityError.MissingAudio`, rule V4), so for
> validly-seeded content every verse has exactly one audio row and the queue is **1:1 with the
> verses**. Therefore FR-020 ("skip a missing/unreadable verse with a brief notice") is realized at
> **runtime** — a track whose *file* fails to load emits `AudioEngineEvent.TrackError`, and the
> controller skips it with a `SkippedMissingVerse` notice (§6, contracts). The build-time
> "verse with no audio row" case is a **defensive** guard only (should not occur for seeded
> content): `BuildPlaybackQueueUseCase` omits such a verse from the queue rather than crashing; it
> produces no user notice because there is nothing playable to reach. The on-device test file that
> is intentionally absent (tasks T002) keeps its `audio_asset` row, so it exercises the **runtime**
> skip+notice path, not the defensive omission.

---

## 4. Repository & use-case additions

### 4.1 `AudioAssetRepository` (interface, +1 method — additive)

```
interface AudioAssetRepository {
    suspend fun getAudioForVerse(verseId: String, reciterId: String = DEFAULT_RECITER): Resource<AudioAsset?>   // existing
    suspend fun getAudioForMatn(matnId: String, reciterId: String = DEFAULT_RECITER): Resource<List<AudioAsset>> // NEW → selectAudioForMatnOrdered
    companion object { const val DEFAULT_RECITER = "reciter-default-v1" }
}
```

### 4.2 `BuildPlaybackQueueUseCase : UseCase<BuildPlaybackQueueUseCase.Params, PlaybackQueue>`

- **Params**: `matnId`, `startVerseId: String?` (null → start at index 0), `reciterId = DEFAULT`.
- **Behaviour**: reads the ordered verses (Phase 1 `ObserveVerses`/`VerseRepository`) + ordered audio
  (`getAudioForMatn`), zips them into `AudioTrack`s in `display_number` order, resolves each
  `fileRef` → `uri` via `AudioSourceResolver`, computes `startIndex` from `startVerseId`, and returns
  `PlaybackQueue`. For validly-seeded content the queue is **1:1 with the verses** (Phase 0's V4
  `MissingAudio` validation guarantees every verse has an audio row — see §3); a verse with no audio
  row is a **defensive** case only and is omitted (no notice — nothing playable to reach). Returns
  `Resource.Failure(AppError.NotFound)` if the matn has zero playable tracks (→ controller
  `NoPlayableAudio`). The FR-020 skip+notice is realized at **runtime** via `TrackError` (§6).
- No coroutine launched inside (Principle III); returns `Resource` (Phase 0/1 convention).

---

## 5. Presentation UI-state additions

### 5.1 `MatnDetailsUiState` (+ fields — additive, D9)

```
data class MatnDetailsUiState(
    // …existing Phase 1 fields (header, verses, chapters, showTableOfContents, fontSize, …)…
    val activeVerseId: String? = null,   // highlight target (FR-008)
    val isPlaying: Boolean = false,      // for the row play/pause affordance
)
```
- Fed from `PlaybackController.state`; a verse row is highlighted when `id == activeVerseId`.
- Adds a per-verse **play** intent → `playFromVerse(verseId)` (FR-001).

### 5.2 `PlayerBarUiState` (new — the persistent control bar, FR-010)

```
data class PlayerBarUiState(
    val visible: Boolean = false,        // shown only when a session exists
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val activeVerseNumber: Int? = null,  // "verse N"
    val positionMs: Long = 0,
    val durationMs: Long = 0,            // scrub bounds within the active verse (FR-013)
    val speed: PlaybackSpeed = PlaybackSpeed.DEFAULT,
    val canPrevious: Boolean = false,    // false only meaningfully at boundaries (FR-012)
    val canNext: Boolean = false,
)
```
- Pure projection of `PlaybackState`; the bar composable renders it and emits transport intents.

---

## 6. State transitions (session state machine — owned by `PlaybackController`, tested in `commonTest`)

```
IDLE ──playFromVerse──▶ LOADING ──queue ready──▶ PLAYING
PLAYING ──pause(User)──▶ PAUSED(USER) ──resume──▶ PLAYING
PLAYING ──engine track-complete & more verses──▶ PLAYING (activeVerseId := next)      [gapless, FR-005/006]
PLAYING ──engine track-complete & last verse──▶ ENDED (highlight kept? no → cleared)  [FR-007]
PLAYING ──engine load-error on a track──▶ PLAYING (skip to next) + notice=SkippedMissingVerse  [FR-020]
        └─ no next playable ──▶ ENDED + notice=NoPlayableAudio
PLAYING ──interruptionBegan(transient)──▶ PAUSED(TRANSIENT) ; interruptionEnded(resume) ─▶ PLAYING  [FR-019]
PLAYING ──interruptionBegan(non-transient)──▶ PAUSED(NON_TRANSIENT) ; ended ─▶ stays PAUSED         [FR-019]
PLAYING/PAUSED ──next──▶ activeIndex+1 (or ENDED at end)                               [FR-011/012]
PLAYING/PAUSED ──previous──▶ if positionMs>threshold or first verse: restart current; else index-1  [FR-012]
PLAYING/PAUSED ──seekTo(pos)──▶ same verse, positionMs:=pos (reaching end triggers advance)          [FR-013]
any(hasSession) ──stop──▶ IDLE (activeVerseId=null, position=0)                        [FR-004]
any ──setSpeed(s)──▶ speed:=s applied to engine immediately, retained across transitions             [FR-014]
```

Every labelled edge above is asserted in `PlaybackControllerTest` against `FakeAudioEngine`
(Principle V). Gapless/focus/wake-lock/notification behaviours are device-validated (quickstart).

---

## 7. Validation rules

- **Ordering**: queue order == `verse.display_number` ascending; the highlight always matches the
  engine's currently playing item (no drift) (FR-005/FR-008, SC-003).
- **Boundaries**: `next` at last verse → `ENDED`, never an index overflow; `previous` at first verse
  → restart current, never a negative index (FR-012).
- **Speed domain**: only `PlaybackSpeed` members; default `X1` (FR-014).
- **Skip**: a track that fails to load advances exactly one step and emits one notice; it never
  crashes or stalls the session (FR-020, SC-009-adjacent).
- **Offline**: all URIs are bundled `files/audio/…`; no network URI is ever constructed (FR-021,
  SC-010).
