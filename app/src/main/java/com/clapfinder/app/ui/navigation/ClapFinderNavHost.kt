package com.clapfinder.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clapfinder.app.ui.screens.HomeScreen
import com.clapfinder.app.ui.screens.HowToUseScreen
import com.clapfinder.app.ui.screens.OnboardingScreen
import com.clapfinder.app.ui.screens.PaywallScreen
import com.clapfinder.app.ui.screens.SettingsScreen
import com.clapfinder.app.ui.screens.SoundScreen
import com.clapfinder.app.ui.screens.SplashScreen
import com.clapfinder.app.ui.screens.WhistleScreen

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SOUND = "sound"
    const val SETTINGS = "settings"
    const val WHISTLE = "whistle"
    const val HOW_TO = "how_to"
    const val PAYWALL = "paywall"
}

private const val FADE_MS = 250

@Composable
fun ClapFinderNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(tween(FADE_MS)) },
        exitTransition = { fadeOut(tween(FADE_MS)) },
        popEnterTransition = { fadeIn(tween(FADE_MS)) },
        popExitTransition = { fadeOut(tween(FADE_MS)) },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(onFinished = { onboardingDone ->
                navController.navigate(if (onboardingDone) Routes.HOME else Routes.ONBOARDING) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.open(Routes.SETTINGS) },
                onOpenPaywall = { navController.open(Routes.PAYWALL) },
                onOpenHowTo = { navController.open(Routes.HOW_TO) },
                onOpenSound = { navController.open(Routes.SOUND) },
                onOpenWhistle = { navController.open(Routes.WHISTLE) },
            )
        }

        composable(Routes.PAYWALL) {
            PaywallScreen(onClose = { navController.back(Routes.PAYWALL) })
        }

        composable(Routes.SOUND) {
            SoundScreen(onBack = { navController.back(Routes.SOUND) })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.back(Routes.SETTINGS) })
        }

        composable(Routes.WHISTLE) {
            WhistleScreen(onBack = { navController.back(Routes.WHISTLE) })
        }

        composable(Routes.HOW_TO) {
            HowToUseScreen(onBack = { navController.back(Routes.HOW_TO) })
        }
    }
}

private fun NavHostController.open(route: String) {
    navigate(route) { launchSingleTop = true }
}

private fun NavHostController.back(route: String) {
    if (currentDestination?.route == route) popBackStack()
}