# Specification Quality Checklist: Phase 12 — Audio Capture & Slicing

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-07-31
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Validation pass 1 (2026-07-31) found three issues, all fixed in the same pass:
  1. **Provider names leaked into the spec.** Early drafts named Supabase Storage and the MP3
     codec choice (JLayer / ffmpeg) directly. Both are HOW, not WHAT — rewritten as "remote
     storage under the matn's own prefix" and "a decoder for the teacher's recording formats",
     with the dependency-justification obligation kept as an assumption rather than a decision.
  2. **"Audio capture" was ambiguous** between live microphone recording and ingesting recorded
     files. Resolved in favour of ingest-only (the roadmap describes only upload and split paths),
     stated as an assumption and repeated in Out of Scope so it cannot be re-read as a gap.
  3. **The publish gate was unstated.** Whether a partially recorded matn may publish changes the
     phase's scope materially. Resolved to "publishing requires complete audio", per Phase 11's
     FR-027, and recorded as an assumption plus FR-028/FR-030.

- Validation pass 2 (2026-07-31), after the user answered both open questions: publish-requires-
  complete-audio and ingest-only both confirmed as written. A third decision surfaced from the
  answer and is now recorded in the spec's Clarifications section — the continuous recording is
  **sliced into per-verse files on save**, not stored alongside a start/end map. The map model was
  raised as a direct conflict with the constitution's per-verse audio lock and rejected by the
  user with the trade-offs stated. FR-003 sharpened to name MP3 as the supplied format.

- Validation pass 3 (2026-07-31), after `/speckit-clarify` — five questions asked and answered, all
  integrated. All 16 items still pass; three were re-checked closely because the answers touched
  them:
  - *"Requirements are testable and unambiguous"* — strengthened, not weakened. FR-004's ceilings
    were unnamed numbers and are now 10 MB / 300 MB / 4 hours; FR-005's "one distribution format"
    was an outcome with no stated mechanism and is now a reject-on-mismatch rule (FR-005a/b).
  - *"No implementation details"* — the storage-bucket limit in FR-004b is the closest call in the
    spec. Kept, because a client-side check with no server backstop is a real requirement gap, and
    the requirement is stated as "at least as permissive as the tool's ceilings" rather than as a
    configuration value.
  - *"Edge cases are identified"* — six added from the answers (profile mismatch, profile reset on
    full removal, stranded file after a failed delete, abandoned-split leftovers, scope changed
    mid-plan, reorder breaking scope contiguity).
  - Two contradictions the answers exposed were resolved rather than left: resume-by-derivation
    (Q3) initially collided with FR-019's all-or-nothing split, fixed by deriving split resume from
    storage contents rather than the matn row (FR-019b) and guarding stale leftovers (FR-019c); and
    the whole-matn splitting flow collided with User Story 5's partial re-split, fixed by scoping a
    split to a chosen verse run (FR-013a).

- Two success criteria carry a deliberately implementation-adjacent number: SC-004's 50 ms
  boundary tolerance and SC-013's 32 ms frame budget. Both are user-observable (an audible mis-cut,
  a visible stutter) and SC-013 matches Phase 11's FR-025, so they are kept rather than softened.
