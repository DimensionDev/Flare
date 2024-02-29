package dev.dimension.flare.ui.screen.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.popUpTo
import com.ramcosta.composedestinations.spec.Direction
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.screen.destinations.TabSplashScreenDestination

@Destination(
    wrappers = [ThemeWrapper::class],
)
@RootNavGraph(start = true)
@Composable
internal fun TabSplashScreen(
    args: SplashScreenArgs,
    navigator: DestinationsNavigator,
) {
    LaunchedEffect(Unit) {
        navigator.navigate(args.direction) {
            popUpTo(TabSplashScreenDestination) {
                inclusive = true
            }
        }
    }
    SplashScreen()
}

data class SplashScreenArgs(
    val direction: Direction,
)
