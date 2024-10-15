package dev.dimension.flare.ui.screen.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.TabSplashScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.Direction
import dev.dimension.flare.ui.component.ThemeWrapper

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
    start = true,
)
@Composable
internal fun TabSplashScreen(
    args: SplashScreenArgs,
    navigator: DestinationsNavigator,
) {
    LaunchedEffect(Unit) {
        navigator.navigate(direction = args.direction) {
            popUpTo(TabSplashScreenDestination) {
                inclusive = true
            }
        }
    }
    SplashScreen()
}

internal data class SplashScreenArgs(
    val direction: Direction,
)
