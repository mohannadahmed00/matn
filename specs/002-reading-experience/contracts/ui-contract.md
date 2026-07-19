# Contract: Presentation (ViewModels, Screens, Navigation) — Phase 1

The UI contract this phase exposes: the ViewModel state/intent surface, the navigation graph,
and the RTL/typography/localization rendering guarantees. Compose screens are pure functions of
state (Principle II); all logic lives in ViewModels over use cases.

## Base ViewModel (new, Phase 1-owned — Principle III)

```
abstract class BaseViewModel<S>(initialState: S) {
    val state: StateFlow<S>                 // single immutable observable UI state
    protected fun setState(reduce: (S) -> S)
    protected val scope: CoroutineScope     // cancelled in onCleared/deinit
    protected fun <T> Flow<T>.collectInto(onEach: (T) -> Unit)   // shared collection helper
    // shared loading/error handling for Resource<*> results
}
```

- Exposes exactly one `StateFlow<S>`; Composables render it and emit intents. No Compose,
  `Context`, or platform UI types referenced (Principle II).
- Lives in `commonMain` (Principle IV).

## ViewModels

### `HomeViewModel : BaseViewModel<HomeUiState>`
- On init: collects `ObserveLibraryUseCase()` → maps to `items`; empty list → `isEmpty = true`.
- Intent `OnMatnClicked(matnId)` → emits navigation to `matn/{matnId}` (FR-003).
- Guarantees: loading → loaded/empty transitions; never a blank screen or crash (SC-008).

### `MatnDetailsViewModel : BaseViewModel<MatnDetailsUiState>`
- Params: `matnId` (from route arg).
- On init: `GetMatnDetailsUseCase(matnId)` → header + chapters + `showTableOfContents`;
  collects `ObserveVersesUseCase(matnId)` → `verses`; collects `GetFontSizeUseCase()` →
  `fontSize`.
- Intents:
  - `OnChapterSelected(chapterId)` → emits a scroll-to effect targeting that chapter's
    `firstVerseDisplayNumber` (FR-014).
  - `OnFontSizeChanged(size)` → `SetFontSizeUseCase(size)` (persist) → state updates live
    (FR-016/FR-017).
- Guarantees: verses in matn-global order, Arabic verbatim with diacritics (FR-006/FR-008);
  simple matn shows no TOC (FR-015).

One-shot effects (navigation, scroll-to) are exposed as a separate `Flow`/`Channel` of effects,
distinct from the `StateFlow` of render state, so they fire once and don't replay on
recomposition.

## Navigation graph (CMP Navigation)

```
NavHost(startDestination = "home") {
    composable("home")            -> HomeScreen         // onMatnClick -> navigate("matn/$id")
    composable("matn/{matnId}")   -> MatnDetailsScreen  // reads matnId arg
}
```

- Back from details returns to Home (system back / RTL-aware back affordance).
- `SC-001`: Home → reading a matn is **2 taps max** (tap card → screen opens; verses visible).

## Rendering guarantees (screens)

| Guarantee | Rule | Requirement |
|-----------|------|-------------|
| RTL | `LocalLayoutDirection = Rtl` at root; only `start`/`end` used, never `left`/`right`. | FR-010, SC-005 |
| Arabic typeface | Verse text uses bundled **Amiri** `FontFamily`; diacritics rendered, never clipped. | FR-008, FR-009 |
| Font-size steps | Verse `fontSize` from state maps to the `ReadingFontSize` `sp` scale; live update; legible + no clip/overlap at SMALL and XLARGE. | FR-016, SC-007 |
| Empty state | `HomeUiState.isEmpty` → localized empty message composable. | FR-004, SC-008 |
| Missing cover | Null/absent `coverImageRef` → shared placeholder composable (card + header). | Edge cases, SC-008 |
| Large lists | Verse list = `LazyColumn` keyed by verse `id`; grid = `LazyVerticalGrid` keyed by matn `id`. | FR-011, SC-003 |
| Localization | Chrome strings via Compose resources; **Arabic base** + `values-en`; content Arabic never localized. | FR-021 |
| Offline | No network image/loader; all rendering from local store. | FR-018, SC-006 |

## DI (Koin)

`contentModule()` (extended) provides: `ReadingPreferencesRepository`, the five use cases, and
factory bindings for `HomeViewModel` / `MatnDetailsViewModel` (details takes `matnId`). No
manual singletons across layer boundaries.

## Out of scope (FR-020) — must NOT appear

No play/pause or any playback control, no active-verse highlight or auto-scroll, no progress bar
in the header, no search/bookmarks/notes, no dark-mode toggle, no tablet-adaptive layout. Screens
must not crash on rotation or differing screen sizes (Assumptions), but adaptive layout is Phase 8.
