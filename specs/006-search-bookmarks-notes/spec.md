# Feature Specification: Search, Bookmarks & Notes

**Feature Branch**: `006-search-bookmarks-notes`

**Created**: 2026-07-24

**Status**: Draft

**Input**: User description: "read @docs/ROADMAP.md and create a specification for the Phase 6 — Search, Bookmarks & Notes"

## Clarifications

### Session 2026-07-24

- Q: What is the Arabic matching normalization scope for search? → A: Diacritics + common letter-form folding — strip تشكيل, and treat أ/إ/آ/ٱ ≈ ا, ى ≈ ي, ة ≈ ه as equivalent.
- Q: Where does search live in the app? → A: An entry point on the Library (Home) tab's top bar, opening a dedicated full-screen search experience.
- Q: Are chapter/section titles (فصول) of structured متون part of the search corpus? → A: Yes — chapter/section titles are matchable; selecting a chapter result navigates to the start of that chapter in the reading screen.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find a verse by searching (Priority: P1)

A student remembers a fragment of a verse (or a verse number, or part of a matn's title) and wants to jump straight to it instead of scrolling through long verse lists. They open search, type a few Arabic words — without worrying about typing the diacritics (تشكيل) exactly as they appear in the text — and see matching verses and متون as they type. Tapping a result takes them directly to that verse in its reading screen.

**Why this priority**: Search is the highest-leverage utility in this phase — it serves every student on every matn from day one, requires no prior user action (unlike bookmarks/notes, which only pay off after the user creates them), and directly removes the "stuck scrolling a long flat list" friction the product spec calls out.

**Independent Test**: Can be fully tested by installing the app with at least two متون present, searching for a word fragment, a verse number, and a matn title, and confirming each result navigates to the correct verse or matn — with no bookmark or note features needed.

**Acceptance Scenarios**:

1. **Given** the library contains متون with verse text, **When** the student types an Arabic word without diacritics that appears (fully vocalized) in a verse, **Then** that verse appears in the results with its matn name and verse number visible.
2. **Given** the student types a query with diacritics, **When** the stored verse text has different or no diacritics on those letters, **Then** matching still succeeds (matching ignores diacritics on both sides).
3. **Given** results are displayed, **When** the student taps a verse result, **Then** the app opens that verse's matn reading screen positioned at that verse.
4. **Given** results are displayed, **When** the student taps a matn-title result, **Then** the app opens that matn's details screen.
5. **Given** a structured matn with chapters (فصول), **When** the student types part of a chapter title, **Then** that chapter appears in the results, and tapping it opens the reading screen at the chapter's first verse.
6. **Given** the student types a verse number, **When** any matn contains a verse with that number, **Then** those verses appear in the results identified by matn and number.
7. **Given** a query with no matches, **When** results are computed, **Then** a friendly empty state explains that nothing matched, and the app does not appear frozen or broken.
8. **Given** the device has no network connectivity, **When** the student searches, **Then** results appear normally (search is fully local).

---

### User Story 2 - Bookmark verses for quick return (Priority: P2)

While reading or listening, a student flags specific verses they want to return to — tricky passages, the start of tomorrow's assignment, a favorite line. Each verse can be bookmarked or un-bookmarked in place with one tap, and all bookmarks are gathered in one global list (on the Notes tab) so the student can jump back to any flagged verse from anywhere in the app, across app restarts.

**Why this priority**: Bookmarks are the lighter-weight of the two annotation features — a single tap creates durable value — and they give the Notes tab its first real content. They depend on nothing from search.

**Independent Test**: Can be fully tested by bookmarking verses in two different متون, restarting the app, opening the global bookmarks list, and navigating from a bookmark back to its verse — with search and notes untouched.

**Acceptance Scenarios**:

1. **Given** a verse is displayed in the reading screen, **When** the student activates its bookmark action, **Then** the verse is marked as bookmarked and shows a visible bookmarked indicator.
2. **Given** a bookmarked verse, **When** the student activates the bookmark action again, **Then** the bookmark is removed and the indicator disappears.
3. **Given** bookmarks exist in multiple متون, **When** the student opens the global bookmarks list, **Then** every bookmarked verse is listed with enough context to recognize it (matn, verse number, verse text).
4. **Given** the global bookmarks list, **When** the student taps an entry, **Then** the app opens that verse in its matn's reading screen.
5. **Given** bookmarks exist, **When** the app is fully closed and reopened, **Then** all bookmarks are still present.
6. **Given** no bookmarks exist yet, **When** the student opens the bookmarks list, **Then** an empty state invites them to bookmark verses while reading.

---

### User Story 3 - Attach personal notes to verses (Priority: P3)

A student wants to record an explanation from their teacher, a grammar point, or a mnemonic against a specific verse. From the verse, they open a note editor, write free text, and save. The verse then shows a note indicator, the note can be reopened, edited, or deleted at any time, and all notes are browsable in the global list on the Notes tab.

**Why this priority**: Notes deliver the deepest personalization but require the most user effort and UI surface (an editor, editing, deletion). They build on the same global-list surface as bookmarks, so landing bookmarks first makes notes cheaper.

**Independent Test**: Can be fully tested by creating a note on a verse, restarting the app, confirming the note persists and the verse shows its indicator, editing the note, browsing it from the global list, and deleting it — independently of search and bookmarks.

**Acceptance Scenarios**:

1. **Given** a verse without a note, **When** the student opens the note action and saves non-empty text, **Then** the note is attached to that verse and the verse displays a note indicator.
2. **Given** a verse with a note, **When** the student reopens the note, **Then** the saved text is shown and can be edited; saving replaces the previous text.
3. **Given** a verse with a note, **When** the student deletes the note, **Then** the note and the verse's note indicator are removed.
4. **Given** notes exist across multiple متون, **When** the student opens the global notes list, **Then** each note is listed with its verse context (matn, verse number) and a preview of the note text.
5. **Given** the global notes list, **When** the student taps an entry, **Then** the app navigates to that verse.
6. **Given** notes exist, **When** the app is fully closed and reopened, **Then** all notes are intact.
7. **Given** the student opens the note editor and saves empty text (or cancels), **When** the verse previously had no note, **Then** no note is created.

---

### Edge Cases

- **Diacritic and letter-form differences**: A query containing diacritics must match verse text with different or absent diacritics, and vice versa; likewise a query typed with bare letter forms (e.g., `الاسلام`) must match text using hamza-carrier or variant forms (e.g., `الإِسْلَام`) — matching normalizes both the query and the indexed text per FR-002.
- **Digit forms**: Students may type verse numbers using Western digits (5) or Arabic-Indic digits (٥); both must match the same verse number.
- **Empty or whitespace-only query**: Shows the idle search state (no results, no error), never "no results found."
- **Very short queries**: Single-character queries may match a large share of the corpus; results stay responsive and are presented in a sensible order (see FR-006) rather than freezing the interface.
- **Same verse bookmarked and noted**: A verse can carry both a bookmark and a note simultaneously; indicators coexist without conflict.
- **Long note text**: Notes hold at least several paragraphs of text; the list view truncates previews without corrupting or losing the full text.
- **Removed matn audio** (interaction with future Storage phase): Bookmarks and notes attach to verse identity, not audio files — removing a matn's downloaded audio must not destroy its bookmarks or notes.
- **Rapid toggling**: Repeatedly tapping the bookmark action in quick succession must settle on a consistent final state, never a duplicate or phantom bookmark.
- **Navigation during playback**: Jumping to a verse from search, a bookmark, or a note while audio is playing must not crash or corrupt playback state; the reading screen opens on the target verse in a coherent state.

## Requirements *(mandatory)*

### Functional Requirements

#### Search

- **FR-001**: The system MUST let students search, from a single search surface, across all متون present in the library — matching verse text, verse display numbers, matn titles, and chapter/section titles (فصول) of structured متون. The search surface is a dedicated full-screen experience opened from an entry point in the Library (Home) tab's top bar.
- **FR-002**: Matching MUST be diacritic-insensitive and letter-form-tolerant: differences in Arabic diacritical marks (تشكيل) between the query and the stored text MUST NOT prevent a match, and the common letter-form variants أ/إ/آ/ٱ ≈ ا, ى ≈ ي, and ة ≈ ه MUST be treated as equivalent on both the query and the stored text.
- **FR-003**: Verse-number matching MUST accept both Western (0–9) and Arabic-Indic (٠–٩) digits as equivalent.
- **FR-004**: Search MUST operate entirely on locally stored content and function fully without network connectivity.
- **FR-005**: Each search result MUST display enough context to identify it before navigating: for verse matches, the matn title, verse number, and matched verse text; for matn-title matches, the matn title; for chapter-title matches, the chapter title and its matn title.
- **FR-006**: Results MUST be presented in a deterministic, comprehensible order (e.g., grouped by matn in library order, verses in reading order within each matn), so the same query always yields the same arrangement.
- **FR-007**: Selecting a verse result MUST navigate directly to that verse within its matn's reading screen; selecting a matn-title result MUST navigate to that matn's details screen; selecting a chapter-title result MUST navigate to the start of that chapter (its first verse) in the reading screen.
- **FR-008**: Search MUST update results as the student refines the query, and an empty or whitespace-only query MUST present an idle state rather than an error or "no results."
- **FR-009**: A query with no matches MUST present a clear empty state distinguishing "no matches" from "not yet searched."

#### Bookmarks

- **FR-010**: Students MUST be able to bookmark and un-bookmark any verse directly from where the verse is displayed, with a single action.
- **FR-011**: A bookmarked verse MUST show a persistent visible indicator wherever the verse is rendered in the reading screen.
- **FR-012**: The system MUST provide a single global bookmarks list, reachable from the app's Notes tab, aggregating bookmarked verses across all متون with recognizable context (matn title, verse number, verse text).
- **FR-013**: Selecting a bookmark MUST navigate to that verse in its matn's reading screen.
- **FR-014**: Bookmarks MUST persist across app restarts and survive removal of a matn's downloaded audio.

#### Notes

- **FR-015**: Students MUST be able to attach one personal free-text note to any verse, and to view, edit, and delete that note from the verse.
- **FR-016**: A verse with a note MUST show a visible note indicator in the reading screen, distinct from the bookmark indicator.
- **FR-017**: The system MUST provide a global notes list, reachable from the app's Notes tab, showing each note's verse context (matn title, verse number) and a preview of its text; selecting an entry navigates to that verse.
- **FR-018**: Notes MUST persist across app restarts, remain local to the device, and survive removal of a matn's downloaded audio.
- **FR-019**: Saving an empty note MUST NOT create a note; for an existing note, deletion MUST be an explicit action rather than an accidental side effect of clearing text.

#### Shell integration

- **FR-020**: The Notes tab in the app's bottom navigation MUST replace its "coming soon" placeholder with the bookmarks and notes surface delivered by this feature, presenting both collections in one coherent tab.
- **FR-021**: Bookmark and note indicators and empty states MUST render correctly in RTL layout and respect the app's established visual language.

### Key Entities

- **Bookmark**: A student's flag on a single verse. Identified by the verse it points to (stable verse identity, independent of display order or audio assets); carries creation time for ordering. At most one bookmark per verse.
- **Note**: A student's private free-text annotation attached to a single verse (stable verse identity). Carries its text and last-modified time. At most one note per verse; editable and deletable.
- **Search Query / Result** *(transient, not persisted)*: The student's current query text and the derived matches — verse matches (verse + its matn context), matn-title matches, and chapter-title matches (chapter + its matn context). Exists only while searching.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a library of at least 1,000 verses across multiple متون, search results appear within 1 second of the student finishing typing, with no visible interface freeze.
- **SC-002**: A student can go from opening search to reading the target verse in 3 interactions or fewer (enter query, tap result — plus at most one step to reach search).
- **SC-003**: Queries typed without diacritics — or with the folded letter-form variants of FR-002 (e.g., ا for أ/إ/آ, ي for ى, ه for ة) — return exactly the same verse matches as the fully vocalized, exact-form query, in 100% of cases.
- **SC-004**: 100% of bookmarks and notes created are still present and correct after a full app restart.
- **SC-005**: Bookmarking or un-bookmarking a verse reflects visually within 1 second of the action.
- **SC-006**: A first-time user can bookmark a verse and create a note without instruction (discoverable from the verse's own controls) — verified by a task-completion walkthrough with no more than one wrong tap.
- **SC-007**: The Notes tab shows real bookmark/note content (or purposeful empty states) — the "coming soon" placeholder no longer appears anywhere for this tab.

## Assumptions

- **Matching scope** *(clarified 2026-07-24)*: Matching ignores Arabic diacritical marks (fatha, damma, kasra, sukun, shadda, tanwin, and superscript alef) and folds the common letter-form variants أ/إ/آ/ٱ → ا, ى → ي, and ة → ه on both query and text. More aggressive unification (e.g., folding ؤ/ئ to bare forms, tatweel handling beyond removal) is out of scope for this phase; substring matching on the normalized text is sufficient.
- **Note content**: Notes are plain free text — no formatting, attachments, tagging, or categorization in this phase.
- **One note per verse**: The product spec describes "local text attached to a specific verse ID," so this phase supports at most one note per verse; multiple notes per verse are out of scope.
- **Search corpus** *(clarified 2026-07-24)*: Search covers verse text, verse numbers, matn titles, and chapter/section titles of structured متون — it does not search inside the student's notes in this phase.
- **Notes tab layout**: Bookmarks and notes share the existing Notes tab (per the app shell established in Phase 10 and the registered "Bookmarks & Notes" design); this feature fills that tab rather than adding new top-level navigation.
- **All text is local**: Every matn's text content is available on-device regardless of audio download state (per the offline-first model), so search, bookmarks, and notes never depend on the network or on audio being downloaded.
- **No sync**: Bookmarks and notes are device-local in this phase; account sync and shared/community notes remain future (v2) scope per the product spec.
- **Prerequisites**: Phase 2 (reading experience) is in place — this feature attaches its actions and indicators to the existing verse rendering and navigates using existing reading/details screens. The Phase 10 navigation shell provides the Notes tab this feature fills.
