package dev.dimension.flare.ui.screen.login.misskey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.popUpTo
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.login.MisskeyCallbackPresenter
import dev.dimension.flare.ui.screen.destinations.HomeRouteDestination
import dev.dimension.flare.ui.screen.destinations.MisskeyCallbackRouteDestination
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@Preview
@Composable
fun MisskeyCallbackScreenPreview() {
    MisskeyCallbackScreen(
        session = "code",
        toHome = {},
    )
}

@Destination(
    deepLinks = [
        DeepLink(
            uriPattern = "${AppDeepLink.Callback.Misskey}?session={session}",
        ),
    ],
)
@Composable
fun MisskeyCallbackRoute(
    session: String?,
    navigator: DestinationsNavigator,
) {
    MisskeyCallbackScreen(
        session = session,
        toHome = {
            navigator.navigate(HomeRouteDestination) {
                popUpTo(MisskeyCallbackRouteDestination) {
                    inclusive = true
                }
            }
        },
    )
}

@Composable
internal fun MisskeyCallbackScreen(
    session: String?,
    toHome: () -> Unit,
) {
    val state by producePresenter {
        misskeyCallbackPresenter(
            session = session,
            toHome = toHome,
        )
    }
    FlareTheme {
        Scaffold {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it + PaddingValues(horizontal = screenHorizontalPadding)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = stringResource(id = R.string.misskey_login_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(id = R.string.mastodon_login_verify_message),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2f)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (val data = state) {
                        is UiState.Error -> {
                            Text(text = data.throwable.message ?: "Unknown error")
                        }

                        is UiState.Loading -> {
                            CircularProgressIndicator()
                        }

                        is UiState.Success -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun misskeyCallbackPresenter(
    session: String?,
    toHome: () -> Unit,
): UiState<Nothing> {
    return remember(
        session,
        toHome,
    ) {
        MisskeyCallbackPresenter(
            session = session,
            toHome = toHome,
        )
    }.invoke()
}
