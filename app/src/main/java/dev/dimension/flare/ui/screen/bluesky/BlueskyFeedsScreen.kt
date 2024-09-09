package dev.dimension.flare.ui.screen.bluesky

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.material3.placeholder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.BlueskyFeedRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.ListComponent
import dev.dimension.flare.ui.component.status.StatusPlaceholder
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedsPresenter
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedsState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun BlueskyFeedsRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
) {
    BlueskyFeedsScreen(
        accountType = accountType,
        toFeed = {
            navigator.navigate(BlueskyFeedRouteDestination(accountType = accountType, uri = it.id))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlueskyFeedsScreen(
    accountType: AccountType,
    toFeed: (UiList) -> Unit,
) {
    val state by producePresenter {
        presenter(accountType = accountType)
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.home_tab_feeds_title))
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
                LazyColumn(
                    contentPadding = contentPadding,
                ) {
                    item {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(id = R.string.feeds_my_feeds_title))
                            },
                        )
                    }
                    items(
                        state = state.myFeeds,
                        loadingCount = 5,
                        loadingContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier =
                                    Modifier
                                        .padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .placeholder(true)
                                            .size(24.dp)
                                            .clip(MaterialTheme.shapes.small),
                                )
                                Text(
                                    text = "Lorem ipsum dolor sit amet",
                                    modifier = Modifier.placeholder(true),
                                )
                            }
                        },
                    ) { item ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                                Modifier
                                    .clickable {
                                        toFeed.invoke(item)
                                    }.fillMaxWidth()
                                    .padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
                        ) {
                            if (item.avatar != null) {
                                NetworkImage(
                                    model = item.avatar,
                                    contentDescription = item.title,
                                    modifier =
                                        Modifier
                                            .size(24.dp)
                                            .clip(MaterialTheme.shapes.small),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.RssFeed,
                                    contentDescription = null,
                                    modifier =
                                        Modifier
                                            .size(24.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = MaterialTheme.shapes.small,
                                            ),
                                )
                            }
                            Text(text = item.title)
                        }
                    }

                    item {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(id = R.string.feeds_discover_feeds_title))
                            },
                        )
                    }
                    items(
                        state.popularFeeds,
                        loadingCount = 5,
                        loadingContent = {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                StatusPlaceholder(
                                    modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                            }
                        },
                    ) { (item, subscribed) ->
                        Column(
                            modifier =
                                Modifier
                                    .clickable {
                                        toFeed.invoke(item)
                                    },
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ListComponent(
                                modifier =
                                    Modifier
                                        .padding(
                                            horizontal = screenHorizontalPadding,
                                        ),
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
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            if (subscribed) {
                                                state.unsubscribe(item)
                                            } else {
                                                state.subscribe(item)
                                            }
                                        },
                                    ) {
                                        if (subscribed) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                },
                            )
                            item.description?.takeIf { it.isNotEmpty() }?.let {
                                Text(
                                    text = it,
                                    modifier =
                                        Modifier
                                            .padding(
                                                horizontal = screenHorizontalPadding,
                                            ),
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        val scope = rememberCoroutineScope()
        var isRefreshing by remember { mutableStateOf(false) }
        val state =
            remember(accountType) {
                BlueskyFeedsPresenter(accountType = accountType)
            }.invoke()

        object : BlueskyFeedsState by state {
            val isRefreshing: Boolean
                get() = isRefreshing

            fun refresh() {
                isRefreshing = true
                scope.launch {
                    state.refreshSuspend()
                    isRefreshing = false
                }
            }
        }
    }
