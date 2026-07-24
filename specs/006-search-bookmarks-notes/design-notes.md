# Design Notes: Search, Bookmarks & Notes (fetched via Stitch MCP, 2026-07-24)

## T003 — Search Matn (`fa8b63b036c94510ba1be2d90e7a3417`)

**Top bar**: title "Matn" + menu icon. Search field sits prominently directly below the header,
placeholder "search" (RTL-mirrored for Arabic).

**Scope confirmation (Open question 3)**: the fetched HTML queries across the *entire* text
library, not a single matn — matches spec FR-001 (library-wide search). **No conflict** — the
design and the spec agree; nothing to override.

**Result count header**: "تم العثور على N نتائج" (N results found) with filter chips "الكل"
(All) / "المتون" (Texts). We do not implement extra type-filter chips — out of scope for this
phase's contracts (search-contract.md defines one unified result list); noted as a gap, not
followed.

**Result-row anatomy** (adapted to our 3 `SearchResult` kinds, since the fetched design's cards
carry richer bibliographic metadata — author/category tags — that has no equivalent in our
domain model):
- **MatnMatch**: bold matn title (right-aligned Arabic), matching the design's title-first
  hierarchy.
- **ChapterMatch**: matn title (secondary/smaller) + chapter title (primary), mirroring the
  design's "title → attribution" order.
- **VerseMatch**: matn title + verse number (secondary line) + verse excerpt text (primary,
  larger, Amiri body-ar per the token registry).

**Idle / empty state**: fetched design shows "لم تجد ما تبحث عنه؟" (didn't find what you're
looking for?) with an upload-new-text CTA — that CTA is teacher/producer-side and out of scope
for the student app. We keep the *distinct-empty-states* idea (idle prompt vs. no-results
message) but use student-appropriate copy: an idle prompt ("ابحث في مكتبتك") before typing, and a
plain "لا نتائج" no-results message after a non-blank miss — no upload CTA.

**Design note**: the fetched HTML also renders teacher/admin-portal navigation chrome (Dashboard,
Library Management, Upload Matn, System Settings, a named "Ustadh Ahmed" identity). This is
producer-tooling bleed-through from a shared Stitch shell, not part of the student search screen
we're building (constitution's app is the *student* client). Ignored.

## T004 — Bookmarks & Notes (`bc99ab7f8110479086326271d0aa0310`) + reinspection of Reading &
Playback (Updated) (`ac354abd54c246ed841f63b31a19fbf2`)

**Open question 1 — segmentation**: fetched design uses **stacked sections**, not a tabbed/
segmented control (confirms research.md D6). `NotesTabScreen` renders two vertically stacked
sections in one scroll.

**Row anatomy** (design shows combined bookmark+note cards — source/chapter header, verse
excerpt, then an inline "Personal Note" block with preview + edit icon, "Add note" affordance
where absent). Our data contract (annotations-contract.md) keeps bookmarks and notes as two
**independent** entities/lists with their own empty states (T035/T047) — so we do not merge them
into one row type. Instead we take the design's *visual language* per section:
- Shared verse-context header block (matn title + chapter/verse context) — reused between
  `BookmarkRow` and `NoteRow` (Constitution VIII second-use extraction, per T036).
- `NoteRow` additionally shows a truncated (~2-line) note-text preview, echoing the design's
  note-preview treatment.
- `BookmarkRow` is the context block alone (no free-text payload).

**Empty states**: design implies an "add" affordance when a section is empty; SC-007 requires
*purposeful* empty states — we use short, student-facing copy per section ("لا توجد إشارات
مرجعية بعد" / "لا توجد ملاحظات بعد") rather than a CTA button (no cross-navigation action needed
— annotations are created from the reading screen, not from this tab).

**Top bar**: header title "الإشارات المرجعية والملاحظات" (Bookmarks & Notes); design shows a
search/filter affordance in the top bar — out of scope for this phase (no search-within-notes
requirement in the spec); ignored.

### Open question 2 — verse-card bookmark/note affordance placement

Re-inspected `ac354abd…` (Reading & Playback, Updated): the active verse card's **top-right
action row** shows a bookmark icon + a `more_vert` overflow menu. **No distinct note affordance is
visible** in the fetched screen. Per ui-contract.md's fallback rule, the note action joins the
same active-verse-card action row as the bookmark toggle (default placement), positioned next to
the bookmark glyph, with a visually distinct icon (FR-016) — recorded as a design gap below.

**Gap recorded for `docs/DESIGN-SOURCE.md` "Open issues"** (see T052): the *Reading & Playback
(Updated)* design has no dedicated note-taking affordance on the verse card; this phase adds one
to the existing action row (bookmark icon + new note icon) rather than inventing a new screen
region, consistent with D7.
