package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleQuestion
import dev.dimension.flare.LocalContentPadding
import dev.dimension.flare.Res
import dev.dimension.flare.bluesky_login_2fa
import dev.dimension.flare.bluesky_login_password
import dev.dimension.flare.bluesky_login_username
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.mastodon_login_verify_message
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.logoUrl
import dev.dimension.flare.service_select_empty_message
import dev.dimension.flare.service_select_instance_input_placeholder
import dev.dimension.flare.service_select_next_button
import dev.dimension.flare.service_select_welcome_message
import dev.dimension.flare.service_select_welcome_title
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.platform.placeholder
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
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.tlaster.precompose.molecule.producePresenter
import org.apache.commons.lang3.SystemUtils
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ServiceSelectScreen(
    onBack: () -> Unit,
    onXQT: () -> Unit,
    onVVO: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var host by remember { mutableStateOf(TextFieldValue("")) }
    val state by producePresenter {
        presenter(
            onBack = onBack,
        )
    }
    LaunchedEffect(Unit) {
        snapshotFlow { host }
            .distinctUntilChanged()
            .collect {
                state.setFilter(it.text)
            }
    }
    Column(
        modifier = Modifier.padding(LocalContentPadding.current),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.service_select_welcome_title),
            style = FluentTheme.typography.title,
        )
        Text(
            stringResource(Res.string.service_select_welcome_message, SystemUtils.OS_NAME),
            textAlign = TextAlign.Center,
        )
        TextField(
            value = host,
            onValueChange = { host = it },
            placeholder = { stringResource(Res.string.service_select_instance_input_placeholder) },
            trailing = {
                Box(
                    modifier = Modifier.padding(4.dp),
                ) {
                    state.detectedPlatformType
                        .onSuccess {
                            NetworkImage(
                                it.logoUrl,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }.onError {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.CircleQuestion,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }.onLoading {
                            ProgressRing(
                                modifier = Modifier.size(16.dp),
                            )
                        }
                }
            },
            maxLines = 1,
            modifier = Modifier.width(300.dp),
        )
        AnimatedVisibility(state.canNext && state.detectedPlatformType.isSuccess) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state.detectedPlatformType.takeSuccess()) {
                    PlatformType.Mastodon -> {
                        state.mastodonLoginState.resumedState
                            ?.onLoading {
                                Text(
                                    text = stringResource(Res.string.mastodon_login_verify_message),
                                )
                                ProgressBar()
                            }?.onError {
                                Text(
                                    text = it.message ?: "Unknown error",
                                )
                            }
                            ?: run {
                                AccentButton(
                                    onClick = {
                                        state.mastodonLoginState.login(
                                            host.text,
                                            launchUrl = uriHandler::openUri,
                                        )
                                    },
                                    modifier = Modifier.width(300.dp),
                                    disabled = state.mastodonLoginState.loading,
                                ) {
                                    Text(
                                        stringResource(Res.string.service_select_next_button),
                                    )
                                }
                            }
                        state.mastodonLoginState.error?.let {
                            Text(it)
                        }
                    }

                    PlatformType.Misskey -> {
                        state.misskeyLoginState.resumedState
                            ?.onLoading {
                                Text(
                                    text = stringResource(Res.string.mastodon_login_verify_message),
                                )
                                ProgressBar()
                            }?.onError {
                                Text(
                                    text = it.message ?: "Unknown error",
                                )
                            }
                            ?: run {
                                AccentButton(
                                    onClick = {
                                        state.misskeyLoginState.login(
                                            host.text,
                                            launchUrl = uriHandler::openUri,
                                        )
                                    },
                                    modifier = Modifier.width(300.dp),
                                    disabled = state.misskeyLoginState.loading,
                                ) {
                                    Text(
                                        stringResource(Res.string.service_select_next_button),
                                    )
                                }
                            }
                        state.misskeyLoginState.error?.let {
                            Text(it)
                        }
                    }

                    PlatformType.Bluesky -> {
                        var userName by remember { mutableStateOf(TextFieldValue("")) }
                        var password by remember { mutableStateOf(TextFieldValue("")) }
                        var verifyCode by remember { mutableStateOf(TextFieldValue("")) }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            TextField(
                                value = userName,
                                onValueChange = { userName = it },
                                placeholder = { Text(stringResource(Res.string.bluesky_login_username)) },
                                maxLines = 1,
                                modifier = Modifier.width(300.dp),
                            )
                            TextField(
                                value = password,
                                onValueChange = { password = it },
                                placeholder = { Text(stringResource(Res.string.bluesky_login_password)) },
                                maxLines = 1,
                                modifier = Modifier.width(300.dp),
                                visualTransformation = remember { PasswordVisualTransformation() },
                            )
                            AnimatedVisibility(state.blueskyLoginState.require2FA) {
                                TextField(
                                    value = verifyCode,
                                    onValueChange = { verifyCode = it },
                                    placeholder = { Text(stringResource(Res.string.bluesky_login_2fa)) },
                                    maxLines = 1,
                                    modifier = Modifier.width(300.dp),
                                )
                            }
                            AccentButton(
                                onClick = {
                                    state.blueskyLoginState.login(
                                        "https://${host.text}",
                                        userName.text
                                            .toString(),
                                        password.text
                                            .toString(),
                                        verifyCode.text
                                            .takeIf { it.isNotEmpty() },
                                    )
                                },
                                modifier = Modifier.width(300.dp),
                                disabled =
                                    state.blueskyLoginState.loading ||
                                        userName.text.isEmpty() ||
                                        password.text.isEmpty() ||
                                        (state.blueskyLoginState.require2FA && verifyCode.text.isEmpty()),
                            ) {
                                Text(
                                    text = stringResource(Res.string.service_select_next_button),
                                )
                            }
                        }
                    }

                    PlatformType.xQt -> {
                        AccentButton(
                            onClick = onXQT,
                            modifier = Modifier.width(300.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.service_select_next_button),
                            )
                        }
                    }

                    PlatformType.VVo -> {
                        AccentButton(
                            onClick = onVVO,
                            modifier = Modifier.width(300.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.service_select_next_button),
                            )
                        }
                    }

                    null -> Unit
                }
            }
        }
        LazyStatusVerticalStaggeredGrid(
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
            verticalItemSpacing = 0.dp,
        ) {
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
                            onClicked = {
                                if (instance != null) {
                                    host = TextFieldValue(instance.domain)
                                }
                            },
                        )
                    }
                }.onLoading {
                    items(10) {
                        ServiceSelectItem(
                            instance = null,
                            onClicked = {},
                            index = it,
                            totalCount = 10,
                        )
                    }
                }.onEmpty {
                    items(1) {
                        Text(
                            text = stringResource(Res.string.service_select_empty_message),
                            style = FluentTheme.typography.subtitle,
                        )
                    }
                }
        }
    }
}

@Composable
private fun ServiceSelectItem(
    instance: UiInstance?,
    index: Int,
    totalCount: Int,
    onClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveCard(
        modifier = modifier,
        index = index,
        totalCount = totalCount,
    ) {
        Column(
            modifier =
                Modifier
                    .clickable {
                        onClicked.invoke()
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
                            .clip(FluentTheme.shapes.control),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                    style = FluentTheme.typography.subtitle,
                    modifier = Modifier.placeholder(instance == null),
                )
            }
            Text(
                text = instance?.domain ?: "Loading...",
                style = FluentTheme.typography.body,
                modifier = Modifier.placeholder(instance == null),
            )
            Text(
                text =
                    instance?.description
                        ?: "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                style = FluentTheme.typography.caption,
                modifier = Modifier.placeholder(instance == null),
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun presenter(onBack: () -> Unit) =
    run {
        val state =
            remember {
                ServiceSelectPresenter(
                    toHome = {
                        onBack.invoke()
                    },
                )
            }.invoke()

        object : ServiceSelectState by state {
        }
    }
