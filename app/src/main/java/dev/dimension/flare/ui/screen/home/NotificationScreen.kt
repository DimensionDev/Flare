package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.data.datasource.mastodon.mentionTimelineDataSource
import dev.dimension.flare.data.datasource.mastodon.notificationTimelineDataSource
import dev.dimension.flare.data.repository.UiAccount
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.component.status.EmptyStatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.composeFlatMap

@Composable
fun NotificationScreen() {
    val state by producePresenter {
        NotificationPresenter()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            state.onNotificationTypeChanged(NotificationType.All)
                        },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.notificationType == NotificationType.All,
                        onClick = {
                            state.onNotificationTypeChanged(NotificationType.All)
                        }
                    )
                    Text(text = "All")
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            state.onNotificationTypeChanged(NotificationType.Mention)
                        },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.notificationType == NotificationType.Mention,
                        onClick = {
                            state.onNotificationTypeChanged(NotificationType.Mention)
                        }
                    )
                    Text(text = "Mention")
                }
            }
        }
        with(state.listState) {
            status(
                event = state.eventHandler
            )
        }
    }
}

enum class NotificationType {
    All,
    Mention,
}

@Composable
private fun NotificationPresenter() = run {
    var type by remember { mutableStateOf(NotificationType.All) }

    val account by activeAccountPresenter()
    val listState = account.composeFlatMap {
        when (it) {
            is UiAccount.Mastodon -> UiState.Success(
                when (type) {
                    NotificationType.All -> notificationTimelineDataSource(account = it)
                    NotificationType.Mention -> mentionTimelineDataSource(account = it)
                }.collectAsLazyPagingItems()
            )

            null -> UiState.Error(Throwable("Account is null"))
        }
    }
    object {
        val notificationType = type
        val listState = listState
        val eventHandler = EmptyStatusEvent
        fun onNotificationTypeChanged(value: NotificationType) {
            type = value
        }
    }
}