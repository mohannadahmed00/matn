# Design Notes: Teacher Authoring Tool — Foundation & Upload (fetched via Stitch MCP, 2026-07-27)

## T033a — Upload Matn (Per-Verse) (`4f1bee3d7518487b986c7c63cb3c07ff`) + Search Matn, producer chrome (`fa8b63b036c94510ba1be2d90e7a3417`)

Both screens were fetched via the `stitch` MCP server's `get_screen` tool against project
`5201142409061412050` (`docs/DESIGN-SOURCE.md`). *Upload Matn (Per-Verse)*'s retrieved HTML matches
the local export cached at `stitch-designs/11-Upload-Per-Verse.html` byte-for-byte in structure
(same title, same regions, same labels below). *Search Matn* was fetched for its producer-portal
chrome only, per `docs/DESIGN-SOURCE.md` open issue #7 — its own search-results content is Phase 6
material and out of scope here.

### Layout regions (Upload Matn — Per-Verse)

- **Top header bar**: hamburger icon, "Matn" wordmark (Amiri display font), a library-search field,
  and an identity block (display name "Ustadh Ahmed" over "Teacher Portal" label, avatar initials).
- **Sidebar nav rail** (desktop, `md:flex`, right-anchored under RTL): four destinations —
  Dashboard, **Upload Matn** (highlighted/active state), Library Management, and (below a divider)
  System Settings. Below the nav, a **storage-usage card**: "STORAGE USAGE" label, a filled progress
  bar, and "4.2 GB of 10 GB used" text.
- **Main canvas**, two sections:
  1. **Metadata section** (bento grid, 8/4 column split): left card holds "General Information"
     with Matn Title, Author / Scholar + **Category** (a two-column row), and Short Description;
     right column holds a Cover Art picker (800×1200 recommended) and a "Pro-Tip" callout card.
  2. **Verse List & Audio Sync section**: a header row with the section title and two actions
     ("Bulk Import (CSV)" and "Add Verse"), then a divided list of verse rows — each row: a drag
     handle + numbered circle, an auto-expanding Arabic RTL textarea, an audio slot (empty/upload
     prompt, loaded/waveform-with-scrubber, or an implicit "generating" state the capture does not
     show), and a delete button. The list footer has a centered "Add Next Verse" affordance.
  3. **Actions footer**: "Save as Draft" (text button) and "Publish Matn" (filled, primary).
- A floating help affordance (bottom-left "Sakinah Mood" helper) sits outside the main canvas; no
  behavior is specified for it in the capture, and this phase does not implement it.

### Layout regions (Search Matn — producer chrome only)

- Confirms the same header (Matn wordmark, dual search fields) and the same left nav rail
  (Dashboard / Library Management / Upload Matn / System Settings) and identity block ("Ustadh
  Ahmed" / "Teacher Portal") as the Upload screen — this is the one portal shell shared across every
  producer screen, which is what `PortalShell` (T046) implements once. The screen's own
  search-results content and the separate bottom student nav bar it also renders belong to Phase 6
  and are not part of this phase's chrome.

### Two recorded deviations (Principle VIII)

1. **Category is not implemented.** The captured form has a Category selector (Tajweed / Aqeedah /
   Fiqh / Hadith). `SeedMatn` has no category field (`data-model.md` §11), and adding one would
   diverge the catalog schema from what `ContentSeedLoader` can ingest. `EditorContent`'s metadata
   form omits this field entirely — not a placeholder, not a disabled control.
2. **The Arabic, mirrored form of the portal chrome is original work.** The capture is Arabic/RTL
   already (the HTML's own `dir="rtl" lang="ar"` on the Upload screen) with English placeholder
   copy inside it (e.g. "General Information", "Matn Title"). Since FR-006a/b require the *tool
   itself* to fully translate and mirror for an English-interface teacher, the LTR/English rendering
   of this same chrome (nav rail on the left, header search/identity mirrored, `TeacherStrings`
   English labels) is derived by mirroring the captured layout, not by redesigning it, and is
   original work this phase produces.

### What this phase does NOT build from these captures (Phase 12 / out of scope)

- The per-verse **audio** column (upload prompt / waveform+scrubber / delete-audio states) —
  Phase 12's "Upload Timestamp Map" (`stitch-designs/12-Upload-Timestamp-Map.html`) owns it. Phase
  11's `VerseRow` (T064) renders only the drag handle, numbered index, and Arabic text field.
- "Bulk Import (CSV)": FR-023a is plain UTF-8 text, one verse per line — no delimiter, no CSV, no
  quoting. `ImportPreviewDialog`'s (T092) trigger reuses the same toolbar slot but is labelled
  "Bulk Import" (`TeacherStrings.bulkImport`), not CSV (`docs/ROADMAP.md:122`'s "bulk CSV import" is
  fixed to "bulk text import" in T095, same PR).
- The floating help affordance — not specified, not implemented.

### What implementation MUST NOT do

- Do **not** add a Category field anywhere in `MatnDraft`, the Firestore schema, or the editor UI —
  it is a deliberate omission (deviation 1 above), not an oversight to "complete".
- Do **not** invent a new portal-chrome layout for `PortalShell` — reuse the header/nav/identity/
  storage-usage regions common to both fetched screens exactly as captured.
- Do **not** build any audio-upload control in `:teacherApp` in this phase (FR-045) — `VerseRow`
  stops at the text field.
