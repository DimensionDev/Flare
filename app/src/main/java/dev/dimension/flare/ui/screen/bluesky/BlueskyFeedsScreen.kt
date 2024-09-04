package dev.dimension.flare.ui.screen.bluesky

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.material3.placeholder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedsPresenter
import dev.dimension.flare.ui.presenter.invoke

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
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun BlueskyFeedsScreen(accountType: AccountType) {
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
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding,
        ) {
            state.myFeeds
                .onSuccess {
                    stickyHeader {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(id = R.string.feeds_my_feeds_title))
                            },
                        )
                    }
                    items(itemCount) { index ->
                        val item = get(index)
                        if (item != null) {
                            ListItem(
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
                                },
                                modifier =
                                    Modifier.clickable {
                                    },
                            )
                        } else {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = "Lorem ipsum dolor sit amet",
                                        modifier = Modifier.placeholder(true),
                                    )
                                },
                                leadingContent = {
                                    Box(
                                        modifier =
                                            Modifier
                                                .placeholder(true)
                                                .size(24.dp)
                                                .clip(MaterialTheme.shapes.small),
                                    )
                                },
                            )
                        }
                    }
                }.onLoading {
                    stickyHeader {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(id = R.string.feeds_my_feeds_title))
                            },
                        )
                    }
                    items(5) { index ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Lorem ipsum dolor sit amet",
                                    modifier = Modifier.placeholder(true),
                                )
                            },
                            leadingContent = {
                                Box(
                                    modifier =
                                        Modifier
                                            .placeholder(true)
                                            .size(24.dp)
                                            .clip(MaterialTheme.shapes.medium),
                                )
                            },
                        )
                    }
                }

            stickyHeader {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.feeds_discover_feeds_title))
                    },
                )
            }
//            state.popularFeeds.onSuccess {
//                items(itemCount) { index ->
//
//                }
//            }
        }
    }
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        remember(accountType) {
            BlueskyFeedsPresenter(accountType = accountType)
        }.invoke()
    }
