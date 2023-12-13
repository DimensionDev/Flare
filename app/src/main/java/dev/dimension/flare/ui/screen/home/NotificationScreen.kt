package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import dev.dimension.flare.R
import dev.dimension.flare.data.datasource.NotificationFilter
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.NotificationPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import org.koin.compose.rememberKoinInject

@Destination(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun NotificationRoute() {
    NotificationScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationScreen(modifier: Modifier = Modifier) {
    val state by producePresenter {
        notificationPresenter()
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.home_tab_notifications_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        RefreshContainer(
            indicatorPadding = contentPadding,
            modifier = modifier,
            onRefresh = state.state::refresh,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    contentPadding = contentPadding,
                ) {
                    state.state.allTypes.onSuccess {
                        if (it.size > 1) {
                            item(
                                span = StaggeredGridItemSpan.FullLine,
                            ) {
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
}

private val NotificationFilter.title: Int
    get() =
        when (this) {
            NotificationFilter.All -> R.string.notification_tab_all_title
            NotificationFilter.Mention -> R.string.notification_tab_mentions_title
        }

@Composable
private fun notificationPresenter(statusEvent: StatusEvent = rememberKoinInject()) =
    run {
        val state = remember { NotificationPresenter() }.invoke()
        object {
            val state = state
            val statusEvent = statusEvent
        }
    }
