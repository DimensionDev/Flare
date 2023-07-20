package dev.dimension.flare.ui.screen.login

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.BrowserLoginDeepLinksChannel
import dev.dimension.flare.data.network.mastodon.MastodonOAuthService
import dev.dimension.flare.data.repository.addMastodonAccountUseCase
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.coroutines.launch

@Composable
@Preview(showBackground = true)
fun LoginScreenPreview() {
    LoginScreen(toHome = {})
}

@Composable
internal fun LoginScreen(
    toHome: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val state by producePresenter {
        LoginPresenter(
            toHome = toHome,
            launchUrl = {
                uriHandler.openUri(it)
            },
        )
    }
    FlareTheme {
        Scaffold {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TextField(
                    value = state.host,
                    onValueChange = state::setHost,
                    label = {
                        Text("Host")
                    },
                    enabled = !state.loading,
                )
                Button(
                    onClick = state::login,
                ) {
                    Text("Login")
                }
                if (state.error != null) {
                    Text(state.error!!)
                }
            }
        }
    }
}

private suspend fun mastodonLoginUseCase(
    domain: String,
    launchOAuth: (String) -> Unit,
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
        redirect_uri = AppDeepLink.Callback.Mastodon,
    )
    val application = service.createApplication()
    val target = service.getWebOAuthUrl(application)
    launchOAuth(target)
    val code = BrowserLoginDeepLinksChannel.waitOne().substringAfter("code=")
    require(code.isNotEmpty()) { "Invalid code" }
    val accessTokenResponse = service.getAccessToken(code, application)
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

@Composable
private fun LoginPresenter(
    toHome: () -> Unit,
    launchUrl: (String) -> Unit,
) = run {
    var host by remember { mutableStateOf(TextFieldValue()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    object {
        val host = host
        val loading = loading
        val error = error
        fun setHost(value: TextFieldValue) {
            host = value
        }

        fun login() {
            scope.launch {
                loading = true
                error = null
                runCatching {
                    mastodonLoginUseCase(
                        domain = host.text,
                        launchOAuth = launchUrl,
                    )
                }.onFailure {
                    error = it.message
                }.onSuccess {
                    toHome()
                }
                loading = false
            }
        }
    }
}