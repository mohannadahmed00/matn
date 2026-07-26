# Contract: Teacher UI

**Feature**: `specs/011-teacher-authoring-upload` | Satisfies FR-001, FR-005, FR-006, FR-006a–c, FR-013–FR-025, FR-035–FR-038

Governed by Principle II (stateless content + thin holder, previews per state) and Principle VIII
(canonical design, tokens not literals, extract at the second use).

---

## 1. Design source — fetch before writing (Principle VIII, blocking)

| Screen | Stitch id | What is this phase's |
|--------|-----------|----------------------|
| Upload Matn (Per-Verse) | `4f1bee3d7518487b986c7c63cb3c07ff` | Metadata form, verse list, text entry, reorder handles, draft/publish actions. **The audio column is Phase 12** |
| Search Matn — producer chrome | `fa8b63b0…` | The teacher-portal navigation shell (Dashboard / Upload Matn / Library Management / System Settings, identity block, storage row), per `docs/DESIGN-SOURCE.md` open issue #7 |

**Verified** from the local export `stitch-designs/11-Upload-Per-Verse.html`, the captured screen
contains: portal nav, storage-usage row, "Upload New Matn" heading and lead paragraph, Matn Title,
Author / Scholar, **Category**, Short Description, Cover Art with a recommended 800×1200 hint, a tip
callout, "Verse List & Audio Sync" with per-row drag handle / Arabic text / audio upload / delete,
"Add Next Verse", and "Save as Draft" + "Publish Matn".

**Two recorded deviations** — both go in `design-notes.md`:

1. **Category is not implemented.** The captured form has a Category selector (Tajweed / Aqeedah /
   Fiqh / Hadith). `SeedMatn` has no category field, and adding one would diverge the catalog schema
   from what `ContentSeedLoader` can ingest (spec Assumptions, FR-009). Omitted deliberately.
2. **The Arabic, mirrored form of the portal chrome is original work.** The capture is English LTR
   only. FR-006b requires the whole tool mirror. The Arabic layout is derived by mirroring the
   captured layout, not by redesigning it.

---

## 2. Bilingual chrome (FR-006a–c)

| Concern | Contract |
|---------|----------|
| String source | `TeacherStrings` interface + `ArabicStrings` / `EnglishStrings`, via `staticCompositionLocalOf`. **Not** Compose resources — research D5 verified no public runtime locale override exists in 1.11.1 |
| Completeness | The interface is the contract: a missing translation is a compile error, not a runtime fallback. Satisfies SC-012's "zero untranslated labels" structurally |
| Direction | `MatnTheme(layoutDirection = …)` — `Rtl` for Arabic, `Ltr` for English (research D6). Nothing else in the tool reads or overrides direction |
| Default | Arabic on first launch (Assumptions) |
| Persistence | `JvmLanguagePreference`, survives restart (FR-006a) |
| Verse fields | Always render and edit RTL, in **both** interface languages (FR-021) — the content is Arabic regardless of the chrome |
| Switching | Never mutates stored content or its direction (FR-006c, User Story 1 scenario 6) |

Anything with a side — nav rail, drag handles, drag direction, list affordances, form alignment,
icon mirroring — follows the ambient direction. Nothing may hard-code `start`/`end` as left/right.

---

## 3. Screen inventory

Each screen: a stateless `…Content` composable over an immutable state `data class` plus intent
lambdas, and a thin `…Screen` holder that collects the `StateFlow` and forwards intents. No rendering
logic in the holder (Principle II).

### 3.1 `SignInScreen` — US1

**State**: `email`, `password`, `isSubmitting`, `error: RemoteError?`, `secretStoreUnprotected: Boolean`

| State | Rendering |
|-------|-----------|
| Idle | Email + password, submit disabled until both non-empty |
| Submitting | Progress, inputs disabled |
| Error | Plain-language message from the §6 table, inputs retained |
| Unprotected store | Persistent warning that the session cannot be stored securely (FR-003a) |

### 3.2 `PortalShell` — chrome for every signed-in screen

Nav destinations, identity block (display name), storage-usage row, language switch, sign-out.
Mirrors with direction. Sole owner of the nav — no screen draws its own.

### 3.3 `LibraryScreen` — US5 (FR-035)

**State**: `entries: List<CatalogEntry>`, `isLoading`, `error`

| State | Rendering |
|-------|-----------|
| Loaded | Rows: title, author, publication badge, verse count, audio-completeness badge |
| **Empty** | First-run "no matns yet" with a create action — the gap flagged as Outstanding in clarify |
| Loading | Skeleton rows |
| Error | Retryable message + retry |

Row actions: open, publish/unpublish, per FR-036–FR-038.

### 3.4 `EditorScreen` — US2 + US3 (the phase's centre of gravity)

**State**: `draft: MatnDraft`, `validation: ValidationReport?`, `saveState: Idle|Autosaving|Saving|Saved(at)|Failed(error)`, `isPublished`, `focusedProblem: ErrorId?`

Regions: metadata form (title, author, description, structure kind, cover with preview/replace/
remove), chapter list for `STRUCTURED`, verse list, action bar (save draft / check / publish or
unpublish), and a save-state indicator showing last-saved time (FR-031c).

**Verse list — the FR-025 performance contract**:

| Rule | Why |
|------|-----|
| `LazyColumn` keyed by stable verse `id` | Reorder and delete must not remount unrelated rows |
| Text state lives in the ViewModel; rows take `(verse, onTextChange)` | 500 rows of composable-local mutable state is the failure mode this forbids |
| Row content is a **stateless shared component** | Principle VIII; also what makes it previewable alone |
| Edits are debounced into state | One keystroke must not recompose the list |
| Reorder arithmetic is `VerseOrdering` in `commonMain` | The part that can be wrong is unit-tested with no UI (research D11) |

Editing a **published** matn: banner stating changes go live on save, and no autosave (FR-031b).

### 3.5 `ImportPreviewDialog` — US6 (FR-023)

**State**: `lineCount`, `preview: List<String>` (first ~10), `problemLines: List<Int>`, `canProceed`

Nothing is committed until confirmed. Unreadable lines are listed **by line number**. Cancel leaves
the existing list untouched. Confirm appends in file order (FR-024).

### 3.6 `ValidationPanel` — US4 (FR-028)

Two sections: **must fix before publishing** (`blocking`) and **still outstanding** (`deferred`,
i.e. missing recordings — presented as work remaining, not failure). Every entry is clickable and
scrolls to its verse or chapter. Publish is disabled while `blocking` is non-empty (FR-029).

### 3.7 `PublishConfirmDialog` / `UnpublishConfirmDialog`

Publish states plainly that the matn will have no recordings yet. Unpublish states that students
will no longer see it and that it can be republished unchanged (FR-038).

---

## 4. Shared components (Principle VIII — extract at the second use)

New, in `:teacherApp/presentation/common/`:

| Component | Used by |
|-----------|---------|
| `TeacherTextField` | Editor metadata, sign-in, chapter titles |
| `VerseRow` | Editor verse list, import preview |
| `PublicationBadge` | Library rows, editor header |
| `AudioCompletenessBadge` | Library rows, editor header, publish dialog |
| `SaveStateIndicator` | Editor, portal shell |
| `ProblemRow` | Validation panel, import preview |
| `TeacherDialog` | Publish, unpublish, import, discard |

Reused from `:shared` unchanged: the Phase 10 token set (`Color`/`Type`/`Shape`/`Spacing`),
`MatnTheme` (with the new direction parameter), `CoverImage`, `ByteFormatter`, `IconActionButton`.
Reuse before adding — no near-duplicate of an existing shared component.

**Tokens only.** No raw hex, no magic `.dp`/`.sp` anywhere in `:teacherApp`. Blocking review item.

---

## 5. Preview matrix (Principle II + FR-006a)

Every state-rendering composable ships previews. Screen-level composables preview **each key state in
both languages**, since mirroring is a rendering concern that only a preview catches cheaply:

| Composable | Previews |
|-----------|----------|
| `SignInContent` | idle · error · unprotected-store — ×2 languages |
| `LibraryContent` | loaded · empty · error — ×2 languages |
| `EditorContent` | new draft · loaded draft · published-editing · validation-failed — ×2 languages |
| `VerseRow` | normal · focused · flagged-problem — ×2 directions |
| `ValidationPanel` | blocking-only · deferred-only · both — ×2 languages |
| `ImportPreviewDialog` | clean · with problem lines — ×2 languages |
| Every shared component in §4 | ≥1, both directions |

All previews are driven by hand-built sample state — no ViewModel, no Koin, no network. A new
state-rendering composable without a preview is a blocking review failure.

---

## 6. Error presentation (FR-005)

Every failure states **what failed** and **whether retrying helps**, derived from
`RemoteError.retryable`:

| `RemoteError` | Teacher-facing form | Action offered |
|---------------|--------------------|----------------|
| `Network` | Cannot reach the server | Retry |
| `Unauthorized` | Session ended — sign in again | Sign in; **on-screen work retained** |
| `Forbidden` | This account cannot change content | None |
| `Conflict` | Changed elsewhere since opened | Reload and re-apply |
| `QuotaExceeded` | Storage full | None; explains what to remove |
| `Server` | Server problem | Retry |
| `Decode` | Unexpected response — report this | None |

Two absolute rules: **no raw HTTP status or exception text ever reaches the teacher**, and **no
failure discards on-screen work** (FR-032, SC-010). Autosave failures are non-blocking and surface in
the `SaveStateIndicator`, escalating to a persistent warning after repeated failure — the teacher must
learn they are working unprotected rather than believing the draft is safe (spec edge case).

---

## 7. Accessibility

Follow the existing `A11yLabels` pattern from `:shared`. Every icon-only control carries a label in
the active interface language. Drag handles expose keyboard/pointer-accessible move-up/move-down
alternatives — research D11 keeps these as a first-class affordance, not a fallback, since a
drag-only reorder is unusable without a pointer.
