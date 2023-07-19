package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.dimension.flare.data.datasource.mastodon.homeTimelineDataSource
import dev.dimension.flare.data.repository.UiAccount
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.component.status.MastodonStatusComponent
import dev.dimension.flare.ui.component.status.MastodonStatusState
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.composeFlatMap
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.itemKey
import dev.dimension.flare.ui.model.itemType
import dev.dimension.flare.ui.theme.FlareTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val state by producePresenter {
        HomeTimelinePresenter()
    }
    FlareTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = "Home")
                    }
                )
            }
        ) {
            LazyColumn(
                contentPadding = it
            ) {
                when (val listState = state.listState) {
                    is UiState.Error -> Unit
                    is UiState.Loading -> Unit
                    is UiState.Success -> {
                        items(
                            listState.data.itemCount,
                            key = listState.data.itemKey {
                                it.itemKey
                            },
                            contentType = listState.data.itemContentType {
                                it.itemType
                            }
                        ) {
                            val item = listState.data[it]
                            when (item) {
                                is UiStatus.Mastodon -> MastodonStatusComponent(
                                    data = item,
                                    state = MastodonStatusState(),
                                    event = state.eventHandler,
                                )

                                null -> Box {
                                    Text(text = "Loading...")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTimelinePresenter() = run {
    val account by activeAccountPresenter()
    val listState = account.composeFlatMap {
        when (it) {
            is UiAccount.Mastodon -> UiState.Success(homeTimelineDataSource(account = it).collectAsLazyPagingItems())
            null -> UiState.Error(Exception("Account is null"))
        }
    }
    object {
        val listState = listState
        val eventHandler = object : StatusEvent {
            override fun onUserClick(userKey: MicroBlogKey) {
            }

            override fun onStatusClick(statusKey: MicroBlogKey) {
            }

            override fun onStatusLongClick(statusKey: MicroBlogKey) {
            }

            override fun onReplyClick(statusKey: MicroBlogKey) {
            }

            override fun onReblogClick(statusKey: MicroBlogKey) {
            }

            override fun onLikeClick(statusKey: MicroBlogKey) {
            }

            override fun onBookmarkClick(statusKey: MicroBlogKey) {
            }

            override fun onMediaClick(media: UiMedia) {
            }

            override fun onShowMoreClick(statusKey: MicroBlogKey) {
            }

            override fun onMoreClick(statusKey: MicroBlogKey) {
            }

        }
    }
}