# Phase 1 Quickstart: Validating the Reading Experience

How to prove the static reading experience works end to end. Two validation surfaces:
**device-free unit tests** for the ViewModel/use-case/preference logic (Principle V, primary
gate), and a **manual on-device walkthrough** for the visual guarantees (RTL, Arabic
typography, font-size steps) that can't be asserted headlessly.

See [contracts/use-cases.md](./contracts/use-cases.md),
[contracts/ui-contract.md](./contracts/ui-contract.md), and [data-model.md](./data-model.md)
for the exact types and rules referenced below.

## Prerequisites

- Phase 0 complete: SQLDelight store, content repositories, and at least one **simple** and one
  **structured** matn seeded (Assumptions). Reuse Phase 0's `commonTest` fixtures / seed loader.
- Toolchain from Phase 0 (Kotlin 2.4.10 KMP, Compose Multiplatform, Koin, SQLDelight).

## A. Automated validation (`commonTest`, no device)

Run:

```
./gradlew :shared:allTests            # or :shared:testDebugUnitTest for the Android host
```

Cover the behavioural contract with faked repositories/use cases (in-memory SQLDelight driver
for the preference + summary reads):

| Scenario | Assert | Requirement |
|----------|--------|-------------|
| Library populated | `HomeViewModel.state.items` has one `MatnSummary` per matn with correct `verseCount` / `totalDurationMs` | FR-002 |
| Library empty | `HomeUiState.isEmpty == true`, no error | FR-004, SC-008 |
| Open matn | `MatnDetailsUiState.header` totals correct; `verses` in ascending `displayNumber` order | FR-005, FR-006 |
| Arabic integrity | each `VerseRow.arabicText` equals the stored string **byte-for-byte** (diacritics intact) | FR-008, SC-002 |
| Structured matn | `showTableOfContents == true`, chapters ordered, each `firstVerseDisplayNumber` = min displayNumber of its verses | FR-013, FR-014 |
| Simple matn | `chapters` empty, `showTableOfContents == false`, verses one flat list | FR-015 |
| Font size change | `SetFontSizeUseCase` persists; `GetFontSizeUseCase` re-emits new size | FR-016 |
| Font size persistence | fresh ViewModel over the same store reads back the persisted size; unset → `MEDIUM` | FR-017, SC-007 |
| Derived totals | `SUM(duration_ms)` / `COUNT(*)` match the seeded verses | FR-002, FR-005 |

All of the above must run with **no device, emulator, or network** and land in the same change
as the code (Principle V).

## B. Manual walkthrough (Android + iOS)

Run the app:

```
./gradlew :androidApp:installDebug     # Android device/emulator
# iOS: open iosApp in Xcode and run on a simulator
```

Then verify the visual guarantees against the acceptance scenarios:

1. **Home library (US2)** — cards show cover (or placeholder), title, author, verse count,
   duration; whole screen is **RTL**. Empty store → localized empty state, not a blank/crash.
2. **Open a matn (US1)** — ≤ 2 taps from Home to visible verses (SC-001). Header shows
   cover/title/author/description/count/duration. Every verse shows its number + Arabic text in
   **Amiri**, diacritics intact, RTL, no clipping. Long/diacritic-heavy verse wraps fully.
3. **Structured matn TOC (US3)** — chapters listed in order; selecting one scrolls to that
   chapter's first verse. Open a **simple** matn → no TOC, one continuous list.
4. **Font size (US4)** — adjust across Small…X-Large: verse text resizes **live**, stays legible
   with no clip/overlap at both extremes; reopen the app → chosen size retained.
5. **Offline (SC-006)** — enable airplane mode, repeat 1–4: everything renders; **zero** network
   requests.
6. **Locale (FR-021)** — device in Arabic → Arabic chrome; device in English → English chrome;
   device in a third locale → Arabic chrome (fallback). Reading UI stays RTL regardless.

## Definition of done (Phase 1)

- [ ] `commonTest` suite (Section A) passes on the CI host (Principle V gate).
- [ ] Manual walkthrough (Section B) passes on both Android and iOS.
- [ ] All FR-001…FR-021 satisfied; SC-001…SC-008 met.
- [ ] No FR-020 out-of-scope feature present; app builds and is independently testable
      (constitution: each phase ships a buildable, testable slice).
