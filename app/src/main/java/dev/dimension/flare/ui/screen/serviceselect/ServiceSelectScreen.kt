package dev.dimension.flare.ui.screen.serviceselect

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.screen.destinations.BlueskyLoginRouteDestination
import dev.dimension.flare.ui.screen.destinations.MastodonLoginRouteDestination
import dev.dimension.flare.ui.screen.destinations.MisskeyLoginRouteDestination
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch

@Composable
@Destination
fun ServiceSelectRoute(navigator: DestinationsNavigator) {
    ServiceSelectScreen(
        toMastodon = {
            navigator.navigate(MastodonLoginRouteDestination)
        },
        toMisskey = {
            navigator.navigate(MisskeyLoginRouteDestination)
        },
        toBluesky = {
            navigator.navigate(BlueskyLoginRouteDestination)
        },
        onBack = navigator::navigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceSelectScreen(
    toMastodon: () -> Unit,
    toMisskey: () -> Unit,
    toBluesky: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val view = LocalView.current
    val state by producePresenter {
        serviceSelectPresenter(activityContext = view.context)
    }
    FlareTheme {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = {
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigate_back),
                            )
                        }
                    },
                )
            },
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(it + PaddingValues(horizontal = screenHorizontalPadding))
                        .fillMaxSize(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(0.8f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) {
                        Text(
                            text = stringResource(id = R.string.service_select_welcome_title),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = stringResource(id = R.string.service_select_welcome_message),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                    LazyVerticalGrid(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(2f)
                                .padding(horizontal = 16.dp),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp,
                                Alignment.CenterHorizontally,
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp,
                                Alignment.Top,
                            ),
                        columns = GridCells.Adaptive(112.dp),
                    ) {
                        item {
                            Card(
                                onClick = {
                                    state.launchPasskey()
                                },
                                modifier =
                                    Modifier
                                        .aspectRatio(1f),
                                enabled = state.loading.not(),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            8.dp,
                                            Alignment.CenterVertically,
                                        ),
                                ) {
                                    Text(text = stringResource(id = R.string.service_select_passkey))
                                }
                            }
                        }
                        item {
                            Card(
                                onClick = toMastodon,
                                modifier =
                                    Modifier
                                        .aspectRatio(1f),
                                enabled = state.loading.not(),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            8.dp,
                                            Alignment.CenterVertically,
                                        ),
                                ) {
                                    NetworkImage(
                                        model = "https://joinmastodon.org/logos/logo-purple.svg",
                                        contentDescription = "Mastodon Logo",
                                        modifier = Modifier.size(64.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                    Text(text = stringResource(id = R.string.service_select_mastodon))
                                }
                            }
                        }
                        item {
                            Card(
                                onClick = toMisskey,
                                enabled = state.loading.not(),
                                modifier =
                                    Modifier
                                        .aspectRatio(1f),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            8.dp,
                                            Alignment.CenterVertically,
                                        ),
                                ) {
                                    NetworkImage(
                                        model = "https://raw.githubusercontent.com/misskey-dev/assets/main/favicon.png",
                                        contentDescription = "Misskey Logo",
                                        modifier = Modifier.size(64.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                    Text(text = stringResource(id = R.string.service_select_misskey))
                                }
                            }
                        }
                        item {
                            Card(
                                onClick = toBluesky,
                                enabled = state.loading.not(),
                                modifier =
                                    Modifier
                                        .aspectRatio(1f),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            8.dp,
                                            Alignment.CenterVertically,
                                        ),
                                ) {
                                    NetworkImage(
                                        model = "https://blueskyweb.xyz/images/apple-touch-icon.png",
                                        contentDescription = "Bluesky Logo",
                                        modifier = Modifier.size(64.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                    Text(text = stringResource(id = R.string.service_select_bluesky))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

val requestJson =
    """
    {
      "challenge": "T1xCsnxM2DNL2KdK5CLa6fMhD7OBqho6syzInk_n-Uo",
      "allowCredentials": [],
      "timeout": 1800000,
      "userVerification": "required",
      "rpId": "credential-manager-app-test.glitch.me"
    }
    """.trimIndent()

@Composable
private fun serviceSelectPresenter(activityContext: Context) =
    run {
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

                    val getPublicKeyCredentialOption =
                        GetPublicKeyCredentialOption(
                            requestJson = requestJson,
                        )

                    val getCredRequest =
                        GetCredentialRequest(
                            listOf(getPasswordOption, getPublicKeyCredentialOption),
                        )

                    runCatching {
                        credentialManager.getCredential(
                            context = activityContext,
                            request = getCredRequest,
                        )
                    }
                    loading = false
                }
            }
        }
    }
