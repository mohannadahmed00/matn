# Contract: `AudioEngine` additions for repetition

**Feature**: Phase 3 — Repetition Engine | **Date**: 2026-07-20

Additive to the Phase 2 [`audio-engine.md`](../../003-core-audio-playback/contracts/audio-engine.md)
contract. Every Phase 2 method keeps its exact semantics. Both additions are **mechanical playlist
operations** — the engine decides nothing (Principle IV).

---

## 1. New primitives

```kotlin
interface AudioEngine {
    // … Phase 2 members unchanged …

    /**
     * Replace every item AFTER the currently playing one with [tracks].
     * MUST NOT interrupt, re-prepare, or re-buffer the currently playing item.
     * Called on every window refill and on every live counter/range change.
     */
    fun replaceUpcoming(tracks: List<AudioTrack>)

    /**
     * Drop every item BEFORE the currently playing one, keeping the playlist bounded.
     * MUST NOT interrupt the currently playing item. The engine re-bases its own index;
     * the controller re-bases `window` in lockstep.
     */
    fun dropConsumed()
}
```

### Invariants

1. **The current item is sacred.** Neither call may pause, stop, seek, re-prepare, or re-buffer the
   item being played. This is what makes live reconfiguration inaudible (SC-005).
2. **Gapless preserved.** After `replaceUpcoming`, the new first upcoming item must be pre-buffered
   as any playlist successor is — including when it is the *same file* as the current item, which is
   how repetition stays gapless (FR-017).
3. **Index re-basing.** After `dropConsumed`, the engine's `currentIndex` and subsequent
   `TrackTransition(newIndex)` events are relative to the trimmed playlist. The controller applies
   the same trim to `window` in the same step, so `window[engineIndex]` stays valid.
4. **No-ops are safe.** `replaceUpcoming(emptyList())` (drain the tail) and `dropConsumed()` with
   nothing consumed are legal no-ops.

---

## 2. Platform mapping

| | Android (`Media3AudioEngine`) | iOS (`AvQueueAudioEngine`) |
|---|---|---|
| `replaceUpcoming` | `player.replaceMediaItems(currentIndex + 1, mediaItemCount, items)` — available since Media3 1.1; project is on **1.4.1** | Remove queued items after `currentItem`, then `insert(item, after:)` in order |
| `dropConsumed` | `player.removeMediaItems(0, currentIndex)` | Items already played are removed from `AVQueuePlayer` automatically; drop any explicitly retained ones |
| Gapless repeat of the same file | Two `MediaItem`s with the same URI are ordinary consecutive items — ExoPlayer pre-buffers normally | Two distinct `AVPlayerItem`s over the same `NSURL`, pre-enqueued |

**Note on iOS**: a *separate* `AVPlayerItem` instance is required per repetition — an `AVPlayerItem`
cannot be re-enqueued after it has played to the end. The mapping above constructs one per window
entry, which the sliding window already does naturally.

---

## 3. `FakeAudioEngine` additions (`commonTest`)

The fake records both calls so the window behavior is assertable with no device:

```kotlin
var upcomingReplacements: List<List<AudioTrack>>   // one entry per replaceUpcoming call
var dropConsumedCount: Int
val playlist: List<AudioTrack>                     // simulated current playlist
```

The fake maintains a real simulated playlist so tests can assert invariant 3 (index re-basing) and
contract row **C2** (playlist stays bounded at `1 + WINDOW_AHEAD`).

---

## 4. Carried-forward platform risk

`AvQueueAudioEngine` remains the **documented Phase 2 stub**: its transport body is authored and
validated on macOS with Xcode, because the Windows Kotlin/Native sysroot does not expose the
`AVPlayer` playback symbols. These two primitives inherit that constraint — they are authored in this
phase and validated on macOS together with the outstanding Phase 2 iOS work (T038/T040). **This phase
does not close that gap.** All shared repetition logic is nevertheless fully proven on Windows
through `commonTest` + `FakeAudioEngine`, and Android is validated end to end on-device.
