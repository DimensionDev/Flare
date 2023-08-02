package dev.dimension.flare.ui.screen.serviceselect

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.screen.destinations.MastodonLoginRouteDestination
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding

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
//            navigator.navigate(MisskeyLoginRouteDestination)
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
                            onClick = toPasskey,
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Text(text = stringResource(id = R.string.service_select_passkey))
                        }
                        FilledTonalButton(
                            onClick = toMastodon,
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Text(text = stringResource(id = R.string.service_select_mastodon))
                        }
                        FilledTonalButton(
                            onClick = toMisskey,
                            enabled = false,
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
