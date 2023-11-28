package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.placeholder.placeholder
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.component.status.mastodon.StatusPlaceholder
import dev.dimension.flare.ui.component.status.mastodon.UserPlaceholder
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.DiscoverPresenter
import dev.dimension.flare.ui.presenter.home.DiscoverState
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import org.koin.compose.rememberKoinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoverSearch(user: UiState<UiUser>) {
    val state by producePresenter("discoverSearchPresenter") { discoverSearchPresenter() }
    SearchBar(
        query = state.query,
        onQueryChange = { state.setQuery(it) },
        onSearch = { state.setSearching(false) },
        active = state.isSearching,
        onActiveChange = { state.setSearching(it) },
        placeholder = {
            Text(text = stringResource(R.string.discover_search_placeholder))
        },
        trailingIcon = {
            IconButton(onClick = {
                state.setSearching(false)
            }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                )
            }
        },
        leadingIcon = {
            AnimatedContent(state.isSearching) {
                if (it) {
                    IconButton(onClick = {
                        state.setSearching(false)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                } else {
                    user.onSuccess {
                        IconButton(onClick = {
                        }) {
                            NetworkImage(
                                model = it.avatarUrl,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape),
                            )
                        }
                    }
                }
            }
        },
    ) {
    }
}

@Composable
private fun discoverSearchPresenter() =
    run {
        var query by remember { mutableStateOf("") }
        var isSearching by remember { mutableStateOf(false) }

        object {
            val query = query
            val isSearching = isSearching

            fun setSearching(new: Boolean) {
                isSearching = new
            }

            fun setQuery(new: String) {
                query = new
            }
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DiscoverScreen(
    contentPadding: PaddingValues,
    onUserClick: (MicroBlogKey) -> Unit,
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
                LazyColumn(
                    contentPadding = it + PaddingValues(horizontal = screenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.users.onSuccess { users ->
                        stickyHeader {
                            ListItem(
                                headlineContent = {
                                    Text(text = stringResource(R.string.discover_users))
                                },
                            )
                        }
                        item {
                            LazyHorizontalGrid(
                                modifier = Modifier.height(128.dp),
                                rows = GridCells.Fixed(2),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    state.status.onSuccess { status ->
                        stickyHeader {
                            ListItem(
                                headlineContent = {
                                    Text(text = stringResource(R.string.discover_status))
                                },
                            )
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                status.onSuccess {
                                    with(status) {
                                        with(state.statusEvent) {
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
                                                Card(
                                                    modifier = Modifier.width(256.dp).height(192.dp),
                                                ) {
                                                    StatusItem(it, horizontalPadding = 0.dp)
                                                }
                                            }
                                        }
                                    }
                                }.onLoading {
                                    items(10) {
                                        Card(
                                            modifier = Modifier.width(256.dp),
                                        ) {
                                            StatusPlaceholder(
                                                modifier = Modifier.padding(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    state.hashtags.onSuccess { hashtags ->
                        stickyHeader {
                            ListItem(
                                headlineContent = {
                                    Text(text = stringResource(R.string.discover_hashtags))
                                },
                            )
                        }
                        hashtags.onSuccess {
                            items(
                                hashtags.itemCount,
                            ) {
                                val hashtag = hashtags[it]
                                if (hashtag != null) {
                                    Text(text = hashtag.hashtag)
                                } else {
                                    Text(
                                        text = "Lorem Ipsum is simply dummy text of the printing and typesetting industry",
                                        modifier = Modifier.placeholder(true),
                                    )
                                }
                            }
                        }.onLoading {
                            items(10) {
                                Text(
                                    text = "Lorem Ipsum is simply dummy text of the printing and typesetting industry",
                                    modifier = Modifier.placeholder(true),
                                )
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
