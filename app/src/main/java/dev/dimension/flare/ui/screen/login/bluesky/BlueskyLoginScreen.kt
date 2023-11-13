package dev.dimension.flare.ui.screen.login.bluesky

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text2.input.TextFieldLineLimits
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.delete
import androidx.compose.foundation.text2.input.insert
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.popUpTo
import dev.dimension.flare.R
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.OutlinedSecureTextField2
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.presenter.login.BlueskyLoginPresenter
import dev.dimension.flare.ui.screen.destinations.BlueskyLoginRouteDestination
import dev.dimension.flare.ui.screen.destinations.HomeRouteDestination
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@Composable
@Destination
fun BlueskyLoginRoute(navigator: DestinationsNavigator) {
    BlueskyLoginScreen(
        onBack = navigator::navigateUp,
        toHome = {
            navigator.navigate(HomeRouteDestination) {
                popUpTo(BlueskyLoginRouteDestination) {
                    inclusive = true
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun BlueskyLoginScreen(
    onBack: () -> Unit = {},
    toHome: () -> Unit = {},
) {
    val state by producePresenter {
        loginPresenter(toHome)
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    FlareTheme {
        Scaffold(
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
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(it + PaddingValues(horizontal = screenHorizontalPadding)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
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
                        text = stringResource(id = R.string.bluesky_login_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(id = R.string.bluesky_login_message),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
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
                    OutlinedTextField2(
                        state = state.baseUrl,
                        label = {
                            Text(text = stringResource(id = R.string.bluesky_login_base_url_hint))
                        },
                        enabled = !state.state.loading,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(
                                    focusRequester = focusRequester,
                                ),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    state.setDropdown(!state.showDropdown)
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = stringResource(id = R.string.navigate_back),
                                )
                                DropdownMenu(
                                    expanded = state.showDropdown,
                                    onDismissRequest = { state.setDropdown(false) },
                                ) {
                                    KnownInstance.entries.forEach {
                                        DropdownMenuItem(
                                            text = { Text(text = it.url) },
                                            onClick = { state.selectBaseUrl(it.url) },
                                        )
                                    }
                                }
                            }
                        },
                    )
                    OutlinedTextField2(
                        state = state.username,
                        label = {
                            Text(text = stringResource(id = R.string.bluesky_login_username_hint))
                        },
                        enabled = !state.state.loading,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(
                                    focusRequester = focusRequester,
                                ),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    OutlinedSecureTextField2(
                        state = state.password,
                        label = {
                            Text(text = stringResource(id = R.string.bluesky_login_password_hint))
                        },
                        enabled = !state.state.loading,
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        onSubmit = {
                            state.state.login(
                                state.baseUrl.text.toString(),
                                state.username.text.toString(),
                                state.password.text.toString(),
                            )
                            true
                        },
                    )
                    Button(
                        onClick = {
                            state.state.login(
                                state.baseUrl.text.toString(),
                                state.username.text.toString(),
                                state.password.text.toString(),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(id = R.string.login_button))
                    }
                    state.state.error?.let { error ->
                        Text(text = error.toString())
                    }
                }
            }
        }
    }
}

private enum class KnownInstance(val url: String) {
    Main("https://bsky.social"),
    Staging("https://staging.bsky.dev"),
    Local("http://localhost:2583"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun loginPresenter(toHome: () -> Unit = {}) =
    run {
        val baseUrl by remember { mutableStateOf(TextFieldState("https://bsky.social")) }
        val username by remember { mutableStateOf(TextFieldState("")) }
        val password by remember { mutableStateOf(TextFieldState("")) }
        var showDropdown by remember { mutableStateOf(false) }

        val state =
            remember(toHome) {
                BlueskyLoginPresenter(toHome)
            }.invoke()

        object {
            val baseUrl = baseUrl
            val username = username
            val password = password
            var showDropdown = showDropdown
            val state = state

            fun setDropdown(value: Boolean) {
                showDropdown = value
            }

            fun selectBaseUrl(value: String) {
                baseUrl.edit {
                    this.delete(0, baseUrl.text.length)
                    this.insert(0, value)
                }
                showDropdown = false
            }
        }
    }
