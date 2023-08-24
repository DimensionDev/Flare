package dev.dimension.flare.ui.screen.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.moriatsushi.koject.compose.rememberInject
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.data.datasource.mastodon.statusDataSource
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.data.repository.app.activeAccountPresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.map
import dev.dimension.flare.ui.onSuccess
import dev.dimension.flare.ui.theme.FlareTheme

@Composable
@Destination(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER"
        )
    ]
)
fun StatusRoute(
    statusKey: MicroBlogKey,
    navigator: DestinationsNavigator
) {
    StatusScreen(
        statusKey,
        onBack = navigator::navigateUp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusScreen(
    statusKey: MicroBlogKey,
    onBack: () -> Unit
) {
    val state by producePresenter(statusKey.toString()) {
        statusPresenter(statusKey)
    }
    FlareTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = stringResource(id = R.string.status_title))
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigate_back)
                            )
                        }
                    }
                )
            }
        ) {
            val listState = rememberLazyListState()
            RefreshContainer(
                modifier = Modifier.padding(it),
                refreshing = state.refreshing,
                onRefresh = state::refresh,
                content = {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        with(state.listState) {
                            with(state.statusEvent) {
                                status()
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun statusPresenter(
    statusKey: MicroBlogKey,
    statusEvent: StatusEvent = rememberInject()
) = run {
    val accountState by activeAccountPresenter()
    val listState = accountState.map {
        when (it) {
            is UiAccount.Mastodon -> statusDataSource(it, statusKey).collectAsLazyPagingItems()
            is UiAccount.Misskey -> dev.dimension.flare.data.datasource.misskey.statusDataSource(
                it,
                statusKey
            ).collectAsLazyPagingItems()

            is UiAccount.Bluesky -> dev.dimension.flare.data.datasource.bluesky.statusDataSource(
                it,
                statusKey
            ).collectAsLazyPagingItems()
        }
    }

    val refreshing =
        listState is UiState.Loading ||
            listState is UiState.Success && listState.data.loadState.refresh is LoadState.Loading && listState.data.itemCount != 0
    object {
        val listState = listState
        val refreshing = refreshing
        val statusEvent = statusEvent
        fun refresh() {
            listState.onSuccess {
                it.refresh()
            }
        }
    }
}
