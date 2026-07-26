# Matn — Product Vision & Requirements (v3.0)

Matn is an educational application that helps students read, listen to, and memorize Islamic texts (المتون). The app provides a calm, beautiful, and distraction-free learning experience focused on repetition and active memorization. The application must feel like an active memorization companion, not a passive audio player.

## Purpose & Scope

Students studying Islamic متون need to repeatedly listen to their teacher's recitation while reading the text. The app must support both simple and structured layouts:

- **Simple:** A flat list of sequential verses.
- **Structured:** Verses organized hierarchically into chapters or sections (فصول), with table-of-contents navigation (see below).

Each individual verse object consists of:
- Verse ID / Number (stable UUID + sequential display number)
- Arabic text string (with support for diacritics/تَشْكِيل)
- Audio reference (see Audio Architecture below)

---

# Audio Architecture

**Audio is split into individual micro-files per verse** (e.g., `matn_01_verse_005.mp3`), matching how the teacher's recordings are produced and delivered. Each verse maps to its own dedicated audio asset — there is no shared/continuous file to seek within.

- **Benefit:** simplest highlighting/looping logic — file boundary = verse boundary, and no dependency on precise manual timestamp tagging.
- **Engineering requirement:** gapless playback between separate files must be handled deliberately on both platforms — pre-buffer the next verse's file ahead of the current one finishing, using AVQueuePlayer (iOS) and ExoPlayer's gapless/ConcatenatingMediaSource config (Android). Without this, audible clicks or gaps will appear between verses, which undermines the "natural recitation flow" goal.
- **Production dependency:** this places a standing requirement on content intake — every verse, for every matn, needs a correctly trimmed individual audio file (consistent lead-in/lead-out silence, consistent loudness) before it can be added to the app. Worth formalizing as a lightweight content-prep checklist for whoever processes the teacher's recordings.

### State Memory & Cache
The app must persist locally, updated on every verse transition or configuration change:
- `last_opened_matn_id`
- `last_listened_verse_id`
- Playback position (millisecond accuracy)
- Current repetition settings (verse and matn counters)
- If stopped mid A–B loop: the loop range itself, so "Continue Learning" resumes the loop, not just a single verse

---

# Playback & Repetition Logic

Two input fields customize the repetition matrix before or during playback:

1. **Verse Repeat Counter ($V_r$):** How many times an individual verse loops before advancing.
2. **Whole Matn Repeat Counter ($M_r$):** How many times the entire selected range loops once the end is reached.

Both counters accept a finite integer or **∞ (unlimited)**, represented explicitly in the data model (e.g., `0` or `null` reserved to mean "unlimited") rather than left implicit.

### Playback Modes

- **Normal Continuous Mode:** $V_r = 1$, $M_r = 1$ — plays every verse once, start to finish, then stops.
- **Memorization Mode:** Plays Verse $X$ for $V_r$ times, advances to $X+1$, repeats $V_r$ times, and so on. Once the range completes, if $M_r > 1$ (or unlimited), loops back to the start of the range.
- **A–B Loop Mode:** Student selects start and end verses. Playback restricts to and loops within this range, ignoring matn-level boundaries, until manually stopped.

---

# Home Screen (Library & Dashboard)

- **Continue Learning:** One-tap resume populated from cached state, including in-progress A–B loops.
- **Daily Memorization Progress:** Visual ring/metric toward today's goal.
- **Collection of متون:** Grid or list, each card showing cover image, title, author, verse count, estimated listening duration, and per-matn download status (see Storage & Downloads).

---

# Matn Details & Reading Screen

- **Header:** Cover image, title, author, description, total verses, total duration, progress bar.
- **Table of Contents:** For structured متون, a chapter/section jump list so students aren't stuck scrolling a long flat list to reach a section.
- **Verse list:** Verse number, Arabic text, play icon, individual duration.

### Reading Experience & Auto-Scroll
- Active verse receives clear visual highlighting during playback.
- The list auto-scrolls to keep the active verse comfortably in view (centered or near-top, not jarring).
- Verse-to-verse transitions must be gapless regardless of which audio architecture is chosen.

---

# Audio Player Controls

Persistent control bar / bottom sheet providing:
- Play / Pause / Resume / Stop
- Next / Previous verse
- Scrub bar within the active verse
- Playback speed (0.5x–1.5x)
- Graceful handling of **audio interruptions** — phone calls, other apps requesting audio focus, Bluetooth device changes — pausing and allowing manual or auto-resume rather than silently dying.

---

# Features & Utilities

### Search
Local, indexed, diacritic-insensitive matching on verse text, verse number, and matn title. Selecting a result navigates directly to that verse.

**Scope follows what's on the device.** Verse text arrives with a download, not with the catalog overview, so full-text search covers **downloaded** متون; undownloaded ones are reachable by **title** only, and selecting one leads to its details page and install prompt rather than a verse. Search itself stays fully offline.

### Bookmarks & Notes
- **Bookmarks:** Flag verses for quick access via a global list/drawer, persisted across sessions.
- **Notes:** Local text attached to a specific verse ID (explanations, grammar points, mnemonics).

### Memorization Progress & Daily Goals — *(revised from v2)*

Progress must reflect **actual memorization signal, not raw listen count.** Passive play counts alone are gameable (looping one verse 50 times shouldn't show "progress") and don't measure what the app claims to help with.

Use one or both of:
- **Explicit self-report:** a "Mark as Memorized" action per verse or section, giving students direct control and an honest record.
- **Derived heuristic (optional, additive):** a verse counts toward progress only after being practiced across multiple distinct sessions with time gaps between them (a light spaced-repetition signal), not simply replayed many times in one sitting.

Daily goals remain as in v2 (e.g., "practice 10 verses" or "listen 20 minutes"), displayed via a completion ring — but the underlying "memorized" metric must be decoupled from raw playback frequency.

---

# Navigation & App Shell

A persistent bottom navigation bar with four tabs is the app's primary navigation surface,
present on every top-level screen:

- **Library** — Home Screen (see above).
- **Goals** — Memorization Progress & Daily Goals.
- **Notes** — Bookmarks & Notes.
- **Settings** — App preferences, storage management, onboarding-related permissions.

Tabs for features not yet built are shown but route to a simple "coming soon" placeholder rather
than being hidden — the shell itself is stable chrome shared by every screen, independent of which
feature phases have landed.

---

# User Experience & UI Guidelines

- **Typography:** Elegant, legible classical Arabic typeface (e.g., Amiri, Uthman Taha) with adjustable font-size slider.
- **RTL:** Fully native right-to-left layout.
- **Wake Lock:** Screen stays awake during active playback (hands-free recitation/standing use).
- **Themes & Responsiveness:** Minimalist UI, full dark mode, tablet-friendly adaptive layouts, accessibility-contrast compliant.
- **Onboarding:** First-launch flow requesting only the permissions actually needed (notifications for daily goal reminders, storage), with a brief explanation of offline/download model.

---

# Content Catalog, Storage & Downloads

**Nothing is bundled in the app binary.** All content originates from teachers publishing through the authoring tool; students reach it over the network. The model is an online catalog plus selective download:

- The catalog lists every published matn as an **overview** — cover image, title, author, description, verse count, and download size. Overviews sync to the device so the library browses offline once seen; the verse text and audio arrive only on download.
- Each matn is downloadable/removable individually, with visible file size before download.
- Show total storage used by downloaded content in settings.
- Once downloaded, a matn is **fully usable with no network** — reading, playback, repetition, progress, bookmarks, notes, resume. This is the offline guarantee, and it is unconditional for downloaded content.
- Because nothing ships in the binary, a **first launch with no connectivity is an empty library** showing a connect-to-browse state. Deliberate trade: content is only ever what a teacher published and can revise.
- This keeps the app lightweight for students who only study a subset of available متون, and lets the catalog grow without growing the binary.

---

# Future-Proof Engineering (V2 Scalability)

The online catalog and download manager, previously listed here as a v2 item, are **v1** — see *Content Catalog, Storage & Downloads* above. Data models, relations, and ID schemes (UUIDs) must still be structured to support what remains deferred:
- Remote **student** accounts, cloud backup, cross-device sync. (v1 has no student accounts: catalog reads are public and only the teacher authenticates, to publish.)
- Multi-teacher permissions and a content-ownership model. v1 is scoped to a single teacher per institution.
- Multi-reciter support — mapping alternate audio to the same verse IDs.
- Shared community notes, quizzes, and teacher evaluation analytics.

---

# Overall Vision

Matn is not simply an audio player. It is a modern memorization companion that helps students read, listen, repeat, memorize, and review their متون with the least friction and the greatest possible focus — and whose own progress metrics honestly reflect memorization, not just time spent listening.
