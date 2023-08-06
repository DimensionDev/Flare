package dev.dimension.flare.ui.screen.serviceselect

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.screen.destinations.MastodonLoginRouteDestination
import dev.dimension.flare.ui.screen.destinations.MisskeyLoginRouteDestination
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch

@Composable
@Destination
fun ServiceSelectRoute(
    navigator: DestinationsNavigator
) {
    ServiceSelectScreen(
        toMastodon = {
            navigator.navigate(MastodonLoginRouteDestination)
        },
        toPasskey = {
//            navigator.navigate(PasskeyLoginRouteDestination)
        },
        toMisskey = {
            navigator.navigate(MisskeyLoginRouteDestination)
        }
    )
}

@Composable
fun ServiceSelectScreen(
    toMastodon: () -> Unit,
    toPasskey: () -> Unit,
    toMisskey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val state by producePresenter {
        serviceSelectPresenter(activityContext = view.context)
    }
    FlareTheme {
        Scaffold(
            modifier = modifier
        ) {
            Box(
                modifier = Modifier
                    .padding(it + PaddingValues(horizontal = screenHorizontalPadding))
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                    ) {
                        Text(
                            text = stringResource(id = R.string.service_select_welcome_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = stringResource(id = R.string.service_select_welcome_message),
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
                        Button(
                            onClick = {
                                state.launchPasskey()
                            },
                            modifier = Modifier
                                .fillMaxWidth(),
                            enabled = state.loading.not()
                        ) {
                            Text(text = stringResource(id = R.string.service_select_passkey))
                        }
                        FilledTonalButton(
                            onClick = toMastodon,
                            modifier = Modifier
                                .fillMaxWidth(),
                            enabled = state.loading.not()
                        ) {
                            Text(text = stringResource(id = R.string.service_select_mastodon))
                        }
                        FilledTonalButton(
                            onClick = toMisskey,
                            enabled = state.loading.not(),
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Text(text = stringResource(id = R.string.service_select_misskey))
                        }
                    }
                }
            }
        }
    }
}

val requestJson = """
    {
      "challenge": "T1xCsnxM2DNL2KdK5CLa6fMhD7OBqho6syzInk_n-Uo",
      "allowCredentials": [],
      "timeout": 1800000,
      "userVerification": "required",
      "rpId": "credential-manager-app-test.glitch.me"
    }
""".trimIndent()

@Composable
private fun serviceSelectPresenter(
    activityContext: Context
) = run {
    var loading by remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()
    object {
        val loading = loading
        fun launchPasskey() {
            scope.launch {
                // https://developer.android.com/training/sign-in/passkeys
                loading = true
                val credentialManager = CredentialManager.create(activityContext)
                val getPasswordOption = GetPasswordOption()

                val getPublicKeyCredentialOption = GetPublicKeyCredentialOption(
                    requestJson = requestJson
                )

                val getCredRequest = GetCredentialRequest(
                    listOf(getPasswordOption, getPublicKeyCredentialOption)
                )

                val result = runCatching {
                    credentialManager.getCredential(
                        context = activityContext,
                        request = getCredRequest
                    )
                }
                loading = false
            }
        }
    }
}
