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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.dimension.flare.data.network.misskey.MisskeyOauthService
import dev.dimension.flare.data.repository.app.UiApplication
import dev.dimension.flare.data.repository.app.addMisskeyAccountUseCase
import dev.dimension.flare.data.repository.app.getPendingOAuthUseCase
import dev.dimension.flare.data.repository.app.setPendingOAuthUseCase
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.common.plus
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

private suspend fun misskeyAuthCheckUseCase(
    host: String,
    session: String,
) {
    val response = MisskeyOauthService(
        host = host,
        session = session,
    ).check()
    requireNotNull(response.ok) { "No response" }
    require(response.ok) { "Response is not ok" }
    requireNotNull(response.token) { "No token" }
    val id = response.user?.id
    requireNotNull(id) { "No user id" }
    addMisskeyAccountUseCase(
        host = host,
        token = response.token,
        accountKey = MicroBlogKey(
            id = id,
            host = host,
        ),
    )
}

@Composable
private fun misskeyCallbackPresenter(
    session: String?,
    toHome: () -> Unit,
): UiState<Nothing> {
    if (session == null) {
        return UiState.Error(Exception("No code"))
    }
    var error by remember { mutableStateOf<Exception?>(null) }
    LaunchedEffect(session) {
        val pendingOAuth = getPendingOAuthUseCase()
        if (pendingOAuth.isEmpty()) {
            error = Exception("No pending OAuth")
        }
        for (application in pendingOAuth) {
            try {
                if (application is UiApplication.Misskey) {
                    misskeyAuthCheckUseCase(
                        host = application.host,
                        session = session,
                    )
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
        error = Exception("No pending OAuth")
    }
    if (error != null) {
        return UiState.Error(error!!)
    }
    return UiState.Loading()
}
