package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.text2.input.TextFieldLineLimits
import androidx.compose.foundation.text2.input.rememberTextFieldState
import androidx.compose.foundation.text2.input.textAsFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.NavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.NavResult
import com.ramcosta.composedestinations.result.ResultRecipient
import dev.dimension.flare.R
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.logoUrl
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.OnNewIntent
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.OutlinedSecureTextField2
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.placeholder.placeholder
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.ServiceSelectPresenter
import dev.dimension.flare.ui.presenter.login.ServiceSelectState
import dev.dimension.flare.ui.screen.destinations.XQTLoginRouteDestination
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
@Destination(
    wrappers = [ThemeWrapper::class],
)
fun ServiceSelectRoute(
    navigator: DestinationsNavigator,
    resultRecipient: ResultRecipient<XQTLoginRouteDestination, Boolean>,
) {
    resultRecipient.onNavResult {
        when (it) {
            NavResult.Canceled -> Unit
            is NavResult.Value -> {
                if (it.value) {
                    navigator.navigateUp()
                }
            }
        }
    }
    ServiceSelectScreen(
        onBack = navigator::navigateUp,
        onXQT = {
            navigator.navigate(XQTLoginRouteDestination)
        },
    )
}

@NavGraph
annotation class EntryNavGraph(
    val start: Boolean = false,
)

@Composable
@Destination(
    wrappers = [ThemeWrapper::class],
)
@EntryNavGraph(start = true)
fun EntryServiceSelectRoute(navigator: DestinationsNavigator) {
    ServiceSelectScreen(
        onBack = navigator::navigateUp,
        onXQT = {
            navigator.navigate(XQTLoginRouteDestination)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ServiceSelectScreen(
    onXQT: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val state by producePresenter {
        serviceSelectPresenter(uriHandler::openUri, onBack)
    }
    FlareTheme {
        Scaffold(
            modifier = modifier,
            topBar = {
                if (onBack != null) {
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
                }
            },
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(it + PaddingValues(horizontal = screenHorizontalPadding))
                        .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                OutlinedTextField2(
                    state = state.instanceInputState,
                    placeholder = {
                        Text(
                            text = stringResource(id = R.string.service_select_instance_input_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (state.instanceInputState.text.any()) {
                                state.clearInstance()
                            }
                        }) {
                            if (state.instanceInputState.text.any()) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    modifier = Modifier.width(300.dp),
                    leadingIcon = {
                        state.detectedPlatformType.onSuccess {
                            NetworkImage(
                                it.logoUrl,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }.onError {
                            Icon(
                                imageVector = Icons.Filled.QuestionMark,
                                contentDescription = null,
                            )
                        }.onLoading {
                            Icon(
                                imageVector = Icons.Filled.Web,
                                contentDescription = null,
                                modifier = Modifier.placeholder(true),
                            )
                        }
                    },
                    enabled = !state.loading,
                )
                if (state.canNext) {
                    state.detectedPlatformType.onSuccess {
                        when (it) {
                            PlatformType.Bluesky -> {
                                OutlinedTextField2(
                                    state = state.blueskyInputState.username,
                                    label = {
                                        Text(text = stringResource(id = R.string.bluesky_login_username_hint))
                                    },
                                    enabled = !state.blueskyLoginState.loading,
                                    modifier =
                                        Modifier
                                            .width(300.dp),
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                )
                                OutlinedSecureTextField2(
                                    state = state.blueskyInputState.password,
                                    label = {
                                        Text(text = stringResource(id = R.string.bluesky_login_password_hint))
                                    },
                                    enabled = !state.blueskyLoginState.loading,
                                    modifier =
                                        Modifier
                                            .width(300.dp),
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    onSubmit = {
                                        state.blueskyLoginState.login(
                                            "https://${state.instanceInputState.text}",
                                            state.blueskyInputState.username.text.toString(),
                                            state.blueskyInputState.password.text.toString(),
                                        )
                                        true
                                    },
                                )
                                Button(
                                    onClick = {
                                        state.blueskyLoginState.login(
                                            "https://${state.instanceInputState.text}",
                                            state.blueskyInputState.username.text.toString(),
                                            state.blueskyInputState.password.text.toString(),
                                        )
                                    },
                                    modifier = Modifier.width(300.dp),
                                    enabled = state.blueskyInputState.canLogin && !state.blueskyLoginState.loading,
                                ) {
                                    Text(text = stringResource(id = R.string.login_button))
                                }
                            }

                            PlatformType.Misskey -> {
                                OnNewIntent {
                                    state.misskeyLoginState.resume(it.dataString.orEmpty())
                                }
                                state.misskeyLoginState.resumedState?.onLoading {
                                    Text(
                                        text = stringResource(id = R.string.mastodon_login_verify_message),
                                    )
                                    CircularProgressIndicator()
                                }?.onError {
                                    Text(text = it.message ?: "Unknown error")
                                } ?: run {
                                    Button(
                                        onClick = {
                                            state.misskeyLoginState.login(
                                                state.instanceInputState.text.toString(),
                                            )
                                        },
                                        modifier = Modifier.width(300.dp),
                                        enabled = !state.misskeyLoginState.loading,
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.service_select_next_button),
                                        )
                                    }
                                    state.misskeyLoginState.error?.let {
                                        Text(text = it)
                                    }
                                }
                            }

                            PlatformType.Mastodon -> {
                                OnNewIntent {
                                    state.mastodonLoginState.resume(it.dataString.orEmpty())
                                }
                                state.mastodonLoginState.resumedState?.onLoading {
                                    Text(
                                        text = stringResource(id = R.string.mastodon_login_verify_message),
                                    )
                                    CircularProgressIndicator()
                                }?.onError {
                                    Text(text = it.message ?: "Unknown error")
                                } ?: run {
                                    Button(
                                        onClick = {
                                            state.mastodonLoginState.login(
                                                state.instanceInputState.text.toString(),
                                            )
                                        },
                                        modifier = Modifier.width(300.dp),
                                        enabled = !state.mastodonLoginState.loading,
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.service_select_next_button),
                                        )
                                    }
                                    state.mastodonLoginState.error?.let {
                                        Text(text = it)
                                    }
                                }
                            }

                            PlatformType.xQt -> {
                                Button(
                                    onClick = {
                                        onXQT?.invoke()
                                    },
                                    modifier = Modifier.width(300.dp),
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.service_select_next_button),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                        columns = StaggeredGridCells.Adaptive(300.dp),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp,
                                Alignment.CenterHorizontally,
                            ),
                        verticalItemSpacing = 8.dp,
                    ) {
                        state.instances.onSuccess {
                            items(
                                count = state.instances.itemCount,
                            ) {
                                val instance = state.instances.peek(it)
                                ServiceSelectItem(
                                    instance = instance,
                                    modifier =
                                        Modifier.clickable {
                                            if (instance != null) {
                                                state.selectInstance(instance)
                                            }
                                        },
                                )
                            }
                        }.onLoading {
                            items(10) {
                                ServiceSelectItem(
                                    instance = null,
                                )
                            }
                        }.onEmpty {
                            items(1) {
                                Text(
                                    text = stringResource(id = R.string.service_select_empty_message),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceSelectItem(
    instance: UiInstance?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
    ) {
        Box {
            instance?.bannerUrl?.let {
                NetworkImage(
                    it,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .alpha(0.15f),
                )
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    NetworkImage(
                        instance?.iconUrl ?: "",
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .placeholder(instance == null),
                    )
                    Text(
                        text = instance?.name ?: "Loading...",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.placeholder(instance == null),
                    )
                }
                Text(
                    text = instance?.domain ?: "Loading...",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.placeholder(instance == null),
                )
                Text(
                    text =
                        instance?.description
                            ?: "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.placeholder(instance == null),
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
private fun serviceSelectPresenter(
    launchUrl: (String) -> Unit,
    onBack: (() -> Unit)?,
) = run {
    val instanceInputState = rememberTextFieldState()
    val state =
        remember {
            ServiceSelectPresenter(
                toHome = {
                    instanceInputState.edit {
                        replace(0, instanceInputState.text.length, "")
                    }
                    onBack?.invoke()
                },
                launchUrl = launchUrl,
            )
        }.invoke()
    LaunchedEffect(Unit) {
        instanceInputState
            .textAsFlow()
            .distinctUntilChanged()
            .debounce(666L)
            .collect {
                state.setFilter(it.toString())
            }
    }
    val blueskyLoginState = blueskyLoginPresenter()
    object : ServiceSelectState by state {
        val instanceInputState = instanceInputState
        val blueskyInputState = blueskyLoginState

        fun selectInstance(instance: UiInstance) {
            instanceInputState.edit {
                replace(0, instanceInputState.text.length, instance.domain)
            }
            setFilter(instance.domain)
        }

        fun clearInstance() {
            instanceInputState.edit {
                replace(0, instanceInputState.text.length, "")
            }
            setFilter("")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun blueskyLoginPresenter() =
    run {
        val username = rememberTextFieldState()
        val password = rememberTextFieldState()
        val canLogin by remember(username, password) {
            derivedStateOf {
                username.text.isNotEmpty() && password.text.isNotEmpty()
            }
        }
        object {
            val username = username
            val password = password
            val canLogin = canLogin
        }
    }
