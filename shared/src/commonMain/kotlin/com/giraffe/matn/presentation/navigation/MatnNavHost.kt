package com.giraffe.matn.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.read
import com.giraffe.matn.di.MatnKoinHolder
import com.giraffe.matn.domain.usecase.GetFontSizeUseCase
import com.giraffe.matn.domain.usecase.GetMatnDetailsUseCase
import com.giraffe.matn.domain.usecase.ObserveLibraryUseCase
import com.giraffe.matn.domain.usecase.ObserveVersesUseCase
import com.giraffe.matn.domain.usecase.SetFontSizeUseCase
import com.giraffe.matn.presentation.details.MatnDetailsScreen
import com.giraffe.matn.presentation.details.MatnDetailsViewModel
import com.giraffe.matn.presentation.home.HomeScreen
import com.giraffe.matn.presentation.home.HomeViewModel

/**
 * App-wide navigation graph (CMP Navigation, Decision 5). Two routes in Phase 1:
 *  * `home` — the library grid (US2)
 *  * `matn/{matnId}` — the reading/details screen (US1, US3, US4)
 *
 * The graph is authored once and extended per story. ViewModels are built per destination
 * with `androidx.lifecycle.viewmodel.compose.viewModel { ... }`, injecting use cases from the
 * common [MatnKoinHolder] (the platform shell starts Koin via `initMatnKoin`).
 */
@Composable
fun MatnNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val koin = MatnKoinHolder.koin
            val viewModel: HomeViewModel = viewModel {
                HomeViewModel(
                    observeLibrary = koin.get<ObserveLibraryUseCase>(),
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
                )
            }
            MatnDetailsScreen(viewModel)
        }
    }
}

object Routes {
    const val MATN_ID_ARG = "matnId"
    const val HOME = "home"
    const val MATN_DETAILS = "matn/{$MATN_ID_ARG}"
    fun matnDetails(matnId: String): String = "matn/$matnId"
}