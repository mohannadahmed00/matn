# Quickstart: Storage & Downloads

How to validate this feature end to end. Contracts and entity details live in
[contracts/](./contracts/) and [data-model.md](./data-model.md) — this file is the run guide.

## Prerequisites

- JDK 17+, Android SDK (compileSdk 36), and for the Android delivery pass **bundletool**
  ([download](https://github.com/google/bundletool/releases)) — asset packs cannot be exercised from
  a plain debug APK (research D12).
- For the iOS pass: Xcode with the ODR tags configured on the `iosApp` target.
- Nothing else. All logic tests run device-free.

## 1. Automated tests (the primary gate)

```bash
./gradlew :shared:allTests
```

Everything that matters is device-free in `commonTest`, driven by `FakeContentDeliveryEngine` and
`FakeDeviceStorage`. A green run covers install preconditions, progress and cancellation, process-death
and eviction recovery, removal (including personal-data preservation and both removal outcomes),
storage reporting, the playback gate, and the schema migration. See
[content-delivery-contract.md §6](./contracts/content-delivery-contract.md) for the full obligation list.

Migration only:

```bash
./gradlew :shared:allTests --tests '*MigrationV4Test*'   # or the platform host-test task the repo uses
```

Expected: v4 data survives v4 → v5, and every existing matn gains a `content_pack` row.

## 2. Android manual pass (real Play Asset Delivery)

A debug APK cannot resolve asset packs. Build a bundle and install it with local testing, which
serves packs from device storage:

```bash
./gradlew :androidApp:bundleDebug
bundletool build-apks --local-testing \
  --bundle=androidApp/build/outputs/bundle/debug/androidApp-debug.aab \
  --output=build/matn.apks
bundletool install-apks --apks=build/matn.apks
```

Then walk the three user stories:

| Step | Expected |
|---|---|
| Fresh launch, airplane mode on | Library lists every matn. The starter (الأجرومية) plays immediately; the others show "not installed" with a declared size (FR-014, FR-003, SC-011) |
| **Count the taps** from opening the library to a playing, freshly installed matn | Three or fewer interactions, excluding the install wait (SC-001) |
| **Time the install** of a ~100-verse matn on broadband, from confirmation to playable | Under 30 seconds (SC-010). Record the actual figure in the PR description |
| **Compare the stated size** against the Settings figure after install completes | Within 5% (SC-002). If it is not, the declared size in `bundledSampleMatns()` is stale — re-measure per T006 |
| Tap install while still offline | Refused with a clear, retryable message; matn stays not installed (FR-015) |
| Airplane mode off, tap install | Progress advances; a cancel action is available (FR-005) |
| Switch away mid-install, return | Transfer continued; screen shows its true state, not a restart (FR-005) |
| Cancel a fresh install | Returns to not installed; storage total unchanged (FR-006) |
| Let one complete, play it | Every verse plays; re-enable airplane mode and confirm playback, repetition, A–B loop and resume all still work (FR-012). **This is the only check for FR-012 — it is a device property with no automated equivalent, so do not skip it** |
| Open Settings and read the total | It includes the starter matn's size, not just on-demand content, and matches the OS's reported app storage within ~5% (SC-003, FR-025) |
| Try to play a not-installed matn | Install prompt appears — never silence or an error (FR-011, SC-007) |
| Kill the app mid-install, relaunch | Matn reports not installed with a retry; no orphaned bytes in the total (FR-009, SC-006) |
| Bookmark a verse, write a note, mark verses memorized, then remove the matn | Confirmation states the bytes reclaimed; after removal the matn is still listed, readable and searchable, and every bookmark, note and memorized mark survives (FR-016 – FR-020, SC-005) |
| Reinstall it | Playback works again and resume lands on the same verse and position (FR-023) |
| Remove the matn while it is playing | Playback stops first; player left in its defined stopped state (FR-021) |
| Remove the matn referenced by Continue Learning | The Home card offers reinstall rather than dead-ending (FR-022) |
| Open Settings | Real Settings screen with total used, free space, and a size-ordered breakdown; starter row marked "part of the app" with no remove action (FR-024, FR-025, FR-027, FR-030) |
| Remove from Settings | Row and total update immediately, no restart (FR-026, SC-004) |
| "Remove all downloaded content" | Every on-demand matn removed, starter still playable, all personal data intact (FR-029) |
| Fill the device, then install | Refused, stating required versus available space (FR-007) |

## 3. iOS manual pass (On-Demand Resources)

Same walkthrough, run from Xcode so ODR is hosted locally, **with one expected difference**:

> Removal on iOS reports `ReleasedPendingSystemReclaim`. The confirmation and post-removal copy say
> the space is released and reclaimed by the system when it needs it — the storage total may not drop
> immediately. This is a platform limitation, not a bug: iOS exposes no API to force-purge ODR
> content (research D2). **Verify against SC-004a, not SC-004**: the matn must report as not
> installed within 2 seconds, but the reclaimed bytes are the OS's business. SC-004's ≥95%-within-2s
> guarantee is scoped to Android by design.

Everything else — install, progress, cancel, gating, personal-data preservation, reinstall-and-resume,
Settings reporting — behaves identically.

## 4. What "done" looks like

- `./gradlew :shared:allTests` green, including `MigrationV4Test`.
- Both manual passes complete, with the measured SC-001 / SC-002 / SC-010 figures recorded in the PR
  description and the iOS removal difference verified against SC-004a rather than "fixed".
- `specs/008-storage-downloads/design-notes.md` written, listing the Settings screen and the
  install/remove states as original compositions (no captured Stitch design) — matching the Phase 6
  and 7 precedent.
- Every new state-rendering composable has a `@Preview`; no raw hex / `.dp` / `.sp` literals
  (Principle VIII, blocking review items).
