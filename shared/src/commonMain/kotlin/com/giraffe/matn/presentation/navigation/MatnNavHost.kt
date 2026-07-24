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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.read
import com.giraffe.matn.di.MatnKoinHolder
import com.giraffe.matn.domain.repository.RepetitionSettingsStore
import com.giraffe.matn.domain.usecase.DismissContinueLearningUseCase
import com.giraffe.matn.domain.usecase.GetFontSizeUseCase
import com.giraffe.matn.domain.usecase.GetMatnDetailsUseCase
import com.giraffe.matn.domain.usecase.ObserveContinueLearningUseCase
import com.giraffe.matn.domain.usecase.ObserveLibraryUseCase
import com.giraffe.matn.domain.usecase.ObserveVersesUseCase
import com.giraffe.matn.domain.usecase.ResolveResumeTargetUseCase
import com.giraffe.matn.domain.usecase.SetFontSizeUseCase
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.presentation.details.MatnDetailsScreen
import com.giraffe.matn.presentation.details.MatnDetailsViewModel
import com.giraffe.matn.presentation.home.HomeScreen
import com.giraffe.matn.presentation.home.HomeViewModel
import com.giraffe.matn.presentation.player.PlayerBarViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * App-wide navigation graph (CMP Navigation, Decision 5), wrapped in a persistent bottom
 * [NavigationBar] (specs/010-design-system-adoption User Story 3; `docs/PRODUCT-SPEC.md` §
 * Navigation & App Shell). Routes:
 *  * `home` — the library grid, [NavigationTab.LIBRARY]
 *  * `matn/{matnId}` — the reading/details screen (no bottom bar — the focused reading/playback
 *    surface owns the whole screen, matching the canonical design)
 *  * `goals`/`notes`/`settings` — [NavigationTab.GOALS]/[NOTES]/[SETTINGS], all routed to the
 *    shared [ComingSoonScreen] until Phases 6-8 land (FR-007)
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
                    )
                }
                HomeScreen(
                    viewModel = viewModel,
                    onOpenMatn = { id -> navController.navigate(Routes.matnDetails(id)) },
                )
            }
            composable(Routes.MATN_DETAILS) { backStackEntry ->
                val matnId = backStackEntry.arguments?.read { getString(Routes.MATN_ID_ARG) } ?: ""
                val koin = MatnKoinHolder.koin
                val viewModel: MatnDetailsViewModel = viewModel {
                    MatnDetailsViewModel(
                        matnId = matnId,
                        getMatnDetails = koin.get<GetMatnDetailsUseCase>(),
                        observeVerses = koin.get<ObserveVersesUseCase>(),
                        getFontSize = koin.get<GetFontSizeUseCase>(),
                        setFontSize = koin.get<SetFontSizeUseCase>(),
                        koin.get<PlaybackController>(),
                    )
                }
                val playerBar: PlayerBarViewModel = viewModel {
                    PlayerBarViewModel(koin.get<PlaybackController>())
                }
                MatnDetailsScreen(viewModel = viewModel, playerBar = playerBar)
            }
            composable(Routes.GOALS) {
                ComingSoonScreen(tab = NavigationTab.GOALS, onBackToLibrary = { navController.navigate(Routes.HOME) })
            }
            composable(Routes.NOTES) {
                ComingSoonScreen(tab = NavigationTab.NOTES, onBackToLibrary = { navController.navigate(Routes.HOME) })
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
    const val HOME = "home"
    const val MATN_DETAILS = "matn/{$MATN_ID_ARG}"
    const val GOALS = "goals"
    const val NOTES = "notes"
    const val SETTINGS = "settings"
    fun matnDetails(matnId: String): String = "matn/$matnId"
}
