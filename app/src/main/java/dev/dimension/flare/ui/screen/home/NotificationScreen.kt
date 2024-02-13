package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import dev.dimension.flare.R
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.NotificationPresenter
import dev.dimension.flare.ui.presenter.home.NotificationState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import org.koin.compose.koinInject

@Destination(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun NotificationRoute(
//    screen: Screen
) {
    NotificationScreen(
//        screen = screen
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun NotificationScreen(
//    screen: Screen
) {
    val state by producePresenter {
        notificationPresenter()
    }
//    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyStaggeredGridState()
//    LaunchedEffect(screen) {
//        screen.scrollToTop = {
//            scope.launch {
//                lazyListState.animateScrollToItem(0)
//            }
//        }
//    }
    val windowInfo = currentWindowAdaptiveInfo()
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.home_tab_notifications_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
                actions = {
                    if (windowInfo.windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact) {
                        state.state.allTypes.onSuccess {
                            if (it.size > 1) {
                                NotificationFilterSelector(it, state.state)
                            }
                        }
                    }
                },
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        RefreshContainer(
            indicatorPadding = contentPadding,
            onRefresh = state.state::refresh,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    state = lazyListState,
                    contentPadding = contentPadding,
                ) {
                    if (windowInfo.windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
                        state.state.allTypes.onSuccess {
                            if (it.size > 1) {
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    NotificationFilterSelector(
                                        it,
                                        state.state,
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = screenHorizontalPadding),
                                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationFilterSelector(
    filters: ImmutableList<NotificationFilter>,
    notificationState: NotificationState,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier,
    ) {
        filters.forEachIndexed { index, notificationType ->
            SegmentedButton(
                selected = notificationState.notificationType == notificationType,
                onClick = {
                    notificationState.onNotificationTypeChanged(notificationType)
                },
                shape =
                    SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = filters.size,
                    ),
            ) {
                Text(text = stringResource(id = notificationType.title))
            }
        }
    }
}

private val NotificationFilter.title: Int
    get() =
        when (this) {
            NotificationFilter.All -> R.string.notification_tab_all_title
            NotificationFilter.Mention -> R.string.notification_tab_mentions_title
        }

@Composable
private fun notificationPresenter(statusEvent: StatusEvent = koinInject()) =
    run {
        val state = remember { NotificationPresenter() }.invoke()
        object {
            val state = state
            val statusEvent = statusEvent
        }
    }
