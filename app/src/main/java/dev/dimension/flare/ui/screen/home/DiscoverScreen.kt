package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.placeholder.placeholder
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.mastodon.UserPlaceholder
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.DiscoverPresenter
import dev.dimension.flare.ui.presenter.home.DiscoverState
import dev.dimension.flare.ui.screen.destinations.ProfileRouteDestination
import dev.dimension.flare.ui.screen.destinations.QuickMenuDialogRouteDestination
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import org.koin.compose.rememberKoinInject

@Destination(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun DiscoverRoute(navigator: DestinationsNavigator) {
    val state by producePresenter("discoverSearchPresenter") { discoverSearchPresenter() }
    Scaffold(
        topBar = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                DiscoverSearch(
                    state = state,
                    onAccountClick = {
                        navigator.navigate(QuickMenuDialogRouteDestination)
                    },
                )
            }
        },
    ) {
        DiscoverScreen(
            contentPadding = it,
            onUserClick = { navigator.navigate(ProfileRouteDestination(it)) },
            onHashtagClick = {
                state.commitSearch(it)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DiscoverScreen(
    contentPadding: PaddingValues,
    onUserClick: (MicroBlogKey) -> Unit,
    onHashtagClick: (String) -> Unit,
) {
    val state by producePresenter { discoverPresenter() }
    Scaffold(
        modifier =
            Modifier.padding(
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                top = contentPadding.calculateTopPadding(),
                bottom =
                    with(LocalDensity.current) {
                        WindowInsets.navigationBars.getBottom(LocalDensity.current).toDp()
                    },
            ),
    ) {
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = it,
            onRefresh = {
            },
            content = {
                LazyStatusVerticalStaggeredGrid(
                    contentPadding = it,
                ) {
                    state.users.onSuccess { users ->
                        item(
                            span = StaggeredGridItemSpan.FullLine,
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(text = stringResource(R.string.discover_users))
                                },
                            )
                        }
                        item(
                            span = StaggeredGridItemSpan.FullLine,
                        ) {
                            LazyHorizontalGrid(
                                modifier = Modifier.height(128.dp),
                                rows = GridCells.Fixed(2),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                            ) {
                                users.onSuccess {
                                    items(
                                        users.itemCount,
                                        key = users.itemKey { it.itemKey },
                                    ) {
                                        val user = users[it]
                                        Card(
                                            modifier =
                                                Modifier
                                                    .width(256.dp),
                                        ) {
                                            if (user != null) {
                                                CommonStatusHeaderComponent(
                                                    data = user,
                                                    onUserClick = onUserClick,
                                                    modifier = Modifier.padding(8.dp),
                                                )
                                            } else {
                                                UserPlaceholder(
                                                    modifier = Modifier.padding(8.dp),
                                                )
                                            }
                                        }
                                    }
                                }.onLoading {
                                    items(10) {
                                        Card(
                                            modifier =
                                                Modifier
                                                    .width(256.dp),
                                        ) {
                                            UserPlaceholder(
                                                modifier = Modifier.padding(8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    state.hashtags.onSuccess { hashtags ->
                        item(
                            span = StaggeredGridItemSpan.FullLine,
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(text = stringResource(R.string.discover_hashtags))
                                },
                            )
                        }
                        item(
                            span = StaggeredGridItemSpan.FullLine,
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                            ) {
                                hashtags.onSuccess {
                                    items(
                                        hashtags.itemCount,
                                    ) {
                                        val hashtag = hashtags[it]
                                        Card(
                                            modifier =
                                                Modifier
                                                    .width(192.dp),
                                            onClick = {
                                                onHashtagClick("#${hashtag?.hashtag}")
                                            },
                                        ) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .padding(8.dp)
                                                        .height(48.dp),
                                            ) {
                                                if (hashtag != null) {
                                                    Text(text = hashtag.hashtag)
                                                } else {
                                                    Text(
                                                        text = "Lorem Ipsum is simply dummy text",
                                                        modifier = Modifier.placeholder(true),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }.onLoading {
                                    items(10) {
                                        Card(
                                            modifier = Modifier.width(192.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(8.dp),
                                            ) {
                                                Text(
                                                    text = "Lorem Ipsum is simply dummy text",
                                                    modifier = Modifier.placeholder(true),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    state.status.onSuccess {
                        item(
                            span = StaggeredGridItemSpan.FullLine,
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(text = stringResource(R.string.discover_status))
                                },
                            )
                        }
                        with(state.status) {
                            with(state.statusEvent) {
                                status()
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun discoverPresenter(statusEvent: StatusEvent = rememberKoinInject()) =
    run {
        val state = remember { DiscoverPresenter() }.invoke()

        object : DiscoverState by state {
            val statusEvent = statusEvent
        }
    }
