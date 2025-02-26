package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@Composable
internal fun CommonProfileHeader(
    bannerUrl: String?,
    avatarUrl: String?,
    displayName: UiRichText,
    userKey: MicroBlogKey,
    handle: String,
    isBigScreen: Boolean,
    modifier: Modifier = Modifier,
    onAvatarClick: (() -> Unit)? = null,
    onBannerClick: (() -> Unit)? = null,
    headerTrailing: @Composable RowScope.() -> Unit = {},
    handleTrailing: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val statusBarHeight =
        with(LocalDensity.current) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
    val actualBannerHeight =
        remember(statusBarHeight) {
            ProfileHeaderConstants.BANNER_HEIGHT.dp + statusBarHeight
        }
    Box(
        modifier =
            modifier
//                .sharedBounds(
//                    rememberSharedContentState(key = "header-$userKey"),
//                    animatedVisibilityScope = this@AnimatedVisibilityScope,
//                    renderInOverlayDuringTransition = false,
//                    enter = EnterTransition.None,
//                    exit = ExitTransition.None,
//                    resizeMode =
//                        SharedTransitionScope.ResizeMode.ScaleToBounds(
//                            contentScale = ContentScale.FillWidth,
//                            alignment = Alignment.TopStart,
//                        ),
//                    placeHolderSize = SharedTransitionScope.PlaceHolderSize.animatedSize,
//                )
//                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                .padding(bottom = 8.dp),
    ) {
        bannerUrl?.let {
            NetworkImage(
                model = it,
                contentDescription = null,
                modifier =
                    Modifier
//                        .sharedElement(
//                            rememberSharedContentState(key = "profile-banner-$userKey"),
//                            animatedVisibilityScope = this@AnimatedVisibilityScope,
//                        )
                        .clipToBounds()
                        .fillMaxWidth()
                        .height(actualBannerHeight)
                        .let {
                            if (onBannerClick != null) {
                                it.clickable {
                                    onBannerClick.invoke()
                                }
                            } else {
                                it
                            }
                        },
            )
        } ?: Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(actualBannerHeight)
                    .background(PlatformTheme.colorScheme.card),
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
                        .padding(start = screenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(
                                top = (actualBannerHeight - ProfileHeaderConstants.AVATAR_SIZE.dp / 2),
                            ),
                ) {
                    AvatarComponent(
                        data = avatarUrl,
                        size = ProfileHeaderConstants.AVATAR_SIZE.dp,
//                        beforeModifier =
//                            Modifier
//                                .sharedElement(
//                                    rememberSharedContentState(key = "profile-avatar-$userKey"),
//                                    animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                ),
                        modifier =
                            Modifier
                                .let {
                                    if (onAvatarClick != null) {
                                        it.clickable {
                                            onAvatarClick.invoke()
                                        }
                                    } else {
                                        it
                                    }
                                },
                    )
                }
                if (!isBigScreen) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(top = actualBannerHeight),
                    ) {
                        RichText(
                            text = displayName,
                            textStyle = PlatformTheme.typography.title,
//                        modifier =
//                            Modifier
//                                .sharedElement(
//                                    rememberSharedContentState(key = "profile-display-name-$userKey"),
//                                    animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                ),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlatformText(
                                text = handle,
                                style = PlatformTheme.typography.caption,
//                            modifier =
//                                Modifier
//                                    .sharedElement(
//                                        rememberSharedContentState(key = "profile-handle-$userKey"),
//                                        animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                    ),
                            )
                            handleTrailing.invoke(this)
                        }
                    }
                } else {
                    Spacer(
                        modifier =
                            Modifier
                                .weight(1f),
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .padding(top = actualBannerHeight),
                ) {
                    headerTrailing()
                }
            }
            if (isBigScreen) {
                Column(
                    modifier =
                        Modifier
                            .padding(horizontal = screenHorizontalPadding),
                ) {
                    RichText(
                        text = displayName,
                        textStyle = PlatformTheme.typography.title,
//                        modifier =
//                            Modifier
//                                .sharedElement(
//                                    rememberSharedContentState(key = "profile-display-name-$userKey"),
//                                    animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                ),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlatformText(
                            text = handle,
                            style = PlatformTheme.typography.caption,
//                            modifier =
//                                Modifier
//                                    .sharedElement(
//                                        rememberSharedContentState(key = "profile-handle-$userKey"),
//                                        animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                    ),
                        )
                        handleTrailing.invoke(this)
                    }
                }
            }
            // content
            Box {
                content()
            }
        }
    }
}

internal object ProfileHeaderConstants {
    const val BANNER_HEIGHT = 150
    const val AVATAR_SIZE = 96
}
