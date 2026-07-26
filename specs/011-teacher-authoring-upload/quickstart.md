# Quickstart: Phase 11 — Teacher Authoring Tool

**Feature**: `specs/011-teacher-authoring-upload` | **Date**: 2026-07-26

How to run and validate this phase end to end. Design detail is in
[plan.md](./plan.md), [data-model.md](./data-model.md), and [contracts/](./contracts/) — this file
does not repeat it.

---

## Prerequisites

| Need | Why |
|------|-----|
| JDK 17+, the repo's Gradle wrapper | Existing project baseline |
| A Firebase project with Firestore, Storage, and Email/Password auth enabled | The backend under test |
| Firebase CLI (`npm install -g firebase-tools`) | Emulator, and rule deployment. **Only** needed for the rule tests (§4) and deployment (§6) |
| One teacher account, created by hand in the console | Provisioning is manual (research D14) |
| A `teachers/{uid}` document for that account | The authorization marker the rules check |

### Configuration

```bash
cp firebase/firebase.local.properties.template firebase/firebase.local.properties
# fill in: projectId, apiKey, storageBucket
```

`firebase.local.properties` is gitignored (research D13). No service-account key is used anywhere in
this design — the tool authenticates as the teacher, never as an administrator.

---

## 1. Run the teacher tool

```bash
./gradlew :teacherApp:run
```

Expected on a clean machine: sign-in screen, Arabic chrome, right-to-left layout.

Sanity checks that the student clients are untouched (FR-044, SC-015):

```bash
./gradlew :desktopApp:run          # student desktop app, unchanged
./gradlew :androidApp:assembleDebug
```

---

## 2. Fast feedback — the pure core

Everything risky in this phase is a pure function or takes injected collaborators, so most of it
validates without a network, an emulator, or a device:

```bash
# On Windows — the iOS test tasks cannot execute here:
./gradlew :shared:jvmTest :shared:testDebugUnitTest
# On macOS or CI, the full form:
./gradlew :shared:allTests
```

Covers: `ContentIntegrityValidator` rule by rule, `MatnDraft` ↔ `SeedMatn` round-trip,
`VerseOrdering` move/renumber, `VerseTextImport` parsing, `DraftAutosaveScheduler` on virtual time,
and the `FirestoreValue` codec.

```bash
./gradlew :shared:jvmTest
```

Adds the REST clients against Ktor `MockEngine` — request shapes, the status → `RemoteError` mapping,
and token-refresh behaviour.

**The extraction guard**: the existing `ContentSeedLoader` tests must pass **unchanged**. If they
needed editing, the validator extraction changed behaviour and is wrong
([validation-contract.md](./contracts/validation-contract.md) §1.1).

---

## 3. Manual walkthrough — the acceptance path

Runs the spec's five user stories in order. Expected results reference the spec's own criteria.

### 3.1 Sign in (US1)

1. Launch, enter the teacher's credentials → portal, display name shown, storage usage shown.
2. Quit and relaunch → **still signed in** (FR-003).
3. Sign out → sign-in screen; any save attempt is refused (FR-004).
4. Wrong password → understandable message, no session stored (FR-001 #2).
5. Disconnect the network, try to sign in → "cannot reach the server", not a generic failure (FR-001 #5).
6. Switch language → every label changes, layout mirrors, choice survives a restart (FR-006a/b).

**Credential check (SC-013)**: after a full sign-in cycle, no file the tool writes contains the token
in plaintext. On Windows the persisted value is DPAPI ciphertext; on macOS/Linux it is in the OS
store and not in the app directory at all. If the tool shows the unprotected-fallback warning, that
is the fallback path (FR-003a) and should be noted, not ignored.

### 3.2 Create a draft (US2)

1. New matn: title, author, description, cover image, structure kind `STRUCTURED`, two chapters.
2. Save as draft → confirmed; the document exists in Firestore with `published: false`.
3. Quit, relaunch, reopen → every field including the cover restored (FR-002 #4).
4. Try a 10 MB cover → rejected with the limits named, rest of the form preserved (FR-002 #5).
5. Leave a required field empty and save → flagged inline, nothing stored (FR-002 #2).

**Autosave (FR-031a, SC-014)**: type without saving, wait ~60 s, watch the last-saved time update.
Kill the process mid-edit, relaunch, reopen — at most the last ~60 s of typing is gone.

### 3.3 Enter verse text (US3)

1. Add five verses of Arabic text → RTL rendering, diacritics preserved.
2. Drag one to a new position → order updates, numbering stays `1..n` (FR-020).
3. Delete one → no numbering gap.
4. Assign verses to chapters → each belongs to exactly one existing chapter.
5. Save, reload → order, numbering, and text intact.

### 3.4 Validate and publish (US4)

1. Introduce a duplicate verse number, run check → the problem is named with its verse; publish
   refused (FR-029).
2. Note the **outstanding-work** section listing missing recordings — informational, not blocking
   (FR-027, US4 #2).
3. Fix the duplicate, publish → state becomes `published`, `audioCompleteness: "NONE"` (US4 #3).
4. In a private browser window or `curl` with no credentials, read the document → **allowed**.
5. Same for a draft → **denied** (FR-040).

### 3.5 Manage the catalog (US5)

1. Portal list shows both matns with state, verse count, and audio-completeness (FR-035).
2. Reopen the draft → loads exactly as saved (FR-036).
3. Edit a verse in the **published** matn and save → stays published; an anonymous read returns the
   correction (FR-037).
4. Unpublish → anonymous read now denied (FR-038).
5. Republish → readable again, **same identifiers** (FR-038, US5 #5).

**Conflict (FR-043)**: open the same matn in two instances, save in the first, then save in the
second → the second reports a conflict and does not overwrite.

### 3.6 Bulk import (US6)

1. Prepare a UTF-8 file, one verse per line, ~50 lines, including two blank lines and one line with
   commas and quotation marks.
2. Import → preview shows the count and first rows; blank lines are silently skipped, not reported as
   problems (FR-023a).
3. Confirm → verses appended in file order, fully editable (FR-024).
4. Check the punctuation line → commas and quotes preserved verbatim, not split (US6 #5).
5. Try a non-UTF-8 file → rejected naming the expected encoding; the existing list is untouched
   (US6 #4).

---

## 4. Security-rule tests (FR-042, SC-008)

The rules are the only gate on student visibility, so this is not optional verification.

```bash
firebase emulators:exec --only firestore,storage,auth \
  "./gradlew :shared:jvmTest --tests '*SecurityRules*'"
```

Runs the 23-case matrix in [security-rules.md](./contracts/security-rules.md) §3 — 17 refusals and
6 permitted cases — through the project's own Ktor client, so the request shape under test is the one
the app actually sends.

Without `FIREBASE_EMULATOR_HOST` set these tests **skip**, which is why an ordinary
`./gradlew test` stays green on a machine with no Firebase CLI — and why CI needs a job that runs the
command above. Without that job the tests exist but never execute.

---

## 5. Performance check (FR-025, SC-009)

The budget is **no frame over 32 ms** (FR-025, SC-009). Run with Compose's frame timing visible — on
desktop, launch with `-Dcompose.desktop.render.onframe.log=true`, or record the operations and confirm
no dropped-frame cluster.

1. Import a 500-line verse file.
2. Type into a verse near the middle — no frame over 32 ms, and the list must not recompose wholesale.
3. Scroll top to bottom — no frame over 32 ms.
4. Drag a verse from position 400 to position 5 — no frame over 32 ms, and numbering correct
   afterwards.

If typing is janky, the usual cause is verse text state having crept into the row composable instead
of the ViewModel ([teacher-ui-contract.md](./contracts/teacher-ui-contract.md) §3.4).

---

## 6. Deploy the rules

```bash
firebase deploy --only firestore:rules,storage:rules
```

Do this **before** the first real publish. Firestore's default rules deny everything, so an
undeployed ruleset looks exactly like a broken client.

---

## Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| Every read denied, including published matns | Rules not deployed (§6), or the collection query is not constrained to `published == true` — see [firestore-schema.md](./contracts/firestore-schema.md) §4.1 |
| Every write denied while signed in | No `teachers/{uid}` marker document for this account (research D14) |
| Sign-in fails with a valid password | Wrong `apiKey` or `projectId` in `firebase.local.properties`; or Email/Password not enabled in the console |
| Save always reports a conflict | `remoteUpdateTime` not being refreshed from the write response — the next save then sends a stale token |
| Rule tests all skip | `FIREBASE_EMULATOR_HOST` unset; run through `firebase emulators:exec` |
| Chrome does not mirror on switch | Direction not threaded from the language preference into `MatnTheme(layoutDirection = …)` (research D6) |
| An English label appears in Arabic mode | Impossible via `TeacherStrings` — a missing translation is a compile error. If seen, the string is hard-coded in a composable instead of routed through the table |
