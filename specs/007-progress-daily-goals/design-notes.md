# Design Notes: Progress & Daily Goals (fetched via Stitch MCP, 2026-07-24)

## T004 — Progress & Goals (`f059cccd2f634bc9ba2cf4d620e5df80`) + ring region of Home / Library
(`618643f891144557b5a4ddf4bbad0c03`)

Both screens were fetched successfully via the `stitch` MCP server (`get_screen`); the underlying
generated HTML was retrieved and summarized below (WebFetch condensed rather than dumping raw
markup — the structural facts below are what implementation should rely on).

### Ring anatomy (`DailyGoalRing`)

- A circular, stroke-based determinate progress ring (SVG/Canvas arc), not a Material default
  `CircularProgressIndicator` skin — render it with a `Canvas` arc so the stroke width and track
  color are fully controllable via tokens, matching the fetched look.
- The percentage/fraction text sits **centered inside** the ring (e.g. "80%", "70%") — this is the
  dominant numeral on the screen, so give it a large type scale (headline-class, not body).
  Do not additionally cram "practiced/goal" as a second number inside the ring; the fetched design
  keeps the ring's interior to the single percentage and pushes the "X out of Y verses" / "تم حفظ 7
  أبيات من 10" phrasing to a **caption line directly beneath the ring**, not inside it.
- On Home, the ring sits in the top header region, below the Arabic greeting line
  ("السلام عليكم، …") and above the "Continue Learning" CTA — i.e. it is a headline element of the
  top bar/greeting block, not buried in a scrollable list.
- On the Goals tab, the ring is the first/topmost content element, with the "X out of Y" caption
  immediately below it, matching the Home treatment (component reuse, D8).

### Goals tab section order (top → bottom)

1. Greeting/header (title region — "Goals"/الأهداف equivalent for this tab; Home reuses its own
   greeting instead)
2. Ring + caption ("N out of goal verses today")
3. Daily-target editor: a **stepper control** — "Verses per day" label, a numeric current-value
   display (e.g. "5"), and explicit increment/decrement affordances (not a free-text field, not a
   drag slider) — mirror this with the existing counter/stepper idiom already used in
   `RepetitionSetupSheet.kt` rather than inventing a new control.
4. Per-matn progress list (one row per matn in the library)
5. (Home screen only, not the Goals tab) a streak/day tracker row (out of scope for this phase's
   FRs — spec does not request a streak feature; noted as a gap below, not implemented)

### Per-matn row anatomy (`MatnProgressBar` usage)

Each row: leading icon (book/history/library-style glyph — decorative, not required by our FRs),
title (matn name, right-aligned Arabic), a `"memorized / total"` fraction line (e.g. "24 / 61"),
a percentage readout right-aligned to the fraction (e.g. "39%"), and a horizontal progress bar
beneath the text line. We reuse this row shape for both the Goals dashboard and (already-existing)
details header, per `MatnProgressBar`'s second-use extraction (T019).

### Goal editor control

Stepper with a visible current value and separate increment/decrement affordances (fetched design
shows something like a "-"/"+" pair around a numeral, e.g. "5", with the label "Verses per day" /
"Daily Target"). Implement as whole-number steps of 1, floor at 1 (matches FR-009/D5's ≥1 bound).
Do not use a slider or freeform text input — the fetched control is explicitly a stepper.

### Zero / empty state

The fetched screens did not surface an explicit zero-state (both were captured with populated
data), so no direct visual reference exists for "nothing memorized yet". Per SC-007 the empty
state must still be purposeful, not the generic "coming soon" placeholder — build it from the same
tokens as the populated state: ring at 0% with the same caption pattern ("0 out of 10 verses
today"), and a short inviting message in place of the per-matn list (e.g. "ابدأ الحفظ لتظهر هنا
أهدافك" / start memorizing to see your progress here) rather than an empty list with no
explanation. This is a **gap in the fetched design**, filled with Phase 10 tokens per the task's
fallback instruction.

### RTL

Arabic text throughout both screens ("السلام عليكم، أحمد", "تم حفظ 7 أبيات من 10", "متن
الأجرومية") confirms `dir="rtl"` content flow; numeric fractions/percentages ("24 / 61", "80%")
stay LTR-ordered digits inside the RTL flow, matching how the rest of the app already handles
mixed Arabic/numeral text (existing verse-number treatment). The stepper's "+"/"-" affordances
must **not** be mirrored into the wrong logical order — increment must still mean "more" — this is
called out explicitly in T047a's RTL device pass.

### Library-card progress (Home grid)

The fetched Home/Library screen's grid cards show title, author attribution, a verse-count
("240 بيت") and a sync-status icon (`cloud_done`/`download`) — **no per-card progress bar or
percentage was present** in the fetched design. This phase's FR-006 requires the library card to
show per-matn progress, which is therefore a **gap relative to the fetched design**, not a
conflict with it: T025 adds a compact progress affordance to `MatnCard` using the same
`MatnProgressBar`-style rendering (or a condensed variant of it) rather than inventing an
unrelated visual language, and this addition is recorded in `docs/DESIGN-SOURCE.md` "Open issues"
(T050).

### Design-fidelity gaps recorded (for T050 / `docs/DESIGN-SOURCE.md`)

1. No progress affordance exists on the fetched Home/Library grid card — added per FR-006 using
   `MatnProgressBar`'s visual language, condensed for card width.
2. No explicit zero/empty state was present in either fetched capture — the Goals tab's zero state
   (SC-007) is an original composition built from the same ring/list tokens, not a captured design.
3. The fetched Home screen additionally shows a streak/day-of-week tracker row (THU/WED/TUE/MON)
   below the daily-target stepper — out of scope for this phase's functional requirements; not
   implemented, and not treated as a regression since no FR asks for it.
