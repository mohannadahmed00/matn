# Contract: Security Rules

**Feature**: `specs/011-teacher-authoring-upload` | Satisfies FR-033, FR-039, FR-040, FR-041, FR-042

FR-039 makes these rules the **only** gate on student visibility, and FR-042 requires them tested
rather than merely written. This file is the rule text and the test matrix that proves it.

---

## 1. Firestore rules — `firebase/firestore.rules`

```javascript
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {

    function isTeacher() {
      return request.auth != null
        && exists(/databases/$(database)/documents/teachers/$(request.auth.uid));
    }

    match /matns/{matnId} {
      // FR-040: published matns are readable by anyone; drafts only by the teacher.
      allow read: if resource.data.published == true || isTeacher();

      // FR-041: every mutation requires the teacher marker. Anonymous callers get nothing.
      allow create, update, delete: if isTeacher();
    }

    match /teachers/{uid} {
      // Provisioned by hand in the console (research D14). No client may read or write it —
      // the rules' own exists() check is a privileged read and is unaffected by this.
      allow read, write: if false;
    }

    // Anything not matched above is denied by default. Stated explicitly so a future
    // collection cannot be added without a deliberate rule.
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

**Why `exists()` and not a custom claim**: a marker document needs no Admin SDK, is visible in the
console, and makes FR-013's "must not block multiple teachers" true at the cost of adding one
document. Research D14 records the alternatives.

**Why `published` is a boolean**: rules compare it directly. A string would work but invites
`"true"` / `"TRUE"` drift in a field that is the entire security boundary.

---

## 2. Storage rules — `firebase/storage.rules`

```javascript
rules_version = '2';

service firebase.storage {
  match /b/{bucket}/o {

    function isTeacher() {
      return request.auth != null
        && firestore.exists(/databases/(default)/documents/teachers/$(request.auth.uid));
    }

    match /matns/{matnId}/{fileName} {
      // Cover images are public: a student must be able to render a catalog entry
      // before downloading anything, and before they have any account at all.
      allow read: if true;

      // FR-041: writes require the teacher marker. Cover images only in this phase —
      // audio arrives in Phase 12 and will extend the path match, not this predicate.
      allow write: if isTeacher()
        && request.resource.size < 5 * 1024 * 1024
        && request.resource.contentType.matches('image/(png|jpeg|webp)');
    }

    match /{allPaths=**} {
      allow read, write: if false;
    }
  }
}
```

**Cover images are readable without checking the matn's `published` flag.** This is deliberate and
worth stating: an unpublished matn's cover is reachable by anyone who guesses `matns/{uuid}/cover.png`.
A UUID is not guessable in practice, and a cover image is not the content — the verse text, which is
what a draft is protecting, stays gated by the Firestore rules. Gating Storage reads on a
`firestore.get()` per image would add a Firestore read to every catalog thumbnail in Phase 13. If a
future phase makes drafts genuinely sensitive, this is the line to revisit.

The size and content-type predicates enforce FR-016 server-side as well as in the client, so a
malformed upload cannot land even if the client check is bypassed.

---

## 3. Test matrix (FR-042)

Every row is one test. Run in `jvmTest` against the Firebase emulator, driven through this project's
own Ktor client (research D9), so the request shape under test is the one the app actually sends.
Tests skip when `FIREBASE_EMULATOR_HOST` is unset.

### 3.1 Firestore — reads

| # | Caller | Target | Expected | Requirement |
|---|--------|--------|----------|-------------|
| R1 | Anonymous | `matns/{id}` with `published: true` | **Allow** | FR-040 |
| R2 | Anonymous | `matns/{id}` with `published: false` | **Deny** | FR-040 |
| R3 | Anonymous | Collection query filtered `published == true` | **Allow** | FR-040 |
| R4 | Anonymous | Unfiltered collection listing | **Deny** | Query-vs-rule semantics, see schema §4.1 |
| R5 | Teacher | `matns/{id}` with `published: false` | **Allow** | FR-036 |
| R6 | Authenticated non-teacher (no marker doc) | `matns/{id}` with `published: false` | **Deny** | FR-013 forward-compat |
| R7 | Anonymous | `teachers/{uid}` | **Deny** | §1 |

### 3.2 Firestore — writes

| # | Caller | Operation | Expected | Requirement |
|---|--------|-----------|----------|-------------|
| W1 | Anonymous | Create `matns/{id}` | **Deny** | FR-041 |
| W2 | Anonymous | Update an existing published matn | **Deny** | FR-041 |
| W3 | Anonymous | Flip `published` false → true | **Deny** | FR-041, FR-039 |
| W4 | Anonymous | Flip `published` true → false (unpublish) | **Deny** | FR-041, FR-038 |
| W5 | Anonymous | Delete `matns/{id}` | **Deny** | FR-041 |
| W6 | Authenticated non-teacher | Any of W1–W5 | **Deny** | FR-013 forward-compat |
| W7 | Teacher | Create, update, publish, unpublish | **Allow** | FR-032, FR-034, FR-038 |
| W8 | Teacher | Write with a stale `currentDocument.updateTime` | **Deny** — `FAILED_PRECONDITION` | FR-043 |
| W9 | Anonymous | Write `teachers/{uid}` (self-promotion attempt) | **Deny** | §1 |

W9 is the one that matters most: without the explicit `teachers/{uid}` deny, an anonymous caller who
could create their own marker document would grant themselves write access to the entire catalog.

### 3.3 Storage

| # | Caller | Operation | Expected | Requirement |
|---|--------|-----------|----------|-------------|
| S1 | Anonymous | Read `matns/{id}/cover.png` | **Allow** | §2 |
| S2 | Anonymous | Write `matns/{id}/cover.png` | **Deny** | FR-041 |
| S3 | Teacher | Write a 1 MB `image/png` | **Allow** | FR-014 |
| S4 | Teacher | Write a 10 MB `image/png` | **Deny** — over the size limit | FR-016 |
| S5 | Teacher | Write `application/zip` | **Deny** — content type | FR-016 |
| S6 | Anonymous | Write outside `matns/**` | **Deny** | §2 catch-all |
| S7 | Authenticated non-teacher | Write `matns/{id}/cover.png` | **Deny** | FR-013 forward-compat |

**17 refusal cases and 6 permitted cases, 23 in total.** SC-008 requires 100% of the refusals to be
blocked; this matrix is what measures it.

---

## 4. Running the tests

```bash
# One-off: install the Firebase CLI (not a project dependency)
npm install -g firebase-tools

# From the repo root
firebase emulators:exec --only firestore,storage,auth \
  "./gradlew :shared:jvmTest --tests '*SecurityRules*'"
```

`firebase/firebase.json` points the emulator at the two rule files. The Gradle test task reads
`FIREBASE_EMULATOR_HOST`, which `emulators:exec` sets; with it unset the tests report as skipped and
the ordinary `./gradlew test` stays green on a machine with no Firebase CLI.

**CI**: one job must run the command above. Without it these tests exist but never execute, and
FR-042 is satisfied on paper only. This is listed as an open item in
[../research.md](../research.md).

---

## 5. Deployment

```bash
firebase deploy --only firestore:rules,storage:rules
```

Rules are tracked in the repo (research D13) — they are code, they are tested, and a rule change is a
reviewable diff. Only project configuration is gitignored, and no service-account key exists in this
design at all.
