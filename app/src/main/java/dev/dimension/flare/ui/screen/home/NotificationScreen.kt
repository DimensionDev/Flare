package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.model.BottomBarBehavior
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.ui.common.isCompat
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.TabRowIndicator
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.AllNotificationPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(
    ExperimentalMaterial3Api::class,
)
@Composable
internal fun NotificationScreen() {
    val state by producePresenter(key = "notification") {
        presenter()
    }
    val lazyListState = rememberLazyStaggeredGridState()
    RegisterTabCallback(
        lazyListState = lazyListState,
        onRefresh = {
            state.refresh()
        },
    )
    val windowInfo = currentWindowAdaptiveInfo()
    val topAppBarScrollBehavior =
        if (LocalAppearanceSettings.current.bottomBarBehavior == BottomBarBehavior.AlwaysShow) {
            TopAppBarDefaults.pinnedScrollBehavior()
        } else {
            TopAppBarDefaults.enterAlwaysScrollBehavior()
        }
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    if (state.notifications.size > 1) {
                        SecondaryScrollableTabRow(
                            containerColor = Color.Transparent,
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                            selectedTabIndex = state.selectedAccountIndex,
                            edgePadding = 0.dp,
                            divider = {},
                            indicator = {
                                TabRowIndicator(
                                    selectedIndex = state.selectedAccountIndex,
                                )
                            },
                            minTabWidth = 48.dp,
                        ) {
                            state.notifications.forEach { (account, badge) ->
                                LeadingIconTab(
                                    modifier = Modifier.clip(CircleShape),
                                    selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    unselectedContentColor = LocalContentColor.current,
                                    selected = state.selectedAccount?.key == account.key,
                                    onClick = {
                                        state.setAccount(account)
                                    },
                                    text = {
                                        Text(
                                            text = account.handle,
                                            maxLines = 1,
                                        )
                                    },
                                    icon = {
                                        Box(
                                            contentAlignment = Alignment.BottomEnd,
                                        ) {
                                            AvatarComponent(
                                                account.avatar,
                                                size = 24.dp,
                                            )
                                            if (badge > 0) {
                                                Badge {
                                                    Text(
                                                        text = badge.toString(),
                                                        maxLines = 1,
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    } else {
                        Text(text = stringResource(id = R.string.home_tab_notifications_title))
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
                actions = {
                    if (!windowInfo.windowSizeClass.isCompat()) {
                        state.supportedNotificationFilters.onSuccess { filters ->
                            if (filters.size > 1) {
                                NotificationFilterSelector(
                                    filters,
                                    state.selectedFilter,
                                    onFilterChanged = {
                                        state.setFilter(it)
                                    },
                                    modifier =
                                        Modifier
                                            .padding(horizontal = screenHorizontalPadding),
                                )
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
            isRefreshing = state.timeline.isRefreshing,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    state = lazyListState,
                    contentPadding = contentPadding,
                ) {
                    if (windowInfo.windowSizeClass.isCompat()) {
                        state.supportedNotificationFilters.onSuccess {
                            if (it.size > 1) {
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = screenHorizontalPadding),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        NotificationFilterSelector(
                                            it,
                                            state.selectedFilter,
                                            onFilterChanged = {
                                                state.setFilter(it)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    status(state.timeline)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotificationFilterSelector(
    filters: ImmutableList<NotificationFilter>,
    selectedFilter: NotificationFilter?,
    onFilterChanged: (NotificationFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = {
                    onFilterChanged(filter)
                },
                label = {
                    Text(stringResource(id = filter.title))
                },
            )
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
private fun presenter() =
    run {
        val scope = rememberCoroutineScope()
        val state =
            remember {
                AllNotificationPresenter()
            }.invoke()
        object : AllNotificationPresenter.State by state {
            fun refresh() {
                scope.launch {
                    state.refreshSuspend()
                }
            }
        }
    }
