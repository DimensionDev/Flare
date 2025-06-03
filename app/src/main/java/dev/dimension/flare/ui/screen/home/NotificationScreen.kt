package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.common.isCompat
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.LocalBottomBarShowing
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.NotificationPresenter
import dev.dimension.flare.ui.presenter.home.NotificationState
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(
    ExperimentalMaterial3Api::class,
)
@Composable
internal fun NotificationScreen(
    accountType: AccountType,
    toQuickMenu: () -> Unit,
) {
    val state by producePresenter(key = "notification_$accountType") {
        notificationPresenter(accountType = accountType)
    }
    val lazyListState = rememberLazyStaggeredGridState()
    RegisterTabCallback(lazyListState = lazyListState)
    val windowInfo = currentWindowAdaptiveInfo()
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.home_tab_notifications_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
                actions = {
                    if (!windowInfo.windowSizeClass.isCompat()) {
                        state.state.allTypes.onSuccess {
                            if (it.size > 1) {
                                NotificationFilterSelector(it, state.state)
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (LocalBottomBarShowing.current) {
                        state.user.onSuccess {
                            IconButton(
                                onClick = toQuickMenu,
                            ) {
                                AvatarComponent(it.avatar, size = 24.dp)
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
            onRefresh = state::refresh,
            isRefreshing = state.isRefreshing,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    state = lazyListState,
                    contentPadding = contentPadding,
                ) {
                    if (windowInfo.windowSizeClass.isCompat()) {
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
                    status(state.state.listState)
                }
            },
        )
    }
}

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
            NotificationFilter.Comment -> R.string.notification_tab_comments_title
            NotificationFilter.Like -> R.string.notification_tab_likes_title
        }

@Composable
private fun notificationPresenter(accountType: AccountType) =
    run {
        val scope = rememberCoroutineScope()
        val accountState =
            remember { UserPresenter(accountType = accountType, userKey = null) }.invoke()
        val state = remember { NotificationPresenter(accountType = accountType) }.invoke()
        object : UserState by accountState {
            val state = state
            val isRefreshing = state.listState.isRefreshing

            fun refresh() {
                scope.launch {
                    state.refresh()
                }
            }
        }
    }
