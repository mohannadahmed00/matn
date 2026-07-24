# Matn — Development Roadmap

## Executive Summary

Matn is a Kotlin Multiplatform (Android + iOS) mobile application that helps students read, listen to, and memorize Islamic texts (متون). It is designed as an active memorization companion — combining synced Arabic text, teacher-recorded audio, and flexible repetition tools — rather than a passive audio player.

Development is broken into sequential **phases**, each scoped to a single [spec-kit](https://github.com/github/spec-kit) cycle (`/specify` → `/plan` → `/tasks` → `/implement`). Each phase produces an independently buildable and testable slice of the app. Phases 1–3 are hard prerequisites for everything else; Phases 4–9 can largely be reordered to fit priorities, with the exception of Phase 5, which depends on Phases 2–4 being complete.

> Phase numbers here are 1-indexed and match the `specs/NNN-*` folder each phase corresponds to
> (Phase 1 → `specs/001-*`, Phase 5 → `specs/005-*`, etc.).

The audio architecture is locked in as **one micro-audio file per verse**, matching how the teacher's recordings are produced, with gapless playback handled via ExoPlayer (Android) and AVQueuePlayer (iOS). Progress tracking is based on actual recall signals (explicit "mark as memorized" and/or spaced practice), not raw listen count.

> Detailed feature requirements for everything sequenced below live in
> [`PRODUCT-SPEC.md`](PRODUCT-SPEC.md) (the **WHAT**);
> this roadmap defines the **HOW / WHEN** of delivery.

---

## Phases

### Phase 1 — Foundation & Data Model
Core domain entities: Matn, Chapter/Section, Verse (UUID-based). Local persistence layer (e.g. SQLDelight). KMP module scaffolding (`commonMain` / `androidMain` / `iosMain`). No UI yet — this is the schema and repository layer everything else builds on. Locks in the per-verse micro-audio-file asset model.

### Phase 2 — Reading Experience (static)
Home screen library grid, Matn Details screen, verse list rendering, RTL layout, Arabic typography, table of contents for structured متون. No audio yet — validates the core reading UI/UX before playback is layered in.

### Phase 3 — Core Audio Playback
Play / pause / resume / stop, next / previous verse, scrub bar, playback speed control, gapless verse-to-verse playback, active-verse highlighting with auto-scroll, background playback, screen wake lock, and audio interruption handling (calls, other apps, Bluetooth changes).

### Phase 4 — Repetition Engine
Verse Repeat Counter ($V_r$) and Matn Repeat Counter ($M_r$), Normal / Memorization / A–B Loop playback modes, and explicit unlimited-repetition handling. Builds directly on the Phase 3 playback engine.

### Phase 5 — Continue Learning & State Persistence
Caches last opened matn, last listened verse, playback position, repetition settings, and in-progress A–B loop range. Powers one-tap resume from the Home screen. Depends on Phases 2–4 being in place.

### Phase 6 — Search, Bookmarks & Notes
Diacritic-insensitive local search across verse text, verse number, and matn title. Verse bookmarking. Per-verse personal notes. Mostly additive and low-risk; can be built any time after Phase 2.

### Phase 7 — Progress & Daily Goals
"Mark as memorized" flow, per-matn progress percentage, daily goal setting, and a daily completion ring — using a recall-based progress metric rather than raw playback frequency.

### Phase 8 — Storage & Downloads
Per-matn download and removal, file size display before download, and total storage usage in settings. Matters once the content library grows beyond a single matn.

### Phase 9 — Polish & Accessibility
Dark mode, tablet-friendly adaptive layouts, accessibility contrast compliance, first-launch onboarding and permissions flow, and interface animations/transitions.

---

## Dependency Notes

- **Phases 1, 2, 3** must be completed in order — each is a hard prerequisite for the next.
- **Phase 5** requires Phases 2–4 to be complete, since there's no meaningful state to resume before then.
- **Phases 4, 6, 7, 8, 9** can be reordered relative to each other based on priorities, as long as their individual prerequisites above are respected.
