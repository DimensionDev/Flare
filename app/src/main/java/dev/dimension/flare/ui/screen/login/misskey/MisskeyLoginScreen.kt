package dev.dimension.flare.ui.screen.login.misskey

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text2.input.TextFieldLineLimits
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.network.misskey.MisskeyOauthService
import dev.dimension.flare.data.repository.app.addMisskeyApplicationUseCase
import dev.dimension.flare.data.repository.app.setPendingOAuthUseCase
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch
import java.util.UUID

@Destination
@Composable
fun MisskeyLoginRoute() {
    MisskeyLoginScreen()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MisskeyLoginScreen(
    modifier: Modifier = Modifier
) {
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
        Scaffold(
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it + PaddingValues(horizontal = screenHorizontalPadding)),
                horizontalAlignment = Alignment.CenterHorizontally,
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
                        text = stringResource(id = R.string.misskey_login_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(id = R.string.misskey_login_message),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
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
                            Text(text = stringResource(id = R.string.misskey_login_hint))
                        },
                        enabled = !state.loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(
                                focusRequester = focusRequester
                            ),
                        lineLimits = TextFieldLineLimits.SingleLine
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

private suspend fun misskeyLoginUseCase(
    host: String,
    launchOAuth: (String) -> Unit
) {
    val session = UUID.randomUUID().toString()
    val service = MisskeyOauthService(
        host = host,
        name = "Flare",
        callback = AppDeepLink.Callback.Misskey
    )
    addMisskeyApplicationUseCase(host, session)
    setPendingOAuthUseCase(host, true)
    val target = service.getAuthorizeUrl()
    launchOAuth(target)
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
                    misskeyLoginUseCase(
                        host = hostTextState.text.toString(),
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
