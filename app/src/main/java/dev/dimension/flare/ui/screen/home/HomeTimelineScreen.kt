package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.moriatsushi.koject.compose.rememberInject
import dev.dimension.flare.data.datasource.mastodon.homeTimelineDataSource
import dev.dimension.flare.data.repository.UiAccount
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.component.status.DefaultMastodonStatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.flatMap

@Composable
internal fun HomeTimelineScreen() {
    val state by producePresenter {
        HomeTimelinePresenter()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        with(state.listState) {
            status(
                event = state.eventHandler
            )
        }
    }
}


@Composable
private fun HomeTimelinePresenter(
    defaultEvent: DefaultMastodonStatusEvent = rememberInject(),
) = run {
    val account by activeAccountPresenter()
    val listState = account.flatMap {
        when (it) {
            is UiAccount.Mastodon -> UiState.Success(homeTimelineDataSource(account = it).collectAsLazyPagingItems())
        }
    }
    object {
        val listState = listState
        val eventHandler = defaultEvent
    }
}