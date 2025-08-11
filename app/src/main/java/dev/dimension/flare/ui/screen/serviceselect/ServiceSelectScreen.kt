package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleQuestion
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.R
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.logoUrl
import dev.dimension.flare.ui.common.OnNewIntent
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.ServiceSelectPresenter
import dev.dimension.flare.ui.presenter.login.ServiceSelectState
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.fornewid.placeholder.material3.placeholder
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ServiceSelectScreen(
    onXQT: () -> Unit,
    onVVO: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val state by producePresenter {
        serviceSelectPresenter(onBack)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        modifier =
            modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (onBack != null) {
                FlareTopAppBar(
                    title = {
                    },
                    navigationIcon = {
                        BackButton(onBack = onBack)
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { contentPadding ->
        LazyStatusVerticalStaggeredGrid(
            modifier =
                Modifier.fillMaxSize(),
            columns = StaggeredGridCells.Adaptive(300.dp),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                    Alignment.CenterHorizontally,
                ),
            verticalItemSpacing = 0.dp,
            contentPadding = contentPadding,
        ) {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                Column(
                    modifier =
                        Modifier
                            .padding(
                                horizontal = screenHorizontalPadding,
                            ),
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
                        textAlign = TextAlign.Center,
                    )
                    OutlinedTextField(
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
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Xmark,
                                        contentDescription = null,
                                    )
                                } else {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.MagnifyingGlass,
                                        contentDescription = null,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.width(300.dp),
                        leadingIcon = {
                            state.detectedPlatformType
                                .onSuccess {
                                    NetworkImage(
                                        it.logoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }.onError {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.CircleQuestion,
                                        contentDescription = null,
                                    )
                                }.onLoading {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                        },
                        enabled = !state.loading,
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    AnimatedVisibility(state.canNext && state.detectedPlatformType.isSuccess) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            when (state.detectedPlatformType.takeSuccess()) {
                                null -> Unit
                                PlatformType.Bluesky -> {
                                    val oauthString = stringResource(id = R.string.bluesky_login_oauth_button)
                                    val passwordString =
                                        stringResource(id = R.string.bluesky_login_use_password_button)
                                    ButtonGroup(
                                        overflowIndicator = {},
                                    ) {
                                        toggleableItem(
                                            state.blueskyInputState.usePasswordLogin,
                                            onCheckedChange = {
                                                state.blueskyLoginState.clear()
                                                state.blueskyOauthLoginState.clear()
                                                state.blueskyInputState.setUsePasswordLogin(it)
                                            },
                                            label = passwordString,
                                        )
                                        toggleableItem(
                                            !state.blueskyInputState.usePasswordLogin,
                                            onCheckedChange = {
                                                state.blueskyLoginState.clear()
                                                state.blueskyOauthLoginState.clear()
                                                state.blueskyInputState.setUsePasswordLogin(!it)
                                            },
                                            label = oauthString,
                                        )
                                    }
                                    OutlinedTextField(
                                        state = state.blueskyInputState.username,
                                        label = {
                                            Text(text = stringResource(id = R.string.bluesky_login_username_hint))
                                        },
                                        enabled =
                                            !state.blueskyOauthLoginState.loading &&
                                                !state.blueskyLoginState.loading,
                                        modifier =
                                            Modifier
                                                .width(300.dp),
                                        lineLimits = TextFieldLineLimits.SingleLine,
                                    )
                                    AnimatedVisibility(state.blueskyInputState.usePasswordLogin) {
                                        OutlinedSecureTextField(
                                            state = state.blueskyInputState.password,
                                            label = {
                                                Text(text = stringResource(id = R.string.bluesky_login_password_hint))
                                            },
                                            enabled = !state.blueskyLoginState.loading,
                                            modifier =
                                                Modifier
                                                    .width(300.dp),
                                        )
                                    }
                                    AnimatedVisibility(state.blueskyLoginState.require2FA && state.blueskyInputState.usePasswordLogin) {
                                        OutlinedTextField(
                                            state = state.blueskyInputState.authFactorToken,
                                            label = {
                                                Text(text = stringResource(id = R.string.bluesky_login_auth_factor_token_hint))
                                            },
                                            enabled = !state.blueskyLoginState.loading,
                                            modifier =
                                                Modifier
                                                    .width(300.dp),
                                            lineLimits = TextFieldLineLimits.SingleLine,
                                        )
                                    }
                                    AnimatedVisibility(!state.blueskyInputState.usePasswordLogin) {
                                        Text(stringResource(R.string.bluesky_login_oauth_hint))
                                    }
                                    if (!state.blueskyInputState.usePasswordLogin) {
                                        OnNewIntent {
                                            state.blueskyOauthLoginState.resume(it.dataString.orEmpty())
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (state.blueskyInputState.usePasswordLogin) {
                                                state.blueskyLoginState.login(
                                                    baseUrl = state.instanceInputState.text.toString(),
                                                    username =
                                                        state.blueskyInputState.username.text
                                                            .toString(),
                                                    password =
                                                        state.blueskyInputState.password.text
                                                            .toString(),
                                                    authFactorToken =
                                                        state.blueskyInputState.authFactorToken.text
                                                            .toString(),
                                                )
                                            } else {
                                                state.blueskyOauthLoginState.login(
                                                    userName =
                                                        state.blueskyInputState.username.text
                                                            .toString(),
                                                    launchUrl = uriHandler::openUri,
                                                )
                                            }
                                        },
                                        modifier = Modifier.width(300.dp),
                                        enabled =
                                            state.blueskyInputState.canLogin &&
                                                (
                                                    !state.blueskyOauthLoginState.loading &&
                                                        !state.blueskyLoginState.loading
                                                ),
                                    ) {
                                        Text(text = stringResource(id = R.string.login_button))
                                    }
                                    if (state.blueskyOauthLoginState.error != null) {
                                        Text(
                                            text = state.blueskyOauthLoginState.error.toString(),
                                        )
                                    }
                                    if (state.blueskyOauthLoginState.loading) {
                                        LinearWavyProgressIndicator()
                                    }
                                }

                                PlatformType.Misskey -> {
                                    OnNewIntent {
                                        state.misskeyLoginState.resume(it.dataString.orEmpty())
                                    }
                                    state.misskeyLoginState.resumedState
                                        ?.onLoading {
                                            Text(
                                                text = stringResource(id = R.string.mastodon_login_verify_message),
                                            )
                                            LinearWavyProgressIndicator()
                                        }?.onError {
                                            Text(text = it.message ?: "Unknown error")
                                        } ?: run {
                                        Button(
                                            onClick = {
                                                state.misskeyLoginState.login(
                                                    state.instanceInputState.text.toString(),
                                                    launchUrl = uriHandler::openUri,
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
                                    state.mastodonLoginState.resumedState
                                        ?.onLoading {
                                            Text(
                                                text = stringResource(id = R.string.mastodon_login_verify_message),
                                            )
                                            LinearWavyProgressIndicator()
                                        }?.onError {
                                            Text(text = it.message ?: "Unknown error")
                                        } ?: run {
                                        Button(
                                            onClick = {
                                                state.mastodonLoginState.login(
                                                    state.instanceInputState.text.toString(),
                                                    launchUrl = uriHandler::openUri,
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
                                            onXQT.invoke()
                                        },
                                        modifier = Modifier.width(300.dp),
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.service_select_next_button),
                                        )
                                    }
                                }

                                PlatformType.VVo -> {
                                    Button(
                                        onClick = {
                                            onVVO.invoke()
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
                    }
                }
            }

            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                Spacer(
                    modifier =
                        Modifier
                            .height(16.dp),
                )
            }

            state.instances
                .onSuccess {
                    items(
                        count = itemCount,
                    ) {
                        val instance = get(it)
                        ServiceSelectItem(
                            instance = instance,
                            index = it,
                            totalCount = itemCount,
                            onClick = {
                                if (instance != null) {
                                    state.selectInstance(instance)
                                }
                            },
                        )
                    }
                }.onLoading {
                    items(10) {
                        ServiceSelectItem(
                            index = it,
                            totalCount = 10,
                            instance = null,
                            onClick = {},
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

@Composable
private fun ServiceSelectItem(
    instance: UiInstance?,
    onClick: () -> Unit,
    index: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    AdaptiveCard(
        index = index,
        totalCount = totalCount,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .clickable {
                        onClick.invoke()
                    }.fillMaxWidth()
                    .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            instance?.bannerUrl?.let {
                NetworkImage(
                    it,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .clip(MaterialTheme.shapes.medium),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (instance?.iconUrl.isNullOrEmpty() != true) {
                    NetworkImage(
                        instance.iconUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(24.dp),
                    )
                }
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

@OptIn(FlowPreview::class)
@Composable
private fun serviceSelectPresenter(onBack: (() -> Unit)?) =
    run {
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
                )
            }.invoke()
        LaunchedEffect(Unit) {
            snapshotFlow {
                instanceInputState.text
            }.distinctUntilChanged()
                .collect {
                    state.setFilter(it.toString())
                }
        }
        val blueskyInputState = blueskyInputPresenter()
        object : ServiceSelectState by state {
            val instanceInputState = instanceInputState
            val blueskyInputState = blueskyInputState

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

@Composable
private fun blueskyInputPresenter() =
    run {
        var usePasswordLogin by remember { mutableStateOf(true) }
        val username = rememberTextFieldState()
        val password = rememberTextFieldState()
        val authFactorToken = rememberTextFieldState()
        val canLogin by remember(username, password) {
            derivedStateOf {
                username.text.isNotEmpty() &&
                    if (usePasswordLogin) {
                        password.text.isNotEmpty()
                    } else {
                        true
                    }
            }
        }
        object {
            val username = username
            val password = password
            val authFactorToken = authFactorToken
            val usePasswordLogin = usePasswordLogin
            val canLogin = canLogin

            fun setUsePasswordLogin(value: Boolean) {
                usePasswordLogin = value
            }
        }
    }
