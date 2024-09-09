package dev.dimension.flare.ui.screen.bluesky

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.eygraber.compose.placeholder.material3.placeholder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.ListComponent
import dev.dimension.flare.ui.component.status.StatusActionButton
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedPresenter
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun BlueskyFeedRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    uri: String,
) {
    BlueskyFeedScreen(
        accountType = accountType,
        uri = uri,
        onBack = navigator::navigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlueskyFeedScreen(
    accountType: AccountType,
    uri: String,
    onBack: () -> Unit,
) {
    val state by producePresenter {
        presenter(
            accountType = accountType,
            uri = uri,
        )
    }

    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    state.info
                        .onSuccess {
                            Text(text = it.title)
                        }.onLoading {
                            Text(
                                text = "Loading...",
                                modifier = Modifier.placeholder(true),
                            )
                        }
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
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = state.isRefreshing,
            onRefresh = state::refresh,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    contentPadding = contentPadding,
                ) {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        state.info.onSuccess { item ->
                            Column(
                                modifier =
                                    Modifier
                                        .padding(
                                            horizontal = screenHorizontalPadding,
                                        ),
                            ) {
                                ListComponent(
                                    headlineContent = {
                                        Text(text = item.title)
                                    },
                                    leadingContent = {
                                        if (item.avatar != null) {
                                            NetworkImage(
                                                model = item.avatar,
                                                contentDescription = item.title,
                                                modifier =
                                                    Modifier
                                                        .size(AvatarComponentDefaults.size)
                                                        .clip(MaterialTheme.shapes.medium),
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.RssFeed,
                                                contentDescription = null,
                                                modifier =
                                                    Modifier
                                                        .size(AvatarComponentDefaults.size)
                                                        .background(
                                                            color = MaterialTheme.colorScheme.primaryContainer,
                                                            shape = MaterialTheme.shapes.medium,
                                                        ),
                                            )
                                        }
                                    },
                                    supportingContent = {
                                        Text(
                                            text =
                                                stringResource(
                                                    R.string.feeds_discover_feeds_created_by,
                                                    item.creator?.handle ?: "Unknown",
                                                ),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier =
                                                Modifier
                                                    .alpha(MediumAlpha),
                                        )
                                    },
                                )
                                item.description?.takeIf { it.isNotEmpty() }?.let {
                                    Text(
                                        text = it,
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    state.subscribed
                                        .onSuccess { subscribed ->
                                            FilledTonalButton(
                                                onClick = {
                                                    if (subscribed) {
                                                        state.unsubscribe(item)
                                                    } else {
                                                        state.subscribe(item)
                                                    }
                                                },
                                            ) {
                                                Text(
                                                    if (subscribed) {
                                                        stringResource(R.string.feeds_unsubscribe)
                                                    } else {
                                                        stringResource(R.string.feeds_subscribe)
                                                    },
                                                )
                                            }
                                        }.onLoading {
                                            FilledTonalButton(
                                                onClick = { },
                                                modifier = Modifier.placeholder(true),
                                            ) {
                                                Text(
                                                    "Loading...",
                                                    modifier = Modifier.placeholder(true),
                                                )
                                            }
                                        }

                                    StatusActionButton(
                                        icon = if (item.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        text = item.likedCountHumanized,
                                        color = if (item.liked) Color.Red else LocalContentColor.current,
                                        onClicked = {
                                            if (item.liked) {
                                                state.unfavorite(item)
                                            } else {
                                                state.favorite(item)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        HorizontalDivider()
                    }
                    with(state.timeline) {
                        status()
                    }
                }
            },
        )
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    uri: String,
) = run {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val state =
        remember(accountType, uri) {
            BlueskyFeedPresenter(
                accountType = accountType,
                uri = uri,
            )
        }.invoke()

    object : BlueskyFeedState by state {
        val isRefreshing = isRefreshing

        fun refresh() {
            isRefreshing = true
            scope.launch {
                refreshSuspend()
                isRefreshing = false
            }
        }
    }
}
