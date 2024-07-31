package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.eygraber.compose.placeholder.material3.placeholder
import com.ramcosta.composedestinations.generated.destinations.ServiceSelectRouteDestination
import dev.dimension.flare.R
import dev.dimension.flare.common.deeplink
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.theme.DisabledAlpha
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding

context(LazyStaggeredGridScope, UiState<LazyPagingItems<UiTimeline>>, AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun status(
    detailStatusKey: MicroBlogKey? = null,
    showVVOStatus: Boolean = true,
) {
    onSuccess { lazyPagingItems ->
        if (lazyPagingItems.itemCount > 0) {
            when (val refresh = lazyPagingItems.loadState.refresh) {
                is LoadState.Error ->
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        when (refresh.error) {
                            is LoginExpiredException -> {
                                LoginExpiredError()
                            }

                            else -> Unit
                        }
                    }
                else -> Unit
            }
            with(lazyPagingItems) {
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
                ) {
                    Column {
                        val item = get(it)
                        StatusItem(
                            item,
//                            this@StatusEvent,
                            detailStatusKey = detailStatusKey,
//                            isDetail = item?.statusKey == detailStatusKey,
                            showVVOStatus = showVVOStatus,
                            modifier =
                                Modifier
                                    .let {
                                        if (item != null) {
                                            it.sharedBounds(
                                                rememberSharedContentState(key = item.itemKey),
                                                animatedVisibilityScope = this@AnimatedVisibilityScope,
                                                // ANY transition will lead to the entire screen being animated to
                                                // exit state after list -> detail -> go back -> scroll a little bit,
                                                // I have no idea why, so just use None here
                                                enter = EnterTransition.None,
                                                exit = ExitTransition.None,
                                                renderInOverlayDuringTransition = false,
                                                placeHolderSize = SharedTransitionScope.PlaceHolderSize.animatedSize,
                                            )
                                        } else {
                                            it
                                        }
                                    }.background(MaterialTheme.colorScheme.background),
                        )
                        if (it != itemCount - 1) {
                            HorizontalDivider(
                                modifier = Modifier.alpha(DisabledAlpha),
                            )
                        }
                    }
                }
            }
            when (val state = lazyPagingItems.loadState.append) {
                is LoadState.Error -> {
                    when (state.error) {
                        is LoginExpiredException -> {
                            item(
                                span = StaggeredGridItemSpan.FullLine,
                            ) {
                                LoginExpiredError()
                            }
                        }
                        else -> {
                            item(
                                span = StaggeredGridItemSpan.FullLine,
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .clickable {
                                                lazyPagingItems.retry()
                                            }.fillMaxWidth()
                                            .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoodBad,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.status_loadmore_error),
                                    )
                                }
                            }
                        }
                    }
                }

                LoadState.Loading ->
                    items(10) {
                        Column {
                            StatusPlaceholder(
                                modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(
                                modifier = Modifier.alpha(DisabledAlpha),
                            )
                        }
                    }

                is LoadState.NotLoading ->
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.status_loadmore_end),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
            }
        } else if (
            lazyPagingItems.loadState.refresh == LoadState.Loading ||
            lazyPagingItems.loadState.prepend == LoadState.Loading ||
            lazyPagingItems.loadState.append == LoadState.Loading
        ) {
            items(10) {
                Column {
                    StatusPlaceholder(
                        modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        modifier = Modifier.alpha(DisabledAlpha),
                    )
                }
            }
        } else if (
            lazyPagingItems.loadState.refresh is LoadState.Error ||
            lazyPagingItems.loadState.prepend is LoadState.Error
        ) {
            val error =
                lazyPagingItems.loadState.refresh as? LoadState.Error
                    ?: lazyPagingItems.loadState.prepend as? LoadState.Error
            when (error?.error) {
                is LoginExpiredException -> {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        LoginExpiredError()
                    }
                }
                else -> {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .clickable {
                                        lazyPagingItems.retry()
                                    }.fillMaxWidth()
                                    .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoodBad,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(text = stringResource(R.string.status_loadmore_error))
                        }
                    }
                }
            }
        } else {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                Column(
                    modifier =
                        Modifier
                            .clickable {
                                lazyPagingItems.refresh()
                            },
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEmotions,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = stringResource(id = R.string.status_empty),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
    onLoading {
        items(10) {
            Column {
                StatusPlaceholder(
                    modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    modifier = Modifier.alpha(DisabledAlpha),
                )
            }
        }
    }
    onError {
        when (it) {
            is LoginExpiredException -> {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    LoginExpiredError()
                }
            }

            else -> {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .clickable {
                                },
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoodBad,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = stringResource(id = R.string.status_loadmore_error_retry),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginExpiredError(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .clickable {
                    uriHandler.openUri(ServiceSelectRouteDestination.deeplink())
                },
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(id = R.string.login_expired),
        )
        Text(
            text = stringResource(id = R.string.login_expired_message),
        )
    }
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun StatusItem(
    item: UiTimeline?,
//    event: StatusEvent,
    modifier: Modifier = Modifier,
    detailStatusKey: MicroBlogKey? = null,
    horizontalPadding: Dp = screenHorizontalPadding,
    showVVOStatus: Boolean = true,
) {
    if (item == null) {
        Column(
            modifier = modifier.padding(horizontal = horizontalPadding),
        ) {
            StatusPlaceholder()
            Spacer(modifier = Modifier.height(8.dp))
        }
    } else {
        UiTimelineComponent(
            item = item,
            detailStatusKey = detailStatusKey,
            modifier =
                modifier
                    .padding(horizontal = horizontalPadding),
        )
    }
}

@Composable
internal fun StatusPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        UserPlaceholder()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
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
internal fun UserPlaceholder(modifier: Modifier = Modifier) {
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
            Text(
                text = "Placeholder",
                modifier =
                    Modifier
                        .placeholder(true),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "username@Placeholder",
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .alpha(MediumAlpha)
                        .placeholder(true),
            )
        }
    }
}
