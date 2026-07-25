# Research: Storage & Downloads

Phase 0 decisions. Each resolves an unknown in the plan's Technical Context or a design choice the
spec's clarifications deliberately left to implementation. Format: Decision / Rationale /
Alternatives.

The spec fixed the *what* (Clarifications § Session 2026-07-25): platform on-demand asset delivery,
per-matn atomic availability, no upgrade migration, declared catalog sizes, installs continue while
backgrounded. This document fixes the *how*, and surfaces the one place where the platforms cannot
both honour the spec.

## D1 — Per-platform delivery mechanism

**Decision**: **Android** → Play Asset Delivery (PAD) **on-demand asset packs**, driven by
`AssetPackManager` from `com.google.android.play:asset-delivery-ktx`. **iOS** → **On-Demand
Resources** (ODR), driven by `NSBundleResourceRequest` over per-matn tags. Both sit behind one
`commonMain` interface, `ContentDeliveryEngine` — the same shape as the existing
`AudioEngine`/`WakeLock` platform seams.

**Rationale**: These are the only mechanisms that make uninstalled متون genuinely absent from the
app's initial store download (FR-013) without standing up hosting. Both are first-party, need no
credentials in the repo, and deliver into app-managed storage so no user-facing permission is
involved (spec Assumptions § Permissions). PAD gives the richer contract — `requestPackStates()`
returns `totalBytesToDownload()` before installing, `requestFetch()` reports
`bytesDownloaded()`/`totalBytesToDownload()`, `cancel()` aborts, `requestRemovePack()` schedules
deletion, and `getPackLocation(pack).assetsPath()` yields a plain filesystem path ExoPlayer can
play. ODR covers begin/progress/release but is materially weaker on two points (D2, D4).

**Alternatives**: (a) Self-hosted catalog + HTTP download — rejected for v1: needs hosting,
certificates, a CDN bill, and its own retry/resume stack, and the spec explicitly framed a remote
catalog as the *later* model (FR-032 keeps that door open). (b) Bundling everything install-time —
rejected: that is the status quo this phase exists to end. (c) Android dynamic *feature* modules —
rejected: they carry executable code and are the wrong tool for pure assets.

## D2 — iOS cannot guarantee reclamation on removal (spec-vs-platform conflict)

**Decision**: Model removal as an outcome, not a void: `RemovalOutcome.Reclaimed(bytes)` (Android)
vs `RemovalOutcome.ReleasedPendingSystemReclaim(bytes)` (iOS). The domain, repository, and
ViewModels are identical on both platforms; only the confirmation copy and the post-removal storage
figure differ, driven by the outcome. On iOS, "remove" means: call `endAccessingResources()` on the
retained request and drop the tag's preservation priority to the lowest band, after which the
system reclaims the space when it needs it.

**Rationale**: iOS provides **no API to force-purge ODR content**. `endAccessingResources()` only
releases the app's claim; `setPreservationPriority(_:forTags:)` is documented as a *hint* that
orders eviction, and the OS purges unretained tags only when it needs storage. That directly
weakens three spec statements on iOS: FR-018 (confirmation stating space reclaimed), FR-026 (sizes
reflect space actually occupied), and SC-004 (≥95% reclaimed, figures updated within 2 s). Rather
than let the UI claim something untrue, the outcome type forces every call site to say what actually
happened. Per Constitution Principle VIII's "raise the conflict rather than silently implement it",
this was raised rather than implemented silently — and **resolved on 2026-07-25**: the spec now
carries SC-004 (Android: ≥95% within 2 s) and SC-004a (iOS: released within 2 s, reclaimed by the OS
when it needs space), so spec and platform agree. FR-026's "sizes reflect actual space occupied"
holds on both, since iOS reports the matn as not installed immediately.

**Alternatives**: (a) Show the same "freed X" copy on both platforms — rejected: it would be false
on iOS, and SC-003's "total matches actual device footprint" would drift with no way to reconcile.
(b) Drop ODR and self-host on iOS only — rejected as a much larger scope change, and no longer
needed now that SC-004a states the iOS guarantee honestly; it remains the escalation path if an
identical cross-platform guarantee is ever required, and the `ContentDeliveryEngine` seam makes it a
one-implementation swap. (c) Ship iOS audio install-time —
rejected: abandons the phase's goal on one platform.

## D3 — Pack granularity, naming, and the catalog

**Decision**: One delivery unit per matn, holding that matn's complete per-verse audio. Identifier
is a stable, lowercase, underscore-delimited slug derived from the matn's content id and fixed at
authoring time — `matn_ajurrumiyya` — used verbatim as the Android asset-pack module name and as the
iOS ODR tag. The mapping matn → pack id is data, stored in a new `content_pack` table, never
computed from a title.

**Rationale**: Matches the spec's atomic per-matn unit (FR-001) and keeps one identifier across both
platforms, so `ContentDeliveryEngine` takes a single `packId` and neither actual invents naming.
Android asset-pack names must be valid Gradle module names (letters, digits, underscores) — the slug
satisfies both platforms' constraints. Storing the mapping keeps pack ids stable if a title is ever
edited, which Principle VI's stable-identity rule demands.

**Alternatives**: (a) Use the matn UUID as the pack name — rejected: Gradle module names cannot
start with a digit or contain hyphens, and unreadable module names make the build tree hostile.
(b) Group several متون per pack — rejected: breaks per-matn install/remove.

## D4 — Where the pre-install size comes from

**Decision**: Every matn carries a **declared size** persisted in `content_pack.declared_size_bytes`,
**measured from its actual audio files** at authoring time (and re-measured whenever that audio
changes — this is what makes SC-002's within-5% guarantee hold without a live lookup), seeded with
the catalog and therefore always readable offline. For the bundled starter matn, whose audio has no
pack directory to measure at runtime, this measured figure is also its `occupiedBytes` in storage
reporting (data-model §2.1). On Android, `requestPackStates()`
supplies a **live size** that supersedes the declared figure whenever a query succeeds. On iOS no
live size exists, so the declared figure is authoritative there. Once installed, both platforms
report **occupied bytes** measured from the filesystem, which supersedes both.

**Rationale**: This is exactly the three-tier model the spec's FR-003 clarification asked for, and it
is forced by the platforms: PAD can quote a size only with network and only for packs it knows;
ODR has no size-query API at all. A declared size shipped with the catalog is the only figure
guaranteed to render on a cold, offline first launch (FR-003, SC-011) — and keeping it in the
database rather than in code means content changes do not require a code change.

**Alternatives**: (a) Query the platform lazily and show a spinner — rejected: FR-003 forbids
blocking the install action on a size lookup, and it is dead on iOS. (b) Cache the last live size
only — rejected: nothing to show before the first successful query, which is precisely the cold-start
case. (c) Compute size from verse count × average bitrate — rejected: an estimate no more accurate
than a declared value and impossible to correct per matn.

## D5 — Availability is derived, never persisted

**Decision**: `ContentAvailability` is computed at read time from the delivery platform
(`getPackLocations()` / `AssetPackStatus` on Android, tag-retention state on iOS) combined with the
`content_pack` row. No availability column is written to the database. In-flight progress lives in
an in-memory `StateFlow` owned by the repository.

**Rationale**: FR-010 requires state derived from what is actually present rather than an optimistic
record, and the OS can evict content behind the app's back (spec edge case). A persisted flag would
be a second source of truth guaranteed to drift — the exact failure the edge case describes. Reading
through to the platform makes eviction self-healing: the matn simply reports not installed on next
read. It also means no migration is needed for availability itself.

**Alternatives**: (a) Persist availability and reconcile on launch — rejected: a reconcile pass is
strictly more code than reading the truth directly, and it is wrong between passes. (b) Persist a
cache with a TTL — rejected: same drift, plus a TTL to tune.

## D6 — Audio source resolution becomes matn-scoped

**Decision**: Widen the existing seam from `AudioSourceResolver.resolve(fileRef)` to
`resolve(matnId, fileRef)`. The implementation asks `ContentDeliveryEngine.locate(packId)` for the
installed pack's root and returns a file URI beneath it; for the starter matn it keeps returning
today's Compose-resources URI. `BuildPlaybackQueueUseCase` — the single caller — passes the matnId it
already has.

**Rationale**: Today's `Res.getUri("files/audio/$fileRef")` assumes every file is bundled in the app,
which is precisely the assumption this phase removes. Resolution now depends on *which* matn a file
belongs to, because that determines whether it lives in Compose resources (starter) or under a
delivered pack root. One call site changes, and the resolver stays an interface so `FakeAudioSource`
in tests is unaffected in shape.

**Alternatives**: (a) Keep the single-argument signature and encode the matn in `fileRef` — rejected:
smears identity into a filename string and breaks the seeded data. (b) Resolve at seed time and store
absolute paths — rejected: pack roots change across installs and evictions, so stored paths rot.

## D7 — The starter matn

**Decision**: الأجرومية (the existing simple seeded matn, `b3f1e2a4-…-000000000001`) is the starter.
Its four audio files stay exactly where they are — `shared/src/commonMain/composeResources/files/audio/`
— so it ships install-time inside the base app. Its `content_pack` row is flagged `is_starter = 1`,
which is what makes it non-removable (FR-027) and keeps it out of "remove all" (FR-029). The
structured sample's audio moves out to an on-demand pack, giving the phase a real second case to
exercise.

**Rationale**: FR-014 demands a working app with no network on a fresh install, and the simple matn
is the smaller of the two seeds, matching the spec's "smallest complete matn" default. Leaving its
files in Compose resources means zero change to the working Phase 1–7 playback path for that matn —
the starter is the regression anchor while everything else moves.

**Alternatives**: (a) Make the structured matn the starter — rejected: larger, and the simple one is
the better first-run experience. (b) No starter at all — rejected: violates FR-014 and leaves a
network-less first launch with nothing to play.

## D8 — Measuring occupied and free space

**Decision**: A new `commonMain` interface `DeviceStorage` with `freeSpaceBytes()` and
`sizeOfDirectory(path)`, implemented per platform — Android via `StatFs` on the app's files
directory, iOS via `NSFileManager.attributesOfFileSystemForPath` plus a recursive enumeration for
directory size. Injected everywhere; faked in `commonTest`.

**Rationale**: FR-026 (actual occupied space) and FR-030 (free space) cannot be answered from
common code, but the *reporting logic* — summing, ordering largest-first, formatting, deciding what
counts toward the total — is business logic that Principle IV requires in `commonMain`. An interface
rather than `expect`/`actual` keeps it fakeable per Principle V, matching how `AudioEngine` and
`WakeLock` are already handled.

**Alternatives**: (a) `expect`/`actual` functions — rejected: not injectable, so storage tests would
need a device. (b) Trust the platform's reported pack size instead of measuring — rejected: PAD
reports download size, not on-disk size, and iOS reports neither.

## D9 — Where playback is gated

**Decision**: A single `EnsureMatnPlayableUseCase` consulted at **session start** — inside
`PlaybackController.startSession` via an injected availability check, and again at the UI entry
points (library card, details play action, Continue Learning) so the install prompt can be shown
before a session is even attempted. No per-verse or per-transition check.

**Rationale**: Availability is per matn and atomic (FR-001), so one check per session is sufficient
and correct — this is the simplification the spec's Q1 clarification bought. Threading a check
through `moveToVerse`/`applyCursor` would add a suspend hop to the gapless transition path, which
Principle VII protects. `BuildPlaybackQueueUseCase` already fails with `AppError.NotFound` when a
matn has no playable tracks; the gate turns that into an actionable install prompt (FR-011) rather
than a dead end.

**Alternatives**: (a) Gate inside `BuildPlaybackQueueUseCase` only — rejected: the UI needs to know
*before* the play action to render an install affordance instead of a play button. (b) Per-verse
checks — rejected: models a partial state the spec abolished, and risks audible stalls.

## D10 — Progress, background continuation, and cancellation

**Decision**: `ContentDeliveryEngine.observe(packId): Flow<DeliveryProgress>` is the single progress
surface. Android backs it with an `AssetPackStateUpdateListener` registered for the app's lifetime;
iOS with KVO on the request's `NSProgress.fractionCompleted`. Because PAD downloads are managed by
Play outside the process and ODR requests survive backgrounding, FR-005's "continues while
backgrounded" is the platforms' default behaviour — the app re-reads true state on resume rather
than tracking it itself. Cancellation maps to `cancel()` (Android) and releasing the request
(iOS). No notifications are posted (FR-005).

**Rationale**: Re-reading state on resume is the only approach that survives process death, which
FR-009 explicitly covers. Deriving progress from the platform rather than mirroring it in app state
is D5 applied to the in-flight case.

**Alternatives**: (a) A foreground service / background task mirroring progress — rejected:
unnecessary, since both platforms already own the transfer, and it would invite the notification the
spec forbids. (b) Polling `requestPackStates()` on a timer — rejected: wasteful and laggy versus the
listener.

## D11 — Large downloads, metered connections, and user confirmation

**Decision**: Map PAD's `WAITING_FOR_WIFI` and `REQUIRES_USER_CONFIRMATION` statuses onto explicit
domain states (`Installing.WaitingForNetworkPolicy` and a `RequiresConfirmation` signal the
ViewModel surfaces). The app itself adds no Wi-Fi-only preference (spec Assumptions) — it renders
what the platform reports and, when Play asks for confirmation, forwards it via
`showConfirmationDialog()`.

**Rationale**: Play requires explicit user consent for downloads over ~200 MB on non-Wi-Fi
connections and will park the pack in `WAITING_FOR_WIFI` if Wi-Fi is lost mid-transfer. Without
domain states for these, such an install would look stuck — a silent stall, which SC-007 forbids.
This is also the honest implementation of the spec's metered-connection edge case.

**Alternatives**: (a) Treat these statuses as failures — rejected: they are recoverable waits, and
failing would discard a partially transferred pack. (b) Ignore them — rejected: produces the stall.

## D12 — Testing without Play or the App Store

**Decision**: All logic is tested in `commonTest` against a `FakeContentDeliveryEngine` and a
`FakeDeviceStorage` that script every status path (progress, cancel, failure, eviction, waiting for
Wi-Fi, both removal outcomes). On-device verification of the real Android path uses
`bundletool build-apks --local-testing` + `install-apks`, which serves asset packs from device
storage without a Play upload; iOS ODR is exercised through Xcode's built-in ODR hosting in debug
builds.

**Rationale**: Principle V requires device-free tests for all domain/data logic, and PAD is
unavailable to a bare debug APK — the documentation is explicit that asset packs require an app
bundle. Putting every decision behind the engine interface means the untestable-in-CI surface is
reduced to two thin adapters. `--local-testing` is the documented escape hatch for the manual pass.

**Alternatives**: (a) Instrumented tests against real Play delivery — rejected: needs a Play upload
per run, cannot run in CI, and is not reproducible. (b) Skip testing the delivery paths — rejected:
they are the phase.

## D13 — Concurrency

**Decision**: Accept concurrent install requests and let the platform schedule them. The repository
keeps one progress `StateFlow` per pack; `AssetPackManager.requestFetch` already accepts and tracks
multiple packs independently, and each ODR request is its own object.

**Rationale**: FR-008 requires independent, individually cancellable states and that one failure not
affect the others — which is exactly the platforms' native behaviour. Serialising installs in app
code would add a queue with no user benefit, and the spec's Assumptions explicitly leave
simultaneous-versus-sequential to implementation.

**Alternatives**: (a) A single-flight queue — rejected: more code, slower for the student, no
requirement asks for it.
