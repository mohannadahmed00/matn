# Feature Specification: Student Remote Catalog & Download

**Feature Branch**: `013-student-remote-catalog`

**Created**: 2026-08-01

**Status**: Draft

**Input**: User description: "read @docs/ROADMAP.md and create a specification to Phase 13"

---

## Overview

Phase 13 completes the content-delivery replacement begun in Phases 11–12. The teacher's published
متون become the *only* content a student can ever see, reached over the network rather than
compiled into the app.

Two halves, and the second is as large as the first:

- **Added** — a catalog the student browses (overviews only: cover, title, author, description,
  verse count, download size), per-matn download of verse text and audio onto the device, and an
  honest empty/offline state for a library that starts with nothing in it.
- **Retired** — everything that made content arrive any other way: the matn compiled into the app
  binary, the store-delivered content packs, the platform-specific delivery mechanisms, and the
  permanently-present "starter" matn that no student could remove.

What does *not* change is the student's experience of content they already have. Once a matn is on
the device it behaves exactly as it does today — reading, playback, repetition, A–B loops, progress,
bookmarks, notes, and resume all work with no network, forever.

---

## Clarifications

### Session 2026-08-02

- Q: How is student-facing content access controlled, given FR-027 forbids student accounts? → A: Fully public read — catalog overviews, verse text and audio are readable anonymously with no credential; only the teacher authenticates, and only to publish.
- Q: What scale should the catalog and per-matn downloads be designed for? → A: Small — at most ~50 متون in the catalog, each up to a few hundred verses and tens of MB downloaded; a whole-catalog fetch in a single request is sufficient.
- Q: Do cached cover images count as "downloaded content" for the storage breakdown and "remove all"? → A: No — covers are catalog data, excluded from the storage breakdown entirely and unaffected by removal.
- Q: How many downloads may run at once? → A: One active transfer; further requests are accepted, shown as queued, and run in request order.
- Q: How often does the automatic catalog sync actually fire on library open? → A: Only when the cached catalog is older than a 1-hour staleness window; otherwise the cached catalog renders immediately. Explicit refresh always syncs regardless.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse what the teacher has published (Priority: P1)

A student opens the app for the first time. The library is empty — nothing shipped with the app —
so the app fetches the list of متون the teacher has published and shows them as browsable cards,
each with its cover, title, author, short description, verse count, and the space a download would
take. Nothing has been downloaded yet; the student is simply seeing what is available. Once seen,
that list stays on the device, so a later launch with no signal still shows the same library rather
than an empty screen.

**Why this priority**: Nothing else in this phase is reachable without it. With no bundled content,
the catalog *is* the library — a student who cannot see what exists cannot download, study, or
search anything. It is also the slice that proves the new content channel works end to end, from
what a teacher published to what a student sees.

**Independent Test**: Can be fully tested by publishing a matn from the authoring tool, opening a
fresh install of the student app with connectivity, confirming the matn appears with its correct
cover, title, author, description, verse count and download size, force-quitting and reopening with
the network disabled to confirm the same list still renders from the device — all with no download
performed and no playback attempted.

**Acceptance Scenarios**:

1. **Given** a fresh install and a working connection, **When** the student opens the library,
   **Then** every matn the teacher has published appears as a card showing cover, title, author,
   description, verse count and download size, and each clearly reads as not yet downloaded.
2. **Given** a fresh install and **no** connection, **When** the student opens the library,
   **Then** the screen shows an actionable "connect to browse" state explaining that content is
   fetched from the teacher and offering a retry — not an error page, not a blank grid, and not a
   spinner that never resolves.
3. **Given** the student has browsed the catalog at least once, **When** they reopen the app with no
   connection, **Then** the previously seen catalog still renders in full, with every matn's stated
   download size still shown.
4. **Given** the teacher has published nothing, **When** the student opens the library with a working
   connection, **Then** the screen states that no متون are available yet rather than implying a
   failure.
5. **Given** a matn the teacher has left unpublished, **When** the student browses the catalog,
   **Then** it does not appear anywhere in the app.
6. **Given** a matn whose cover image cannot be fetched, **When** the student browses the catalog,
   **Then** the card still renders with a placeholder cover and every other field intact.
7. **Given** the student is browsing the catalog, **When** they open a matn that is not downloaded,
   **Then** its details screen shows the same overview information plus a download action, and does
   not show verse text.

---

### User Story 2 - Download a matn and study it offline (Priority: P2)

A student picks a matn from the catalog and downloads it. They see progress while its verse text and
per-verse recitations transfer onto the device. When it finishes, the matn is fully theirs: they can
read every verse, play the recitation gaplessly, run repetition and A–B loops, mark verses
memorized, bookmark, and take notes — with the network off, on a plane, indefinitely.

**Why this priority**: This is the phase's core promise and the point at which a student can
actually study. It depends on User Story 1 existing, but the moment it does it delivers the whole
product value. It ranks below browsing only because a catalog with nothing downloaded is still a
demonstrable, testable slice while a download with no catalog is not reachable at all.

**Independent Test**: Can be fully tested by downloading one matn from the catalog, watching
progress to completion, disabling the network, then reading its verses, playing them end to end,
running a repetition count and an A–B loop, marking a verse memorized, and restarting the app to
confirm the matn is still present and still plays — with no removal or storage screen involved.

**Acceptance Scenarios**:

1. **Given** a matn in the catalog that is not downloaded, **When** the student activates its
   download action, **Then** the transfer begins and the matn shows an advancing progress state.
2. **Given** a download in progress, **When** it completes, **Then** the matn reads as downloaded,
   its download action is replaced by a remove action, its verse text is readable, and every one of
   its verses is playable.
3. **Given** a downloaded matn and a device with no connection, **When** the student reads, plays,
   repeats, loops A–B, marks memorized, bookmarks, writes a note, or resumes, **Then** all of it
   works with no network round-trip and nothing is degraded.
4. **Given** a download in progress, **When** the student cancels it, **Then** the transfer stops,
   the matn returns to the not-downloaded state, and any space already consumed is released.
5. **Given** a download in progress, **When** the connection is lost mid-transfer, **Then** the matn
   resolves to not-downloaded with a stated reason and a retry action — never to a partially
   available state that claims to be playable.
6. **Given** a download in progress, **When** the student leaves the app and returns later,
   **Then** the transfer has continued in the meantime and the screen shows its true current state —
   advancing, complete, or failed with a retry — rather than having silently restarted.
7. **Given** a matn that is not downloaded, **When** the student attempts to play one of its verses,
   **Then** playback does not start and they are shown an actionable prompt to download it instead
   of a silent failure.
8. **Given** less free space on the device than the matn requires, **When** the student attempts to
   download it, **Then** the download does not start and they are told how much space is needed
   against how much is available.
9. **Given** no connection, **When** the student attempts to download a matn, **Then** the download
   does not start, they are told connectivity is required and offered a retry, and the matn remains
   accurately reported as not downloaded.
10. **Given** one download running, **When** the student starts a second, **Then** the second is
    accepted and shown as queued rather than transferring, both are tracked with independent,
    individually cancellable states, the queued one starts when the first settles, and both
    eventually complete.
11. **Given** a queued download that has not started, **When** the student cancels it, **Then** it
    returns to not-downloaded, nothing was transferred for it, and the running download and any
    other queued متون are unaffected.
12. **Given** a downloaded matn, **When** the app is fully closed and reopened, or the device is
    restarted, **Then** the matn is still downloaded and plays without transferring anything again.
13. **Given** a matn the teacher published with recitations missing for some verses, **When** the
    student downloads it, **Then** the download succeeds, the verses that have audio play normally,
    and the verses without audio are readable and clearly marked as having no recitation rather
    than failing playback for the whole matn.

---

### User Story 3 - Remove a matn to reclaim space (Priority: P3)

A student who has finished with a matn — or is running low on space — removes it. Before confirming
they see how much space they get back. The matn stays in the library as a catalog entry they can
download again, and everything personal they built around it (bookmarks, notes, memorized verses,
progress) survives untouched. Settings shows the total space downloaded content occupies, broken
down per matn, and offers a single action to clear it all. Unlike before, **no matn is exempt** —
there is no permanently-installed item the student cannot remove.

**Why this priority**: Removal is what makes downloading safe and reversible; without it students
ration their downloads. Most of this behaviour already exists — this phase carries it across to the
new content source and removes the one exception that used to sit in it.

**Independent Test**: Can be fully tested by downloading two متون, bookmarking a verse, writing a
note and marking a verse memorized in one of them, removing it (confirming the reclaimed-space
figure), verifying it reverts to a catalog entry with its overview intact and all personal data
preserved, checking the Settings storage total drops accordingly and lists no non-removable row, and
re-downloading to confirm playback resumes where it left off.

**Acceptance Scenarios**:

1. **Given** a downloaded matn, **When** the student activates remove, **Then** a confirmation
   stating the space to be reclaimed is shown, and removal happens only on confirmation.
2. **Given** removal completes, **When** the student returns to the library, **Then** the matn still
   appears as a catalog entry with its full overview, reads as not downloaded, and offers download
   again.
3. **Given** a removed matn, **When** the student inspects their bookmarks, notes, memorized marks
   and progress, **Then** all of it is intact and still attributed to that matn.
4. **Given** a removed matn, **When** the student re-downloads it, **Then** reading and playback
   resume at the verse and position they had reached.
5. **Given** several downloaded متون, **When** the student opens Settings, **Then** the total space
   used by downloaded content is shown with a per-matn breakdown, and **no** row is marked
   non-removable or excluded from removal.
6. **Given** downloaded content exists, **When** the student uses "remove all downloaded content",
   **Then** every downloaded matn is removed with no exceptions, all overviews remain browsable, and
   all personal data survives.
7. **Given** a download in progress, **When** the student removes that same matn, **Then** the
   transfer is abandoned, the matn resolves to not downloaded, and no partial content is left behind.

---

### User Story 4 - Keep up with what the teacher changes (Priority: P4)

The teacher publishes a new matn, revises one, or withdraws one. The student's library reflects it
the next time they refresh — new متون appear, revised ones are flagged so the student can pull the
newer version, and withdrawn ones stop being offered. Content the student already downloaded is
never taken away from them: a withdrawn matn they already have keeps working, offline, forever.

**Why this priority**: The catalog is only trustworthy if it tracks its source. Without this the
student sees a snapshot frozen at first launch. It ranks below downloading because the initial
catalog and download flow already deliver a working product for a static library.

**Independent Test**: Can be fully tested by opening the app with a synced catalog, then publishing
one new matn, revising a second, and unpublishing a third from the authoring tool, refreshing the
student library, and confirming the new one appears, the revised one is flagged as having an update,
and the withdrawn one is gone from the catalog while a previously downloaded copy of it still reads
and plays offline.

**Acceptance Scenarios**:

1. **Given** the teacher publishes a new matn, **When** the student refreshes the library, **Then**
   the new matn appears in the catalog with its full overview.
2. **Given** the teacher unpublishes a matn the student has **not** downloaded, **When** the student
   refreshes, **Then** it disappears from the catalog entirely.
3. **Given** the teacher unpublishes a matn the student **has** downloaded, **When** the student
   refreshes, **Then** the downloaded matn remains present, readable and playable offline, and is
   clearly no longer offered as a new download to anyone.
4. **Given** the teacher revises a matn the student has downloaded, **When** the student refreshes,
   **Then** the matn is flagged as having a newer version available and the student's existing copy
   keeps working untouched until they choose to update.
5. **Given** a matn flagged as having an update, **When** the student chooses to update it,
   **Then** the newer verse text and recitations replace the old ones and the student's bookmarks,
   notes, memorized marks and progress for verses that still exist are preserved.
6. **Given** a refresh that fails (no connection, or the source is unreachable), **When** the student
   is browsing, **Then** the previously synced catalog stays fully usable and the failure is
   reported as a non-blocking, retryable notice rather than emptying the library.
7. **Given** the student is browsing the library, **When** they explicitly request a refresh,
   **Then** the catalog re-syncs and any change since the last sync is reflected.

---

### User Story 5 - Find a matn when the library grows (Priority: P5)

A student with a mix of downloaded and not-yet-downloaded متون searches. Titles and authors across
the **whole** catalog are searchable, so they can find something they have never downloaded and be
taken to its details screen to get it. Verse text is searchable within the متون they have
downloaded, because that is the only place verse text exists on their device. Search itself never
needs the network.

**Why this priority**: This is a narrowing correction to already-shipped search behaviour rather than
new capability. It matters for honesty — a student must not conclude a verse does not exist when it
is merely not downloaded — but the app is fully usable without it.

**Independent Test**: Can be fully tested by downloading one matn and leaving another undownloaded,
then searching for a word appearing in the downloaded matn's verses (expecting verse hits), a word
appearing only in the undownloaded matn's verses (expecting no verse hits, with an explanation), and
the undownloaded matn's title (expecting a catalog hit that leads to its details screen) — all with
the network disabled.

**Acceptance Scenarios**:

1. **Given** متون in the catalog, downloaded or not, **When** the student searches a title or author,
   **Then** every matching matn is returned regardless of download state.
2. **Given** a search result for a matn that is not downloaded, **When** the student selects it,
   **Then** they land on its details screen with a download action rather than on a verse.
3. **Given** a downloaded matn, **When** the student searches text appearing in its verses, **Then**
   matching verses are returned and selecting one opens that verse.
4. **Given** a matn that is not downloaded, **When** the student searches text that appears only in
   its verses, **Then** no verse results are returned for it and the student is told that verse
   search covers downloaded متون only.
5. **Given** no connection, **When** the student searches, **Then** search behaves identically —
   nothing about it requires connectivity.

---

### Edge Cases

- **Nothing published at all**: a synced-but-empty catalog reads as "no متون available yet", visibly
  distinct from "could not reach the catalog" and from "you have not downloaded anything yet".
- **Connectivity lost mid-download**: resolves to not-downloaded with a retryable reason; never a
  half-available matn.
- **Connectivity lost mid-catalog-sync**: the last successfully synced catalog remains intact; a
  partial sync never replaces a good catalog with a truncated one.
- **Device runs out of space mid-download**: the download fails, the matn reports not downloaded, and
  the partially transferred bytes are released rather than silently occupying space.
- **A matn is unpublished while the student is downloading it**: the in-flight download either
  completes into a usable matn or fails cleanly to not-downloaded; the student is never left with a
  matn that half-exists.
- **A matn is revised while the student is downloading it**: the resulting download is internally
  consistent — verse text and recitations come from a single version, never a mix of two.
- **A recitation object is missing or corrupt for one verse**: that verse is marked as having no
  playable recitation; the rest of the matn downloads and plays.
- **Continue Learning points at a matn that has been removed**: the resume entry offers to
  re-download rather than failing to open or disappearing without explanation.
- **Continue Learning points at a matn that was unpublished but is still downloaded**: resume works
  normally — the offline guarantee is unaffected by publication state.
- **A previously downloaded matn's files are deleted outside the app** (device cleaner, OS storage
  reclamation): the matn accurately reports not downloaded on the next read and offers download
  again, rather than claiming to be playable and failing.
- **Upgrading from a build that bundled a starter matn**: the previously bundled matn no longer
  exists as an app-provided item. If the teacher has published it, it appears in the catalog as an
  ordinary downloadable entry; if not, it is absent. Personal data keyed to its verses is preserved
  and reattaches if that matn is later downloaded. Nothing is re-fetched automatically.
- **Two devices, same content**: nothing about the catalog or downloads assumes a student account;
  each device's downloads and personal data stand alone.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Catalog

- **FR-001**: The app MUST obtain its entire library from the teacher's published content. No matn,
  verse text, or recitation may be present in the app's installed binary.
- **FR-002**: The catalog MUST expose, for every published matn, an **overview**: stable identity,
  title, author, description, cover image reference, structure (simple or chaptered), verse count,
  and download size in bytes.
- **FR-003**: A catalog sync MUST NOT transfer verse text or recitation audio. Those arrive only
  with an explicit per-matn download.
- **FR-004**: Only متون the teacher has marked published MUST be visible to students. An unpublished
  matn MUST be absent from the catalog, from search, and from every other surface.
- **FR-005**: Synced overviews MUST persist on the device, so the catalog browses in full with no
  connectivity after the first successful sync.
- **FR-006**: The app MUST sync the catalog automatically when the library is opened with
  connectivity available **and** the cached catalog is older than a staleness window of 1 hour. When
  the cached catalog is within that window, the library MUST render it immediately without a network
  round-trip, so repeated navigation into the library costs nothing. The app MUST additionally offer
  an explicit student-initiated refresh, which syncs unconditionally and ignores the window.
- **FR-007**: A failed or interrupted sync MUST leave the last successfully synced catalog intact
  and report the failure as a retryable, non-blocking notice.
- **FR-008**: A sync MUST reconcile against the source: newly published متون are added, revised متون
  are flagged, and متون no longer published are withdrawn from the catalog.
- **FR-009**: A withdrawn matn that is currently downloaded MUST remain fully usable on the device.
  Withdrawal removes it from what can be newly downloaded; it never removes what a student already
  has.
- **FR-010**: The app MUST record the version of each downloaded matn, and MUST flag a downloaded
  matn whose published version has advanced as having an update available, without altering the
  student's existing copy until they choose to update.
- **FR-011**: Updating a downloaded matn MUST replace its verse text and recitations with the newer
  version and MUST preserve the student's bookmarks, notes, memorized marks and progress for every
  verse that still exists in the newer version.
- **FR-012**: Cover images MUST be fetched and cached opportunistically; a cover that cannot be
  fetched MUST NOT block or degrade any other part of the overview. A cached cover is catalog data,
  not downloaded content: it MUST NOT be counted in the storage breakdown, MUST NOT be attributed to
  any matn's occupied bytes, and MUST survive both individual removal and "remove all downloaded
  content" so the catalog still renders offline afterwards.

#### Download & availability

- **FR-013**: Students MUST be able to download an individual matn, bringing its verse text and its
  per-verse recitations onto the device.
- **FR-014**: A download MUST show advancing progress and MUST be individually cancellable.
- **FR-015**: Multiple downloads MUST be trackable concurrently with independent, individually
  cancellable states. Exactly one transfer runs at a time: additional requests MUST be accepted,
  shown as **queued** and distinguishable from the transfer actually in progress, then started in
  request order as each preceding one settles. A queued matn MUST be cancellable before it starts,
  and cancelling or removing one MUST NOT disturb any other. Every accepted request MUST eventually
  run.
- **FR-016**: Availability MUST be tracked per matn only. A download is atomic from the student's
  point of view: it is downloaded or it is not. No partial-availability state is ever tracked or
  shown.
- **FR-017**: An interrupted, failed or cancelled download MUST resolve to not-downloaded, carrying a
  reason the student can act on, and MUST release any space it consumed.
- **FR-018**: A download MUST continue while the app is backgrounded, and the queue MUST keep
  advancing to the next request while backgrounded. The true state of every download — running,
  queued, complete or failed — MUST be reflected on return.
- **FR-019**: The app MUST refuse to start a download when free space is insufficient, stating the
  space required against the space available.
- **FR-020**: The app MUST refuse to start a download with no connectivity, stating that connectivity
  is required and offering a retry.
- **FR-021**: Attempting to play a matn that is not downloaded MUST present an actionable download
  prompt rather than failing silently or erroring.
- **FR-022**: Download state MUST survive app restart and device restart, determined from what is
  actually present on the device rather than from a stored flag that can drift.
- **FR-023**: A matn whose published recitations are incomplete MUST still download and read; verses
  without a recitation are marked as such and MUST NOT prevent the rest of the matn from playing.
- **FR-024**: Every downloaded recitation MUST be a self-contained per-verse audio file. The app MUST
  NOT accept, store, or play content expressed as time offsets into a shared or continuous recording.

#### Offline guarantee

- **FR-025**: For every downloaded matn, reading, playback, gapless verse transitions, repetition
  counters, A–B loops, playback speed, progress, memorized marks, bookmarks, notes, daily goals and
  resume MUST work with no connectivity and MUST NOT make any network request.
- **FR-026**: No personal student data (bookmarks, notes, progress, resume state, settings) may
  depend on a network round-trip or be lost when content is removed.
- **FR-027**: Students MUST NOT need an account, sign-in, or any credential to browse the catalog or
  download content. Published overviews, verse text and recitations MUST be readable anonymously:
  no student-side token, embedded key, shared secret, or per-download URL grant may gate a read.
  Authentication exists on the teacher's side only, and only for publishing.

#### Removal & storage

- **FR-028**: Students MUST be able to remove any downloaded matn individually, seeing the space to
  be reclaimed before confirming.
- **FR-029**: Removal MUST preserve the matn's catalog entry (it remains browsable and
  re-downloadable) and MUST preserve every piece of personal data attached to it.
- **FR-030**: Settings MUST show total space used by downloaded content — verse text and recitations,
  excluding cached cover images — with a per-matn breakdown, and MUST offer a single action to remove
  all downloaded content.
- **FR-031**: **No matn may be exempt from removal.** Every item in the storage breakdown is
  removable, and "remove all downloaded content" MUST spare nothing.
- **FR-032**: Removing a matn whose download is still in progress or still queued MUST abandon that
  transfer, drop it from the queue, and leave nothing partial behind.

#### Search

- **FR-033**: Search MUST match titles and authors across the **entire** catalog, including متون that
  are not downloaded.
- **FR-034**: Search MUST match verse text within **downloaded** متون only.
- **FR-035**: When a search result is a matn that is not downloaded, selecting it MUST lead to its
  details screen with a download action, not to a verse.
- **FR-036**: The app MUST make the scope of verse search explicit to the student, so an absent
  result is understood as "not downloaded" rather than "does not exist".
- **FR-037**: Search MUST remain entirely offline and MUST retain its existing diacritic-insensitive
  matching behaviour.

#### Retirement of the superseded delivery model

- **FR-038**: The app MUST NOT ship any matn, verse text, cover image, or recitation inside its
  installed binary or as any store-side companion download.
- **FR-039**: The app MUST NOT depend on any platform store's on-demand asset-delivery mechanism for
  content.
- **FR-040**: The concept of a permanently-present, non-removable "starter" matn MUST be gone from
  the product entirely — from the library, the details screen, the storage breakdown, "remove all",
  and audio resolution.
- **FR-041**: All three client platforms MUST acquire content through one common mechanism; no
  platform may retain its own separate content-acquisition path.
- **FR-042**: The app MUST remain independently buildable, runnable and testable on every client
  platform after the retirement, with no residual references to the removed delivery model.

#### Errors & resilience

- **FR-043**: Every failure a student can hit — unreachable catalog, failed download, insufficient
  space, missing recitation, unreachable cover — MUST surface as a specific, human-readable message
  with a clear next action, never as a raw error code or a silent no-op.
- **FR-044**: The app MUST distinguish, in what it shows the student, between "no connectivity",
  "catalog reachable but empty", and "you have not downloaded anything yet".
- **FR-045**: A downloaded matn whose files have disappeared from the device outside the app MUST be
  reported as not downloaded on the next read and MUST offer download again.

### Key Entities

- **Catalog Overview**: The student-visible summary of one published matn — stable identity, title,
  author, description, cover reference, structure, verse count, download size, published version.
  Carries no verse text and no audio. Synced from the teacher's published content and cached on the
  device.
- **Downloaded Matn**: A matn whose verse text and per-verse recitations are present on the device.
  Fully usable offline. Distinct from its catalog overview, which exists whether or not the matn is
  downloaded.
- **Download State**: Per matn, one of not-downloaded (optionally carrying a failure reason), queued
  (accepted, awaiting the single active transfer slot), downloading (with progress), or downloaded
  (with occupied bytes). Derived from what is actually on the device, never from a persisted flag.
- **Verse Recitation**: One self-contained audio file for exactly one verse, with its duration.
  Arrives only with a download.
- **Catalog Sync State**: When the catalog was last successfully synced, and whether the most recent
  attempt failed — the basis for the offline/empty/error distinctions the student sees, and for
  deciding whether an automatic sync is due against the staleness window.
- **Storage Usage**: Total bytes occupied by downloaded content — verse text and recitations only,
  never cached cover images — with a per-matn breakdown and free device space. Every entry removable.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a fresh install with a working connection, a student sees the published catalog —
  up to the full ~50 متون — within 3 seconds of opening the library.
- **SC-002**: 100% of متون a teacher publishes appear in the student catalog after one refresh, and
  100% of متون the teacher unpublishes disappear from it after one refresh.
- **SC-003**: A student can go from opening a fresh install to hearing the first verse of a chosen
  matn — one of a few hundred verses and tens of megabytes — in under 3 minutes on a typical mobile
  connection.
- **SC-004**: For a downloaded matn, 100% of study features — reading, playback, repetition, A–B
  loops, progress, bookmarks, notes, resume — work with the network fully disabled, and zero network
  requests are issued while using them.
- **SC-005**: The app's install size does not grow with the catalog. Adding a matn to the catalog
  adds 0 bytes to what a student downloads from the store.
- **SC-006**: 100% of interrupted, cancelled or failed downloads resolve to not-downloaded with a
  stated reason and a retry, and release the space they consumed. Zero resolve to a state that
  claims playability.
- **SC-007**: A student with no connectivity — on first launch or any later launch — reaches an
  actionable state within 3 seconds: either the cached catalog or a connect-to-browse prompt. Never
  a blank screen, an unresolved spinner, or a raw error.
- **SC-008**: Storage figures reported in Settings are within 5% of the space downloaded content
  actually occupies on the device.
- **SC-009**: 100% of items in the storage breakdown are removable; "remove all downloaded content"
  leaves 0 bytes of downloaded content behind.
- **SC-010**: Removing and re-downloading a matn preserves 100% of the student's bookmarks, notes,
  memorized marks and resume position for it.
- **SC-011**: Searching a title returns matches across the whole catalog with 100% recall regardless
  of download state, and search returns results in under 1 second with no connectivity — measured
  against a full ~50-matn catalog with the verses of several downloaded متون on the device.
- **SC-012**: After the retirement, zero content files ship in any client binary and zero
  platform-specific content-acquisition paths remain; all three student clients build, run and pass
  their tests.

---

## Assumptions

- **Single teacher, no student accounts.** Published content is world-readable: catalog overviews,
  verse text and recitations are fetched anonymously, with no credential on the student side at all
  (FR-027). Only the teacher authenticates, and only to publish. The content is freely distributable
  Islamic text, and any key shipped in a client binary would be extractable anyway, so gating reads
  would add secret-management cost without adding protection. Multi-teacher permissions and remote
  student accounts remain deferred, and nothing here blocks adding them later.
- **The published flag is the only gate on student visibility.** There is no separate release,
  review, or staging step between a teacher publishing and a student seeing it.
- **Audio-incomplete متون are still visible.** Because publication is the only gate, a matn published
  with some or no recitations reaches students. It is downloadable and readable; verses lacking a
  recitation are marked rather than hidden. Deciding whether to publish an incomplete matn is the
  teacher's, in the authoring tool.
- **Automatic sync is on library open, throttled, not continuous.** The catalog re-syncs when the
  student opens the library with connectivity *and* the cached copy is more than an hour old, plus
  whenever they explicitly refresh. Within the hour, opening the library is a pure local read — the
  catalog changes rarely and a round-trip on every back-navigation would cost data and latency for
  nothing. There is no background polling, no push, and no notification when new content appears.
- **Update detection is a flag, not an automatic replacement.** A revised matn is surfaced to the
  student, who chooses when to pull it. Nothing on the device is ever replaced without an explicit
  action, which keeps the offline guarantee unconditional.
- **Verse identity is stable across revisions.** Personal data survives an update because verses keep
  their identity when a matn is revised; personal data attached to a verse the teacher deleted is
  orphaned, not migrated.
- **No migration path from the previous delivery model.** The app has not shipped, so there is no
  installed base to protect. After upgrade, no matn reports as downloaded and the student downloads
  what they want. Nothing is fetched automatically.
- **The library is small by design.** The catalog holds on the order of tens of متون — at most ~50 —
  each up to a few hundred verses and tens of megabytes once downloaded. A sync may therefore fetch
  the whole catalog in one request; pagination, delta sync and incremental catalog transfer are
  unnecessary at this scale and are not required. The performance targets in Success Criteria are
  stated against a catalog of this size.
- **Download sizes come from the catalog overview**, so a size always renders — including offline —
  and no download is ever blocked waiting on a size lookup.
- **The existing content-availability and storage behaviour from Phase 8 carries over unchanged**
  except where this spec states otherwise (notably: the starter exemption is gone, and availability
  now covers verse text as well as audio). Its concurrency rules — duplicate download is a no-op,
  duplicate removal is a no-op, and a removal racing an in-flight download of the same matn wins —
  continue to hold.
- **Existing personal-data behaviour is untouched.** Bookmarks, notes, progress, goals, and resume
  keep their current semantics and storage; this phase changes only where content comes from.

## Dependencies

- **Phase 11** — the published catalog schema and the publication gate the student reads against.
- **Phase 12** — the per-verse recitation artifacts the student downloads, and their published
  layout and integrity guarantees.
- **Phase 8** — its content-availability and storage abstractions survive and are re-backed here; its
  delivery mechanism does not.
- At least one matn published from the authoring tool is required to test any part of this phase
  end to end.

## Out of Scope

- Remote student accounts, cloud backup, and cross-device sync of personal data.
- Multi-teacher permissions or a content-ownership model.
- Multi-reciter support (alternate recitations mapped to the same verses).
- Background or automatic downloading of new content without a student action.
- Push notifications or any alert when the teacher publishes something new.
- Partial or per-verse download granularity — download is per matn and atomic.
- Any change to the authoring tool.
