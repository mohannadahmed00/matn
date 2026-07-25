---

description: "Task list for Phase 9 — Polish & Accessibility"
---

# Tasks: Polish & Accessibility

**Input**: Design documents from `specs/009-polish-accessibility/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Included. The spec's success criteria are stated as "100% of …", and research D6 settled
that contrast and label coverage are enforced by automated tests. Those tests are not optional here.

**Organization**: Grouped by user story. Each story is independently completable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US5, matching spec.md
- Every task names its exact file path

---

## HOW TO WORK THIS LIST

Read this section once before starting. It exists so no task needs outside context.

### Project layout

| Path prefix | Meaning |
|---|---|
| `shared/src/commonMain/kotlin/com/giraffe/matn/` | Shared Kotlin — domain, data, presentation. **Most work is here.** |
| `shared/src/androidMain/kotlin/com/giraffe/matn/` | Android-only implementations |
| `shared/src/iosMain/kotlin/com/giraffe/matn/` | iOS-only implementations |
| `shared/src/commonTest/kotlin/com/giraffe/matn/` | Shared tests |
| `shared/src/commonMain/composeResources/values{,-en}/strings.xml` | Arabic (default) and English strings |
| `androidApp/src/main/` | Android app shell, manifest, resources |
| `iosApp/iosApp/` | iOS app shell |

### Five rules that must never be broken

1. **NEVER put an animation on the audio playback path.** Do not add any `animate*`, `Animatable`,
   or `delay` call inside `playback/PlaybackController.kt`, or in any function it calls during a
   verse transition. Verse-to-verse audio must stay gapless (FR-035). This is the single most
   important constraint in this phase.
2. **NEVER write a raw colour hex, `.dp`, or `.sp` literal in a screen or component file.** Use
   `MaterialTheme.colorScheme.*`, `MatnSpacing.*`, `MatnShapes.*`, `MatnMotion.*`. The only files
   allowed to contain colour hex literals are `presentation/theme/Color.kt` and
   `presentation/theme/Motion.kt` (durations).
3. **NEVER add a required parameter to `MatnTheme`.** All new parameters must have defaults, or all
   88 existing `@Preview`s break (research D12).
4. **NEVER move the storage section off the top of `SettingsScreen`.** SC-008 requires the storage
   figure be reachable without scrolling past other preferences. New settings rows go **below** it.
5. **NEVER request a permission during onboarding or at app launch.** The only permission request is
   at first playback, behind a rationale (FR-021).

### Verify your work

- After any task touching `shared/`: `./gradlew :shared:compileKotlinIosArm64` (fastest full check),
  or `./gradlew :shared:allTests` when the task adds or changes a test.
- After any task touching `androidApp/`: `./gradlew :androidApp:assembleDebug`.
- Full check before a checkpoint: `./gradlew :shared:allTests`.

### Patterns to copy (do not invent new ones)

| When you need to write… | Copy the structure of… |
|---|---|
| A repository interface | `domain/repository/ReadingPreferencesRepository.kt` |
| A repository implementation over `app_setting` | `data/repository/ReadingPreferencesRepositoryImpl.kt` |
| A one-shot use case | `domain/usecase/SetFontSizeUseCase.kt` |
| An observing use case | `domain/usecase/GetFontSizeUseCase.kt` |
| A ViewModel | `presentation/settings/SettingsViewModel.kt` |
| A screen (stateless content + thin holder) | `presentation/settings/SettingsScreen.kt` |
| A platform seam interface | `domain/delivery/DeviceStorage.kt` |
| An enum with a storage fallback | `domain/model/ReadingFontSize.kt` |
| A repository test | `shared/src/commonTest/.../data/PersistentRepetitionSettingsStoreTest.kt` |
| A ViewModel test | `shared/src/commonTest/.../presentation/SettingsViewModelTest.kt` |

---

## Phase 1: Setup

**Purpose**: Add the one new dependency this phase needs.

- [X] T001 In `gradle/libs.versions.toml`, add `androidx-splashscreen = "1.0.1"` under `[versions]`, and `androidx-core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "androidx-splashscreen" }` under `[libraries]`
- [X] T002 In `androidApp/build.gradle.kts`, add `implementation(libs.androidx.core.splashscreen)` to the `dependencies { }` block
- [X] T003 Run `./gradlew :androidApp:assembleDebug` and confirm it succeeds before continuing (deferred to user — see build-verification note)

**Checkpoint**: Dependency resolves; build is green.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain types, preference storage, platform seams, and token files that every user story
below depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Domain enums

- [X] T004 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/ThemeMode.kt` — `enum class ThemeMode { SYSTEM, LIGHT, DARK }` with `companion object { val DEFAULT = SYSTEM; fun fromStorageOrDefault(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: DEFAULT }`. Copy the exact structure of `domain/model/ReadingFontSize.kt`
- [X] T005 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/Appearance.kt` — `enum class Appearance { LIGHT, DARK }`, plus a top-level `fun ThemeMode.effectiveAppearance(systemIsDark: Boolean): Appearance = when (this) { ThemeMode.LIGHT -> Appearance.LIGHT; ThemeMode.DARK -> Appearance.DARK; ThemeMode.SYSTEM -> if (systemIsDark) Appearance.DARK else Appearance.LIGHT }`
- [X] T006 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/OnboardingStatus.kt` — `enum class OnboardingStatus { NOT_COMPLETED, COMPLETED }`
- [X] T007 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/PermissionStatus.kt` — `enum class PermissionStatus { NOT_DETERMINED, GRANTED, DENIED, PERMANENTLY_DENIED }`

### Platform seam interfaces (interfaces only — implementations come later)

- [X] T008 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/appearance/AppearanceMirror.kt` — `interface AppearanceMirror { fun write(appearance: Appearance) }`. KDoc: records the resolved appearance where the platform launch window can read it synchronously (research D9)
- [X] T009 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/permission/NotificationPermission.kt` — `interface NotificationPermission { suspend fun status(): PermissionStatus; suspend fun request(): PermissionStatus; fun openSystemSettings() }`
- [X] T010 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/preferences/MotionPreferences.kt` — `interface MotionPreferences { fun observeReduceMotion(): Flow<Boolean> }`

### Preference repositories

- [X] T011 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/repository/AppearancePreferencesRepository.kt` — `fun observeThemeMode(): Flow<ThemeMode>`, `fun themeModeNow(): ThemeMode`, `suspend fun setThemeMode(mode: ThemeMode): Resource<Unit>`. See `contracts/theming-contract.md` §1
- [X] T012 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/repository/OnboardingRepository.kt` — `fun observeStatus(): Flow<OnboardingStatus>`, `fun statusNow(): OnboardingStatus`, `suspend fun markCompleted(): Resource<Unit>`
- [X] T013 Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/repository/AppearancePreferencesRepositoryImpl.kt` over the **existing** `app_setting` table, key `"theme_mode"`. Copy `ReadingPreferencesRepositoryImpl.kt` line for line and change the key, type, and accessor. `themeModeNow()` uses `db.contentQueries.selectSetting("theme_mode").executeAsOneOrNull()` (synchronous). `setThemeMode` wraps `upsertSetting` in `storageCall { }`. **Do not create a table or migration** — `app_setting` already exists (`Content.sq:56`)
- [X] T014 Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/repository/OnboardingRepositoryImpl.kt`, same pattern, key `"onboarding_completed"`, values `"true"` / `"false"`, absent → `NOT_COMPLETED`

### Use cases

- [X] T015 [P] Create three files in `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/`: `ObserveThemeModeUseCase.kt` and `SetThemeModeUseCase.kt` matching `GetFontSizeUseCase.kt` / `SetFontSizeUseCase.kt` exactly, plus `GetThemeModeNowUseCase.kt` — a plain `operator fun invoke(): ThemeMode` delegating to `AppearancePreferencesRepository.themeModeNow()`, not `suspend` and returning no `Resource`. It exists so the presentation layer never injects a repository directly (Principle I); see `contracts/theming-contract.md` §2
- [X] T016 [P] Create three files in the same package: `ObserveOnboardingStatusUseCase.kt`, `CompleteOnboardingUseCase.kt`, and `GetOnboardingStatusNowUseCase.kt` — a plain `operator fun invoke(): OnboardingStatus` delegating to `OnboardingRepository.statusNow()`. See `contracts/onboarding-permissions-contract.md` §1.1

### Token files

- [X] T017 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/Motion.kt` — `object MatnMotion { const val durationShort = 150; const val durationMedium = 250; const val durationLong = 400; val easingStandard = FastOutSlowInEasing; val easingEmphasized = FastOutSlowInEasing; val easingExit = FastOutLinearInEasing }`, plus `val LocalReduceMotion = staticCompositionLocalOf { false }`
- [X] T018 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/WindowSize.kt` — `enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }`; `fun widthClassFor(width: Dp): WindowWidthClass = when { width < 600.dp -> COMPACT; width < 840.dp -> MEDIUM; else -> EXPANDED }`; `val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthClass.COMPACT }`
- [X] T019 In `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/Spacing.kt`, add to `MatnSpacing`: `fun horizontalMargin(w: WindowWidthClass): Dp = if (w == WindowWidthClass.COMPACT) marginMobile else marginDesktop`; `val readingMaxWidth: Dp = 640.dp`; `val surfaceMaxWidth: Dp = 480.dp` (control bars, sheets, dialogs — deliberately narrower than the reading measure, see `contracts/adaptive-motion-contract.md` §A2); `fun libraryColumns(w: WindowWidthClass): Int = when (w) { COMPACT -> 2; MEDIUM -> 3; EXPANDED -> 4 }`. Keep the four existing values unchanged

### Theme wiring

- [X] T020 In `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/MatnTheme.kt`, change the signature to `fun MatnTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, reduceMotion: Boolean = false, content: @Composable () -> Unit)`. Inside: `val appearance = themeMode.effectiveAppearance(isSystemInDarkTheme())` (import `androidx.compose.foundation.isSystemInDarkTheme`), pick the scheme with `if (appearance == Appearance.DARK) MatnDarkColors else MatnLightColors`, and add `LocalReduceMotion provides reduceMotion` to the existing `CompositionLocalProvider`. **Both parameters MUST keep their defaults** (rule 3). `MatnDarkColors` does not exist yet — for this task only, temporarily reference `MatnLightColors` for both branches and leave a `// TODO(T037)` comment

### Strings (both locales — add to BOTH files, same order, same names)

- [X] T021 Add these entries to `shared/src/commonMain/composeResources/values/strings.xml` (Arabic) and `shared/src/commonMain/composeResources/values-en/strings.xml` (English). Keep both files at an identical `<string name=...>` set — `A11yLabelCatalogTest` (T056) checks this.

  | name | Arabic (`values/`) | English (`values-en/`) |
  |---|---|---|
  | `settings_appearance` | `المظهر` | `Appearance` |
  | `theme_system` | `حسب النظام` | `Follow the device` |
  | `theme_light` | `فاتح دائماً` | `Always light` |
  | `theme_dark` | `داكن دائماً` | `Always dark` |
  | `settings_permissions` | `الأذونات` | `Permissions` |
  | `permission_notifications` | `الإشعارات` | `Notifications` |
  | `permission_not_requested` | `لم يُطلب بعد` | `Not requested yet` |
  | `permission_granted` | `مُفعَّل` | `Granted` |
  | `permission_denied` | `مرفوض` | `Declined` |
  | `permission_open_settings` | `فتح إعدادات النظام` | `Open device settings` |
  | `permission_rationale_title` | `إشعار التشغيل` | `Playback notification` |
  | `permission_rationale_body` | `يسمح لك بالتحكم في التشغيل من شاشة القفل. يمكنك الرفض والاستمرار في الاستماع كالمعتاد.` | `Lets you control playback from the lock screen. You can decline and keep listening as normal.` |
  | `onboarding_skip` | `تخطٍّ` | `Skip` |
  | `onboarding_continue` | `متابعة` | `Continue` |
  | `onboarding_start` | `ابدأ` | `Get started` |
  | `onboarding_p1_tagline` | `سَكينةٌ لطالبِ العلم` | `Sakinah for seekers` |
  | `onboarding_p2_title` | `رافقك في الحفظ` | `Your memorization companion` |
  | `onboarding_p2_body` | `اقرأ، واستمع لتسجيل شيخك، وكرِّر حتى يثبت المتن.` | `Read, listen to your teacher's recitation, and repeat until the matn holds.` |
  | `onboarding_p3_title` | `كيف يعمل المحتوى` | `How content works` |
  | `onboarding_p3_body` | `يأتي التطبيق بمتنٍ جاهز. وبقية المتون تُنزَّل عند طلبك، ثم تعمل بدون إنترنت إلى الأبد.` | `The app ships with one matn ready to go. Other متون install when you ask, then work offline forever.` |
  | `onboarding_reopen` | `إعادة عرض التعريف` | `Show the introduction again` |
  | `a11y_close` | `إغلاق` | `Close` |
  | `a11y_retry` | `إعادة المحاولة` | `Retry` |
  | `a11y_state_bookmarked` | `معلَّم` | `Bookmarked` |
  | `a11y_state_not_bookmarked` | `غير معلَّم` | `Not bookmarked` |
  | `a11y_state_memorized` | `محفوظ` | `Memorized` |
  | `a11y_state_not_memorized` | `غير محفوظ` | `Not memorized` |
  | `a11y_state_installed` | `مُنزَّل` | `Installed` |
  | `a11y_state_installing` | `قيد التنزيل` | `Installing` |
  | `a11y_state_not_installed` | `غير مُنزَّل` | `Not installed` |

### Platform implementations

- [X] T022 [P] Create `shared/src/androidMain/kotlin/com/giraffe/matn/appearance/AndroidAppearanceMirror.kt` — takes `Context`, writes `appearance.name` to `SharedPreferences` file `"matn_appearance"` under key `"appearance"`
- [X] T023 [P] Create `shared/src/androidMain/kotlin/com/giraffe/matn/permission/AndroidNotificationPermission.kt`. `status()` returns `GRANTED` when `Build.VERSION.SDK_INT < 33`; otherwise maps `ContextCompat.checkSelfPermission(POST_NOTIFICATIONS)`. `request()` suspends on an `ActivityResultLauncher` registered by `MainActivity`. `openSystemSettings()` starts `Settings.ACTION_APP_NOTIFICATION_SETTINGS`
- [X] T024 [P] Create `shared/src/androidMain/kotlin/com/giraffe/matn/preferences/AndroidMotionPreferences.kt` — `observeReduceMotion()` emits `Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f`, re-emitting via a `ContentObserver` in a `callbackFlow`
- [X] T025 [P] Create `shared/src/iosMain/kotlin/com/giraffe/matn/appearance/IosAppearanceMirror.kt` — writes to `NSUserDefaults.standardUserDefaults` key `"matn_appearance"`
- [X] T026 [P] Create `shared/src/iosMain/kotlin/com/giraffe/matn/permission/IosNotificationPermission.kt` — wraps `UNUserNotificationCenter.currentNotificationCenter()`: `getNotificationSettingsWithCompletionHandler` for `status()`, `requestAuthorizationWithOptions` for `request()`, and `UIApplication.sharedApplication.openURL(UIApplicationOpenSettingsURLString)` for `openSystemSettings()`
- [X] T027 [P] Create `shared/src/iosMain/kotlin/com/giraffe/matn/preferences/IosMotionPreferences.kt` — `UIAccessibilityIsReduceMotionEnabled()`, re-emitting on `UIAccessibilityReduceMotionStatusDidChangeNotification`

### Dependency injection and app shells

- [X] T028 Register everything in `shared/src/commonMain/kotlin/com/giraffe/matn/di/ContentModule.kt` (both repositories and all four use cases from T015–T016) and widen `di/MatnKoinStarter.kt`'s `initMatnKoin(...)` to accept `appearanceMirror: AppearanceMirror`, `notificationPermission: NotificationPermission`, `motionPreferences: MotionPreferences` — following exactly how `deliveryEngine` and `deviceStorage` are already threaded through
- [X] T029 Update `androidApp/src/main/kotlin/com/giraffe/matn/MainActivity.kt`: pass the three new Android implementations into `initMatnKoin(...)`; **delete** `requestPostNotificationsIfNeeded()`, its call on line 41, and the now-unused `requestPermissionLauncher` field (rule 5). Register a launcher that `AndroidNotificationPermission` can await instead
- [X] T030 Update `shared/src/iosMain/kotlin/com/giraffe/matn/MainViewController.kt` to pass the three new iOS implementations into `initMatnKoin(...)`

### Test doubles and foundational tests

- [X] T031 [P] Create fakes in `shared/src/commonTest/kotlin/com/giraffe/matn/`: `appearance/FakeAppearanceMirror.kt` (records the last written value), `permission/FakeNotificationPermission.kt` (scriptable `status`/`request` results, counts `request()` calls), `preferences/FakeMotionPreferences.kt` (a `MutableStateFlow<Boolean>`)
- [X] T032 [P] Create `shared/src/commonTest/kotlin/com/giraffe/matn/theme/ThemeModeTest.kt` — assert `effectiveAppearance` for all 6 combinations (3 modes × systemIsDark true/false), and `fromStorageOrDefault` for `null`, `""`, `"NONSENSE"`, `"DARK"`
- [X] T033 [P] Create `shared/src/commonTest/kotlin/com/giraffe/matn/theme/WindowWidthClassTest.kt` — assert `widthClassFor` at 319, 320, 599, 600, 839, 840, 1280 dp
- [X] T034 [P] Create `shared/src/commonTest/kotlin/com/giraffe/matn/presentation/MatnSpacingTest.kt` — assert `horizontalMargin` and `libraryColumns` for all three classes, that `libraryColumns(EXPANDED) >= 2 * libraryColumns(COMPACT)` (SC-011), and that `surfaceMaxWidth < readingMaxWidth` (the invariant behind FR-029 — a control bar must not stretch to a reading measure)
- [X] T035 [P] Create `shared/src/commonTest/kotlin/com/giraffe/matn/data/AppearancePreferencesRepositoryTest.kt` and `OnboardingRepositoryTest.kt` using the in-memory driver from `TestDatabase.kt`: default when absent, round-trip persistence, `themeModeNow()` agrees with the flow's first emission
- [X] T036 Run `./gradlew :shared:allTests` — all new and pre-existing tests must pass

**Checkpoint**: Foundation ready. Every user story below can now begin.

---

## Phase 3: User Story 1 — Dark mode (Priority: P1) 🎯 MVP

**Goal**: A complete dark theme on every screen, following the device by default with an explicit
override in Settings, applied before the first frame with no light flash.

**Independent Test**: Set the device to dark, walk every screen and state, confirm nothing renders
light-theme colours; pin light and dark from Settings and confirm the choice survives a restart.

### The dark palette and its gate

- [X] T037 [US1] In `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/Color.kt`, add the dark role constants and `val MatnDarkColors: ColorScheme = darkColorScheme(...)` mapping all 34 roles, using **exactly these verified values** (derived per research D3 and pre-checked against the contrast table — do not substitute your own):

  ```
  primary #B6CCBA          onPrimary #233427        primaryContainer #384B3C   onPrimaryContainer #D2E8D6
  inversePrimary #506354
  secondary #D7C4A5        onSecondary #3B2F16      secondaryContainer #53452B onSecondaryContainer #F3E0C1
  tertiary #C8C6C1         onTertiary #31302C       tertiaryContainer #484742  onTertiaryContainer #E4E2DC
  background #121414       onBackground #E1E3E3     surface #121414            onSurface #E1E3E3
  surfaceVariant #434843   onSurfaceVariant #C2C8C2 surfaceTint #B6CCBA
  inverseSurface #E1E3E3   inverseOnSurface #2F3131
  outline #8C928C          outlineVariant #434843
  error #FCB5A7            onError #660806          errorContainer #900B0F     onErrorContainer #F9DCD6
  surfaceDim #121414       surfaceBright #383939
  surfaceContainerLowest #0D0E0E  surfaceContainerLow #1B1C1C  surfaceContainer #1F2020
  surfaceContainerHigh #292A2A    surfaceContainerHighest #343535
  ```

  Name the constants `DarkPrimary`, `DarkOnPrimary`, … to avoid clashing with the existing light names.

  Also add a comment above `Surface`/`DarkSurface` recording that these two values are **mirrored in
  two places the Kotlin layer cannot reach** — `androidApp/src/main/res/values{,-night}/themes.xml`
  (T047) and `iosApp/iosApp/Assets.xcassets` `LaunchBackground` (T050) — and that all three must be
  changed together. The platform launch window is selected before any Kotlin runs, so this
  duplication is unavoidable; the comment is the drift guard (T105 re-checks it).
- [X] T038 [US1] Remove the `// TODO(T037)` placeholder from `presentation/theme/MatnTheme.kt` (added in T020) so the dark branch uses `MatnDarkColors`
- [X] T039 [US1] Create `shared/src/commonTest/kotlin/com/giraffe/matn/theme/ContrastRatio.kt` — a test-only helper with `fun contrastRatio(a: Color, b: Color): Double` implementing the WCAG 2.1 formulas exactly as written in `contracts/accessibility-contract.md` §1.1
- [X] T040 [US1] Create `shared/src/commonTest/kotlin/com/giraffe/matn/theme/ColorContrastTest.kt` — assert every one of the 24 pairs in `contracts/accessibility-contract.md` §1.2 against **both** `MatnLightColors` and `MatnDarkColors` (48 assertions). Failure messages must name the pair, the scheme, and the measured ratio. **Do not add an `outlineVariant`-on-`surface` assertion** — §1.2a explains why that role is deliberately not gated

### Applying the theme at the root

- [X] T041 [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/AppViewModel.kt` extending `BaseViewModel`, exposing `data class AppUiState(val themeMode: ThemeMode, val reduceMotion: Boolean)`. Its **initial** state uses `GetThemeModeNowUseCase` (from T015) so the first frame is already correct (research D12); it then collects `ObserveThemeModeUseCase` and `MotionPreferences.observeReduceMotion()`. Inject the **use cases**, never `AppearancePreferencesRepository` directly — Principle I routes presentation to domain through use cases
- [X] T042 [US1] Update `shared/src/commonMain/kotlin/com/giraffe/matn/App.kt` to build `AppViewModel` via `viewModel { }` from `MatnKoinHolder.koin` (copy how `MatnNavHost.kt` does it), collect its state, and pass `themeMode` and `reduceMotion` into `MatnTheme(...)`

### Appearance setting in Settings

- [X] T043 [US1] In `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/settings/SettingsUiState.kt`, add `val themeMode: ThemeMode = ThemeMode.SYSTEM`. Change nothing else
- [X] T044 [US1] In `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/settings/SettingsViewModel.kt`, inject `ObserveThemeModeUseCase` and `SetThemeModeUseCase`, collect the mode into state, and add `fun onThemeModeSelected(mode: ThemeMode)`. Leave all existing storage behaviour untouched
- [X] T045 [US1] In `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/settings/SettingsScreen.kt`, add an "Appearance" section with three selectable rows (`theme_system` / `theme_light` / `theme_dark`) **below the existing storage section** (rule 4). Use `RadioButton` + `Text` inside a `Row` with `Modifier.selectable(...)`
- [X] T046 [US1] In `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/navigation/MatnNavHost.kt`, add the two new use cases to the `SettingsViewModel { }` construction in the `Routes.SETTINGS` composable

### Launch surface (no light flash)

- [X] T047 [P] [US1] Create `androidApp/src/main/res/values/themes.xml` and `androidApp/src/main/res/values-night/themes.xml`, each defining `Theme.Matn.Splash` with parent `Theme.SplashScreen`, `windowSplashScreenBackground` set to the `surface` role for that appearance (`#FCF9F8` light, `#121414` dark), `windowSplashScreenAnimatedIcon` pointing at the launcher icon, and `postSplashScreenTheme` set to a plain no-action-bar theme. **No animation** (research D9)
- [X] T048 [US1] In `androidApp/src/main/AndroidManifest.xml`, replace `android:theme="@android:style/Theme.Material.Light.NoActionBar"` on `<application>` (line 15) with `android:theme="@style/Theme.Matn.Splash"`. This hard-coded light theme is the direct cause of the white flash FR-005 forbids
- [X] T049 [US1] In `androidApp/src/main/kotlin/com/giraffe/matn/MainActivity.kt`, call `installSplashScreen()` before `super.onCreate(...)`, then read the mirrored appearance from `SharedPreferences` file `"matn_appearance"` key `"appearance"` and call `setTheme(...)` for the matching post-splash theme
- [X] T050 [P] [US1] In `iosApp/iosApp/Assets.xcassets`, add a colour set `LaunchBackground` with an Any appearance of `#FCF9F8` and a Dark appearance of `#121414`; point `iosApp/iosApp/LaunchScreen.storyboard` (create if absent) at it, with the brand mark centred and no animation

### Previews and tests

- [X] T051 [P] [US1] Add `@Preview`s to `presentation/theme/MatnTheme.kt` rendering a swatch sheet of every colour role, once with `themeMode = ThemeMode.LIGHT` and once with `ThemeMode.DARK`
- [X] T052 [P] [US1] Add dark `@Preview`s (`themeMode = ThemeMode.DARK`) to the existing content composables in `presentation/settings/SettingsScreen.kt`, `presentation/home/HomeScreen.kt`, `presentation/details/MatnDetailsScreen.kt`, `presentation/player/ReadingCarousel.kt`, `presentation/player/PlayerBar.kt`, `presentation/search/SearchScreen.kt`, `presentation/notes/NotesTabScreen.kt`, `presentation/goals/GoalsScreen.kt` — one per file, beside the existing previews
- [X] T053 [US1] Extend `shared/src/commonTest/kotlin/com/giraffe/matn/presentation/SettingsViewModelTest.kt`: selecting a mode updates state and calls the use case; existing storage assertions still pass
- [ ] T054 [US1] Run `./gradlew :shared:allTests` and `./gradlew :androidApp:assembleDebug`; both green

**Checkpoint**: US1 complete. Dark mode works everywhere, persists, and cold launch shows no light
flash. This is a shippable MVP on its own.

---

## Phase 4: User Story 2 — Accessibility (Priority: P2)

**Goal**: AA contrast in both themes, a screen-reader label and state on every control, 48dp touch
targets, and no clipping at the largest font scale.

**Independent Test**: Walk the app with TalkBack/VoiceOver hitting no unlabeled control; set the
system font scale to 200% on a 320dp screen and find no clipping.

### Label catalogue and the shared action button

- [X] T055 [US2] Create `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/common/A11yLabels.kt` — the `A11yAction` enum (30 members) and `val A11yLabels: Map<A11yAction, StringResource>`. **Transcribe the mapping table in `contracts/accessibility-contract.md` §2.1 exactly** — do not invent members or pick your own resources. 26 of the 30 resources already exist in `strings.xml`; the other 4 (`a11y_close`, `a11y_retry`, `onboarding_skip`, `onboarding_continue`) come from T021. Note §2.1a: bookmark, note, and memorized are **single toggle actions** (`TOGGLE_BOOKMARK` → `toggle_bookmark`, etc.), **not** add/remove pairs — the add/remove distinction is carried by `stateDescription` in T061, so the control's name never changes under a screen reader
- [X] T056 [US2] Create `shared/src/commonTest/kotlin/com/giraffe/matn/presentation/A11yLabelCatalogTest.kt` — assert the map has one entry per `A11yAction` value, no entry is duplicated across two actions, and that `values/strings.xml` and `values-en/strings.xml` declare an identical set of `<string name=…>` (parse both XML files from the resource directory)
- [X] T057 [US2] Create `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/common/IconActionButton.kt` with the signature in `contracts/accessibility-contract.md` §3. It MUST apply `Modifier.minimumInteractiveComponentSize()` and set `contentDescription` from `A11yLabels[action]`. The `action` parameter is **required and non-nullable** — do not add a nullable label escape hatch. Add a `@Preview`

### Migrating call sites (exact list — nothing to search for)

- [X] T058 [US2] Replace these three icon-only `Modifier.clickable` sites with `IconActionButton`: `presentation/common/ContinueLearningCard.kt:91` (`A11yAction.CLOSE`), `presentation/details/MatnDetailsScreen.kt:620` (`A11yAction.PLAY`), `presentation/player/PlayerBar.kt:296` (the transport button — pass the action through from the caller)
- [X] T059 [US2] Add an `onClickLabel` to the eight remaining `Modifier.clickable` sites so each announces its purpose: `common/ContinueLearningCard.kt:70` and `:123`, `common/MatnCard.kt:52`, `details/TableOfContents.kt:69`, `notes/AnnotationRows.kt:53` and `:65`, `search/SearchResultRow.kt:40`, `player/PlayerBar.kt:333`. These are content rows — do **not** convert them to `IconActionButton`
- [X] T060 [US2] In `presentation/player/PlayerBar.kt:330` (`SpeedPill`), change the `BorderStroke` colour from `scheme.outlineVariant` to `scheme.outline`. That border is the only affordance identifying a clickable control, so it must meet 3:1 — see `contracts/accessibility-contract.md` §1.2a. **Leave all 13 other `outlineVariant` usages alone**; they are decorative and deliberately not gated

### State announcement and decorative hiding

- [X] T061 [P] [US2] Add `stateDescription` (via `Modifier.semantics { stateDescription = … }`) to every toggle, using the `a11y_state_*` strings from T021: bookmark toggles in `presentation/notes/AnnotationRows.kt` and `presentation/player/ReadingCarousel.kt`; the memorized toggle in `presentation/details/MatnDetailsScreen.kt`; the availability badge in `presentation/common/ContentAvailabilityBadge.kt`; play/pause in `presentation/player/PlayerBar.kt`
- [X] T062 [P] [US2] Set `contentDescription = null` on decorative imagery so screen readers skip it (FR-010): the cover artwork in `presentation/common/CoverImage.kt` when a real cover is present (keep the existing `cover_placeholder_desc` for the placeholder), and every purely ornamental `Canvas`/divider in `presentation/player/ReadingCarousel.kt`
- [X] T063 [US2] Audit screen-reader traversal order (FR-012) on each of these eight files by reading their layout order against the forced RTL direction: `presentation/home/HomeScreen.kt`, `presentation/details/MatnDetailsScreen.kt`, `presentation/player/ReadingCarousel.kt`, `presentation/player/PlayerBar.kt`, `presentation/search/SearchScreen.kt`, `presentation/notes/NotesTabScreen.kt`, `presentation/goals/GoalsScreen.kt`, `presentation/settings/SettingsScreen.kt`. Where the composition order does **not** match the visual right-to-left order, apply `Modifier.semantics { isTraversalGroup = true }` on the container and `traversalIndex` on the children to correct it. Apply it **only** where there is a genuine mismatch — a blanket `traversalIndex` sweep makes focus order harder to reason about, not easier. Record which screens needed it (expected: few or none, since the layout is RTL end to end)
- [X] T064 [US2] In `presentation/player/PlayerBar.kt`, ensure the scrub position is **not** announced on every tick — apply `Modifier.clearAndSetSemantics { }` to the progress readout and keep semantics only on the discrete transport controls. A screen reader must not talk over the recitation

### Non-colour status signals (FR-016)

- [X] T065 [P] [US2] Verify each of these conveys status by more than colour, adding a glyph or text where it does not: `presentation/common/ContentAvailabilityBadge.kt`, `presentation/common/InstallProgressIndicator.kt` (add a percentage text), `presentation/common/MatnProgressBar.kt`, `presentation/common/DailyGoalRing.kt` (already renders "4/10" — verify only), the memorized toggle in `presentation/details/MatnDetailsScreen.kt`, and the selected tab in `presentation/navigation/MatnNavHost.kt`

### Font scaling

- [X] T066 [US2] Run `grep -rn "fontScale\|LocalDensity" shared/src/commonMain/kotlin/com/giraffe/matn/` and confirm nothing provides or overrides `LocalDensity`/`fontScale` — the app must never clamp the student's scale (FR-014). If any override exists, remove it. Expected result today: no matches outside `presentation/theme/FontScale.kt`, which maps `ReadingFontSize` to `sp` and is correct
- [X] T067 [P] [US2] Add `@Preview(fontScale = 2.0f, widthDp = 320)` to the content composable in each of these eight files: `presentation/home/HomeScreen.kt`, `presentation/details/MatnDetailsScreen.kt`, `presentation/player/ReadingCarousel.kt`, `presentation/player/PlayerBar.kt`, `presentation/search/SearchScreen.kt`, `presentation/notes/NotesTabScreen.kt`, `presentation/goals/GoalsScreen.kt`, `presentation/settings/SettingsScreen.kt`. Fix any clipping, truncation, or overlap the previews reveal — `ReadingCarousel.kt` at `ReadingFontSize.XLARGE` × 2.0 is the binding case. Additionally add `@Preview(fontScale = 0.8f)` with `ReadingFontSize.SMALL` to `ReadingCarousel.kt` only, and confirm verse text is still legible at that minimum combination (~14.4sp) — FR-015 covers both ends of the range, not just the top
- [ ] T068 [US2] Run `./gradlew :shared:allTests`; `ColorContrastTest` and `A11yLabelCatalogTest` must both pass

**Checkpoint**: US2 complete. Contrast is machine-verified in both themes, every control is labeled,
and no screen clips at the largest scale.

---

## Phase 5: User Story 3 — Onboarding & permissions (Priority: P3)

**Goal**: A three-panel first-launch flow that explains the download model and requests nothing; the
notification permission moves to first playback behind a rationale, asked at most once.

**Independent Test**: Fresh install → onboarding appears once, requests no permission, is skippable;
first playback shows the rationale; declining leaves playback working and never re-prompts.

### Onboarding screen

- [X] T069 [P] [US3] Create `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/onboarding/OnboardingUiState.kt` — `data class OnboardingUiState(val panelIndex: Int = 0)` with `val isLastPanel: Boolean get() = panelIndex == 2`
- [X] T070 [US3] Create `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/onboarding/OnboardingViewModel.kt` extending `BaseViewModel`, injecting `CompleteOnboardingUseCase`, with `onNext()`, `onSkip()`, and `onFinish()`. Both `onSkip()` and `onFinish()` call the use case — skipping and finishing produce identical state (FR-018)
- [X] T071 [US3] Create `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/onboarding/OnboardingScreen.kt` as a stateless content composable plus a thin stateful holder (Principle II). Three panels using the strings from T021 (`onboarding_p1_tagline`, `onboarding_p2_*`, `onboarding_p3_*`), a Skip action visible on **every** panel, and Continue/Get started. **Request no permission anywhere** (rule 5). No network access — all copy and imagery is bundled

### Navigation

- [X] T072 [US3] In `presentation/navigation/MatnNavHost.kt`: add `const val ONBOARDING = "onboarding"` to `Routes`; add a `composable(Routes.ONBOARDING)` wiring `OnboardingViewModel`; and set `startDestination` to `Routes.ONBOARDING` when `GetOnboardingStatusNowUseCase` (from T016) returns `NOT_COMPLETED`, else `Routes.HOME` — resolve it via `koin.get<GetOnboardingStatusNowUseCase>()`, not by injecting `OnboardingRepository` (Principle I). On completion, navigate to `HOME` with `popUpTo(Routes.ONBOARDING) { inclusive = true }` so system back never returns to onboarding. The existing `showBottomBar` check already excludes this route — leave it alone
- [X] T073 [US3] Add a "Show the introduction again" row (`onboarding_reopen`) to `presentation/settings/SettingsScreen.kt`, below the appearance section, navigating forward to `Routes.ONBOARDING`. Re-opening must **not** clear the completed flag

### Permission gate at first playback

- [X] T074 [US3] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/EnsureNotificationPermissionUseCase.kt` implementing the five-step sequence in `contracts/onboarding-permissions-contract.md` §4, using `NotificationPermission` plus the `app_setting` key `"notification_permission_asked"`. **It must never block or fail playback** — its result only decides whether to show the rationale
- [X] T075 [US3] In `shared/src/commonMain/kotlin/com/giraffe/matn/playback/PlaybackController.kt`, invoke the gate **once inside `startSession` only**. Do not touch `moveToVerse`, `applyCursor`, or any per-verse path (rule 1). Playback must proceed regardless of the permission outcome
- [X] T076 [US3] Add the rationale UI as a bottom sheet in `presentation/player/PlayerBar.kt` (or a shared `presentation/common/PermissionRationaleSheet.kt` if it is needed on more than one screen), using `permission_rationale_title` / `permission_rationale_body`, with Continue and Not now. The system prompt fires only after Continue

### Permission state in Settings

- [X] T077 [US3] Add `val notificationStatus: PermissionStatus = PermissionStatus.NOT_DETERMINED` to `SettingsUiState`, populate it in `SettingsViewModel` (re-reading whenever the screen resumes), and render a permissions row per `contracts/onboarding-permissions-contract.md` §5 in `SettingsScreen.kt` — below the appearance section. `PERMANENTLY_DENIED` shows a button calling `openSystemSettings()`

### Previews and tests

- [X] T078 [P] [US3] Add `@Preview`s in `presentation/onboarding/OnboardingScreen.kt` for all three panels in light (`themeMode = ThemeMode.LIGHT`) and dark (`ThemeMode.DARK`) — six previews — plus light and dark previews of the rationale sheet in whichever file T076 created it in
- [X] T079 [P] [US3] Create `shared/src/commonTest/kotlin/com/giraffe/matn/presentation/OnboardingViewModelTest.kt` — panel advance; skip from each of the three panels completes; completing twice is idempotent; **and the interrupted path (FR-025)**: with `onboarding_completed` still absent after advancing partway, a freshly constructed ViewModel starts at panel 0 and `GetOnboardingStatusNowUseCase` still returns `NOT_COMPLETED`, so the flow restarts rather than stranding the student
- [X] T080 [US3] Create `shared/src/commonTest/kotlin/com/giraffe/matn/permission/NotificationPermissionGateTest.kt` using `FakeNotificationPermission` — assert all five branches of §4, that the asked-flag is set on **every** terminal path including dismissal, that `request()` is called at most once across repeated sessions, and that a `DENIED` result still lets playback proceed
- [X] T081 [US3] Extend `shared/src/commonTest/kotlin/com/giraffe/matn/playback/PlaybackControllerTest.kt` — the gate runs once per session start and never on a verse transition; a denied permission does not prevent a session
- [ ] T082 [US3] Run `./gradlew :shared:allTests`; all green

**Checkpoint**: US3 complete. First launch explains the download model, asks for nothing, and the
permission is requested in context at most once.

---

## Phase 6: User Story 4 — Adaptive layout (Priority: P4)

**Goal**: Layouts follow available window width, so tablets use the space and narrow split-screen
panes get the phone layout.

**Independent Test**: On a tablet in both orientations and in split-screen, column counts and content
widths adapt; rotating during playback preserves the active verse and playback state.

- [X] T083 [US4] In `shared/src/commonMain/kotlin/com/giraffe/matn/App.kt`, wrap the content in `BoxWithConstraints` and provide `LocalWindowWidthClass provides widthClassFor(maxWidth)` (research D8). This file was also edited in T042 — apply this on top of that change
- [X] T084 [P] [US4] In `presentation/home/HomeScreen.kt`, drive the library grid's column count from `MatnSpacing.libraryColumns(LocalWindowWidthClass.current)` and its horizontal padding from `MatnSpacing.horizontalMargin(...)` (FR-027)
- [X] T085 [P] [US4] In `presentation/player/ReadingCarousel.kt`, constrain verse content to `MatnSpacing.readingMaxWidth` and centre it when the available width exceeds that (FR-028)
- [X] T086 [P] [US4] Apply the same `readingMaxWidth`-and-centre treatment to the list content in `presentation/settings/SettingsScreen.kt`, `presentation/goals/GoalsScreen.kt`, `presentation/notes/NotesTabScreen.kt`, and `presentation/search/SearchScreen.kt`
- [X] T087 [P] [US4] In `presentation/player/PlayerBar.kt`, bound the control bar to `MatnSpacing.surfaceMaxWidth` and centre it rather than letting it span a tablet's full width (FR-029). Use the same token as T088 — do not introduce a separate width for the bar
- [X] T088 [P] [US4] Bound the four sheet/dialog surfaces to `MatnSpacing.surfaceMaxWidth` and centre them when the available width exceeds it (FR-029): `presentation/player/RepetitionSetupSheet.kt`, `presentation/notes/NoteEditorSheet.kt`, `presentation/common/InstallPromptSheet.kt`, `presentation/common/ConfirmRemovalDialog.kt`. Use `Modifier.widthIn(max = MatnSpacing.surfaceMaxWidth).align(Alignment.CenterHorizontally)` on each surface's content root — do not add a per-file width constant
- [X] T089 [P] [US4] In `presentation/details/MatnDetailsScreen.kt`, drive the header and verse-list horizontal padding from `MatnSpacing.horizontalMargin(...)`
- [X] T090 [US4] Fix these four state holders so rotation does not lose them (FR-030): change `remember { mutableStateOf(false) }` to `rememberSaveable { mutableStateOf(false) }` at `presentation/details/MatnDetailsScreen.kt:149` (`repetitionSheetOpen`), `:154` (`installPromptOpen`), and `:524` (`expanded`). `rememberLazyListState()` at `:295` is already saveable — leave it. Leave `PlayerBar.kt:186`/`:187` (`scrubbing`, `scrubValue`) as plain `remember`: a drag gesture is inherently cancelled by rotation
- [X] T091 [US4] Check the note-editor text field in `presentation/notes/NoteEditorSheet.kt` — if its draft text lives in a plain `remember`, move it to `rememberSaveable` or into `NotesTabUiState` so typed text survives rotation (FR-030)
- [X] T092 [P] [US4] Add `@Preview(widthDp = 900)` to the content composable in each of these eight files: `presentation/home/HomeScreen.kt`, `presentation/details/MatnDetailsScreen.kt`, `presentation/player/ReadingCarousel.kt`, `presentation/player/PlayerBar.kt`, `presentation/search/SearchScreen.kt`, `presentation/notes/NotesTabScreen.kt`, `presentation/goals/GoalsScreen.kt`, `presentation/settings/SettingsScreen.kt`. Confirm no screen shows a single stretched column
- [ ] T093 [US4] Run `./gradlew :shared:allTests`; `MatnSpacingTest` and `WindowWidthClassTest` must pass

**Checkpoint**: US4 complete. Layouts adapt to width, and state survives rotation and resize.

---

## Phase 7: User Story 5 — Motion (Priority: P5)

**Goal**: One consistent, RTL-correct motion vocabulary, with a reduce-motion mode that removes
movement without removing information — and with audio still gapless.

**Independent Test**: Every transition is smooth and slides toward the RTL edge; enabling reduce
motion stops animation while every value stays readable; 10+ verses play with no audible gap.

- [X] T094 [US5] In `presentation/navigation/MatnNavHost.kt`, set `enterTransition` / `exitTransition` / `popEnterTransition` / `popExitTransition` on the `NavHost` itself (not per `composable`) using `MatnMotion.durationMedium` and `easingStandard`. Forward navigation slides from the RTL start edge; tab changes fade only. Read `LocalReduceMotion.current` and substitute a plain fade when it is true. **Do not add a blocking overlay, scrim, or input-consuming modifier for the transition's duration** (FR-037) — if you need to prevent a double-navigation, use `launchSingleTop` on the navigate call, which the existing bottom-nav handler already does
- [X] T095 [P] [US5] Animate value changes with `animateFloatAsState` / `animateColorAsState` at `MatnMotion.durationShort` in `presentation/common/MatnProgressBar.kt`, `presentation/common/InstallProgressIndicator.kt`, `presentation/common/DailyGoalRing.kt`, and `presentation/common/ContentAvailabilityBadge.kt`. When `LocalReduceMotion.current` is true, snap to the value instead. **Never animate past the true value** (FR-034)
- [X] T096 [P] [US5] Animate the scrub/progress readout in `presentation/player/PlayerBar.kt` at `durationShort`, honouring `LocalReduceMotion`
- [X] T097 [US5] In `presentation/player/ReadingCarousel.kt`, animate the active-verse change at `MatnMotion.durationMedium`. **The animation must be a reaction to a state change, never something the playback path waits on** (rule 1) — do not add any `suspend` animation call reachable from `PlaybackController`
- [X] T098 [P] [US5] Apply `MatnMotion` enter/exit specs to the sheets and dialogs: `presentation/player/RepetitionSetupSheet.kt`, `presentation/notes/NoteEditorSheet.kt`, `presentation/common/InstallPromptSheet.kt`, `presentation/common/ConfirmRemovalDialog.kt`. Fade only under reduce motion
- [X] T099 [P] [US5] Add the onboarding panel reveal in `presentation/onboarding/OnboardingScreen.kt` at `MatnMotion.durationLong`, suppressed entirely under reduce motion. **Do not add a loading progress bar or an artificial delay** — the fetched splash design's 3000ms "Preparing your workspace…" is deliberately not implemented (research D9)
- [X] T100 [US5] Create `shared/src/commonTest/kotlin/com/giraffe/matn/presentation/ReducedMotionSpecTest.kt` — a pure mapping test asserting every row of `contracts/adaptive-motion-contract.md` §B2.1 resolves to its reduced form when the flag is set. No rendering
- [X] T101 [US5] Extend `shared/src/commonTest/kotlin/com/giraffe/matn/playback/PlaybackControllerTest.kt` with an FR-035 regression guard: verse-transition timing is unchanged with motion enabled, and no animation call sits on the transition path
- [ ] T102 [US5] Run `./gradlew :shared:allTests`; all green

**Checkpoint**: US5 complete. All five stories are now independently functional.

---

## Phase 8: Polish & Cross-Cutting

- [X] T103 [P] Create `specs/009-polish-accessibility/design-notes.md` recording: the derived dark palette (the T037 values and the tone rule that produced them), the decision not to implement the fetched splash's 3000ms fake progress bar, the pinned-vs-system launch residual, and that onboarding panels 2–3 plus the permission rationale are original compositions. Follow `specs/008-storage-downloads/design-notes.md`
- [X] T104 [P] In `docs/DESIGN-SOURCE.md`: close out open issue #5 (dark-mode tokens) as resolved by this phase, and add a "Phase 9 (Polish & Accessibility) — implemented 2026-07-25" entry beside the existing Phase 6/7/8 entries, listing the deviations from T103
- [X] T105 Three final consistency checks: (a) delete the temporary `// TODO(T037)` marker if any remains; (b) run `grep -rn "Color(0x" shared/src/commonMain/kotlin/com/giraffe/matn/presentation --include=*.kt | grep -v theme/Color.kt` — it must return nothing; (c) confirm the surface colour still agrees in all three places it is written: `theme/Color.kt` (`Surface` / `DarkSurface`), `androidApp/src/main/res/values{,-night}/themes.xml`, and `iosApp/iosApp/Assets.xcassets` `LaunchBackground`
- [ ] T106 Work through `specs/009-polish-accessibility/quickstart.md` §3 on a real Android device and an iOS simulator — all six manual passes. §3.2 (screen reader) and §3.6 step 2 (gapless audio) cannot be skipped; no automated test covers them
- [ ] T107 Run the full suite one final time: `./gradlew :shared:allTests` and `./gradlew :androidApp:assembleDebug`

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)**: no dependencies
- **Phase 2 (Foundational)**: needs Phase 1 — **blocks every user story**
- **Phase 3 (US1)**: needs Phase 2
- **Phase 4 (US2)**: needs Phase 2. `ColorContrastTest` (T040) lives in US1, so running US2 before US1 leaves the dark half of the contrast gate unwritten — prefer US1 first
- **Phase 5 (US3)**: needs Phase 2 only. Fully independent of US1/US2/US4/US5
- **Phase 6 (US4)**: needs Phase 2. T083 edits `App.kt`, which T042 (US1) also edits — do US1 first, or reconcile by hand
- **Phase 7 (US5)**: needs Phase 2. T099 edits the onboarding screen created in US3 — skip that one task if US3 is not done
- **Phase 8 (Polish)**: needs every story you intend to ship

### Same-file collisions to respect

| File | Tasks that touch it | Order |
|---|---|---|
| `App.kt` | T042 (US1), T083 (US4) | T042 → T083 |
| `MatnTheme.kt` | T020 (Foundational), T038 (US1) | T020 → T038 |
| `Color.kt` | T037 (US1) | single |
| `SettingsScreen.kt` | T045 (US1), T063 (US2), T073 (US3), T077 (US3), T086 (US4) | sequential |
| `SettingsUiState.kt` / `SettingsViewModel.kt` | T043/T044 (US1), T077 (US3) | US1 → US3 |
| `MatnNavHost.kt` | T046 (US1), T072 (US3), T094 (US5) | sequential |
| `PlayerBar.kt` | T058/T060/T063/T064 (US2), T076 (US3), T087 (US4), T096 (US5) | sequential |
| `MatnDetailsScreen.kt` | T058/T061/T063 (US2), T089/T090 (US4) | sequential |
| `ReadingCarousel.kt` | T061/T062/T063 (US2), T067 (US2), T085 (US4), T097 (US5) | sequential |
| Sheets & dialogs — `RepetitionSetupSheet.kt`, `NoteEditorSheet.kt`, `InstallPromptSheet.kt`, `ConfirmRemovalDialog.kt` | T088 (US4), T098 (US5) | T088 → T098 |
| `HomeScreen.kt` / `SearchScreen.kt` / `NotesTabScreen.kt` / `GoalsScreen.kt` | T063 (US2), T084/T086 (US4) | sequential |
| `PlaybackControllerTest.kt` | T081 (US3), T101 (US5) | US3 → US5 |

### Parallel opportunities

- T004–T012 (nine domain files) — all `[P]`, all new files
- T022–T027 (six platform files) — all `[P]`
- T031–T035 (five test files) — all `[P]`
- T084–T089 and T092 (US4 per-screen work) — `[P]` across different files
- T095, T096, T098, T099 (US5 per-file animation) — `[P]`

---

## Parallel Example: Phase 2 domain layer

```bash
# Nine independent new files — no shared edits, safe to do in one batch:
Task: T004 Create domain/model/ThemeMode.kt
Task: T005 Create domain/model/Appearance.kt
Task: T006 Create domain/model/OnboardingStatus.kt
Task: T007 Create domain/model/PermissionStatus.kt
Task: T008 Create domain/appearance/AppearanceMirror.kt
Task: T009 Create domain/permission/NotificationPermission.kt
Task: T010 Create domain/preferences/MotionPreferences.kt
Task: T011 Create domain/repository/AppearancePreferencesRepository.kt
Task: T012 Create domain/repository/OnboardingRepository.kt
```

---

## Implementation Strategy

### MVP first (US1 only)

1. Phase 1 (T001–T003)
2. Phase 2 (T004–T036) — **blocks everything**
3. Phase 3 (T037–T054)
4. **STOP and VALIDATE** with quickstart.md §3.1
5. Shippable: dark mode complete, persistent, no launch flash

### Incremental delivery

Setup + Foundational → US1 (dark mode) → US2 (accessibility) → US3 (onboarding) → US4 (tablet) →
US5 (motion) → Polish. Each story is independently demoable and none breaks a previous one.

### Recommended order if time is short

US1 and US2 carry the constitution's contractual obligations (Principle VII names dark mode and
contrast). US4 and US5 are quality improvements on top. US3 is independent and can slot in anywhere
after Phase 2.

---

## Notes

- `[P]` = different files, no dependency on an incomplete task
- Commit after each task or each logical group; stop at any checkpoint to validate
- This phase adds **no database table and no migration** — if you find yourself writing a `.sqm`
  file, re-read research D2, because something has gone wrong
- The five rules in **HOW TO WORK THIS LIST** override any conflicting instinct. Rule 1 (no animation
  on the audio path) is the one that would be hardest to notice breaking and most damaging to ship
