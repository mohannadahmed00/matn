package com.giraffe.matn.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.giraffe.matn.domain.repository.RepetitionSettingsStore
import com.giraffe.matn.domain.usecase.DeleteNoteUseCase
import com.giraffe.matn.domain.usecase.DismissContinueLearningUseCase
import com.giraffe.matn.domain.usecase.GetFontSizeUseCase
import com.giraffe.matn.domain.usecase.GetNoteUseCase
import com.giraffe.matn.domain.usecase.GetMatnDetailsUseCase
import com.giraffe.matn.domain.usecase.ObserveBookmarksUseCase
import com.giraffe.matn.domain.usecase.ObserveContinueLearningUseCase
import com.giraffe.matn.domain.usecase.ObserveLibraryUseCase
import com.giraffe.matn.domain.usecase.ObserveNotesUseCase
import com.giraffe.matn.domain.usecase.ObserveVerseAnnotationsUseCase
import com.giraffe.matn.domain.usecase.ObserveVersesUseCase
import com.giraffe.matn.domain.usecase.ResolveResumeTargetUseCase
import com.giraffe.matn.domain.usecase.SaveNoteUseCase
import com.giraffe.matn.domain.usecase.ToggleBookmarkUseCase
import com.giraffe.matn.domain.usecase.SearchLibraryUseCase
import com.giraffe.matn.domain.usecase.SetFontSizeUseCase
import com.giraffe.matn.domain.model.SearchResult
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.presentation.details.MatnDetailsScreen
import com.giraffe.matn.presentation.details.MatnDetailsViewModel
import com.giraffe.matn.presentation.goals.GoalsScreen
import com.giraffe.matn.presentation.goals.GoalsViewModel
import com.giraffe.matn.presentation.home.HomeScreen
import com.giraffe.matn.presentation.home.HomeViewModel
import com.giraffe.matn.presentation.notes.NotesTabScreen
import com.giraffe.matn.presentation.notes.NotesTabViewModel
import com.giraffe.matn.presentation.player.PlayerBarViewModel
import com.giraffe.matn.presentation.search.SearchScreen
import com.giraffe.matn.presentation.search.SearchViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * App-wide navigation graph (CMP Navigation, Decision 5), wrapped in a persistent bottom
 * [NavigationBar] (specs/010-design-system-adoption User Story 3; `docs/PRODUCT-SPEC.md` §
 * Navigation & App Shell). Routes:
 *  * `home` — the library grid, [NavigationTab.LIBRARY]
 *  * `matn/{matnId}` — the reading/details screen (no bottom bar — the focused reading/playback
 *    surface owns the whole screen, matching the canonical design)
 *  * `search` — dedicated search screen (Phase 6 US1), pushed from Home's top-bar entry point;
 *    no bottom bar
 *  * `notes` — [NavigationTab.NOTES]'s real screen (Phase 6 US2/US3): bookmarks + notes
 *  * `goals` — [NavigationTab.GOALS]'s real screen (Phase 7 US3): the Goals dashboard
 *  * `settings` — [NavigationTab.SETTINGS], still routed to the shared [ComingSoonScreen]
 *
 * The graph is authored once and extended per story. ViewModels are built per destination
 * with `androidx.lifecycle.viewmodel.compose.viewModel { ... }`, injecting use cases from the
 * common [MatnKoinHolder] (the platform shell starts Koin via `initMatnKoin`).
 */
@Composable
fun MatnNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = NavigationTab.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                MatnBottomNavigationBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        // Standard Compose Navigation bottom-nav pattern: never stack duplicate
                        // destinations, restore each tab's own state on return (contract § 3) —
                        // this is what keeps switching tabs from recreating Home's ViewModel and
                        // losing its in-progress playback/continue-learning state.
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
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
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
                    )
                }
                HomeScreen(
                    viewModel = viewModel,
                    onOpenMatn = { id -> navController.navigate(Routes.matnDetails(id)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                )
            }
            composable(
                route = Routes.MATN_DETAILS,
                arguments = listOf(
                    navArgument(Routes.FOCUS_VERSE_ID_ARG) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { backStackEntry ->
                val matnId = backStackEntry.arguments?.read { getString(Routes.MATN_ID_ARG) } ?: ""
                val focusVerseId = (backStackEntry.arguments?.read { getString(Routes.FOCUS_VERSE_ID_ARG) } ?: "")
                    .ifBlank { null }
                val koin = MatnKoinHolder.koin
                val viewModel: MatnDetailsViewModel = viewModel {
                    MatnDetailsViewModel(
                        matnId = matnId,
                        getMatnDetails = koin.get<GetMatnDetailsUseCase>(),
                        observeVerses = koin.get<ObserveVersesUseCase>(),
                        getFontSize = koin.get<GetFontSizeUseCase>(),
                        setFontSize = koin.get<SetFontSizeUseCase>(),
                        playbackController = koin.get<PlaybackController>(),
                        focusVerseId = focusVerseId,
                        observeVerseAnnotations = koin.get<ObserveVerseAnnotationsUseCase>(),
                        toggleBookmark = koin.get<ToggleBookmarkUseCase>(),
                        getNote = koin.get<GetNoteUseCase>(),
                        saveNote = koin.get<SaveNoteUseCase>(),
                        deleteNote = koin.get<DeleteNoteUseCase>(),
                        observeMatnProgress = koin.get<com.giraffe.matn.domain.usecase.ObserveMatnProgressUseCase>(),
                        observeVerseMemorization = koin.get<com.giraffe.matn.domain.usecase.ObserveVerseMemorizationUseCase>(),
                        toggleVerseMemorized = koin.get<com.giraffe.matn.domain.usecase.ToggleVerseMemorizedUseCase>(),
                        markChapterMemorized = koin.get<com.giraffe.matn.domain.usecase.MarkChapterMemorizedUseCase>(),
                    )
                }
                val playerBar: PlayerBarViewModel = viewModel {
                    PlayerBarViewModel(koin.get<PlaybackController>())
                }
                MatnDetailsScreen(viewModel = viewModel, playerBar = playerBar)
            }
            composable(Routes.SEARCH) {
                val koin = MatnKoinHolder.koin
                val viewModel: SearchViewModel = viewModel {
                    SearchViewModel(searchLibrary = koin.get<SearchLibraryUseCase>())
                }
                SearchScreen(
                    viewModel = viewModel,
                    onResultClick = { result ->
                        // search-contract.md § 6: every result kind navigates through the matn
                        // route's optional focusVerseId; a chapter with no verses falls back to
                        // the plain matn route.
                        when (result) {
                            is SearchResult.VerseMatch ->
                                navController.navigate(Routes.matnDetails(result.ref.matnId, result.ref.verseId))
                            is SearchResult.ChapterMatch ->
                                navController.navigate(Routes.matnDetails(result.matnId, result.firstVerseId))
                            is SearchResult.MatnMatch ->
                                navController.navigate(Routes.matnDetails(result.matnId))
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.GOALS) {
                val koin = MatnKoinHolder.koin
                val viewModel: GoalsViewModel = viewModel {
                    GoalsViewModel(
                        observeDailyProgress = koin.get<com.giraffe.matn.domain.usecase.ObserveDailyProgressUseCase>(),
                        observeLibraryProgress = koin.get<com.giraffe.matn.domain.usecase.ObserveLibraryProgressUseCase>(),
                        setDailyGoal = koin.get<com.giraffe.matn.domain.usecase.SetDailyGoalUseCase>(),
                        observeLibrary = koin.get<ObserveLibraryUseCase>(),
                    )
                }
                GoalsScreen(viewModel = viewModel)
            }
            composable(Routes.NOTES) {
                val koin = MatnKoinHolder.koin
                val viewModel: NotesTabViewModel = viewModel {
                    NotesTabViewModel(
                        observeBookmarks = koin.get<ObserveBookmarksUseCase>(),
                        observeNotes = koin.get<ObserveNotesUseCase>(),
                    )
                }
                NotesTabScreen(
                    viewModel = viewModel,
                    onNavigateToVerse = { matnId, verseId ->
                        navController.navigate(Routes.matnDetails(matnId, verseId))
                    },
                )
            }
            composable(Routes.SETTINGS) {
                ComingSoonScreen(tab = NavigationTab.SETTINGS, onBackToLibrary = { navController.navigate(Routes.HOME) })
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
    const val HOME = "home"
    const val MATN_DETAILS = "matn/{$MATN_ID_ARG}?$FOCUS_VERSE_ID_ARG={$FOCUS_VERSE_ID_ARG}"
    const val GOALS = "goals"
    const val NOTES = "notes"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    fun matnDetails(matnId: String, focusVerseId: String? = null): String =
        if (focusVerseId != null) "matn/$matnId?$FOCUS_VERSE_ID_ARG=$focusVerseId" else "matn/$matnId"
}
