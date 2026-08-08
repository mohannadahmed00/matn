package com.giraffe.matn.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.giraffe.matn.di.MatnKoinHolder
import com.giraffe.matn.domain.model.OnboardingStatus
import com.giraffe.matn.domain.repository.RepetitionSettingsStore
import com.giraffe.matn.domain.usecase.CompleteOnboardingUseCase
import com.giraffe.matn.domain.usecase.DeleteNoteUseCase
import com.giraffe.matn.domain.usecase.DismissContinueLearningUseCase
import com.giraffe.matn.domain.usecase.GetFontSizeUseCase
import com.giraffe.matn.domain.usecase.GetMatnDetailsUseCase
import com.giraffe.matn.domain.usecase.GetNoteUseCase
import com.giraffe.matn.domain.usecase.GetOnboardingStatusNowUseCase
import com.giraffe.matn.domain.usecase.ObserveBookmarksUseCase
import com.giraffe.matn.domain.usecase.ObserveContinueLearningUseCase
import com.giraffe.matn.domain.usecase.ObserveLibraryUseCase
import com.giraffe.matn.domain.usecase.ObserveMemorizedUseCase
import com.giraffe.matn.domain.usecase.ObserveNotesUseCase
import com.giraffe.matn.domain.usecase.ObserveVerseAnnotationsUseCase
import com.giraffe.matn.domain.usecase.ObserveVersesUseCase
import com.giraffe.matn.domain.usecase.ResolveResumeTargetUseCase
import com.giraffe.matn.domain.usecase.SaveNoteUseCase
import com.giraffe.matn.domain.usecase.SearchLibraryUseCase
import com.giraffe.matn.domain.usecase.SetFontSizeUseCase
import com.giraffe.matn.domain.usecase.ToggleBookmarkUseCase
import com.giraffe.matn.domain.usecase.ToggleVerseMemorizedUseCase
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.presentation.details.MatnDetailsScreen
import com.giraffe.matn.presentation.details.MatnDetailsViewModel
import com.giraffe.matn.presentation.goals.DailyGoalSheet
import com.giraffe.matn.presentation.goals.GoalsViewModel
import com.giraffe.matn.presentation.home.HomeScreen
import com.giraffe.matn.presentation.home.HomeViewModel
import com.giraffe.matn.presentation.onboarding.OnboardingScreen
import com.giraffe.matn.presentation.onboarding.OnboardingViewModel
import com.giraffe.matn.presentation.player.PlayerBarViewModel
import com.giraffe.matn.presentation.reader.ReaderScreen
import com.giraffe.matn.presentation.reader.ReaderViewModel
import com.giraffe.matn.presentation.saved.SavedScreen
import com.giraffe.matn.presentation.saved.SavedViewModel
import com.giraffe.matn.presentation.search.SearchViewModel
import com.giraffe.matn.presentation.settings.SettingsScreen
import com.giraffe.matn.presentation.settings.SettingsViewModel
import com.giraffe.matn.presentation.theme.LocalReduceMotion
import com.giraffe.matn.presentation.theme.MatnMotion
import org.jetbrains.compose.resources.stringResource

/**
 * App-wide navigation graph (CMP Navigation, Decision 5), wrapped in a persistent bottom
 * [NavigationBar] (Matn Design System §02 — *Navigation consolidation*; `docs/PRODUCT-SPEC.md` §
 * Navigation & App Shell).
 *
 * **Four routes, three tabs.** The rule the design applies to every surface: if it does not need its
 * own back-stack entry, its own scroll position, or its own deep link, it is not a route.
 *
 *  * `home` — Library, [NavigationTab.LIBRARY] and the start destination. Catalog, Continue
 *    Learning, the daily-goal ring and the in-place search field.
 *  * `saved` — [NavigationTab.SAVED]: bookmarks, notes and memorized verses, filtered in place.
 *  * `settings` — [NavigationTab.SETTINGS].
 *  * `matn/{matnId}` — the reading/details screen. The one screen that earns full-screen status: it
 *    owns a scroll position, a back-stack entry, and the deep link Continue Learning and every
 *    search result resolve through. No bottom bar.
 *
 * Three former routes are gone: **search** is a field on Library, **goals** is a sheet opened from
 * Library's ring, and **onboarding** is an overlay pager above the whole graph — a route the
 * student sees once should not sit in the back-stack forever.
 */
@Composable
fun MatnNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = NavigationTab.entries.any { it.route == currentRoute }
    // T094 (US5, adaptive-motion-contract.md §B3): read once here (a Composable scope) and
    // captured by the transition lambdas below, which are not themselves Composable.
    val reduceMotion = LocalReduceMotion.current

    // Onboarding is presentation state above the graph, not a destination (T072 (US3),
    // onboarding-permissions-contract.md §2.3 — the completed flag is still resolved via a use
    // case, never by injecting OnboardingRepository directly here, per Principle I).
    val koinRoot = MatnKoinHolder.koin
    val onboardingViewModel: OnboardingViewModel = viewModel {
        OnboardingViewModel(completeOnboarding = koinRoot.get<CompleteOnboardingUseCase>())
    }
    var showOnboarding by rememberSaveable {
        mutableStateOf(
            koinRoot.get<GetOnboardingStatusNowUseCase>()() == OnboardingStatus.NOT_COMPLETED,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    MatnBottomNavigationBar(
                        currentRoute = currentRoute,
                        onTabSelected = { tab ->
                            // Standard Compose Navigation bottom-nav pattern: never stack duplicate
                            // destinations, restore each tab's own state on return (contract § 3) —
                            // this is what keeps switching tabs from recreating Library's ViewModel
                            // and losing its in-progress playback/continue-learning state.
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            // T094 (US5, adaptive-motion-contract.md §B3): shared enter/exit transitions on the
            // NavHost itself, not per `composable`, so every route uses one vocabulary. Sibling tab
            // changes fade only (no slide — sliding implies a hierarchy tabs don't have); forward
            // navigation slides from the layout's start edge via the direction-aware
            // `SlideDirection.Start`/`End` (never a raw pixel offset, which would be LTR-only).
            // `LocalReduceMotion` (captured above, a Composable read) substitutes a plain fade.
            // No blocking overlay/scrim is added for the transition's duration (FR-037) — the
            // existing `launchSingleTop` on every navigate call already prevents double-navigation.
            val tabRoutes = remember { NavigationTab.entries.map { it.route }.toSet() }
            fun isTabChange(scope: AnimatedContentTransitionScope<NavBackStackEntry>): Boolean {
                val from = scope.initialState.destination.route
                val to = scope.targetState.destination.route
                return from in tabRoutes && to in tabRoutes
            }
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    when {
                        isTabChange(this) || reduceMotion ->
                            fadeIn(animationSpec = tween(MatnMotion.durationShort))

                        else -> slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(
                                MatnMotion.durationMedium,
                                easing = MatnMotion.easingStandard,
                            ),
                        ) + fadeIn(animationSpec = tween(MatnMotion.durationMedium))
                    }
                },
                exitTransition = {
                    when {
                        isTabChange(this) || reduceMotion ->
                            fadeOut(animationSpec = tween(MatnMotion.durationShort))

                        else -> slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(
                                MatnMotion.durationMedium,
                                easing = MatnMotion.easingStandard,
                            ),
                        ) + fadeOut(animationSpec = tween(MatnMotion.durationMedium))
                    }
                },
                popEnterTransition = {
                    if (reduceMotion) {
                        fadeIn(animationSpec = tween(MatnMotion.durationShort))
                    } else {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(MatnMotion.durationMedium, easing = MatnMotion.easingExit),
                        ) + fadeIn(animationSpec = tween(MatnMotion.durationMedium))
                    }
                },
                popExitTransition = {
                    if (reduceMotion) {
                        fadeOut(animationSpec = tween(MatnMotion.durationShort))
                    } else {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(MatnMotion.durationMedium, easing = MatnMotion.easingExit),
                        ) + fadeOut(animationSpec = tween(MatnMotion.durationMedium))
                    }
                },
            ) {
                composable(Routes.HOME) {
                    val koin = MatnKoinHolder.koin
                    val viewModel: HomeViewModel = viewModel {
                        HomeViewModel(
                            observeLibrary = koin.get<ObserveLibraryUseCase>(),
                            observeContinueLearning = koin.get<ObserveContinueLearningUseCase>(),
                            resolveResumeTarget = koin.get<ResolveResumeTargetUseCase>(),
                            dismissContinueLearning = koin.get<DismissContinueLearningUseCase>(),
                            playbackController = koin.get<PlaybackController>(),
                            settingsStore = koin.get<RepetitionSettingsStore>(),
                            observeLibraryProgress = koin.get<com.giraffe.matn.domain.usecase.ObserveLibraryProgressUseCase>(),
                            observeDailyProgress = koin.get<com.giraffe.matn.domain.usecase.ObserveDailyProgressUseCase>(),
                            observeLibraryAvailability = koin.get<com.giraffe.matn.domain.usecase.ObserveLibraryAvailabilityUseCase>(),
                            // Phase 13: the grid becomes the catalog — every published matn,
                            // downloaded or not (FR-005) — and the library opening triggers a
                            // staleness-windowed sync (FR-006).
                            observeCatalog = koin.get<com.giraffe.matn.domain.usecase.ObserveCatalogUseCase>(),
                            observeCatalogSyncState = koin.get<com.giraffe.matn.domain.usecase.ObserveCatalogSyncStateUseCase>(),
                            syncCatalog = koin.get<com.giraffe.matn.domain.usecase.SyncCatalogUseCase>(),
                            // FR-012: covers are fetched only from here, the browse path. Passed as
                            // a plain suspend function rather than the cache itself, so the
                            // ViewModel depends on a capability, not on a data-layer type.
                            loadCover = koin.get<com.giraffe.matn.data.cover.CoverImageCache>()
                                .let { cache -> { (matnId, ref) -> cache.load(matnId, ref) } },
                        )
                    }
                    // Search is Library's own state now, so its ViewModel is scoped to this
                    // destination and its query survives a trip into a matn and back.
                    val searchViewModel: SearchViewModel = viewModel {
                        SearchViewModel(searchLibrary = koin.get<SearchLibraryUseCase>())
                    }
                    var showGoalSheet by rememberSaveable { mutableStateOf(false) }

                    HomeScreen(
                        viewModel = viewModel,
                        searchViewModel = searchViewModel,
                        onOpenMatn = { id -> navController.navigate(Routes.matnDetails(id)) },
                        onOpenVerse = { matnId, verseId ->
                            navController.navigate(Routes.matnDetails(matnId, verseId))
                        },
                        onResumeReading = { matnId ->
                            navController.navigate(Routes.reader(matnId, autoplay = false))
                        },
                        onOpenDailyGoal = { showGoalSheet = true },
                    )

                    if (showGoalSheet) {
                        val goalsViewModel: GoalsViewModel = viewModel {
                            GoalsViewModel(
                                observeDailyProgress = koin.get<com.giraffe.matn.domain.usecase.ObserveDailyProgressUseCase>(),
                                observeLibraryProgress = koin.get<com.giraffe.matn.domain.usecase.ObserveLibraryProgressUseCase>(),
                                setDailyGoal = koin.get<com.giraffe.matn.domain.usecase.SetDailyGoalUseCase>(),
                                observeLibrary = koin.get<ObserveLibraryUseCase>(),
                            )
                        }
                        DailyGoalSheet(viewModel = goalsViewModel, onDismiss = { showGoalSheet = false })
                    }
                }
                composable(
                    route = Routes.MATN_DETAILS,
                    arguments = listOf(
                        navArgument(Routes.FOCUS_VERSE_ID_ARG) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    val matnId = entry.arguments?.read { getString(Routes.MATN_ID_ARG) } ?: ""
                    val focusVerseId = (entry.arguments?.read { getString(Routes.FOCUS_VERSE_ID_ARG) } ?: "")
                        .ifBlank { null }
                    val koin = MatnKoinHolder.koin
                    val viewModel: MatnDetailsViewModel = viewModel {
                        MatnDetailsViewModel(
                            matnId = matnId,
                            getMatnDetails = koin.get<GetMatnDetailsUseCase>(),
                            observeVerses = koin.get<ObserveVersesUseCase>(),
                            getFontSize = koin.get<GetFontSizeUseCase>(),
                            setFontSize = koin.get<SetFontSizeUseCase>(),
                            focusVerseId = focusVerseId,
                            observeMatnProgress = koin.get<com.giraffe.matn.domain.usecase.ObserveMatnProgressUseCase>(),
                            observeVerseMemorization = koin.get<com.giraffe.matn.domain.usecase.ObserveVerseMemorizationUseCase>(),
                            toggleVerseMemorized = koin.get<ToggleVerseMemorizedUseCase>(),
                            markChapterMemorized = koin.get<com.giraffe.matn.domain.usecase.MarkChapterMemorizedUseCase>(),
                            observeContentAvailability = koin.get<com.giraffe.matn.domain.usecase.ObserveContentAvailabilityUseCase>(),
                            installMatnContent = koin.get<com.giraffe.matn.domain.usecase.DownloadMatnUseCase>(),
                            cancelInstall = koin.get<com.giraffe.matn.domain.usecase.CancelInstallUseCase>(),
                            removeMatnContent = koin.get<com.giraffe.matn.domain.usecase.RemoveMatnContentUseCase>(),
                            loadCachedCover = koin.get<com.giraffe.matn.data.cover.CoverImageCache>()::cached,
                        )
                    }
                    MatnDetailsScreen(
                        viewModel = viewModel,
                        onOpenReader = { verseId ->
                            navController.navigate(Routes.reader(matnId, verseId, autoplay = true))
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.READER,
                    arguments = listOf(
                        navArgument(Routes.VERSE_ID_ARG) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument(Routes.AUTOPLAY_ARG) {
                            type = NavType.StringType
                            defaultValue = "false"
                        },
                    ),
                ) { entry ->
                    val matnId = entry.arguments?.read { getString(Routes.MATN_ID_ARG) } ?: ""
                    val verseId = (entry.arguments?.read { getString(Routes.VERSE_ID_ARG) } ?: "")
                        .ifBlank { null }
                    val autoplay = (entry.arguments?.read { getString(Routes.AUTOPLAY_ARG) } ?: "") == "true"
                    val koin = MatnKoinHolder.koin
                    val viewModel: ReaderViewModel = viewModel {
                        ReaderViewModel(
                            matnId = matnId,
                            requestedVerseId = verseId,
                            autoplay = autoplay,
                            getMatnDetails = koin.get<GetMatnDetailsUseCase>(),
                            observeVerses = koin.get<ObserveVersesUseCase>(),
                            getFontSize = koin.get<GetFontSizeUseCase>(),
                            setFontSize = koin.get<SetFontSizeUseCase>(),
                            playbackController = koin.get<PlaybackController>(),
                            observeVerseAnnotations = koin.get<ObserveVerseAnnotationsUseCase>(),
                            toggleBookmark = koin.get<ToggleBookmarkUseCase>(),
                            getNote = koin.get<GetNoteUseCase>(),
                            saveNote = koin.get<SaveNoteUseCase>(),
                            deleteNote = koin.get<DeleteNoteUseCase>(),
                            observeVerseMemorization = koin.get<com.giraffe.matn.domain.usecase.ObserveVerseMemorizationUseCase>(),
                            toggleVerseMemorized = koin.get<ToggleVerseMemorizedUseCase>(),
                        )
                    }
                    val playerBar: PlayerBarViewModel = viewModel {
                        PlayerBarViewModel(koin.get<PlaybackController>())
                    }
                    ReaderScreen(
                        viewModel = viewModel,
                        playerBar = playerBar,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.SAVED) {
                    val koin = MatnKoinHolder.koin
                    val viewModel: SavedViewModel = viewModel {
                        SavedViewModel(
                            observeBookmarks = koin.get<ObserveBookmarksUseCase>(),
                            observeNotes = koin.get<ObserveNotesUseCase>(),
                            observeMemorized = koin.get<ObserveMemorizedUseCase>(),
                            toggleBookmark = koin.get<ToggleBookmarkUseCase>(),
                            deleteNote = koin.get<DeleteNoteUseCase>(),
                            saveNote = koin.get<SaveNoteUseCase>(),
                            toggleVerseMemorized = koin.get<ToggleVerseMemorizedUseCase>(),
                        )
                    }
                    SavedScreen(
                        viewModel = viewModel,
                        onNavigateToVerse = { matnId, verseId ->
                            navController.navigate(Routes.matnDetails(matnId, verseId))
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    val koin = MatnKoinHolder.koin
                    val viewModel: SettingsViewModel = viewModel {
                        SettingsViewModel(
                            observeStorageUsage = koin.get<com.giraffe.matn.domain.usecase.ObserveStorageUsageUseCase>(),
                            removeMatnContent = koin.get<com.giraffe.matn.domain.usecase.RemoveMatnContentUseCase>(),
                            removeAllContent = koin.get<com.giraffe.matn.domain.usecase.RemoveAllContentUseCase>(),
                            observeThemeMode = koin.get<com.giraffe.matn.domain.usecase.ObserveThemeModeUseCase>(),
                            setThemeMode = koin.get<com.giraffe.matn.domain.usecase.SetThemeModeUseCase>(),
                            notificationPermission = koin.get<com.giraffe.matn.domain.permission.NotificationPermission>(),
                            // Reading font size is a global preference, so Settings observes and
                            // writes the same use cases the reading screen does.
                            getFontSize = koin.get<GetFontSizeUseCase>(),
                            setFontSize = koin.get<SetFontSizeUseCase>(),
                        )
                    }
                    SettingsScreen(
                        viewModel = viewModel,
                        // T073 (US3): re-opening never clears the persisted completed flag — it
                        // raises the overlay again (contract §2.3).
                        onReopenOnboarding = {
                            onboardingViewModel.onReopened()
                            showOnboarding = true
                        },
                    )
                }
            }
        }

        // The overlay pager, above the graph and the bottom bar. Opaque, so the library beneath is
        // not a distraction on first launch; dismissed by skipping or finishing, both of which
        // persist the same completed state (FR-018).
        if (showOnboarding) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onCompleted = { showOnboarding = false },
                )
            }
        }
    }
}

/** The persistent bottom nav bar itself — a pure function of the current route (Principle II). */
@Composable
private fun MatnBottomNavigationBar(currentRoute: String?, onTabSelected: (NavigationTab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    NavigationBar(containerColor = scheme.surfaceContainerLow) {
        NavigationTab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = { NavTabIcon(tab = tab, color = if (selected) scheme.primary else scheme.onSurfaceVariant) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

object Routes {
    const val MATN_ID_ARG = "matnId"
    const val FOCUS_VERSE_ID_ARG = "focusVerseId"
    const val VERSE_ID_ARG = "v"
    const val AUTOPLAY_ARG = "autoplay"
    const val HOME = "home"
    const val MATN_DETAILS = "matn/{$MATN_ID_ARG}?$FOCUS_VERSE_ID_ARG={$FOCUS_VERSE_ID_ARG}"
    const val READER = "matn/{$MATN_ID_ARG}/read?$VERSE_ID_ARG={$VERSE_ID_ARG}&$AUTOPLAY_ARG={$AUTOPLAY_ARG}"
    const val SAVED = "saved"
    const val SETTINGS = "settings"

    fun matnDetails(matnId: String, focusVerseId: String? = null): String =
        if (focusVerseId != null) "matn/$matnId?$FOCUS_VERSE_ID_ARG=$focusVerseId" else "matn/$matnId"

    /**
     * The reader. [verseId] says where to open; [autoplay] says whether to start a session on
     * arrival — Details' play controls pass `true` so a tap is one gesture, while Continue Learning
     * has already started the session itself and passes `false` so the reader adopts it rather than
     * restarting it from the top.
     */
    fun reader(matnId: String, verseId: String? = null, autoplay: Boolean = false): String =
        "matn/$matnId/read?$VERSE_ID_ARG=${verseId.orEmpty()}&$AUTOPLAY_ARG=$autoplay"
}
