package dev.dimension.flare.ui.screen.login.mastodon

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
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.MastodonCallbackPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding

// @Destination<RootGraph>(
//    deepLinks = [
//        DeepLink(
//            uriPattern = "${AppDeepLink.Callback.MASTODON}?code={code}",
//        ),
//    ],
//    wrappers = [ThemeWrapper::class],
// )
// @Composable
// fun MastodonCallbackRoute(
//    code: String?,
//    navigator: DestinationsNavigator,
// ) {
//    MastodonCallbackScreen(
//        code = code,
//        toHome = {
//            navigator.navigate(HomeRouteDestination) {
//                popUpTo(MastodonCallbackRouteDestination) {
//                    inclusive = true
//                }
//            }
//        },
//    )
// }

@Composable
internal fun MastodonCallbackScreen(
    code: String?,
    toHome: () -> Unit,
) {
    val state by producePresenter {
        mastodonCallbackPresenter(
            code = code,
            toHome = toHome,
        )
    }
    Scaffold {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it + PaddingValues(horizontal = screenHorizontalPadding)),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = stringResource(id = R.string.mastodon_login_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(id = R.string.mastodon_login_verify_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Column(
                modifier =
                    Modifier
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

@Composable
private fun mastodonCallbackPresenter(
    code: String?,
    toHome: () -> Unit,
): UiState<Nothing> {
    if (code == null) {
        return UiState.Error(Throwable("No code"))
    }
    return remember(code, toHome) {
        MastodonCallbackPresenter(
            code = code,
            toHome = toHome,
        )
    }.invoke()
}
