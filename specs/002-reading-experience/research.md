# Phase 1 Research: Reading Experience (static)

This document resolves the technical unknowns for the first UI phase. The stack is fixed by
the constitution (KMP + Compose Multiplatform, MVVM, Koin DI, base package `com.giraffe.matn`)
and by the Phase 0 foundation (SQLDelight store, `Matn`/`Chapter`/`Verse` domain models,
`MatnRepository`/`VerseRepository` interfaces, `Resource`/`AppError`). The open decisions are
the presentation-layer shape and the supporting UI libraries. No `NEEDS CLARIFICATION` markers
remain after this document.

---

## Decision 1 — Presentation architecture: use cases + `BaseViewModel` + `StateFlow`

- **Decision**: Introduce the presentation layer now, wired strictly as
  **Compose screen → ViewModel → use case → repository interface**. Each screen exposes a
  single immutable UI-state `data class`/sealed type via `StateFlow`, produced by a
  `ViewModel` that extends a new `BaseViewModel`. ViewModels never touch data-layer classes
  or repositories directly — they call use cases. Introduce the shared use-case contracts the
  constitution requires:
  - `UseCase<in P, out R>` — suspend, one-shot (`suspend operator fun invoke(params): Resource<R>`).
  - `FlowUseCase<in P, out R>` — returns `Flow<R>` for observable reads.
  Phase 1 use cases: `ObserveLibraryUseCase`, `GetMatnDetailsUseCase`, `ObserveVersesUseCase`,
  `GetChaptersUseCase`, plus preference use cases (Decision 2).
- **Rationale**: Principle I requires presentation to reach the domain **only through use
  cases**; Principle II makes MVVM + a single observable `StateFlow` state non-negotiable;
  Principle III requires a `BaseViewModel` (shared state container, coroutine scope, loading
  and error handling) and a shared `UseCase`/`FlowUseCase` contract. Phase 0 deferred these
  base types explicitly because it had no UI; Phase 1 is the phase that owns them. Reusing the
  existing `Resource`/`AppError` sealed type keeps fallible flows DRY.
- **Alternatives considered**:
  - *ViewModels calling repositories directly* — rejected: violates Principle I ("never
    directly on data-layer classes") and the "through use cases" clause.
  - *MVI framework (e.g. Orbit, Ballast)* — rejected for this phase: a new dependency whose
    reducer/intent machinery is heavier than a handful of read-only screens need; the
    constitution's MVVM + unidirectional `StateFlow` requirement is met with plain
    coroutines/Flow. Revisit if intent handling grows in later playback phases.

## Decision 2 — Reading font-size preference: persist via SQLDelight (reuse, no new dependency)

- **Decision**: Model the font-size step as a domain enum `ReadingFontSize`
  (`SMALL, MEDIUM, LARGE, XLARGE`; default `MEDIUM`) behind a new domain interface
  `ReadingPreferencesRepository` (`observeFontSize(): Flow<ReadingFontSize>` +
  `suspend fun setFontSize(size): Resource<Unit>`). Persist it in a tiny **key/value settings
  table** added to the existing SQLDelight `ContentDatabase` (`app_setting(key TEXT PK, value
  TEXT)`), written on every change (Principle VI). Expose it through `GetFontSizeUseCase` /
  `SetFontSizeUseCase`.
- **Rationale**: The constitution requires new dependencies to be justified against a simpler
  alternative; SQLDelight is already present and cross-platform, so reusing it for one
  key/value row is the simplest option and needs no `expect`/`actual`. FR-017 requires a single
  **global** preference retained across sessions — a one-row setting, not per-matn state. A
  domain interface keeps persistence behind the domain boundary (Principle I) and makes the
  preference fakeable in ViewModel tests (Principle V). This is the only Phase 1-owned
  persisted state, matching the spec's "lightweight reading display preference".
- **Alternatives considered**:
  - *multiplatform-settings / AndroidX DataStore (KMP)* — rejected: a new dependency for a
    single value when SQLDelight already covers it; DataStore multiplatform also adds an
    `expect`/`actual` surface with no benefit here.
  - *A separate settings database/file* — rejected: unnecessary second store; the content DB
    already provides transactional, observable storage.
  - *Per-matn persistence* — rejected by clarification (single global preference, FR-017).

## Decision 3 — Localization / interface language: Compose resources with Arabic as the base

- **Decision**: Put all interface-chrome strings in **Compose Multiplatform string resources**
  (`compose.components.resources`, already a dependency). Author the **base** `values/strings`
  in **Arabic** and provide an **English** override in `values-en/strings`. Compose resources
  resolve English on English-locale devices and fall back to the base (Arabic) for every other
  locale — exactly the FR-021 rule (Arabic + English, fall back to Arabic when neither).
- **Rationale**: Uses the resource framework already on the classpath (no new dependency),
  gets automatic device-locale selection, and encodes "fall back to Arabic" simply by making
  Arabic the base resource set. Keeps interface chrome (labels, empty state, TOC header,
  buttons) cleanly separated from stored Arabic *content* (which is never localized).
- **Alternatives considered**:
  - *moko-resources* — rejected: a new dependency; Compose's built-in resources now cover
    multiplatform strings and locale fallback.
  - *English base + Arabic override* — rejected: FR-021 requires Arabic as the fallback for
    unmatched locales, so Arabic must be the base set.

## Decision 4 — Arabic reading typeface: bundle a classical OFL font (Amiri)

- **Decision**: Bundle **Amiri** (SIL Open Font License) as a Compose font resource and apply
  it to verse text only, via a shared `FontFamily` in the theme. Interface chrome uses the
  platform default. Amiri renders full تَشْكِيل (diacritics) faithfully with classical Naskh
  shaping.
- **Rationale**: FR-009 requires an elegant, legible classical Arabic typeface for متون; a
  bundled font guarantees identical, offline rendering across Android and iOS (FR-018/SC-006)
  instead of depending on divergent system Arabic fonts. OFL permits redistribution inside the
  app. Amiri is purpose-built for diacriticized classical Arabic (Quran/متون) and preserves
  every mark without clipping (FR-008/SC-002).
- **Alternatives considered**:
  - *Scheherazade New (also OFL)* — an equally valid classical option; kept as the documented
    fallback if Amiri's metrics cause line-height issues with heavy diacritics. Either satisfies
    the requirement.
  - *System Arabic font* — rejected: not guaranteed present or consistent across platforms/OS
    versions; risks diacritic clipping and visual drift between Android and iOS.

## Decision 5 — Navigation: adopt Compose Multiplatform Navigation (`androidx.navigation`)

- **Decision**: Use the official **Jetbrains Compose Multiplatform Navigation** library
  (`org.jetbrains.androidx.navigation:navigation-compose`, version **2.9.2** — the build bundled
  with Compose Multiplatform 1.11.0, confirmed against the CMP 1.11.0 release notes) with a
  `NavHost` and two routes: `home` and `matn/{matnId}`. A single root composable hosts the graph;
  `matnId` is passed as a route argument. Koin provides ViewModels per destination.
- **Rationale**: Phase 1 has two screens but the roadmap grows to ~8 phases and many screens
  (details, player, search, bookmarks, settings, progress); a real back stack, type-safe args,
  and saved-state survival are worth a justified dependency now rather than hand-rolling and
  replacing later. It is the first-party CMP navigation solution, keeps navigation logic in
  `commonMain` (Principle IV), and integrates with the lifecycle-viewmodel artifacts already on
  the classpath.
- **Alternatives considered**:
  - *State-based navigation (a `sealed Screen` held in a root state)* — viable for two screens
    and the simplest option, but gives no back stack, argument, or saved-state handling and
    would be rebuilt in Phase 2+. Documented as the minimal fallback if adding the navigation
    dependency proves problematic on iOS.
  - *Voyager / Decompose* — rejected: capable but heavier third-party frameworks; the
    first-party CMP navigation covers this app's needs with less surface area.

## Decision 6 — Derived totals (verse count, total duration): SQLDelight aggregate reads

- **Decision**: Derive each matn's verse count and total estimated duration with **read-only
  aggregate queries** added to `Content.sq` (e.g. `SELECT COUNT(*)` and
  `SELECT SUM(duration_ms)` grouped by `matn_id`), surfaced through the repository as a
  `MatnSummary` (matn + `verseCount` + `totalDurationMs`) for the library grid, and computed
  once for the details header. No schema change; these are additive queries over existing
  columns.
- **Rationale**: The Assumptions section states totals are **derived**, not stored. Computing
  them in SQL avoids loading every verse just to count/sum on the library screen (relevant at
  the 500-verse scale, SC-003) and keeps the derivation in the data layer, not the ViewModel.
  Reuses Phase 0's `verse.duration_ms`.
- **Alternatives considered**:
  - *Summing in the ViewModel from observed verse lists* — rejected for the library grid: would
    stream every verse of every matn just to show a card. Acceptable on the details screen where
    verses are already loaded, but a single derivation path (SQL) is cleaner and DRY.
  - *Persisting totals on the matn row* — rejected: contradicts the "derived, not authoritative"
    assumption and would need recomputation/invalidation on content reload.

## Decision 7 — Native RTL layout

- **Decision**: Force **right-to-left** for all Phase 1 screens by providing
  `LocalLayoutDirection = LayoutDirection.Rtl` at the app root (independent of device locale, so
  even an English-locale device renders the Arabic reading UI RTL, FR-010/SC-005). Rely on
  Compose's RTL-aware `start`/`end` paddings, `Arrangement`, and text alignment; never hard-code
  `left`/`right`.
- **Rationale**: FR-010 requires a fully native RTL layout for reading direction, text
  alignment, and element placement, with zero LTR regressions (SC-005). Providing the layout
  direction at the root is the idiomatic Compose approach and keeps every child RTL without
  per-component flags. Arabic text runs render RTL via the platform bidi algorithm; mixed
  Arabic/Latin/numeric content is handled by the Unicode bidi order (edge case).
- **Alternatives considered**: *Per-screen RTL wrappers* — rejected: repetitive and easy to
  miss a screen (regression risk). *Relying on device locale to pick RTL* — rejected: the
  reading UI must be RTL even when the interface chrome is English.

## Decision 8 — Large-list performance & cover images

- **Decision**: Render the verse list and library grid with **`LazyColumn`/`LazyVerticalGrid`**
  keyed by stable entity `id`, holding immutable item state so only visible rows compose
  (FR-011/SC-003). Cover images are **local bundled resources** referenced by
  `coverImageRef`; a missing/absent ref renders a shared placeholder composable. No network
  image loading (FR-018/SC-006).
- **Rationale**: Lazy layouts with stable keys give smooth scrolling at 500 verses without
  loading the whole list into the composition. Keeping cover images local honors the offline
  guarantee and the missing-cover edge case (SC-008) with a deterministic placeholder.
- **Alternatives considered**: *Eager `Column` of all verses* — rejected: composes all 500 rows
  up front, defeating SC-003. *A network image loader (Coil/Kamel)* — rejected: unnecessary and
  would violate the zero-network requirement; covers are bundled assets in this phase.

---

## Resolved unknowns summary

| Unknown | Resolution |
|---------|-----------|
| Presentation architecture | Compose → ViewModel(`BaseViewModel`) → use case → repo; `StateFlow` state; new `UseCase`/`FlowUseCase` base |
| Font-size persistence | Domain `ReadingPreferencesRepository` over a SQLDelight `app_setting` key/value row (no new dependency) |
| Interface localization | Compose string resources; **Arabic base** + English (`values-en`) override → Arabic fallback |
| Arabic typeface | Bundled **Amiri** (OFL); Scheherazade New as fallback |
| Navigation | First-party CMP Navigation (`navigation-compose`), routes `home` + `matn/{matnId}` |
| Derived totals | SQLDelight aggregate `COUNT`/`SUM` queries → `MatnSummary` (no schema change) |
| RTL | `LocalLayoutDirection = Rtl` at root; RTL-aware `start/end` throughout |
| Large lists / covers | `LazyColumn`/`LazyVerticalGrid` keyed by id; local cover resource + placeholder, no network |

All Technical Context items are resolved; no open clarifications block Phase 1 design.

## New dependencies (justified per constitution)

- `org.jetbrains.androidx.navigation:navigation-compose` **2.9.2** (matches CMP 1.11.0) —
  first-party CMP navigation for a growing multi-screen app (Decision 5). Simpler alternative
  (state-based nav) documented and rejected for lack of back stack / args / saved state.
- **Amiri font asset** (OFL) — bundled reading typeface (Decision 4); an asset, not a code
  dependency.

No other new libraries: SQLDelight, coroutines/Flow, Koin, Compose Multiplatform, and Compose
resources are all already present from Phase 0.
