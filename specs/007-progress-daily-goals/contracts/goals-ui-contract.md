# Contract: Progress & Goals UI

Presentation contract for the Goals tab, the Home ring, the details-header progress, and the
library-card progress. All surfaces are stateless-content + thin-holder (Principle II), token-driven
and RTL-native (Principle VIII). Designs MUST be fetched before implementation:
*Progress & Goals* `f059cccd2f634bc9ba2cf4d620e5df80` and the ring region of *Home / Library*
`618643f891144557b5a4ddf4bbad0c03` (docs/DESIGN-SOURCE.md). Maps to FR-006, FR-012, FR-015…FR-019.

## 1. Goals tab (`presentation/goals`)

Replaces the `ComingSoonScreen` at `Routes.GOALS` (FR-015 / SC-007).

**`GoalsUiState`**
```
data class GoalsUiState(
    val isLoading: Boolean = true,
    val dailyProgress: DailyProgress? = null,   // ring + goal editor
    val matnProgress: List<MatnProgress> = emptyList(),
    val isEmpty: Boolean = false,               // zero متون in library
)
```

**`GoalsViewModel`** (BaseViewModel): collects `ObserveDailyProgressUseCase` and
`ObserveLibraryProgressUseCase`; exposes an `onGoalChanged(Int)` intent → `SetDailyGoalUseCase`.
Owns no navigation.

**States the content composable + previews MUST cover**:

| State | Render |
|-------|--------|
| Loading | until first emissions arrive |
| Populated | `DailyGoalRing` (today's ring + editable goal) above a per-matn progress list (`MatnProgressBar` per row) matching each matn's details screen (FR-016) |
| Zero/empty | nothing memorized and default goal → purposeful zero state inviting the student to start; **never** the "coming soon" placeholder (FR-017/SC-007) |

**Goal editing**: editable from the Goals tab; the edit persists and the Home ring reflects the new
goal on return (US3 scenario 4 — both read the same `observeGoal()` source).

## 2. Home daily-goal ring

The `DailyGoalUiState` placeholder already exists (Phase 10) and is wired for real here.

**`DailyGoalUiState`** (MODIFY): drop `isPlaceholder`; carry `practiced: Int`, `goal: Int`,
`fraction: Float`, `isComplete: Boolean` (from `DailyProgress`). `HomeViewModel` collects
`ObserveDailyProgressUseCase` into it.

**Ring states** (FR-012): partial fill for `practiced < goal`; complete (100%) at `practiced ≥ goal`;
resets to empty on local-day rollover (FR-013 — driven by the reactive count, no UI timer). Rescales
immediately when the goal changes (US2 scenario 6).

## 3. Details-header progress (FR-006)

`MatnDetailsUiState.MatnHeader` gains `memorizedCount: Int` + `progressFraction: Float`;
`MatnDetailsUiState` gains `memorizedVerseIds: Set<String>`. `MatnDetailsViewModel` collects
`ObserveMatnProgressUseCase(matnId)` and `ObserveVerseMemorizationUseCase(matnId)`. The header renders
a `MatnProgressBar`; the value equals the library card and Goals row for the same matn (SC-002).

## 4. Library-card progress (FR-006)

`HomeUiState` gains `progressByMatn: Map<String, Float>` from `ObserveLibraryProgressUseCase`,
merged in `HomeViewModel` (not into domain `MatnSummary`). `MatnCard` accepts an optional
`progressFraction: Float?` and shows a progress affordance when present.

## 5. Reading-carousel memorized action + indicator (FR-002)

`ReadingCarousel` renders, on the **active** verse, a `MemorizedGlyph` (new, in `AnnotationGlyphs.kt`)
as both indicator (memorized) and toggle action — sitting alongside the existing bookmark/note
glyphs, visually distinct from them (distinct shape, not a color variant; FR-002 "distinct from …
bookmark/note indicators"). Toggling forwards a `onToggleMemorized(verseId)` intent →
`ToggleVerseMemorizedUseCase`. A "mark entire chapter" affordance (in the chapter/section context of
the reading screen) forwards `onMarkChapterMemorized(chapterId, memorized)` →
`MarkChapterMemorizedUseCase` (FR-003).

## 6. Shared components (`presentation/common`, Principle VIII)

| Component | Used by | Parameters (stateless) |
|-----------|---------|------------------------|
| `DailyGoalRing` | Home top bar + Goals tab | `fraction: Float`, `practiced: Int`, `goal: Int`, `isComplete: Boolean` |
| `MatnProgressBar` | Details header + Goals rows | `fraction: Float` (+ optional label/percent) |
| `MemorizedGlyph` | Reading carousel (+ any progress affordance) | `color`, `filled: Boolean`, `size` |

Each carries ≥1 `@Preview` driven by hand-built state; zero raw hex/`.dp`/`.sp` literals — Phase 10
tokens only.

## 7. Navigation

`MatnNavHost` `composable(Routes.GOALS)` swaps `ComingSoonScreen` for the real `GoalsScreen` wired
through `MatnKoinHolder` (the Notes-tab precedent). `NavigationTab`'s doc comment updates to note the
Goals tab now has a real screen. The `Routes.GOALS` value is unchanged, so the existing bottom-nav
save/restore behavior is preserved.

## 8. Test obligations

- `GoalsViewModelTest`: loading → populated; zero/empty state; `onGoalChanged` persists and re-emits.
- `HomeViewModelTest` (extend): daily-ring state populated from `DailyProgress`; card progress map
  merged without gating the grid (Home load-independence precedent).
- `MatnDetailsViewModelTest` (extend): header progress populated; `onToggleMemorized` flips the
  verse and updates the count.
- `@Preview` for `GoalsScreen` (populated + zero), `DailyGoalRing` (partial + complete),
  `MatnProgressBar`, `MemorizedGlyph` (filled + outline).
