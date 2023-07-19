package dev.dimension.flare.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import dev.dimension.flare.ui.screen.home.HomeScreen
import dev.dimension.flare.ui.screen.login.LoginScreen
import dev.dimension.flare.ui.screen.splash.SplashScreen

internal const val initialRoute = "Splash"

context(NavGraphBuilder, NavHostController)
internal fun main() {
    composable("Splash") {
        SplashScreen(
            toHome = {
                navigate("Home", navOptions { popUpTo("Splash") { inclusive = true } })
            },
            toLogin = {
                navigate("Login", navOptions { popUpTo("Splash") { inclusive = true } })
            },
        )
    }

    composable("Login") {
        LoginScreen(
            toHome = {
                navigate("Home", navOptions { popUpTo("Login") { inclusive = true } })
            },
        )
    }

    composable("Home") {
        HomeScreen()
    }
}