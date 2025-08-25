package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleExclamation
import compose.icons.fontawesomeicons.solid.File
import compose.icons.fontawesomeicons.solid.FileCircleExclamation
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onEndOfList
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.login_expired
import dev.dimension.flare.compose.ui.login_expired_message
import dev.dimension.flare.compose.ui.status_empty
import dev.dimension.flare.compose.ui.status_loadmore_end
import dev.dimension.flare.compose.ui.status_loadmore_error
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.placeholder
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import org.jetbrains.compose.resources.stringResource

public fun LazyStaggeredGridScope.status(
    pagingState: PagingState<UiTimeline>,
    detailStatusKey: MicroBlogKey? = null,
): Unit =
    with(pagingState) {
        onSuccess {
            items(
                itemCount,
                key =
                    itemKey {
                        it.itemKey
                    },
                contentType =
                    itemContentType {
                        it.itemType
                    },
            ) { index ->
                val item = get(index)
                AdaptiveCard(
                    modifier =
                        Modifier
                            .animateItem(),
                    index = index,
                    totalCount = itemCount,
                    content = {
                        StatusItem(
                            item,
                            detailStatusKey = detailStatusKey,
//                        modifier =
//                        Modifier
//                            .let {
//                                if (item != null) {
//                                    it.sharedBounds(
//                                        rememberSharedContentState(key = item.itemKey),
//                                        animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                        // ANY transition will lead to the entire screen being animated to
//                                        // exit state after list -> detail -> go back -> scroll a little bit,
//                                        // I have no idea why, so just use None here
//                                        enter = EnterTransition.None,
//                                        exit = ExitTransition.None,
//                                        renderInOverlayDuringTransition = false,
//                                        placeHolderSize = SharedTransitionScope.PlaceHolderSize.animatedSize,
//                                    )
//                                } else {
//                                    it
//                                }
//                            }
//                            .background(MaterialTheme.colorScheme.background),
                        )
                    },
                )
            }
            appendState
                .onError {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        OnError(
                            modifier = Modifier.animateItem(),
                            error = it,
                            onRetry = { retry() },
                        )
                    }
                }.onLoading {
                    items(10) {
                        AdaptiveCard(
                            content = {
                                OnLoading()
                            },
                            index = it,
                            totalCount = 10,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }.onEndOfList {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .animateItem()
                                    .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            PlatformText(
                                text = stringResource(Res.string.status_loadmore_end),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
        }
        onError {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                OnError(
                    modifier = Modifier.animateItem(),
                    error = it,
                    onRetry = onRetry,
                )
            }
        }
        onLoading {
            items(10) {
                AdaptiveCard(
                    modifier = Modifier.animateItem(),
                    index = it,
                    totalCount = 10,
                    content = {
                        OnLoading()
                    },
                )
            }
        }
        onEmpty {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                Column(
                    modifier =
                        Modifier
                            .animateItem()
                            .clickable {
                                refresh()
                            },
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.File,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                    PlatformText(
                        text = stringResource(resource = Res.string.status_empty),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

@Composable
private fun OnLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        StatusPlaceholder(
            modifier =
                Modifier
                    .padding(
                        horizontal = screenHorizontalPadding,
                        vertical = 8.dp,
                    ),
        )
    }
}

@Composable
private fun OnError(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (error) {
        is LoginExpiredException -> {
            LoginExpiredError(error, modifier)
        }

        else -> {
            Column(
                modifier =
                    modifier
                        .clickable {
                            onRetry.invoke()
                        }.fillMaxWidth()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.FileCircleExclamation,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                PlatformText(text = stringResource(Res.string.status_loadmore_error))
                error.message?.let { PlatformText(text = it) }
            }
        }
    }
}

@Composable
private fun LoginExpiredError(
    exception: LoginExpiredException,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .clickable {
                    uriHandler.openUri(AppDeepLink.LOGIN)
                },
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.CircleExclamation,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        PlatformText(
            text = stringResource(resource = Res.string.login_expired),
        )
        PlatformText(
            text = stringResource(resource = Res.string.login_expired_message),
        )
        PlatformText(
            text = exception.accountKey.toString(),
        )
    }
}

@Composable
public fun StatusItem(
    item: UiTimeline?,
//    event: StatusEvent,
    modifier: Modifier = Modifier,
    detailStatusKey: MicroBlogKey? = null,
    horizontalPadding: Dp = screenHorizontalPadding,
) {
    if (item == null) {
        Column(
            modifier =
                modifier.padding(
                    horizontal = horizontalPadding,
                    vertical = 8.dp,
                ),
        ) {
            StatusPlaceholder()
        }
    } else {
        UiTimelineComponent(
            item = item,
            detailStatusKey = detailStatusKey,
            modifier = modifier,
            horizontalPadding = screenHorizontalPadding,
        )
    }
}

@Composable
public fun StatusPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        UserPlaceholder()
        Spacer(modifier = Modifier.height(8.dp))
        PlatformText(
            text =
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec euismod, nisl eget ultricies" +
                    " ultrices, nisl nisl aliquet nisl, nec aliquam nisl nisl nec.",
            modifier =
                Modifier
                    .placeholder(true),
        )
    }
}

@Composable
public fun UserPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .placeholder(true),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            PlatformText(
                text = "Placeholder",
                modifier =
                    Modifier
                        .placeholder(true),
            )
            Spacer(modifier = Modifier.height(4.dp))
            PlatformText(
                text = "username@Placeholder",
                style = PlatformTheme.typography.caption,
                modifier =
                    Modifier
                        .alpha(MediumAlpha)
                        .placeholder(true),
            )
        }
    }
}
