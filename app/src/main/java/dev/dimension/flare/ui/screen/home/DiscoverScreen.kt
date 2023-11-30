package dev.dimension.flare.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import dev.dimension.flare.R
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.HtmlText2
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.placeholder.placeholder
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.mastodon.UserPlaceholder
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.DiscoverPresenter
import dev.dimension.flare.ui.presenter.home.DiscoverState
import dev.dimension.flare.ui.presenter.home.SearchPresenter
import dev.dimension.flare.ui.presenter.home.SearchState
import dev.dimension.flare.ui.screen.profile.CommonProfileHeader
import dev.dimension.flare.ui.screen.profile.ProfileHeaderLoading
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import org.koin.compose.rememberKoinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun DiscoverSearch(user: UiState<UiUser>) {
    val state by producePresenter("discoverSearchPresenter") { discoverSearchPresenter() }
    val keyboardController = LocalSoftwareKeyboardController.current
    BackHandler(enabled = state.isSearching) {
        state.setSearching(false)
        state.setQuery("")
    }
    SearchBar(
        query = state.query,
        onQueryChange = { state.setQuery(it) },
        onSearch = {
            state.search(it)
            keyboardController?.hide()
            state.setCommited(true)
        },
        active = state.isSearching,
        onActiveChange = { state.setSearching(it) },
        placeholder = {
            Text(text = stringResource(R.string.discover_search_placeholder))
        },
        trailingIcon = {
            IconButton(onClick = {
                state.search(state.query)
                keyboardController?.hide()
                state.setCommited(true)
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
                        state.setQuery("")
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
        if (state.commited) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.user.onSuccess { users ->
                    if (users.loadState.refresh is LoadState.Loading || users.itemCount > 0) {
                        stickyHeader {
                            ListItem(
                                headlineContent = {
                                    Text(text = stringResource(R.string.search_users))
                                },
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                        ) {
                            users.onLoading {
                                items(10) {
                                    ProfileHeaderLoading(
                                        modifier = Modifier.width(256.dp),
                                    )
                                }
                            }.onSuccess {
                                items(users.itemCount) {
                                    val item = users[it]
                                    Card {
                                        if (item == null) {
                                            ProfileHeaderLoading(
                                                modifier = Modifier.fillParentMaxWidth(0.8f),
                                            )
                                        } else {
                                            CommonProfileHeader(
                                                bannerUrl = item.bannerUrl,
                                                avatarUrl = item.avatarUrl,
                                                displayName = item.nameElement,
                                                handle = item.handle,
                                                content = {
                                                    item.descriptionElement?.let {
                                                        HtmlText2(
                                                            element = it,
                                                            maxLines = 2,
                                                            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillParentMaxWidth(0.8f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                state.status.onSuccess {
                    stickyHeader {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(R.string.search_status))
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
        }
    }
}

@Composable
private fun discoverSearchPresenter(statusEvent: StatusEvent = rememberKoinInject()) =
    run {
        var query by remember { mutableStateOf("") }
        var isSearching by remember { mutableStateOf(false) }
        var commited by remember { mutableStateOf(false) }
        val state =
            remember {
                SearchPresenter()
            }.invoke()

        object : SearchState by state {
            val commited = commited
            val query = query
            val isSearching = isSearching
            val statusEvent = statusEvent

            fun setSearching(new: Boolean) {
                isSearching = new
            }

            fun setQuery(new: String) {
                query = new
            }

            fun setCommited(new: Boolean) {
                commited = new
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
                    contentPadding = it,
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
                        stickyHeader {
                            ListItem(
                                headlineContent = {
                                    Text(text = stringResource(R.string.discover_hashtags))
                                },
                            )
                        }
                        item {
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
                                            modifier = Modifier.width(192.dp),
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
                        stickyHeader {
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
