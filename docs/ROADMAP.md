# Matn — Development Roadmap

## Executive Summary

Matn is a Kotlin Multiplatform application that helps students read, listen to, and memorize Islamic texts (متون). It is designed as an active memorization companion — combining synced Arabic text, teacher-recorded audio, and flexible repetition tools — rather than a passive audio player. Android and iOS are the primary targets; a desktop (JVM) client was added in `4941cc7` and runs the same shared UI.

Development is broken into sequential **phases**, each scoped to a single [spec-kit](https://github.com/github/spec-kit) cycle (`/specify` → `/plan` → `/tasks` → `/implement`). Each phase produces an independently buildable and testable slice of the app.

**Phases 1–10 are complete** (`23deb1b` … `2d4d63d`). They delivered the student app: Phases 1–3 in order as hard prerequisites, Phase 5 after Phases 2–4, and Phase 10's cross-cutting design-system retrofit ahead of Phases 6–9's UI work (see Dependency Notes). **Phase 11 is complete** (`d2348c1`) and **Phase 12 is complete** — they replace the binary-bundled content model with a Supabase-backed one (teacher uploads → student catalog → per-matn download), described under *Content Delivery & Authoring* below. **Phase 13 is the current/next work.**

> Phase numbers here are 1-indexed and match the `specs/NNN-*` folder each phase corresponds to
> (Phase 1 → `specs/001-*`, Phase 5 → `specs/005-*`, etc.).
>
> One naming collision to avoid: the branch `feature/011-desktop-app` already exists — it carried
> the desktop student target (`4941cc7`) and has no `specs/011-*` folder behind it. Phase 11 should
> use a fresh branch name (`feature/011-teacher-authoring-upload`).

The audio architecture is locked in as **one micro-audio file per verse**, matching how the teacher's recordings are produced, with gapless playback handled via ExoPlayer (Android) and AVQueuePlayer (iOS). Progress tracking is based on actual recall signals (explicit "mark as memorized" and/or spaced practice), not raw listen count.

> Detailed feature requirements for everything sequenced below live in
> [`PRODUCT-SPEC.md`](PRODUCT-SPEC.md) (the **WHAT**);
> this roadmap defines the **HOW / WHEN** of delivery.

---

## Student App (Phases 1–10) — complete

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

> **Delivery mechanism superseded by Phase 13.** Phase 8 shipped this over store-bundled asset
> packs (Play Asset Delivery / iOS On-Demand Resources). Phase 13 replaces that transport with
> Supabase while keeping the user-facing behaviour and the domain seams it introduced.

### Phase 9 — Polish & Accessibility
Dark mode, tablet-friendly adaptive layouts, accessibility contrast compliance, first-launch onboarding and permissions flow, and interface animations/transitions.

### Phase 10 — Design System Adoption
Retrofits Phases 1–5's UI to the canonical Stitch design set (see `docs/DESIGN-SOURCE.md`):
centralized Compose design tokens (`Color.kt`/`Type.kt`/`Shape.kt`/`Spacing.kt`) replacing the
current ad-hoc "manuscript" palette and the raw `.dp`/`.sp` literals scattered across screens;
re-skinned Home/Library, Matn Details, Reading & Playback, and Repetition Setup screens; the
Reading & Playback screen reworked from a scrollable verse list to the focused 3-verse carousel
(previous/active/next) with a floating control bar; and a persistent bottom-navigation shell
(Library / Goals / Notes / Settings), with the Goals and Notes tabs stubbed as "coming soon"
until Phases 6–7 land. Cross-cutting rather than additive — it changes already-shipped Phase 1–5
screens rather than adding a new feature area.

---

## Content Delivery & Authoring (Phases 11–13) — planned

Phases 1–10 shipped with content compiled into the binary: a hardcoded `List<SeedMatn>` in
`di/MatnKoinStarter.kt` for the catalog, one bundled starter matn, and Gradle asset-pack modules
under `packs/` delivered through Play Asset Delivery (Android) and On-Demand Resources (iOS).
Phases 11–13 replace that model end to end:

> **Teacher uploads to Supabase → students browse a catalog of overviews → students download
> individual matns to their device.**

Nothing ships in the binary and nothing travels through this repo. Four consequences follow, and
they are the point of these phases rather than side effects:

- **Supabase is the only content channel.** `PRODUCT-SPEC.md`'s "online catalog + selective
  download", previously a V2 item, becomes v1.
- **Phase 8's delivery model is retired, not extended.** Phase 13 deletes the `packs/` modules,
  the Play Asset Delivery dependency, the iOS ODR tags, and all three platform delivery engines.
- **First launch requires network.** With nothing bundled, a network-less first launch is an empty
  library. This is a deliberate trade, recorded in the Principle VI amendment (constitution 2.0.0).
- **Content already downloaded stays fully usable offline.** That is the part of the offline-first
  guarantee that survives, and it is unchanged.

Two things are *not* changing. The **per-verse audio lock** holds — the tool's output is always one
micro-audio file per verse (see Phase 12). And the **Phase 10 design tokens**
(`Color.kt`/`Type.kt`/`Shape.kt`/`Spacing.kt`) are shared by every client, including the new
teacher tool; there is no separate teacher design system.

Scope across all three phases is **a single teacher per institution**: no multi-teacher permissions
and no content-ownership model, though the Postgres schema should not actively block adding one.

### Backend access — REST, not platform SDKs

All backend access goes through **Supabase's PostgREST, Storage, and Auth surfaces over Ktor**, in a
single `:shared/commonMain` implementation. Supabase ships no official client SDK for desktop JVM,
so a platform SDK would leave `:desktopApp` and `:teacherApp` unserved and split the code into three
paths; one REST client covers Android, iOS, and both JVM clients uniformly and matches the repo's
existing "domain interface, one implementation" style.
**Ktor is a new dependency** — the project's first HTTP client of any kind — and needs justifying
under the constitution's dependency clause when Phase 11 lands. (The backend started on Firebase.
Object storage moved to Supabase first — Firebase Storage now requires the Blaze plan even at zero
usage — and Firestore plus Identity Toolkit followed, so the whole backend is now one Supabase
project; see `specs/011-teacher-authoring-upload/design-notes.md`.)

Students have **no accounts**. Catalog reads are public, gated by a row-level-security policy on the
row's `published` column; only the teacher authenticates, to write. Remote student accounts stay a V2 item.

### Phase 11 — Teacher Authoring Tool: Foundation & Upload
Stands up `:teacherApp` (JVM/Compose Desktop, alongside the existing `:desktopApp` from `4941cc7`)
and the shared backend client. Covers: Supabase project setup; the Ktor REST client in
`:shared/commonMain`; the Postgres catalog schema, mirroring the `SeedMatn` field set
(`shared/src/commonMain/kotlin/com/giraffe/matn/data/seed/SeedContent.kt`) so Phase 13's sync is a
projection rather than a translation; teacher sign-in; matn metadata entry (title, author,
description, cover image, chapter/section structure); and verse *text* entry — ordered list,
drag-to-reorder, per-row Arabic entry or bulk text import, from the list/text regions of
`stitch-designs/11-Upload-Per-Verse`.

Validation reuses the rules already encoded in `ContentSeedLoaderImpl.validate()` (`InvalidId`,
`DuplicateId`, `DuplicateDisplayNumber`, `DuplicateChapterOrder`, …) rather than defining a parallel
set, so a matn that validates in the tool cannot fail to ingest in the app.

Publishing writes a `draft` → `published` document. **The `published` flag and its security rules
are the only gate on student visibility** — there is no release process behind them — so the rules
are load-bearing and must be tested, not merely written.

### Phase 12 — Audio Capture & Slicing (complete)
Adds the audio half, via two paths producing the **same** artifact — an ordered set of per-verse
files uploaded to Supabase Storage:
- **Per-verse upload** (the audio column of `stitch-designs/11-Upload-Per-Verse`): per-row audio
  upload with duration preview.
- **Split-from-continuous** (adapted from `stitch-designs/12-Upload-Timestamp-Map`): the teacher
  uploads one continuous recording, places verse markers on a waveform, and adjusts per-verse
  start/end (ms) fields, with inline validation for overlapping ranges and undefined gaps. On save
  the tool **slices the recording into per-verse files**. The marker list is a transient authoring
  aid — it is never persisted, never uploaded, and never reaches a student. This is what makes the
  screen implementable: the constitution's per-verse lock forbids a continuous file *as the runtime
  asset model*, not as an authoring input.

Also adds preview playback, so the teacher hears the assembled matn as a student would before
publishing.

**New dependency, shipped narrower than originally scoped — `javazoom:jlayer:1.0.1`, decode only,
`:teacherApp` only.** `DesktopAudioEngine`'s KDoc records that `javax.sound.sampled` "decodes
WAV/AU/AIFF out of the box but ships no MP3 codec." Cutting and duration turned out to need no
decoder at all — pure frame-header arithmetic in `commonMain` — so JLayer is used only for waveform
peaks and preview playback, and only on the JVM. No encoder, no ffmpeg, no `mp3spi`/`tritonus` (see
`research.md` D1 and the plan's Complexity Tracking for the full comparison). `:shared`'s dependency
graph, and every other client's, is unchanged.

### Phase 13 — Student Remote Catalog & Download
The student-app retrofit — as much deletion as addition.

**Adds:** a `RemoteContentDeliveryEngine` in `commonMain` (provider-agnostic name — catalog metadata
reads PostgREST, binary assets read Supabase Storage; see
`specs/011-teacher-authoring-upload/design-notes.md`) satisfying the existing
`ContentDeliveryEngine` seam (`querySize`/`install`/`observe`/`cancel`/`remove`/`locate`/
`isInstalled`); catalog sync pulling published *overviews* — title, author, description, cover,
verse count, size — from Postgres into SQLDelight via the existing `ContentSeedLoader` path;
per-matn download of verse text and audio into app storage; and a first-launch empty/offline state
for a library with nothing in it.

**Removes:** `packs/matn_structured_sample/` and its `settings.gradle.kts` include; the
`play-asset-delivery-ktx` dependency; `PlayAssetDeliveryEngine`, `OnDemandResourcesEngine`, and
`DesktopContentDeliveryEngine` (three platform implementations collapse into the one shared
engine); the iOS On-Demand Resources tags; `bundledSampleMatns()`; and the entire starter path —
`isStarter`, `ContentPackRepository.isStarterMatn()`, `removeAll()`'s starter-sparing, and the
Compose-resource audio routing that served it.

`ContentPackRepository`'s abstraction survives intact: it still owns the `matnId ↔ packId`
translation and still computes availability per read without persisting it. `packId` simply becomes
a Supabase Storage path prefix instead of a Gradle module name.

**One behavioural change to spec, not discover:** Phase 6's FR-001 specified library-wide search
across verse text. With an overview-only catalog, verse text exists locally only for downloaded
matns, so search narrows to **titles across the catalog plus full text within downloads**.

---

## Dependency Notes

- **Phases 1, 2, 3** must be completed in order — each is a hard prerequisite for the next.
- **Phase 5** requires Phases 2–4 to be complete, since there's no meaningful state to resume before then.
- **Phases 4, 6, 7, 8, 9** can be reordered relative to each other based on priorities, as long as their individual prerequisites above are respected.
- **Phase 10** had to land before Phases 6–9 began their own UI work, since it establishes the
  shared design-token system and navigation shell those phases would otherwise have invented
  independently (and then reworked). ✅ Satisfied — Phase 10 shipped first (`e6fd2ec`), ahead of
  Phases 6–9.
- **Phases 11 → 12 → 13** are strictly ordered. 12 adds audio to the matns 11 can already create
  and publish; 13 is the student consumer of what 11 and 12 upload, so building it last means the
  Postgres schema has been exercised by a real producer first.
- **Phase 11** depends on Phase 1 (the UUID data model and `SeedMatn` shape its Postgres schema
  mirrors) and Phase 10 (the design tokens `:teacherApp` consumes) — both landed.
- **Phase 13 supersedes Phase 8** rather than building on it. Phase 8's store-bundled asset-pack
  delivery is removed outright; only its domain seam (`ContentDeliveryEngine`) and repository
  abstraction (`ContentPackRepository`) survive, now backed by Supabase.
- **Phase 13 is the only one of the three that touches the student app**, and it touches it
  substantially — the whole content-acquisition path changes.
