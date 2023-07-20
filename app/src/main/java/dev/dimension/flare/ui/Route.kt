package dev.dimension.flare.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navOptions
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.screen.compose.ComposeScreen
import dev.dimension.flare.ui.screen.home.HomeScreen
import dev.dimension.flare.ui.screen.login.LoginScreen
import dev.dimension.flare.ui.screen.profile.ProfileScreen
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
        HomeScreen(
            toCompose = {
                navigate("Compose")
            }
        )
    }

    composable(
        "Profile/{userKey}",
        deepLinks = listOf(
            navDeepLink {
                uriPattern = AppDeepLink.User.route
            }
        )
    ) {
        val userKey = it.arguments?.getString("userKey")?.let { MicroBlogKey.valueOf(it) }
        ProfileScreen(userKey)
    }

    composable(
        "Compose",
        enterTransition = {
            fadeIn() + slideInVertically { it / 2 }
        },
        exitTransition = {
            fadeOut() + slideOutVertically { it / 2 }
        },
    ) {
        ComposeScreen(
            onBack = {
                navigateUp()
            }
        )
    }
}