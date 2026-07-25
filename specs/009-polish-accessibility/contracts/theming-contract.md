# Contract: Theming, Launch Surface & Appearance Settings

**Feature**: `specs/009-polish-accessibility` | Covers **US1** (FR-001 – FR-006)

---

## 1. Domain seam

```kotlin
// domain/repository/AppearancePreferencesRepository.kt
interface AppearancePreferencesRepository {
    /** Live mode; an absent or unrecognized stored value resolves to ThemeMode.SYSTEM. Never throws. */
    fun observeThemeMode(): Flow<ThemeMode>

    /**
     * Synchronous read of the stored mode, used as the StateFlow's initial value so the first
     * composed frame already carries the student's choice (FR-005, research D12).
     */
    fun themeModeNow(): ThemeMode

    /** Persists immediately (Principle VI) and mirrors the resolved appearance to the platform store. */
    suspend fun setThemeMode(mode: ThemeMode): Resource<Unit>
}
```

Implemented over `app_setting` exactly as `ReadingPreferencesRepositoryImpl` is — `selectSetting` /
`upsertSetting`, `storageCall` for failure mapping, `fromStorageOrDefault` for the fallback.

### 1.1 Platform mirror seam

```kotlin
// domain/appearance/AppearanceMirror.kt
interface AppearanceMirror {
    /** Records the resolved appearance where the platform launch window can read it synchronously. */
    fun write(appearance: Appearance)
}
```

Android → `SharedPreferences`; iOS → `NSUserDefaults`. Contains **no** business logic: it receives an
already-resolved `Appearance` and stores it. Faked in tests.

## 2. Use cases

| Use case | Base | Contract |
|---|---|---|
| `ObserveThemeModeUseCase` | `FlowUseCase` | Emits the current `ThemeMode`; first emission is the synchronous value |
| `GetThemeModeNowUseCase` | plain `operator fun invoke(): ThemeMode` | The synchronous read, for the first composed frame |
| `SetThemeModeUseCase` | `UseCase` | Persists, mirrors, returns `Resource<Unit>` |

`GetThemeModeNowUseCase` exists so the presentation layer never reaches past a use case to a
repository. Principle I states presentation depends on domain **through use cases**; without this,
`AppViewModel` would have to inject `AppearancePreferencesRepository` directly just to obtain the
initial value. It is not `suspend` and returns no `Resource` — the underlying read is a synchronous
SQLDelight query that falls back to `ThemeMode.SYSTEM` rather than failing.

## 3. `MatnTheme`

```kotlin
@Composable
fun MatnTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,   // defaulted — research D12
    reduceMotion: Boolean = false,             // defaulted
    content: @Composable () -> Unit,
)
```

Behaviour, in order:

1. `val systemIsDark = isSystemInDarkTheme()` — the common API confirmed in research D1.
2. `val appearance = themeMode.effectiveAppearance(systemIsDark)` — the single decision point.
3. `MaterialTheme(colorScheme = if (appearance == DARK) MatnDarkColors else MatnLightColors, …)`.
4. Provides `LocalAppearance`, `LocalReduceMotion`, and the existing forced
   `LocalLayoutDirection = Rtl`.
5. Keeps the existing root `Surface` painting `colorScheme.background`.

**Invariants**

- No screen calls `isSystemInDarkTheme()` (FR-006) — the resolution happens once, here.
- No composable outside `theme/` references `MatnLightColors` or `MatnDarkColors` by name.
- Typography, shapes, and spacing are appearance-independent; only the colour scheme swaps.

## 4. Colour scheme parity

`MatnDarkColors` covers the same 34 roles as `MatnLightColors`. Both are constructed from one shared
role list so a role added to one and not the other fails to compile. Values derive from research
D3's tone map and must pass `ColorContrastTest`.

## 5. Appearance setting UI

Added to the **existing** `SettingsScreen`, which Phase 8 created. Placement: **below** the storage
section — SC-008 requires the storage figure to be reachable "without scrolling past unrelated
preferences", so appearance must not displace it.

- A three-option single-choice control: follow the device / always light / always dark (FR-004).
- Selecting applies immediately and persists (FR-004, FR-005).
- `SettingsUiState` gains `themeMode: ThemeMode`; `SettingsViewModel` gains
  `ObserveThemeModeUseCase` + `SetThemeModeUseCase` and an `onThemeModeSelected` intent.
- Existing storage state and behaviour are untouched.

## 6. Launch surface

| | Android | iOS |
|---|---|---|
| Mechanism | `androidx.core:core-splashscreen` (`minSdk 24` < API 31) | Launch storyboard + asset-catalog colour |
| Light/dark source | `values` / `values-night` | Asset catalog Any / Dark appearance |
| Content | Background = `surface` role; brand mark centred | Same |
| Motion | None | None |

- `AndroidManifest.xml:15`'s `@android:style/Theme.Material.Light.NoActionBar` is **replaced** — it
  is the direct cause of the white flash FR-005 forbids.
- `MainActivity` reads the mirrored appearance and calls `setTheme()` **before** `super.onCreate()`.
- The fetched design's 3000 ms fake progress line and "Preparing your workspace…" caption are **not**
  implemented (research D9); the visual treatment is. Record in `design-notes.md`.

**Accepted residual**: for a student whose pinned theme opposes the device, the pre-Activity system
splash follows the device for its duration. Unavoidable within both platforms' launch model; the
`surface` background keeps it a tone difference, not a white flash.

## 7. Previews required

| Preview | Asserts |
|---|---|
| `MatnTheme` light + dark, side by side | Role parity, no unstyled surface |
| Appearance setting — each of the three modes selected | FR-004 |
| Settings screen — dark | Storage section still first (SC-008) |

## 8. Tests (`commonTest`, no device)

| Test | Asserts |
|---|---|
| `ThemeModeTest` | `effectiveAppearance` for all 3 × 2 combinations; `fromStorageOrDefault` on absent / unknown / valid |
| `AppearancePreferencesRepositoryTest` | Round-trip persistence; default on absent; `themeModeNow` agrees with the flow's first emission; mirror written on every change |
| `SettingsViewModelTest` (extend) | Mode selection updates state and calls the use case; storage state unaffected |
| `ColorContrastTest` | See `accessibility-contract.md` — the gate on `MatnDarkColors` |
