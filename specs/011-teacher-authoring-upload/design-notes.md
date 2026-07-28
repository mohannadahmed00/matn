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

## T094 — Post-implementation notes (2026-07-28)

Recorded after all six user stories landed, per the Phase 6/7/8 precedent of closing the design gap
log with what was actually built vs. what the fetched captures showed.

### Deviation 3 — no Material Icons dependency; icon slots render as text/glyphs

The captures show icon glyphs in several chrome slots (nav-rail destinations, the drag handle, the
delete-verse action, the header search field). `:teacherApp` deliberately carries no Material Icons
dependency (Ground Rule 3 — fixed dependency set, Ktor 3.2.3 and jna-platform 5.6.0 only). Every
icon slot in `PortalShell.kt` and `VerseRow.kt` therefore renders the equivalent plain-text label or
a Unicode glyph (e.g. a `"⋮⋮"` drag handle, a text "Delete" action) instead of a vector icon. This
is a fidelity gap from the capture, not an oversight — adding an icon library was rejected in favor
of staying within the frozen dependency set.

### Deviation 4 — V8 EmptyMatn / V9 DocumentTooLarge scoped to the teacher tool only

`ContentIntegrityValidator` (T014) implements V1-V9 from `contracts/validation-contract.md`, but two
of those rules — `EmptyMatn` and `DocumentTooLarge` — are publish-time policy for content the
teacher tool produces, not properties the student-facing bundled-seed loader should enforce
retroactively. `ContentSeedLoaderImpl.validate()` (`shared/src/commonMain/kotlin/com/giraffe/matn/
data/seed/ContentSeedLoaderImpl.kt`) filters both errors out of the problems it returns, so existing
bundled matns (which predate this phase and were never subject to these limits) continue to load
unchanged. This was confirmed with the user directly (an AskUserQuestion decision) after discovering
the two rules would otherwise break `ProgressRepositoryTest`, a pre-existing student-app test.

### T078 — resolved 2026-07-28, partially — Firestore deployed live, Storage deferred

Originally flagged as not performable (no real Firebase project in the sandboxed environment). The
user created one (`matn-437dc`), connected it via the Firebase CLI's own MCP server
(`.mcp.json`'s `firebase` entry — `npx firebase-tools mcp --dir firebase --only firestore,storage`),
and closed the gap for real rather than leaving it purely theoretical:

- **Firestore rules deployed live**: the user ran `firebase deploy --only firestore:rules` from
  `firebase/`. Verified byte-for-byte against `firebase/firestore.rules` via
  `mcp__firebase__firebase_get_security_rules(type: "firestore")` — the deployed ruleset is
  identical to the repo file (teacher-marker `exists()` check, published-read/teacher-write rules,
  explicit deny-all fallback).
- **Teacher provisioning** (research D14) done by hand in the console: one Email/Password account,
  and a `teachers/{uid}` document keyed by that account's UID (content irrelevant — the rule only
  checks existence). One mistake caught and fixed in the process: the document was first created
  with Firestore's auto-generated random ID instead of the UID, which would have silently failed
  every rule check for that teacher; corrected before deploy.
- **Storage rules deployment deliberately deferred**, not forgotten: Firebase Storage now requires
  the Blaze (pay-as-you-go) plan even at zero usage (Google's October 2024 change — Spark/free
  projects can no longer enable it at all). The user chose not to attach billing for this project at
  this stage (`AskUserQuestion` — "skip Storage for now" over "upgrade to Blaze"). Consequence:
  `firebase/storage.rules` exists in the repo and is reviewed, but is not live anywhere. Any
  Storage-touching path (`UploadCoverImageUseCase` in this phase; all of Phase 12's audio upload)
  stays untested against a real backend until that decision changes.
- **`SecurityRulesTest`'s 24 emulator-gated cases still did not run** for real. A local Firebase
  Local Emulator Suite attempt (fake `demo-matn-test` project, no real project touched) got as far
  as starting the Firestore/Auth/Storage emulators, but tripped a Windows Firewall prompt for the
  emulator's local loopback socket that the user (reasonably) declined without context on what it
  was. The user preferred the MCP-to-real-project path instead, so this local run was abandoned
  rather than retried. T097's CI job (`.github/workflows/security-rules-tests.yml`) still runs this
  suite against a throwaway emulator on every relevant PR — that path doesn't depend on any of this
  session's local Windows environment quirks and remains the actual enforcement mechanism for
  FR-042.

### T101/T102 — performance check and manual walkthrough, environment limits

Both tasks call for driving the live desktop app interactively (frame-timing HUD, real clicks) and,
for T102, a live Firebase project with a provisioned teacher account. This environment has no
display and no Firebase credentials, so neither can be executed as written. What was done instead:

- **T101**: `EditorViewModelTest`'s `importing 500 lines then moving verse 400 to position 5 stays
  correct and fast` builds a 500-line file, imports it, and moves verse 400 to position 5 — asserting
  both correctness (500 verses, `1..500` numbering, the right text at the right index) and that the
  underlying `VerseTextImport.parse`/`VerseOrdering.append`/`VerseOrdering.move` calls each complete
  in well under a second. This is a proxy for the data-layer half of the frame budget, not a
  substitute for watching real frame times; the on-screen "no frame over 32 ms while typing/
  scrolling/dragging" observation itself needs a running GUI. Structurally, `VerseRow` (verified by
  reading `VerseRow.kt`) holds no `remember`/`mutableStateOf` of its own for verse text — the
  ViewModel is the sole owner of `arabicText` — which is the actual precondition for the list not
  recomposing wholesale on a keystroke; this was true before T101 and confirmed again here.
- **T102**: not run end-to-end. The scenario-by-scenario substitute, mapping each `quickstart.md` §3
  bullet to the automated test that exercises its logic (everything below the actual click/keystroke
  and everything not requiring a live Firebase project):
  - **3.1 Sign in**: `SignInViewModelTest` (`success clears submitting and error, and sets the
    session`, `failure surfaces the error and leaves the typed email in state`) covers #1/#4.
    #2 ("still signed in" after relaunch) is `RestoreSessionUseCase` + `TokenRefresher`
    (`TokenRefresherTest`), not re-tested per screen. #5 (offline sign-in message) and #6 (language
    switch/mirroring) have no automated coverage — #6 is visual by nature; #5 needs a real network
    failure.
  - **3.2 Create a draft**: `EditorViewModelTest`'s save/missing-field/cover-error cases cover #2/#5.
    #3 (reload after relaunch) is `LoadMatnForEditUseCase` plumbing, exercised via
    `reloading after a conflict replaces the draft with the server version` (same code path, different
    trigger). #4 (oversized cover) is `JvmFileChooser`'s size ceiling — a pure function, not
    ViewModel-level, and not separately unit-tested. Autosave timing (FR-031a) is
    `DraftAutosaveSchedulerTest`'s four cases (5 s idle, 60 s ceiling, coalescing, never-fires-when-
    published).
  - **3.3 Enter verse text**: `adding a verse appends...`, `editing a verse's text updates only that
    verse`, `reordering verses renumbers 1 through n`, `deleting a verse leaves no numbering gap`,
    `assigning a nonexistent chapter id is rejected` cover #1–#4. #5 (save/reload round-trip) is the
    same `FirestoreCatalogRepository` path as 3.2 #3.
  - **3.4 Validate and publish**: `checking for problems populates the validation report`,
    `requesting publish shows the confirm dialog...`, `confirming publish on a valid draft flips
    publicationState to PUBLISHED` cover #1–#3. #4/#5 (anonymous read allowed/denied by publish
    state) are `SecurityRulesTest`'s R-series cases, gated on a live emulator (see T097).
  - **3.5 Manage the catalog**: `LibraryViewModelTest` (loaded/empty/error rendering) covers #1.
    #2 reuses the 3.2 #3 path. #3/#4/#5 (edit-published-and-stays-visible, unpublish, republish with
    stable ids) are `SecurityRulesTest`'s W-series cases plus `FirestoreCatalogRepository`'s
    `unpublish`/`publish` methods — not independently unit-tested beyond the security-rules matrix.
    The conflict case is `reloading after a conflict replaces the draft with the server version and
    clears the failure`.
  - **3.6 Bulk import**: `VerseTextImportTest`'s six cases plus `EditorViewModelTest`'s
    `a valid import stages a preview...`, `confirming an import appends...`,
    `cancelling an import leaves the verse list untouched`, and
    `an invalid-encoding import surfaces an error...` cover all five bullets directly.
  - Left with **zero automated coverage**: the credential-plaintext check (SC-013, needs reading the
    actual DPAPI-encrypted file on disk), the offline/network-disconnected sign-in message, and every
    purely visual observation (RTL mirroring, language-switch relabeling). These need the literal
    manual pass and are flagged here rather than claimed.

### T102 — closed live, 2026-07-28 (superseding the note above)

The user connected their real Firebase project (`matn-437dc`) via the Firebase CLI's MCP server
(`.mcp.json`) and ran the actual `:teacherApp` desktop app against it, with me independently
verifying each step's Firestore/Storage state via MCP reads and raw unauthenticated `curl` calls
(not just trusting what the UI showed). Covered for real: sign-in, draft creation with full Arabic
verse text (109-verse Al-Jazariyyah, 70-verse Tuhfat al-Atfal, 130-verse Al-Ajurrumiyyah, 34-verse
Al-Bayquniyyah), save-as-draft, publish, the FR-040 anonymous-read check (confirmed via a bare
`curl` with zero auth headers — HTTP 200, full document), unpublish, and reopening a matn from the
Library to re-edit it. Still not covered: the offline sign-in message, the credential-plaintext
check, visual RTL/language-switch observation, and cover-image upload (Storage needs the Blaze
plan, which the user declined for now).

**Five real bugs surfaced by this live pass — every one invisible to the existing automated suite,
because `MockEngine`/fake-repository tests don't exercise the real Firebase response shape or real
Compose recomposition timing:**

1. **Sign-in silently failed with "Cannot reach the server."** `SignInRequest.returnSecureToken`
   defaults to `true`, but kotlinx.serialization's `Json` doesn't encode a property equal to its
   declared default unless `encodeDefaults = true` is set — so the flag never reached Identity
   Toolkit, which then omitted `refreshToken`/`expiresIn` from the response entirely, throwing
   `MissingFieldException` (mapped to the generic "Network" error by `RemoteErrorMapper.mapThrowable`,
   which is why the message was misleading). Fixed in `HttpClientFactory.kt` by turning on
   `encodeDefaults`. Root cause only surfaced because a real request body was serialized to the real
   API — no `MockEngine` test asserts exact wire-format JSON.
2. **Library Management never showed newly-created/newly-published matns without an app restart.**
   `LibraryScreen`'s `viewModel {}` call is cached by the ViewModelStore across destination switches
   (no per-destination store in this app), so `LibraryViewModel.init { load() }` only ever ran once
   per process. Fixed with a `LaunchedEffect(Unit) { viewModel.load() }` that reruns on every fresh
   entry into the screen.
3. **Opening a different matn from the Library kept showing whichever matn was opened first.**
   Same root cause as #2, on `EditorScreen`'s `viewModel {}` — fixed by keying the lookup on
   `viewModel(key = initialDraft.id)` so each distinct matn gets its own ViewModel instance.
4. **Typing into a brand-new matn silently reset itself mid-keystroke and spawned dozens of
   throwaway one-character-title documents in Firestore.** Introduced by fixing #3: `initialDraft`'s
   default value (`MatnDraftFactory.newDraft(...)`) was a plain expression re-evaluated on *every*
   recomposition, so once the ViewModel lookup was keyed on `initialDraft.id`, every keystroke handed
   it a fresh random UUID and silently replaced the in-progress ViewModel with a blank one — whose
   orphaned predecessor's pending autosave then fired once on its own, creating a one-off document.
   Fixed by wrapping the default in `remember {}` so it's computed once per composition lifetime.
   38 resulting throwaway documents were found via Firestore MCP and deleted (with the user's
   explicit confirmation first — this is real production data, not a fixture).
5. **Unpublishing a matn didn't update the Library row in place — required navigating away and back.**
   A classic last-response-wins race: `LibraryViewModel.load()` never cancelled a prior in-flight
   fetch, so the slow fetch kicked off on screen entry could resolve *after* the fast reload
   triggered by a same-session unpublish and overwrite the correct new state with the stale one.
   Fixed by tracking the load `Job` and cancelling it before starting a new one; a regression test
   (`a slow initial load does not overwrite a fresher unpublish-triggered reload`, using
   `StandardTestDispatcher` + virtual time to force the race) locks this in.

**One feature added beyond the original scope, at the user's request during this pass**: a "Clear
All" button in the verse-list header (`ClearAllVersesConfirmDialog.kt`), confirmed before wiping the
verse list — a fast undo for a mistaken bulk import, since one-by-one delete was the only prior
option.

### T-storage-swap — Firebase Storage → Supabase Storage (2026-07-28)

Firebase Storage now requires the Blaze (pay-as-you-go) plan even at zero usage (the same change
that limited T078 to Firestore-only), and the user chose not to attach billing to this project.
Since cover-image upload is the only feature in this phase touching Storage, and nothing on the
student side consumes `coverImageRef` as a real image yet (`CoverImage.kt` always renders the
shared placeholder — "Phase 1 has no network image loader" — retaining the field only for a later
phase), the storage backend was fully swappable with zero blast radius. Compared Cloudflare R2 vs.
Supabase Storage:

- **R2** is S3-compatible, meaning either hand-rolled AWS SigV4 request signing in pure
  multiplatform Kotlin (real crypto/canonicalization code, no library permitted under the fixed-
  dependency rule) or a separate backend component (a Cloudflare Worker) to issue presigned upload
  URLs — this project has no backend and isn't designed to have one.
- **Supabase Storage** is a plain REST API with bearer-token auth, the same shape as the existing
  Ktor + Firebase calls, and supports "third-party auth" — verifying the app's existing Firebase ID
  tokens directly. No second login system, no signing code, no new dependency.

Chose Supabase, and to replace Firebase Storage **entirely** (not just for covers) rather than split
storage across two providers once Phase 12's audio uploads land.

**Recorded decisions:**

1. **The Firebase ID token is reused as-is for Supabase Storage calls.** `TokenRefresher` is
   unchanged; Supabase's third-party-auth trust of the Firebase project (configured once, in the
   Supabase dashboard) is what lets it accept that same bearer token. No new auth flow.
2. **`coverImageRef` still stores just the object path** (`matns/{id}/cover.png`), not a full URL —
   unchanged Firestore schema. A base URL is client config (`SupabaseConfig.projectUrl`), matching
   the already-provider-agnostic contract from before this swap.
3. **The RLS write policy checks "any authenticated caller," not a literal `isTeacher()` lookup.**
   Postgres RLS (Supabase's rules language) can't query Firestore's `teachers/{uid}` collection the
   way `storage.rules`' own `firestore.exists(...)` did. This is equivalent in practice: research
   D14 established there is no self-registration anywhere in this app, so the *only* way to ever
   hold a valid Firebase ID token for this project is to be the manually-provisioned teacher.
   "Authenticated" and "is a teacher" coincide under this app's actual provisioning model. A
   deliberate simplification, not an oversight — worth revisiting if this app ever gains a second
   class of authenticated (non-teacher) user.
4. **One bucket, `matn-content`**, preserving the existing nested path scheme
   (`matns/{matnId}/cover.png` today, `matns/{matnId}/verses/{verseId}.mp3` when Phase 12 lands) —
   not a bucket per asset type.

**What changed:**

- `StorageRestClient.kt` rewritten in place (same class name and method shapes — `upload()`,
  `totalUsageBytes()` — so call sites in `FirestoreCatalogRepository` and `TeacherMain.kt` didn't
  change) to target Supabase's REST endpoints instead of Firebase's.
- New `SupabaseConfig.kt`, parallel to `FirebaseConfig.kt`. `FirebaseConfig` dropped its now-unused
  `storageBucket`/`storageBaseUrl`.
- `TeacherModule.kt` wires a `supabaseConfig()` provider reading three new
  `firebase/firebase.local.properties` keys (`supabaseUrl`, `supabaseAnonKey`, `supabaseBucket`),
  same env-var-override-then-file pattern as `firebaseConfig()`.
- `firebase/storage.rules` deleted (retired) and its entry removed from `firebase/firebase.json`;
  replaced by Supabase RLS policies run once in the Supabase SQL editor.
- `SecurityRulesTest.kt`'s S-series (Storage rule) cases removed — they tested Firebase Storage
  rules, which no longer apply. The R/W-series (Firestore) cases are unaffected.

**Live-tested and blocked (2026-07-28):** cover-image upload was tried end-to-end against the real
Supabase project and fails. Two issues surfaced in order:

1. The RLS write policies above (`auth.role() = 'authenticated'`) never matched. `auth.role()` reads
   a Supabase-native GoTrue `role` claim; Firebase ID tokens carry no such claim, so every write was
   rejected as an RLS violation regardless of the caller's identity. Fixed by switching the check to
   `auth.uid() is not null`, which Supabase's third-party-auth layer does populate from the token's
   `sub` claim for any recognized provider.
2. After that fix, uploads still fail — this one has no client-side workaround. Supabase Storage's
   `storage.objects.owner` column is UUID-typed with a foreign key to `auth.users.id`, and the
   Storage API inserts the caller's JWT `sub` claim into it directly on every authenticated write.
   Firebase UIDs (e.g. `YLBEkGBttjdnbz9K4ZSzwhKc2Nv2`) are not valid UUIDs, so every insert throws
   `invalid input syntax for type uuid`. Confirmed via Supabase's own GitHub discussions
   (`supabase/discussions#13534`) as a known, unresolved limitation of their Firebase third-party-auth
   integration (itself labeled private-alpha) — not something fixable from RLS SQL or client code.

Options considered: re-enable Firebase Blaze (its free tier covers this app's scale, but requires a
card on file, which the user wants to avoid); loosen the RLS insert policy to allow the `anon` role
(trades "verified teacher" for "obscure app + client-embedded anon key," i.e. genuine public-write
exposure on the bucket); or embed the Supabase `service_role` key in the desktop client (sidesteps
the bug — service-role tokens carry no `sub` claim — but that key bypasses RLS for the *entire*
Supabase project, not just this bucket, so a extracted key would be full database admin access).

**Decision: defer.** Cover-image upload ships in this phase in a known-broken state — the UI is
present, the pick/validate flow works, and a failed upload surfaces a translated error message
rather than silently doing nothing or crashing — but the network call itself does not succeed
against the current Supabase project. Same treatment as the earlier-deferred Storage
`SecurityRulesTest` cases: recorded here rather than worked around under time pressure. No app code
changes were made for this; `StorageRestClient`/`UploadCoverImageUseCase`/`EditorViewModel` are
otherwise complete and correct — the gap is entirely in the external Supabase Storage service.
Revisit when either Supabase fixes the owner-column bug, or the storage-backend decision above is
revisited.
- `docs/ROADMAP.md` and the constitution's Ktor dependency-justification note reworded from
  "Firebase Storage" to name Supabase Storage; Phase 13's not-yet-built `FirebaseContentDeliveryEngine`
  renamed in the roadmap text to the provider-agnostic `RemoteContentDeliveryEngine` so that phase
  doesn't start from a stale name.

**Not yet done**: live verification (upload a real cover through the app, confirm the object is
publicly fetchable via a bare `curl` with no auth, confirm re-upload overwrites) — needs the user to
create the Supabase project first (parallel to the Firebase project setup earlier this phase).

### RTL (FR-006a/b)

No new left/right-anchored APIs were introduced in `:teacherApp` — every new composable
(`ImportPreviewDialog`, `ValidationPanel`, `PublishConfirmDialog`, `VerseRow`, `PortalShell`, and the
editor/library screens) uses `Row`/`Column` with `Arrangement`/`Alignment.CenterStart`/`CenterEnd`,
which mirror automatically under `MatnTheme(layoutDirection = ...)`'s explicit per-language
direction (research D6). No live on-device RTL pass was possible in this environment (no Android
emulator, no Xcode/macOS) — verified by source audit only (grep for `.Left`/`.Right`/hardcoded
`left =`/`right =`: zero hits in `:teacherApp`).
