package com.pixelexperience.forall.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pixelexperience.forall.MainUiState
import com.pixelexperience.forall.MainViewModel
import com.pixelexperience.forall.controlcenter.ControlCenterPrefs
import com.pixelexperience.forall.controlcenter.ui.ControlCenterScreen
import com.pixelexperience.forall.core.modules.PixelModuleId
import com.pixelexperience.forall.ui.appearance.AppearanceScreen
import com.pixelexperience.forall.ui.home.HomeScreen
import com.pixelexperience.forall.ui.launcher.LauncherScreen
import com.pixelexperience.forall.ui.module.ModuleDetailScreen

object Routes {
    const val HOME = "home"
    const val APPEARANCE = "appearance"
    const val LAUNCHER = "launcher"
    const val CONTROL_CENTER = "controlcenter"
    const val MODULE = "module/{moduleId}"
    const val ARG_MODULE_ID = "moduleId"

    fun module(id: PixelModuleId) = "module/${id.name}"
}

@Composable
fun PixelNavHost(viewModel: MainViewModel, state: MainUiState) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // O interruptor do módulo na Home liga/desliga de verdade o painel da Central de controle.
    val controlCenterEnabled = state.modules
        .firstOrNull { it.info.id == PixelModuleId.CONTROL_CENTER }?.enabled ?: true
    LaunchedEffect(controlCenterEnabled) {
        ControlCenterPrefs.setEnabled(context, controlCenterEnabled)
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                state = state,
                onOpenModule = { id ->
                    val route = when (id) {
                        PixelModuleId.LAUNCHER -> Routes.LAUNCHER
                        PixelModuleId.CONTROL_CENTER -> Routes.CONTROL_CENTER
                        else -> Routes.module(id)
                    }
                    navController.navigate(route)
                },
                onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
                onToggleModule = viewModel::setModuleEnabled,
            )
        }
        composable(Routes.APPEARANCE) {
            AppearanceScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onThemeMode = viewModel::setThemeMode,
                onDynamicColor = viewModel::setDynamicColor,
            )
        }
        composable(Routes.LAUNCHER) {
            LauncherScreen(
                moduleEnabled = state.modules
                    .firstOrNull { it.info.id == PixelModuleId.LAUNCHER }?.enabled ?: true,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CONTROL_CENTER) {
            ControlCenterScreen(
                moduleEnabled = controlCenterEnabled,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.MODULE,
            arguments = listOf(navArgument(Routes.ARG_MODULE_ID) { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString(Routes.ARG_MODULE_ID)
            ModuleDetailScreen(
                module = state.modules.firstOrNull { it.info.id.name == id },
                onBack = { navController.popBackStack() },
                onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
            )
        }
    }
}
