package dev.dimension.flare

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.defaults.RootNavGraphDefaultAnimations
import com.ramcosta.composedestinations.animations.rememberAnimatedNavHostEngine
import dev.dimension.flare.ui.screen.NavGraphs

class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        setContent {
            val navController = rememberNavController()
            LaunchedEffect(Unit) {
                this@MainActivity.navController = navController
            }
            Content(
                navController = navController,
            )
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { navController.navigate(it) }
        // do not use handleDeepLink since it will break navigate up
//        navController.handleDeepLink(intent)
    }
}

@OptIn(ExperimentalMaterialNavigationApi::class, ExperimentalAnimationApi::class)
@Composable
fun Content(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    DestinationsNavHost(
        navGraph = NavGraphs.root,
        modifier = modifier,
        navController = navController,
        engine =
            rememberAnimatedNavHostEngine(
                rootDefaultAnimations =
                    RootNavGraphDefaultAnimations(
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                initialOffset = { it / 4 },
                            ) + fadeIn()
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                targetOffset = { it / 4 },
                            ) + fadeOut()
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                initialOffset = { it / 4 },
                            ) + fadeIn()
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                targetOffset = { it / 4 },
                            ) + fadeOut()
                        },
                    ),
            ),
    )
}
