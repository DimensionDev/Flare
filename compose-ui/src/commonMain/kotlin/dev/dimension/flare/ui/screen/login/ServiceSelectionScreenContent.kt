package dev.dimension.flare.ui.screen.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleQuestion
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.bluesky_login_auth_factor_token_hint
import dev.dimension.flare.compose.ui.bluesky_login_oauth_button
import dev.dimension.flare.compose.ui.bluesky_login_oauth_hint
import dev.dimension.flare.compose.ui.bluesky_login_password_hint
import dev.dimension.flare.compose.ui.bluesky_login_use_password_button
import dev.dimension.flare.compose.ui.bluesky_login_username_hint
import dev.dimension.flare.compose.ui.login_button
import dev.dimension.flare.compose.ui.mastodon_login_verify_message
import dev.dimension.flare.compose.ui.service_select_compatibility_warning
import dev.dimension.flare.compose.ui.service_select_empty_message
import dev.dimension.flare.compose.ui.service_select_instance_input_placeholder
import dev.dimension.flare.compose.ui.service_select_next_button
import dev.dimension.flare.compose.ui.service_select_welcome_hint
import dev.dimension.flare.compose.ui.service_select_welcome_list_hint
import dev.dimension.flare.compose.ui.service_select_welcome_message
import dev.dimension.flare.compose.ui.service_select_welcome_title
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.logoUrl
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.platform.PlatformCircularProgressIndicator
import dev.dimension.flare.ui.component.platform.PlatformFilledTonalButton
import dev.dimension.flare.ui.component.platform.PlatformIconButton
import dev.dimension.flare.ui.component.platform.PlatformLinearProgressIndicator
import dev.dimension.flare.ui.component.platform.PlatformPicker
import dev.dimension.flare.ui.component.platform.PlatformSecureTextField
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextField
import dev.dimension.flare.ui.component.platform.placeholder
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.persistentListOf
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ServiceSelectionScreenContent(
    onXQT: () -> Unit,
    onVVO: () -> Unit,
    onBack: (() -> Unit),
    openUri: (String) -> Unit,
    registerDeeplinkCallback: @Composable ((url: String) -> Unit) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    val state by producePresenter {
        remember { SelectionPresenter(onBack) }.body()
    }
    LazyStatusVerticalStaggeredGrid(
        state = listState,
        modifier =
            Modifier.fillMaxSize(),
        columns = StaggeredGridCells.Adaptive(300.dp),
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
                PlatformText(
                    text = stringResource(Res.string.service_select_welcome_title),
                    style = PlatformTheme.typography.headline,
                )
                PlatformText(
                    text = stringResource(Res.string.service_select_welcome_message),
                    textAlign = TextAlign.Center,
                )
                PlatformTextField(
                    state = state.instanceInputState,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Done,
                            autoCorrectEnabled = false,
                        ),
                    placeholder = {
                        PlatformText(
                            text = stringResource(Res.string.service_select_instance_input_placeholder),
                        )
                    },
                    trailingIcon = {
                        PlatformIconButton(onClick = {
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
                                    it.platformType.logoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }.onError {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.CircleQuestion,
                                    contentDescription = null,
                                )
                            }.onLoading {
                                PlatformCircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                    },
                    enabled = !state.loading,
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                PlatformText(
                    stringResource(Res.string.service_select_welcome_hint),
                    textAlign = TextAlign.Center,
                    style = PlatformTheme.typography.caption,
                )
                AnimatedVisibility(state.canNext && state.detectedPlatformType.isSuccess) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        state.detectedPlatformType.takeSuccess()?.let {
                            if (it.compatibleMode) {
                                PlatformText(
                                    stringResource(
                                        Res.string.service_select_compatibility_warning,
                                        it.software,
                                    ),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        when (state.detectedPlatformType.takeSuccess()?.platformType) {
                            null -> Unit
                            PlatformType.Bluesky -> {
                                val oauthString =
                                    stringResource(Res.string.bluesky_login_oauth_button)
                                val passwordString =
                                    stringResource(Res.string.bluesky_login_use_password_button)
                                PlatformPicker(
                                    options =
                                        remember {
                                            persistentListOf(
                                                passwordString,
                                                oauthString,
                                            )
                                        },
                                    onSelected = {
                                        when (it) {
                                            0 -> {
                                                state.blueskyLoginState.clear()
                                                state.blueskyOauthLoginState.clear()
                                                state.blueskyInputState.setUsePasswordLogin(true)
                                            }

                                            1 -> {
                                                state.blueskyLoginState.clear()
                                                state.blueskyOauthLoginState.clear()
                                                state.blueskyInputState.setUsePasswordLogin(false)
                                            }

                                            else -> {}
                                        }
                                    },
                                )
                                PlatformTextField(
                                    state = state.blueskyInputState.username,
                                    label = {
                                        PlatformText(text = stringResource(Res.string.bluesky_login_username_hint))
                                    },
                                    enabled =
                                        !state.blueskyOauthLoginState.loading &&
                                            !state.blueskyLoginState.loading,
                                    modifier =
                                        Modifier
                                            .width(300.dp),
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    keyboardOptions =
                                        KeyboardOptions(
                                            imeAction = ImeAction.Done,
                                            autoCorrectEnabled = false,
                                        ),
                                    onKeyboardAction = {
                                        if (!state.blueskyInputState.usePasswordLogin) {
                                            state.blueskyOauthLoginState.login(
                                                baseUrl = state.instanceInputState.text.toString(),
                                                userName =
                                                    state.blueskyInputState.username.text
                                                        .toString(),
                                                launchUrl = openUri,
                                            )
                                        }
                                    },
                                )
                                AnimatedVisibility(state.blueskyInputState.usePasswordLogin) {
                                    PlatformSecureTextField(
                                        state = state.blueskyInputState.password,
                                        label = {
                                            PlatformText(text = stringResource(Res.string.bluesky_login_password_hint))
                                        },
                                        enabled = !state.blueskyLoginState.loading,
                                        modifier =
                                            Modifier
                                                .width(300.dp),
                                        onKeyboardAction = {
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
                                        },
                                    )
                                }
                                AnimatedVisibility(state.blueskyLoginState.require2FA && state.blueskyInputState.usePasswordLogin) {
                                    PlatformTextField(
                                        state = state.blueskyInputState.authFactorToken,
                                        label = {
                                            PlatformText(text = stringResource(Res.string.bluesky_login_auth_factor_token_hint))
                                        },
                                        enabled = !state.blueskyLoginState.loading,
                                        modifier =
                                            Modifier
                                                .width(300.dp),
                                        lineLimits = TextFieldLineLimits.SingleLine,
                                        keyboardOptions =
                                            KeyboardOptions(
                                                imeAction = ImeAction.Done,
                                                autoCorrectEnabled = false,
                                            ),
                                        onKeyboardAction = {
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
                                        },
                                    )
                                }
                                AnimatedVisibility(!state.blueskyInputState.usePasswordLogin) {
                                    PlatformText(stringResource(Res.string.bluesky_login_oauth_hint))
                                }
                                if (!state.blueskyInputState.usePasswordLogin) {
                                    registerDeeplinkCallback.invoke { url ->
                                        state.blueskyOauthLoginState.resume(url)
                                    }
//                                    OnNewIntent {
//                                        state.blueskyOauthLoginState.resume(it.dataString.orEmpty())
//                                    }
                                }

                                PlatformFilledTonalButton(
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
                                                baseUrl = state.instanceInputState.text.toString(),
                                                userName =
                                                    state.blueskyInputState.username.text
                                                        .toString(),
                                                launchUrl = openUri,
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
                                    PlatformText(text = stringResource(Res.string.login_button))
                                }
                                if (state.blueskyOauthLoginState.error != null) {
                                    PlatformText(
                                        text = state.blueskyOauthLoginState.error.toString(),
                                    )
                                }
                                if (state.blueskyOauthLoginState.loading) {
                                    PlatformLinearProgressIndicator()
                                }
                            }

                            PlatformType.Misskey -> {
                                registerDeeplinkCallback {
                                    state.misskeyLoginState.resume(it)
                                }
                                state.misskeyLoginState.resumedState
                                    ?.onLoading {
                                        PlatformText(
                                            text = stringResource(Res.string.mastodon_login_verify_message),
                                        )
                                        PlatformLinearProgressIndicator()
                                    }?.onError {
                                        PlatformText(text = it.message ?: "Unknown error")
                                    } ?: run {
                                    PlatformFilledTonalButton(
                                        onClick = {
                                            state.misskeyLoginState.login(
                                                state.instanceInputState.text.toString(),
                                                launchUrl = openUri,
                                            )
                                        },
                                        modifier = Modifier.width(300.dp),
                                        enabled = !state.misskeyLoginState.loading,
                                    ) {
                                        PlatformText(
                                            text = stringResource(Res.string.service_select_next_button),
                                        )
                                    }
                                    state.misskeyLoginState.error?.let {
                                        PlatformText(text = it)
                                    }
                                }
                            }

                            PlatformType.Mastodon -> {
                                registerDeeplinkCallback {
                                    state.mastodonLoginState.resume(it)
                                }
                                state.mastodonLoginState.resumedState
                                    ?.onLoading {
                                        PlatformText(
                                            text = stringResource(Res.string.mastodon_login_verify_message),
                                        )
                                        PlatformLinearProgressIndicator()
                                    }?.onError {
                                        PlatformText(text = it.message ?: "Unknown error")
                                    } ?: run {
                                    PlatformFilledTonalButton(
                                        onClick = {
                                            state.mastodonLoginState.login(
                                                state.instanceInputState.text.toString(),
                                                launchUrl = openUri,
                                            )
                                        },
                                        modifier = Modifier.width(300.dp),
                                        enabled = !state.mastodonLoginState.loading,
                                    ) {
                                        PlatformText(
                                            text = stringResource(Res.string.service_select_next_button),
                                        )
                                    }
                                    state.mastodonLoginState.error?.let {
                                        PlatformText(text = it)
                                    }
                                }
                            }

                            PlatformType.xQt -> {
                                PlatformFilledTonalButton(
                                    onClick = {
                                        onXQT.invoke()
                                    },
                                    modifier = Modifier.width(300.dp),
                                    content = {
                                        PlatformText(
                                            text = stringResource(Res.string.service_select_next_button),
                                        )
                                    },
                                )
                            }

                            PlatformType.VVo -> {
                                PlatformFilledTonalButton(
                                    onClick = {
                                        onVVO.invoke()
                                    },
                                    modifier = Modifier.width(300.dp),
                                    content = {
                                        PlatformText(
                                            text = stringResource(Res.string.service_select_next_button),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!(state.canNext && state.detectedPlatformType.isSuccess)) {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                Spacer(
                    modifier =
                        Modifier
                            .height(16.dp),
                )
            }

            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                PlatformText(
                    text = stringResource(Res.string.service_select_welcome_list_hint),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
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
                        PlatformText(
                            text = stringResource(Res.string.service_select_empty_message),
                            style = PlatformTheme.typography.title,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = screenHorizontalPadding),
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
                            .clip(PlatformTheme.shapes.medium),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!instance?.iconUrl.isNullOrEmpty()) {
                    NetworkImage(
                        instance.iconUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(24.dp),
                    )
                }
                PlatformText(
                    text = instance?.name ?: "Loading...",
                    style = PlatformTheme.typography.title,
                    modifier = Modifier.placeholder(instance == null),
                )
            }
            PlatformText(
                text = instance?.domain ?: "Loading...",
                style = PlatformTheme.typography.caption,
                modifier = Modifier.placeholder(instance == null),
            )
            PlatformText(
                text =
                    instance?.description
                        ?: "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                style = PlatformTheme.typography.caption,
                modifier = Modifier.placeholder(instance == null),
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
