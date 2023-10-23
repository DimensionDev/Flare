package dev.dimension.flare.ui.screen.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.popUpTo
import dev.dimension.flare.R
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.presenter.splash.SplashPresenter
import dev.dimension.flare.ui.screen.destinations.HomeRouteDestination
import dev.dimension.flare.ui.screen.destinations.ServiceSelectRouteDestination
import dev.dimension.flare.ui.screen.destinations.SplashRouteDestination
import dev.dimension.flare.ui.theme.FlareTheme

@RootNavGraph(start = true) // sets this as the start destination of the default nav graph
@Destination
@Composable
fun SplashRoute(
    navigator: DestinationsNavigator,
) {
    SplashScreen(
        toHome = {
            navigator.navigate(HomeRouteDestination) {
                popUpTo(SplashRouteDestination) {
                    inclusive = true
                }
            }
        },
        toLogin = {
            navigator.navigate(ServiceSelectRouteDestination) {
                popUpTo(SplashRouteDestination) {
                    inclusive = true
                }
            }
        },
    )
}

@Composable
internal fun SplashScreen(
    toHome: () -> Unit,
    toLogin: () -> Unit,
) {
    producePresenter {
        SplashPresenter(
            toHome,
            toLogin,
        ).invoke()
    }
    FlareTheme {
        Scaffold {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(it),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                )
            }
        }
    }
}
