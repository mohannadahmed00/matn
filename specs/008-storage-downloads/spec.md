# Feature Specification: Storage & Downloads

**Feature Branch**: `008-storage-downloads`

**Created**: 2026-07-25

**Status**: Draft

**Input**: User description: "read @docs/ROADMAP.md and create a specification for the Phase 8 — Storage & Downloads"

## Clarifications

### Session 2026-07-25

- Q: Does an installation continue when the student switches away from the app? → A: Yes. The transfer continues while the app is backgrounded and its accurate state is reflected on return. No completion notifications are shown, and nothing is ever fetched without an explicit student action — "student-initiated" governs how a transfer starts, not whether it must stay on screen.
- Q: What size is shown before install when the store's figure is not yet known (cold start, offline, store unreachable)? → A: Every matn ships a declared size with the bundled catalog, so a figure always exists and renders offline; the store's live figure supersedes it whenever available. No "size unavailable" placeholder is needed and the install action is never blocked on a size lookup.
- Q: Does the bundled starter matn count toward the Settings storage total and breakdown? → A: Yes to both. It appears in the total and as a breakdown row marked non-removable (part of the app), and is excluded from "remove all downloaded content" — so the reported total stays honest against what the device actually holds without implying an action the student cannot take.
- Q: What happens on upgrade to content that was bundled in the app binary before this phase? → A: No migration path. The app has never shipped, so there is no installed base to protect — after upgrade every matn except the starter reports as not installed and the student installs what they want. Nothing is re-fetched automatically.
- Q: Is content availability tracked per verse or per matn, given atomic pack delivery? → A: Per matn only. The pack is atomic — an interrupted or failed install resolves to "not installed" carrying a retryable failure reason, and no partial-availability state is ever tracked or shown.
- Q: Where does an install pull its content from in v1 — bundled-and-activated, platform on-demand asset delivery, or a remote catalog? → A: Platform on-demand asset delivery. متون that are not installed are genuinely absent from the app's initial download, so installing one actually retrieves it. Connectivity is required at install time only; once installed, the matn is fully usable offline. The base app carries one starter matn so a fresh, network-less install is still a working app.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Install a matn's audio before studying it (Priority: P1)

A student browsing the library sees every matn in the collection, but only the ones they have chosen to install take up space on their device. Each matn shows whether its recitation audio is on the device and, if not, how much space installing it will take. The student picks the matn they are studying, taps install, watches a clear progress indication, and when it finishes the matn is fully playable — with no further waiting, and with no network needed afterwards.

**Why this priority**: Nothing else in this phase means anything until content can be selectively brought onto the device. This is the slice that delivers the phase's core promise from the product spec — "don't bundle every matn by default … keeps the app lightweight for students who only study a subset of available متون." It is also the only slice that is strictly required for the app to remain usable once the library grows beyond a handful of متون.

**Independent Test**: Can be fully tested by opening the library on a fresh install, confirming the starter matn is playable and every other matn shows a not-installed state with a stated install size, installing one of them, watching progress to completion, playing its verses end to end, disconnecting the network to confirm it still plays, and restarting the app to confirm it is still installed — with no removal or Settings surface involved.

**Acceptance Scenarios**:

1. **Given** a matn whose audio is not on the device, **When** the student views it in the library grid or opens its details screen, **Then** it clearly reads as not installed and shows the space its audio will occupy.
2. **Given** a not-installed matn, **When** the student activates its install action, **Then** installation begins and the matn shows an in-progress state with advancing progress.
3. **Given** an installation in progress, **When** it completes, **Then** the matn reads as installed, its install action is replaced by a remove action, and every one of its verses is playable.
4. **Given** an installation in progress, **When** the student cancels it, **Then** installation stops, the matn returns to the not-installed state, and any space already consumed is released.
5. **Given** a matn whose audio is not installed, **When** the student attempts to play one of its verses, **Then** playback does not start and the student is shown an actionable prompt to install the matn instead of a silent failure or error.
6. **Given** a matn was installed, **When** the app is fully closed and reopened (or the device is restarted), **Then** the matn still reads as installed and plays without re-installing.
7. **Given** less free space on the device than the matn requires, **When** the student attempts to install it, **Then** installation does not start and the student is told how much space is needed versus how much is available.
8. **Given** an installation is running, **When** the student requests a second matn's installation, **Then** both requests are tracked with independent, individually cancellable states and both eventually complete.
9. **Given** an installation is in progress, **When** the student switches away from the app and later returns, **Then** the installation has continued in the meantime and the screen shows its true current state — advancing, completed, or failed with a retry — rather than having silently restarted.
10. **Given** the device has no network connection, **When** the student attempts to install a matn, **Then** installation does not start, the student is told connectivity is needed and offered a retry, and the matn remains accurately reported as not installed.
11. **Given** an installed matn and a device with no network connection, **When** the student plays it, **Then** playback, repetition modes, A–B loops, and resume all work normally.
12. **Given** a fresh install of the app and no network connection, **When** the student opens the library, **Then** the starter matn is present and fully playable, and every other matn still shows its declared install size.

---

### User Story 2 - Remove a matn to reclaim space (Priority: P2)

A student who has finished with a matn — or who is running low on space — removes that matn's audio from their device. Before confirming, they see exactly how much space they will get back. After removal the matn stays in the library and stays readable and searchable, and everything personal they built up around it (bookmarks, notes, memorized verses, progress) is untouched. If they come back to it later, reinstalling restores playback and picks up exactly where they left off.

**Why this priority**: Removal is what makes the install decision safe and reversible; without it the storage story is one-way and students will avoid installing at all. It depends on User Story 1 existing but delivers standalone value the moment it does. It ranks below installation only because a student can study productively with install alone.

**Independent Test**: Can be fully tested by installing a matn, bookmarking a verse, writing a note, marking verses memorized, removing the matn (confirming the reclaimed-space figure), verifying the matn is still listed, readable, searchable and that all bookmarks/notes/memorized marks survive, then reinstalling and confirming playback resumes at the same verse and position.

**Acceptance Scenarios**:

1. **Given** an installed matn, **When** the student activates its remove action, **Then** a confirmation is shown stating how much space will be reclaimed, and removal happens only on confirmation.
2. **Given** a removal is confirmed, **When** it completes, **Then** the matn reads as not installed, its audio no longer occupies device space, and the install action is available again.
3. **Given** a matn with bookmarks, notes, and memorized verses, **When** its audio is removed, **Then** all of that personal data is preserved unchanged, and its verse text remains readable and findable in search.
4. **Given** the matn currently playing, **When** the student removes it, **Then** playback stops first and the player is left in a defined stopped state rather than erroring mid-verse.
5. **Given** the matn referenced by the Home screen's "Continue Learning" entry, **When** it is removed, **Then** the resume surface remains coherent — it either offers to reinstall that matn or steps aside — and never dead-ends on a broken resume.
6. **Given** a previously removed matn, **When** the student reinstalls it, **Then** playback works again and resuming returns to the same verse, playback position, and repetition settings that were saved before removal.
7. **Given** a removal is confirmed, **When** the student was mistaken, **Then** no personal data has been destroyed and reinstalling fully restores the previous study state.

---

### User Story 3 - See and manage total storage use in Settings (Priority: P3)

A student wondering why the app is taking up space opens Settings and finds a storage section: how much space the app's installed content uses in total, a per-matn breakdown ordered largest first, and the ability to remove any matn — or all of them — right there. This turns the Settings tab from a "coming soon" placeholder into a real screen.

**Why this priority**: This is the phase's most surface-area-heavy slice and it is only meaningful once متون can be installed and removed (User Stories 1–2). It answers the product spec's explicit requirement to "show total storage used by downloaded content in settings," and delivers the first real Settings screen, but a student can install and remove without it.

**Independent Test**: Can be fully tested by installing two متون of different sizes, opening the Settings tab, confirming the total-used figure, the per-matn breakdown ordered largest first, removing one matn from that screen, and watching the total and the list update immediately without restarting the app.

**Acceptance Scenarios**:

1. **Given** the app shell's bottom navigation, **When** the student opens the Settings tab, **Then** a real Settings screen appears with a storage section instead of the "coming soon" placeholder.
2. **Given** installed متون, **When** the student opens the storage section, **Then** it shows the total space used by all content on the device — the starter matn included — and a per-matn breakdown with each matn's title and size, ordered largest first.
3. **Given** the storage section, **When** the student removes a matn from it, **Then** the same confirmation rules as User Story 2 apply and, on completion, both the breakdown row and the total figure update without an app restart.
4. **Given** no on-demand content is installed, **When** the student opens the storage section, **Then** it shows a purposeful zero state inviting them to install from the library rather than a blank or broken screen, while still accounting for the starter matn.
5. **Given** installed content, **When** the student uses the "remove all downloaded content" action and confirms, **Then** every installed-on-demand matn's audio is removed, the total falls to the starter matn's size alone, the starter matn remains playable, and all bookmarks, notes, and memorized progress are preserved.
6. **Given** the storage breakdown, **When** the student views the starter matn's row, **Then** it is marked as part of the app, offers no remove action, and is left untouched by "remove all downloaded content".
7. **Given** an installation completes or a removal happens elsewhere in the app, **When** the student returns to the storage section, **Then** the figures shown reflect the current state.

---

### Edge Cases

- **Device runs out of space mid-install**: The installation must stop with a clear, non-technical message, release whatever partial content it wrote, and leave the matn accurately reported as not installed — never as installed-but-broken.
- **App is killed or the device restarts mid-install**: On next launch the matn must report an accurate state (not installed, or resumable-in-progress) and must never be reported as fully installed. No orphaned partial content may remain counted against the storage total.
- **Incomplete content on disk**: A matn whose audio is present but incomplete is never treated as playable. Availability is per matn and atomic, so any such matn reports as not installed with a retry offered — there is no half-available matn to reason about.
- **Removing content while it is being installed**: The two actions must not race — one wins and the resulting state is accurate and consistent.
- **Repeated taps on install/remove**: Duplicate requests for the same matn must not start two installs or double-count space.
- **Content evicted by the operating system**: On-demand content can be reclaimed by the platform under storage pressure, or cleared by the student through device settings. This is expected behaviour, not a fault — the app must detect it at next use, report the matn as not installed, preserve all personal data, and offer reinstall rather than failing during playback.
- **Connectivity lost mid-install**: The installation must stop with an accurate state and a retry action; the student must never be left with a matn that reports as installed but cannot play.
- **Metered or slow connection**: The size figure shown before the student commits is what lets them decide; installation only ever starts from an explicit student action, never automatically or in the background without one.
- **Starter matn**: The matn bundled in the base app is always available, never appears as an install target, and is never removable. It is counted in the Settings storage total and shown as a non-removable breakdown row, so the reported total matches what the device actually holds, but it is excluded from "remove all downloaded content" and does not by itself defeat the zero state.
- **Matn with no audio at all** (text-only or zero verses): It must show a defined state (nothing to install / size zero) and must not appear as a broken install target or distort the storage total.
- **Content bundled by earlier development builds**: No migration is performed. Audio that shipped inside the app binary before this phase is not carried forward as installed content; after the change, every matn other than the starter reports as not installed. Nothing is re-fetched automatically, and no personal data is affected.
- **Declared size disagrees with what the install actually consumes**: The declared catalog size is an estimate the store's live figure supersedes when known. Once an install completes, the storage section must report the space actually occupied, not the estimate — and the free-space check before install (FR-007) must use the most authoritative figure available at that moment.
- **Very large or very small sizes**: Size figures must render sensibly (rounded, human-readable, correct units) across the full range and in a right-to-left layout.
- **A-B loop / repetition session active on a matn being removed**: The active session must be ended cleanly, with the saved loop range preserved for a later reinstall.

## Requirements *(mandatory)*

### Functional Requirements

#### Availability & installation

- **FR-001**: The system MUST track content availability per matn — never per verse — with exactly three states: not installed, installing (with progress), and installed. A matn's audio is atomic: it is either wholly available or wholly unavailable, and the system MUST NOT define, persist, or display a partial-availability state.
- **FR-002**: The library grid and the matn details screen MUST each display the current availability state of every matn.
- **FR-003**: The system MUST display the space a matn's audio content will occupy, in human-readable units, before the student commits to installing it. Every matn MUST carry a declared size shipped with the bundled catalog so a figure is always available — including on a cold start, offline, or when the store is unreachable — and the install action MUST NOT be blocked waiting on a size lookup. When the delivery platform reports a live size for a matn, that figure MUST supersede the declared size wherever size is shown.
- **FR-004**: Students MUST be able to install an individual matn's audio content from the matn details screen and from the library.
- **FR-005**: While an installation is running, the system MUST show progress toward completion and MUST allow the student to cancel it. An installation MUST continue while the app is backgrounded, and on return the system MUST reflect its true current state — still running with advancing progress, completed, or failed with a retry. The system MUST NOT show completion notifications.
- **FR-006**: Cancelling an installation MUST release all space consumed by that partial installation and return the matn to the not-installed state.
- **FR-007**: The system MUST check available device space before starting an installation and MUST refuse to start — stating required and available space — when there is not enough.
- **FR-008**: Installation MUST be per-matn and independent: multiple متون may be requested, each with its own state and cancellation, and one failing MUST NOT affect the others.
- **FR-009**: A failed or interrupted installation (app terminated, device restart, storage error, lost connectivity) MUST resolve the matn to the not-installed state carrying a retryable failure reason, MUST NOT leave it reported as installed, and MUST NOT leave unreferenced partial content occupying space or counted in storage totals.
- **FR-010**: Availability state MUST persist across app restarts and MUST be derived from what is actually present on the device, not from an optimistic record alone.
- **FR-011**: The system MUST prevent playback of a matn whose audio is not installed and MUST instead present an actionable install prompt; it MUST NOT fail silently, stall, or crash. Because availability is per matn (FR-001), this is a single check per matn rather than a per-verse lookup.
- **FR-012**: Once a matn is installed, all of its playback features (playback, repetition modes, A–B loops, resume) MUST work with no network connection.
- **FR-013**: Content MUST be delivered on demand by the platform's app-store content-delivery mechanism, such that متون the student has not installed are not part of the app's initial download and do not occupy device space until installed.
- **FR-014**: The app's base download MUST include at least one complete, immediately playable starter matn, so that a fresh install is a working app before any content is retrieved and without any network connection.
- **FR-015**: Connectivity MUST be required only to retrieve content. The system MUST check for connectivity before starting an installation and, when unavailable, MUST refuse to start with a clear, non-technical, retryable message and MUST leave no partial state behind.
- **FR-016**: Losing connectivity during an installation MUST leave the matn in an accurate non-installed or in-progress state with a retry action available, and MUST NOT report the matn as installed.

#### Removal & data preservation

- **FR-017**: Students MUST be able to remove an installed matn's audio content, from both the matn details screen and the Settings storage section.
- **FR-018**: Removal MUST require an explicit confirmation that states how much space will be reclaimed.
- **FR-019**: Removal MUST delete only audio content. The matn's title, author, verse text, and structure MUST remain available, so the matn stays browsable, readable, and searchable after removal.
- **FR-020**: Removal MUST preserve all personal data associated with the matn: bookmarks, notes, memorized-verse state, per-matn progress, daily-goal history, and the saved session state (last verse, playback position, repetition settings, A–B loop range).
- **FR-021**: Removing a matn that is currently playing MUST stop playback first and leave the player in a defined stopped state.
- **FR-022**: Removing the matn referenced by "Continue Learning" MUST leave the resume surface coherent — offering reinstall or standing down — and MUST NOT produce a resume entry that fails when tapped.
- **FR-023**: Reinstalling a previously removed matn MUST restore full playback and MUST resume from the preserved session state.

#### Settings & storage reporting

- **FR-024**: The Settings tab MUST present a real Settings screen containing a storage-management section, replacing the "coming soon" placeholder.
- **FR-025**: The storage section MUST show the total space used by all content on the device — including the bundled starter matn — and a per-matn breakdown listing each present matn with its size, ordered largest first.
- **FR-026**: Reported sizes MUST reflect actual space occupied on the device and MUST update after any installation or removal without requiring an app restart.
- **FR-027**: The starter matn MUST appear in the breakdown marked as part of the app and non-removable, MUST NOT offer a remove action, and MUST be excluded from "remove all downloaded content".
- **FR-028**: The storage section MUST show a purposeful zero state when no on-demand content is installed — the starter matn alone does not count as content the student has added.
- **FR-029**: The storage section MUST offer a "remove all downloaded content" action that requires confirmation, removes every installed-on-demand matn, and preserves all personal data per FR-020.
- **FR-030**: The system MUST also show the device's remaining free space alongside the total used, so the student can judge whether an install will fit.
- **FR-031**: All size figures MUST be presented in human-readable, locale-appropriate units and MUST render correctly in the app's right-to-left layout.

#### Forward compatibility

- **FR-032**: The availability, installation, and storage-reporting behaviour MUST be expressed independently of where content comes from, so that switching to a self-hosted remote catalog in a later version changes only the content source and not these student-facing surfaces.

### Key Entities

- **Matn Content Pack**: The complete set of per-verse audio for one matn — the atomic unit a student installs and removes. Attributes: owning matn, declared size (shipped with the bundled catalog, always available offline), live size (reported by the delivery platform when known, superseding the declared size), and actual occupied size once installed.
- **Content Availability State**: The current status of a matn's content pack — exactly one of not installed, installing, or installed — plus progress while installing and a retryable failure reason when the last attempt failed. Held per matn, never per verse. Derived from what is actually on the device and persisted across restarts.
- **Installation Request**: One student-initiated install of a single content pack, with progress, cancellation, and a terminal outcome (completed, cancelled, failed). Multiple requests coexist independently.
- **Storage Usage Summary**: The reporting view behind the Settings storage section — total space used by installed content, a per-matn breakdown ordered by size, and remaining device free space.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A student can go from opening the library to a fully installed, playable matn in no more than three interactions (excluding the install wait itself).
- **SC-002**: The size shown before installation is within 5% of the space the completed installation actually occupies. Declared sizes are measured from the content itself and re-measured whenever a matn's audio changes, so this holds without a live size lookup.
- **SC-003**: The total reported in Settings equals the sum of the per-matn figures, and matches the app's actual content footprint on the device within 5%.
- **SC-004** *(Android)*: Removing a matn reclaims at least 95% of the space its installation consumed, and the Settings figures reflect the change within 2 seconds without an app restart.
- **SC-004a** *(iOS)*: Removing a matn releases its content within 2 seconds and the matn immediately reports as not installed; the space itself is reclaimed by the operating system when it next needs storage. The platform exposes no way to force immediate reclamation, so the confirmation and post-removal messaging state this rather than promising a figure.
- **SC-005**: 100% of bookmarks, notes, memorized marks, and saved session state survive a remove-then-reinstall cycle, and resuming lands on the same verse and position.
- **SC-006**: After any interruption (app killed, device restart, storage error, cancellation), every matn reports an accurate availability state on next launch in 100% of cases, with no orphaned content counted in the total.
- **SC-007**: 100% of attempts to play uninstalled audio result in an actionable install prompt; none result in silence, a stall, an unhandled error, or a crash.
- **SC-008**: A student can determine how much space the app's content occupies within 10 seconds of opening Settings, without scrolling past unrelated preferences.
- **SC-009**: The app's initial store download contains only the starter matn's audio — a student who studies one further matn out of a ten-matn catalog ends up occupying no more space than the starter plus that one matn requires, rather than the whole catalog.
- **SC-010**: A typical matn (roughly 100 verses of recitation) becomes fully playable within 30 seconds of the student confirming installation, on a mid-range device over a typical broadband connection.
- **SC-011**: With the device offline, every already-installed matn — including the starter matn on a fresh install — is fully playable, and 100% of install attempts fail gracefully with a retry rather than hanging or erroring opaquely.

## Assumptions

- **Audio is the only removable content** *(informed default)*: A matn's verse text, title, author, structure, and cover are small and always available; only the per-verse audio — the overwhelming majority of the footprint — is installed and removed. This keeps the library, search (Phase 6), notes, and progress (Phase 7) fully functional for متون whose audio is not on the device.
- **Only the starter matn is audio-installed by default** *(clarified 2026-07-25)*: A fresh install lists the whole catalog with text available for every matn, but audio present only for one bundled starter matn. Everything else is retrieved on demand, per the product spec's "don't bundle every matn by default."
- **No upgrade migration** *(clarified 2026-07-25)*: The app has never been released (no release tags; `main` holds only scaffolding), so there is no installed base whose content must be preserved. Audio bundled by earlier development builds is simply not carried forward as installed content, and nothing is re-fetched on the student's behalf.
- **Content delivery mechanism** *(clarified 2026-07-25)*: Content is delivered by each platform's own app-store on-demand mechanism rather than by app-bundled packs or a self-hosted catalog. The plan phase owns the concrete choice per platform; the student-facing behaviour in this spec does not depend on it (FR-032).
- **Reconciling on-demand delivery with the offline-first constitution** *(assumption to confirm at the plan's Constitution Check)*: The constitution requires that "the app MUST function fully with no network." This phase reads that as *the app functions fully offline for the content the student has*, while *acquiring new content* requires connectivity — the same contract as any app-store install. The bundled starter matn (FR-014) is what keeps a network-less fresh install a genuinely working app rather than an empty shell. If the Constitution Check judges this a real deviation, it belongs in the plan's Complexity Tracking with this rationale.
- **Which matn is the starter** *(informed default)*: The smallest complete matn in the catalog, so the base app download stays light. The specific choice is a content decision, not a functional one.
- **The starter matn's size is measured, not assumed**: Because the starter's audio ships inside the app rather than in a delivered pack, its size cannot be measured from a pack directory at runtime. It carries a measured size recorded with the catalog, and that figure is what it contributes to the Settings total — so the total stays honest against the device's real footprint (SC-003) even though the starter is never installed or removed.
- **Removal semantics differ by platform** *(resolved 2026-07-25)*: One platform deletes on request; the other releases the content and lets the operating system reclaim it when it needs space. The student-facing flow is identical — confirm, then the matn reports as not installed — but the messaging and the timing of the reclaimed figure follow the platform (SC-004 / SC-004a). Moving the weaker platform to a self-hosted downloader to obtain an identical guarantee is future scope, not part of this phase.
- **Wi-Fi-only preference out of scope**: A student-facing "download over Wi-Fi only" setting is not part of this phase. Installs are always explicit, per-matn, and preceded by a stated size, which is the safeguard against unwanted data use.
- **Retrieval is always student-initiated** *(clarified 2026-07-25)*: No content is ever fetched automatically or without the student first seeing its size and confirming. "Student-initiated" governs how a transfer *starts*, not where it runs — once started, an install continues while the app is backgrounded and reports its true state on return, with no completion notifications.
- **Install granularity is the whole matn** *(clarified 2026-07-25)*: The install/remove unit is one matn's complete audio set, tracked and delivered atomically. Per-chapter or per-verse installation is out of scope, and no per-verse presence is tracked — a matn is either fully playable or not presented as playable.
- **Personal data is never destroyed by a storage action**: No install, removal, or "remove all" action touches bookmarks, notes, memorized state, goals, or session state. Storage management is strictly about audio bytes.
- **Concurrency model** *(informed default)*: Multiple install requests are accepted and tracked independently; whether they run simultaneously or in sequence is an implementation choice, provided each is independently cancellable and reports accurate progress.
- **Device-local only**: Installed content, availability state, and storage figures are per-device. Cross-device awareness of what is installed is v2 scope.
- **Permissions**: Content is stored in the app's own managed storage and retrieved through the platform's own delivery mechanism, so no user-facing storage or network permission prompt is involved. The product spec's onboarding explanation of the offline/download model is owned by Phase 9, not this phase.
- **Streaming is out of scope**: A matn is either installed and played locally, or not playable. Playing content without installing it first (streaming) is not part of this phase.
- **Prerequisites**: Phase 1 supplies the per-verse audio asset model this phase installs and removes; Phase 2 supplies the library and details surfaces the availability state attaches to; Phase 3 supplies the playback that must be gated on availability; Phase 5 supplies the saved session state that removal must preserve; Phase 10 supplies the design tokens and the bottom-navigation Settings tab this phase fills in.
- **Design source**: The library card's existing status affordance and the Settings screen must be taken from the Stitch design set registered in `docs/DESIGN-SOURCE.md`. That set has no captured Settings screen and no captured install/remove states, so those surfaces will need to be composed from the existing token set — a gap to record in the design notes for this phase.
