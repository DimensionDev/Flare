package dev.dimension.flare.ui.screen.login

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.network.mastodon.MastodonOAuthService
import dev.dimension.flare.data.repository.app.UiApplication
import dev.dimension.flare.data.repository.app.addMastodonApplicationUseCase
import dev.dimension.flare.data.repository.app.findApplicationUseCase
import dev.dimension.flare.data.repository.app.setPendingOAuthUseCase
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch

@Composable
@Preview(showBackground = true)
fun LoginScreenPreview() {
    MastodonLoginScreen()
}

@Destination
@Composable
fun MastodonLoginRoute() {
    MastodonLoginScreen()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MastodonLoginScreen() {
    val uriHandler = LocalUriHandler.current
    val state by producePresenter {
        loginPresenter(
            launchUrl = {
                uriHandler.openUri(it)
            }
        )
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    FlareTheme {
        Scaffold {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it + PaddingValues(horizontal = screenHorizontalPadding)),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                ) {
                    Text(
                        text = stringResource(id = R.string.mastodon_login_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(id = R.string.mastodon_login_message),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2f)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField2(
                        state = state.hostTextState,
                        label = {
                            Text(text = stringResource(id = R.string.mastodon_login_hint))
                        },
                        enabled = !state.loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(
                                focusRequester = focusRequester
                            )
                    )
                    Button(
                        onClick = state::login,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(id = R.string.login_button))
                    }
                    state.error?.let { error ->
                        Text(text = error)
                    }
                }
            }
        }
    }
}

private suspend fun mastodonLoginUseCase(
    domain: String,
    launchOAuth: (String) -> Unit
) {
    val baseUrl = if (domain.startsWith("http://", ignoreCase = true) || domain.startsWith(
            "https://",
            ignoreCase = true
        )
    ) {
        Uri.parse(domain)
    } else {
        Uri.parse("https://$domain/")
    }
    val host = baseUrl.host
    requireNotNull(host) { "Invalid host" }
    val service = MastodonOAuthService(
        baseUrl = baseUrl.toString(),
        client_name = "Flare",
        website = "https://github.com/TwidereProject/TwidereX-Android",
        redirect_uri = AppDeepLink.Callback.Mastodon
    )

    val application = findApplicationUseCase(host)?.let {
        if (it is UiApplication.Mastodon) {
            it.application
        } else {
            null
        }
    } ?: service.createApplication().also {
        addMastodonApplicationUseCase(host, it)
    }
    setPendingOAuthUseCase(host, true)
    val target = service.getWebOAuthUrl(application)
    launchOAuth(target)
//    val code = BrowserLoginDeepLinksChannel.waitOne().substringAfter("code=")
//    require(code.isNotEmpty()) { "Invalid code" }
//    val accessTokenResponse = service.getAccessToken(code, application)
//    requireNotNull(accessTokenResponse.accessToken) { "Invalid access token" }
//    val user = service.verifyCredentials(accessToken = accessTokenResponse.accessToken)
//    val id = user.id
//    requireNotNull(id) { "Invalid user id" }
//    addMastodonAccountUseCase(
//        instance = host,
//        accessToken = accessTokenResponse.accessToken,
//        accountKey = MicroBlogKey(id, host),
//    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun loginPresenter(
    launchUrl: (String) -> Unit
) = run {
    val hostTextState by remember {
        mutableStateOf(TextFieldState(""))
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    object {
        val hostTextState = hostTextState
        val loading = loading
        val error = error

        fun login() {
            scope.launch {
                loading = true
                error = null
                runCatching {
                    mastodonLoginUseCase(
                        domain = hostTextState.text.toString(),
                        launchOAuth = launchUrl
                    )
                }.onFailure {
                    error = it.message
                }
                loading = false
            }
        }
    }
}
