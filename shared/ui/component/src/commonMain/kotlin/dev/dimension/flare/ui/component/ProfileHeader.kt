package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Cat
import compose.icons.fontawesomeicons.solid.CircleCheck
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.LocationDot
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.Robot
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.platform.PlatformFilledTonalButton
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.platform.placeholder
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.profile.ProfileState
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ProfileHeader(
    state: ProfileState,
    onAvatarClick: () -> Unit,
    onBannerClick: () -> Unit,
    menu: @Composable RowScope.() -> Unit,
    onFollowListClick: (userKey: MicroBlogKey) -> Unit,
    onFansListClick: (userKey: MicroBlogKey) -> Unit,
    modifier: Modifier = Modifier,
    isBigScreen: Boolean = isBigScreen(),
) {
    when (val userState = state.userState) {
        is UiState.Loading -> {
            ProfileHeaderLoading(modifier = modifier, withStatusBarHeight = true)
        }

        is UiState.Error -> {
            ProfileHeaderError()
        }

        is UiState.Success -> {
            ProfileHeaderSuccess(
                modifier = modifier,
                user = userState.data,
                relationState = state.relationState,
                onFollowClick = state::follow,
                isMe = state.isMe,
                menu = menu,
                expandMatrices = isBigScreen,
                onAvatarClick = onAvatarClick,
                onBannerClick = onBannerClick,
                isBigScreen = isBigScreen,
                onFollowListClick = onFollowListClick,
                onFansListClick = onFansListClick,
            )
        }
    }
}

@Composable
private fun ProfileHeaderSuccess(
    user: UiProfile,
    relationState: UiState<UiRelation>,
    onFollowClick: (userKey: MicroBlogKey, UiRelation) -> Unit,
    onAvatarClick: () -> Unit,
    onBannerClick: () -> Unit,
    isMe: UiState<Boolean>,
    menu: @Composable RowScope.() -> Unit,
    isBigScreen: Boolean,
    onFollowListClick: (userKey: MicroBlogKey) -> Unit,
    onFansListClick: (userKey: MicroBlogKey) -> Unit,
    modifier: Modifier = Modifier,
    expandMatrices: Boolean = false,
) {
    val uriLauncher = LocalUriHandler.current
    CommonProfileHeader(
        modifier = modifier,
        bannerUrl = user.banner,
        avatarUrl = user.avatar,
        displayName = user.name,
        userKey = user.key,
        handle = user.handle,
        isBigScreen = isBigScreen,
        headerTrailing = {
            isMe.onSuccess {
                if (!it) {
                    when (relationState) {
                        is UiState.Error -> Unit
                        is UiState.Loading -> {
                            PlatformFilledTonalButton(
                                onClick = {
                                    // No-op
                                },
                                modifier =
                                    Modifier.placeholder(
                                        true,
//                                    shape = ButtonDefaults.filledTonalShape,
                                    ),
                            ) {
                                PlatformText(text = stringResource(Res.string.profile_header_button_follow))
                            }
                        }

                        is UiState.Success -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                PlatformFilledTonalButton(onClick = {
                                    onFollowClick.invoke(user.key, relationState.data)
                                }) {
                                    PlatformText(
                                        text =
                                            stringResource(
                                                when {
                                                    relationState.data.blocking ->
                                                        Res.string.profile_header_button_blocked

                                                    relationState.data.following ->
                                                        Res.string.profile_header_button_following

                                                    relationState.data.hasPendingFollowRequestFromYou ->
                                                        Res.string.profile_header_button_requested

                                                    else ->
                                                        Res.string.profile_header_button_follow
                                                },
                                            ),
                                    )
                                }
                                if (relationState.data.isFans) {
                                    PlatformText(
                                        text = stringResource(Res.string.profile_header_button_is_fans),
                                        textAlign = TextAlign.Center,
                                        style = PlatformTheme.typography.caption,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            menu.invoke(this)
        },
        onAvatarClick = {
            uriLauncher.openUri(AppDeepLink.RawImage.invoke(user.avatar))
        },
        onBannerClick = {
            user.banner?.let { uriLauncher.openUri(AppDeepLink.RawImage.invoke(it)) }
        },
        handleTrailing = {
            user.mark.forEach {
                when (it) {
                    UiProfile.Mark.Verified ->
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.CircleCheck,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .alpha(MediumAlpha),
                            tint = Color.Blue,
                        )

                    UiProfile.Mark.Cat ->
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Cat,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .alpha(MediumAlpha),
                        )

                    UiProfile.Mark.Bot ->
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Robot,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .alpha(MediumAlpha),
                        )

                    UiProfile.Mark.Locked ->
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Lock,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .alpha(MediumAlpha),
                        )
                }
            }
        },
        content = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                user.description?.let {
                    RichText(
                        text = it,
                    )
                }
                when (val content = user.bottomContent) {
                    is UiProfile.BottomContent.Fields ->
                        UserFields(
                            fields = content.fields,
                        )

                    is UiProfile.BottomContent.Iconify -> {
                        content.items.forEach { (key, value) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                val icon =
                                    when (key) {
                                        UiProfile.BottomContent.Iconify.Icon.Location -> FontAwesomeIcons.Solid.LocationDot
                                        UiProfile.BottomContent.Iconify.Icon.Url -> FontAwesomeIcons.Solid.Globe
                                        UiProfile.BottomContent.Iconify.Icon.Verify -> FontAwesomeIcons.Solid.CircleCheck
                                    }
                                FAIcon(icon, contentDescription = null)
                                RichText(text = value)
                            }
                        }
                    }

                    null -> Unit
                }
                MatricesDisplay(
                    data = user.matrices,
                    expanded = expandMatrices,
                    onClicked = {
                        when (it) {
                            1 -> onFollowListClick.invoke(user.key)
                            2 -> onFansListClick.invoke(user.key)
                        }
                    },
                )
            }
        },
    )
}

@Composable
private fun ProfileHeaderError() {
}

@Composable
public fun ProfileHeaderLoading(
    withStatusBarHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val statusBarHeight =
        with(LocalDensity.current) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
    val actualBannerHeight =
        remember(
            statusBarHeight,
            withStatusBarHeight,
        ) {
            ProfileHeaderConstants.BANNER_HEIGHT.dp + if (withStatusBarHeight) statusBarHeight else 0.dp
        }
    Box(
        modifier =
            modifier
//                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                .padding(bottom = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(actualBannerHeight)
                    .placeholder(true),
        )
        // avatar
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = screenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(
                                top = (actualBannerHeight - ProfileHeaderConstants.AVATAR_SIZE.dp / 2),
                            ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(ProfileHeaderConstants.AVATAR_SIZE.dp)
                                .clip(CircleShape)
                                .placeholder(true),
                    )
                }
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(top = actualBannerHeight),
                ) {
                    PlatformText(
                        text = "Loading user",
                        style = PlatformTheme.typography.title,
                        modifier = Modifier.placeholder(true),
                    )
                    PlatformText(
                        text = "Loading",
                        style = PlatformTheme.typography.caption,
                        modifier = Modifier.placeholder(true),
                    )
                }
            }
            PlatformText(
                text = "Lorem Ipsum is simply dummy text",
                modifier =
                    Modifier
                        .placeholder(true)
                        .padding(horizontal = screenHorizontalPadding),
            )
        }
    }
}
