# Quickstart: Validating Student Remote Catalog & Download

**Feature**: `013-student-remote-catalog`

How to prove this phase works end to end. Every scenario maps to a user story in
[spec.md](./spec.md); details live in [data-model.md](./data-model.md) and
[contracts/](./contracts/) rather than being repeated here.

---

## Prerequisites

1. A Supabase stack — local (`supabase start`, project URL `http://127.0.0.1:54321`) or hosted.
   Phase 11's migrations must be applied; **Phase 13 adds no migration of its own** (research D1).
2. `:teacherApp` able to sign in as a provisioned teacher (a row in `public.teachers`).
3. **At least one published matn with per-verse audio**, authored through `:teacherApp` — the spec's
   Dependencies make this non-optional: nothing in this phase is testable end to end without one.
   Publish a second matn to exercise queueing, and keep a third **unpublished** to prove FR-004.
4. Student clients configured with the same `SupabaseConfig.projectUrl` / `anonKey`, and **no**
   teacher credentials.

---

## Automated checks

```bash
# Domain, data and contract tests — the primary gate (Constitution V)
./gradlew :shared:allTests

# All four clients build (FR-042 / SC-012)
./gradlew :androidApp:assembleDebug :desktopApp:build :teacherApp:build
```

The retirement greps in [contracts/retirement-contract.md](./contracts/retirement-contract.md) §4
must all come back empty. Run them before calling the phase done — SC-012 is not satisfied by a
green build alone.

---

## Scenario 1 — Browse an empty-then-populated catalog (User Story 1)

```bash
./gradlew :desktopApp:run     # fastest client to iterate on
```

1. **Fresh install, network up** → the published متون appear as cards with cover, title, author,
   description, verse count and download size; every one reads as not downloaded, and the
   unpublished matn is nowhere. *(FR-002, FR-004)*
2. Force-quit, **disable the network**, relaunch → the same catalog renders in full, sizes included.
   *(FR-005)*
3. Wipe local state, **start with the network down** → a connect-to-browse prompt with a retry;
   not a spinner, not an error page, not a blank grid. *(FR-044, SC-007)*
4. Unpublish everything in `:teacherApp`, refresh → "no متون available yet", visibly different from
   step 3. *(FR-044)*
5. Open an undownloaded matn → overview plus a download action, **no verse text**. *(FR-003)*

## Scenario 2 — Download and study offline (User Story 2)

1. Download a matn; watch progress advance, then flip to a remove action. *(FR-013, FR-014)*
2. **Disable the network entirely.** Read every verse, play end to end, run a repetition count and an
   A–B loop, mark a verse memorized, bookmark, write a note, change speed, resume. Nothing degrades
   and no request is issued. *(FR-025, SC-004)*
3. Start a second download while the first runs → it shows **queued**, not transferring, then starts
   when the first settles. Cancel a queued one → the running one is untouched.
   *(FR-015, Clarification 4)*
4. Kill the connection mid-transfer → not-downloaded, with a stated reason and a retry; check the
   filesystem: no `.tmp-` directory survives. *(FR-017, SC-006)*
5. Background the app mid-download, return → the transfer advanced; it did not restart. *(FR-018)*
6. Attempt to download with the device nearly full → refused, stating required against available.
   *(FR-019)*
7. Play a verse of an undownloaded matn → a download prompt, not silence and not an error. *(FR-021)*
8. Download a matn published with **some verses lacking audio** → succeeds; those verses read fine
   and are marked recitation-less; the rest plays. *(FR-023)*
9. Restart the device → still downloaded, nothing re-transferred. *(FR-022)*

## Scenario 3 — Remove and reclaim (User Story 3)

1. Bookmark a verse, write a note, mark one memorized. Remove the matn, confirming the reclaimed-space
   figure. *(FR-028)*
2. It reverts to a catalog entry with its full overview and a download action. *(FR-029)*
3. **Check bookmarks, notes, memorized marks and progress — all intact.** This is the one that the
   old schema's `ON DELETE CASCADE` would have destroyed silently (research D5); assert it
   explicitly. *(FR-029, SC-010)*
4. Re-download → reading and playback resume at the verse and position reached before. *(SC-010)*
5. Settings → total with a per-matn breakdown, and **no row marked non-removable**. *(FR-030, FR-031)*
6. "Remove all downloaded content" → nothing spared, zero bytes left under `downloads/`, every
   overview still browsable, every cover still rendering offline.
   *(FR-031, SC-009, Clarification 3)*

## Scenario 4 — Track what the teacher changes (User Story 4)

With a synced catalog, in `:teacherApp`: publish a new matn, revise a second, unpublish a third
(one the student has **not** downloaded), then unpublish a fourth the student **has** downloaded.
Refresh the student library:

| Expected | Requirement |
|---|---|
| the new matn appears | FR-008 |
| the revised one is flagged as having an update; the existing copy is untouched | FR-010, FR-011 |
| the undownloaded unpublished one vanishes | FR-008 |
| the downloaded unpublished one still reads and plays offline, and is no longer offered | FR-009 |
| choosing to update preserves bookmarks, notes, marks and progress for surviving verses | FR-011 |

Then kill the network and refresh → the synced catalog stays fully usable and the failure is a
retryable notice, not an emptied library. *(FR-007)*

**Staleness window** (Clarification 4/5): navigate out of the library and straight back in — no
request is issued. Wait past the 1-hour window, or hit explicit refresh, and one is. *(FR-006)*

## Scenario 5 — Search (User Story 5)

With one matn downloaded and one not, **network disabled**:

- search the undownloaded matn's **title** → it is returned; selecting it lands on its details
  screen with a download action, not on a verse. *(FR-033, FR-035)*
- search a word from the **downloaded** matn's verses → verse hits; selecting one opens that verse.
  *(FR-034)*
- search a word appearing only in the **undownloaded** matn's verses → no verse hits, with the scope
  stated so the student understands it is "not downloaded", not "does not exist". *(FR-036)*
- results return in under a second, offline, with diacritic-insensitive matching intact.
  *(FR-037, SC-011)*

---

## Scenario 6 — The retirement itself (FR-038 – FR-042)

1. Run the greps and the `find packs` check from
   [contracts/retirement-contract.md](./contracts/retirement-contract.md) §4 — all empty.
2. Confirm no `.mp3` ships in any client artifact:
   ```bash
   unzip -l androidApp/build/outputs/apk/debug/androidApp-debug.apk | grep -i '\.mp3'   # expect none
   ```
   *(FR-038, SC-005)*
3. Confirm the APK contains no asset pack and the iOS project no ODR tags. *(FR-039)*
4. Install a build over one that bundled the starter matn → the starter is gone; if the teacher has
   published it, it appears as an ordinary downloadable entry; nothing is fetched automatically.
   *(FR-040, spec edge case)*

---

## Common pitfalls

| Symptom | Likely cause |
|---|---|
| Every student read fails instantly with an auth error, offline or not | `AccessTokenProvider` not bound to the anonymous implementation — the clients still demand a bearer token (research D2) |
| Catalog is empty against a stack that has published متون | teacher published to a different project, or `published` never flipped true |
| Bookmarks vanish after removing a matn | migration `5.sqm` not applied — the `ON DELETE CASCADE` is still in place (research D5) |
| Storage total drifts from reality | covers being counted; they must live outside `downloads/` (research D11) |
| A cancelled download leaves bytes behind | `.tmp-{matnId}/` not deleted on the abort path (FR-017) |
| Desktop reports everything as downloaded | `DesktopContentDeliveryEngine` still bound; it must be deleted, not adapted (research D3) |
