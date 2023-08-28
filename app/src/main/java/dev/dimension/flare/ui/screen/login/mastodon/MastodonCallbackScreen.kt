package dev.dimension.flare.ui.screen.login.mastodon

import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.popUpTo
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.network.mastodon.MastodonOAuthService
import dev.dimension.flare.data.repository.app.UiApplication
import dev.dimension.flare.data.repository.app.addMastodonAccountUseCase
import dev.dimension.flare.data.repository.app.getPendingOAuthUseCase
import dev.dimension.flare.data.repository.app.setPendingOAuthUseCase
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.screen.destinations.HomeRouteDestination
import dev.dimension.flare.ui.screen.destinations.MastodonCallbackRouteDestination
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@Preview
@Composable
fun MastodonCallbackScreenPreview() {
    MastodonCallbackScreen(
        code = "code",
        toHome = {},
    )
}

@Destination(
    deepLinks = [
        DeepLink(
            uriPattern = "${AppDeepLink.Callback.Mastodon}?code={code}",
        ),
    ],
)
@Composable
fun MastodonCallbackRoute(
    code: String?,
    navigator: DestinationsNavigator,
) {
    MastodonCallbackScreen(
        code = code,
        toHome = {
            navigator.navigate(HomeRouteDestination) {
                popUpTo(MastodonCallbackRouteDestination) {
                    inclusive = true
                }
            }
        },
    )
}

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
    FlareTheme {
        Scaffold {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it + PaddingValues(horizontal = screenHorizontalPadding)),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
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
private fun mastodonCallbackPresenter(
    code: String?,
    toHome: () -> Unit,
): UiState<Nothing> {
    if (code == null) {
        return UiState.Error(Exception("No code"))
    }
    var error by remember { mutableStateOf<Exception?>(null) }
    LaunchedEffect(code) {
        val pendingOAuth = getPendingOAuthUseCase()
        if (pendingOAuth.isEmpty()) {
            error = Exception("No pending OAuth")
        }
        for (application in pendingOAuth) {
            try {
                if (application is UiApplication.Mastodon) {
                    tryPendingOAuth(application, code)
                    setPendingOAuthUseCase(application.host, false)
                    toHome.invoke()
                    break
                } else {
                    continue
                }
            } catch (e: Exception) {
                error = e
                e.printStackTrace()
                break
            }
        }
    }
    if (error != null) {
        return UiState.Error(error!!)
    }
    return UiState.Loading()
}

private suspend fun tryPendingOAuth(
    application: UiApplication.Mastodon,
    code: String,
) {
    val host = application.host
    val baseUrl = Uri.parse("https://$host/")
    val service = MastodonOAuthService(
        baseUrl = baseUrl.toString(),
        client_name = "Flare",
        website = "https://github.com/DimensionDev/Flare",
        redirect_uri = AppDeepLink.Callback.Mastodon,
    )
    val accessTokenResponse = service.getAccessToken(code, application.application)
    requireNotNull(accessTokenResponse.accessToken) { "Invalid access token" }
    val user = service.verifyCredentials(accessToken = accessTokenResponse.accessToken)
    val id = user.id
    requireNotNull(id) { "Invalid user id" }
    addMastodonAccountUseCase(
        instance = host,
        accessToken = accessTokenResponse.accessToken,
        accountKey = MicroBlogKey(id, host),
    )
}
