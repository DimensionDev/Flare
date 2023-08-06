package dev.dimension.flare.ui.screen.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.moriatsushi.koject.compose.rememberInject
import dev.dimension.flare.R
import dev.dimension.flare.data.datasource.mastodon.mentionTimelineDataSource
import dev.dimension.flare.data.datasource.mastodon.notificationTimelineDataSource
import dev.dimension.flare.data.datasource.misskey.notificationDataSource
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.data.repository.app.activeAccountPresenter
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.flatMap
import dev.dimension.flare.ui.onSuccess
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    modifier: Modifier = Modifier
) {
    val state by producePresenter {
        notificationPresenter()
    }
    val listState = rememberLazyListState()
    RefreshContainer(
        indicatorPadding = PaddingValues(top = 48.dp),
        modifier = modifier,
        refreshing = state.refreshing,
        onRefresh = state::refresh,
        content = {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = screenHorizontalPadding)
                    ) {
                        NotificationType.values().forEachIndexed { index, notificationType ->
                            SegmentedButton(
                                selected = state.notificationType == notificationType,
                                onClick = {
                                    state.onNotificationTypeChanged(notificationType)
                                },
                                shape = SegmentedButtonDefaults.shape(
                                    position = index,
                                    count = NotificationType.values().size
                                )
                            ) {
                                Text(text = stringResource(id = notificationType.title))
                            }
                        }
                    }
                }
                with(state.listState) {
                    with (state.statusEvent) {
                        status()
                    }
                }
            }
        }
    )
}

enum class NotificationType(@StringRes val title: Int) {
    All(title = R.string.notification_tab_all_title),
    Mention(title = R.string.notification_tab_mentions_title)
}

@Composable
private fun notificationPresenter(
    statusEvent: StatusEvent = rememberInject()
) = run {
    var type by remember { mutableStateOf(NotificationType.All) }

    val account by activeAccountPresenter()
    val listState = account.flatMap {
        when (it) {
            is UiAccount.Mastodon -> UiState.Success(
                when (type) {
                    NotificationType.All -> notificationTimelineDataSource(account = it)
                    NotificationType.Mention -> mentionTimelineDataSource(account = it)
                }.collectAsLazyPagingItems()
            )

            is UiAccount.Misskey -> UiState.Success(
                when (type) {
                    NotificationType.All -> notificationDataSource(account = it)
                    NotificationType.Mention -> dev.dimension.flare.data.datasource.misskey.mentionTimelineDataSource(account = it)
                }.collectAsLazyPagingItems()
            )
        }
    }
    val refreshing =
        listState is UiState.Loading ||
            listState is UiState.Success && listState.data.loadState.refresh is LoadState.Loading && listState.data.itemCount != 0
    object {
        val refreshing = refreshing
        val notificationType = type
        val listState = listState
        val statusEvent = statusEvent
        fun onNotificationTypeChanged(value: NotificationType) {
            type = value
        }

        fun refresh() {
            listState.onSuccess {
                it.refresh()
            }
        }
    }
}
