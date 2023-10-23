package dev.dimension.flare.ui.screen.home

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moriatsushi.koject.compose.rememberInject
import dev.dimension.flare.R
import dev.dimension.flare.data.datasource.NotificationFilter
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.NotificationPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by producePresenter {
        notificationPresenter()
    }
    val listState = rememberLazyListState()
    RefreshContainer(
        indicatorPadding = contentPadding,
        modifier = modifier,
        refreshing = state.state.refreshing,
        onRefresh = state.state::refresh,
        content = {
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.state.allTypes.onSuccess {
                    if (it.size > 1) {
                        item {
                            SingleChoiceSegmentedButtonRow(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = screenHorizontalPadding),
                            ) {
                                it.forEachIndexed { index, notificationType ->
                                    SegmentedButton(
                                        selected = state.state.notificationType == notificationType,
                                        onClick = {
                                            state.state.onNotificationTypeChanged(notificationType)
                                        },
                                        shape =
                                            SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = it.size,
                                            ),
                                    ) {
                                        Text(text = stringResource(id = notificationType.title))
                                    }
                                }
                            }
                        }
                    }
                }
                with(state.state.listState) {
                    with(state.statusEvent) {
                        status()
                    }
                }
            }
        },
    )
}

private val NotificationFilter.title: Int
    get() =
        when (this) {
            NotificationFilter.All -> R.string.notification_tab_all_title
            NotificationFilter.Mention -> R.string.notification_tab_mentions_title
        }

@Composable
private fun notificationPresenter(statusEvent: StatusEvent = rememberInject()) =
    run {
        val state = remember { NotificationPresenter() }.invoke()
        object {
            val state = state
            val statusEvent = statusEvent
        }
    }
