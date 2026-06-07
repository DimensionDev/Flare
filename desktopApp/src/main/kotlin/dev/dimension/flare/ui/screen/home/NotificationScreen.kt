package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ChevronDown
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.AllNotificationPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.component.Badge
import io.github.composefluent.component.BadgeStatus
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.MenuFlyoutContainer
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.SelectorBar
import io.github.composefluent.component.SelectorBarItem
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun NotificationScreen() {
    val state by producePresenter(
        key = "notification",
    ) {
        presenter()
    }
    val listState = rememberLazyStaggeredGridState()
    RegisterTabCallback(listState, onRefresh = state::refresh)
    Box(
        modifier =
            Modifier
                .fillMaxSize(),
    ) {
        FlareScrollBar(listState) {
            LazyStatusVerticalStaggeredGrid(
                contentPadding = LocalWindowPadding.current,
                state = listState,
            ) {
                if (state.notifications.size > 1) {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        if (isBigScreen()) {
                            SelectorBar(
                                modifier =
                                    Modifier
                                        .let {
                                            if (LocalTimelineAppearance.current.timelineDisplayMode == TimelineDisplayMode.Plain) {
                                                it.padding(horizontal = screenHorizontalPadding)
                                            } else {
                                                it
                                            }
                                        },
                            ) {
                                state.notifications.forEach { item ->
                                    val profile = item.profile
                                    val badge = item.badge
                                    SelectorBarItem(
                                        selected = state.selectedAccount?.key == profile.key,
                                        onSelectedChange = {
                                            if (it) {
                                                state.setAccount(profile)
                                            }
                                        },
                                        icon = {
                                            AvatarComponent(
                                                data = profile.avatar,
                                                size = AvatarComponentDefaults.compatSize,
                                            )
                                        },
                                        text = {
                                            Text(
                                                profile.handle.canonical,
                                                maxLines = 1,
                                                modifier = Modifier.padding(start = 8.dp),
                                            )
                                            AnimatedVisibility(badge > 0) {
                                                Badge(
                                                    status = BadgeStatus.Informational,
                                                    content = {
                                                        Text(badge.toString())
                                                    },
                                                    modifier = Modifier.padding(start = 8.dp),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        } else {
                            MenuFlyoutContainer(
                                adaptivePlacement = true,
                                placement = FlyoutPlacement.BottomAlignedStart,
                                flyout = {
                                    state.notifications.forEach { item ->
                                        val profile = item.profile
                                        val badge = item.badge
                                        MenuFlyoutItem(
                                            selected = state.selectedAccount?.key == profile.key,
                                            onSelectedChanged = {
                                                if (it) {
                                                    state.setAccount(profile)
                                                }
                                            },
                                            icon = {
                                                AvatarComponent(
                                                    data = profile.avatar,
                                                    size = AvatarComponentDefaults.compatSize,
                                                )
                                            },
                                            text = {
                                                Text(
                                                    profile.handle.canonical,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(start = 8.dp),
                                                )
                                                AnimatedVisibility(badge > 0) {
                                                    Badge(
                                                        status = BadgeStatus.Informational,
                                                        content = {
                                                            Text(badge.toString())
                                                        },
                                                        modifier = Modifier.padding(start = 8.dp),
                                                    )
                                                }
                                            },
                                        )
                                    }
                                },
                            ) {
                                SubtleButton(
                                    modifier =
                                        Modifier
                                            .let {
                                                if (LocalTimelineAppearance.current.timelineDisplayMode == TimelineDisplayMode.Plain) {
                                                    it.padding(horizontal = screenHorizontalPadding)
                                                } else {
                                                    it
                                                }
                                            },
                                    onClick = {
                                        isFlyoutVisible = !isFlyoutVisible
                                    },
                                ) {
                                    AvatarComponent(
                                        data = state.selectedAccount?.avatar,
                                        size = AvatarComponentDefaults.compatSize,
                                    )
                                    Text(
                                        state.selectedAccount?.handle?.canonical ?: "Select Account",
                                        maxLines = 1,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                    val badge =
                                        remember(
                                            state.notifications,
                                        ) {
                                            state.notifications.sumOf {
                                                it.badge
                                            }
                                        }
                                    AnimatedVisibility(badge > 0) {
                                        Badge(
                                            status = BadgeStatus.Informational,
                                            content = {
                                                Text(badge.toString())
                                            },
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                    FAIcon(
                                        FontAwesomeIcons.Solid.ChevronDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                state.supportedNotificationFilters.onSuccess { types ->
                    if (types.size > 1) {
                        item(
                            span = StaggeredGridItemSpan.FullLine,
                        ) {
                            LiteFilter(
                                modifier =
                                    Modifier
                                        .let {
                                            if (LocalTimelineAppearance.current.timelineDisplayMode == TimelineDisplayMode.Plain) {
                                                it.padding(horizontal = screenHorizontalPadding)
                                            } else {
                                                it
                                            }
                                        },
                            ) {
                                types.forEach { type ->
                                    PillButton(
                                        selected = state.selectedFilter == type,
                                        onSelectedChanged = {
                                            if (it) {
                                                state.setFilter(type)
                                            }
                                        },
                                    ) {
                                        Text(text = type.name)
                                    }
                                }
                            }
                        }
                    }
                }
                status(state.timeline)
            }
        }
        if (state.timeline.isRefreshing) {
            ProgressBar(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun presenter() =
    run {
        val scope = rememberCoroutineScope()
        val state = remember { AllNotificationPresenter() }.invoke()
        object : AllNotificationPresenter.State by state {
            fun refresh() {
                scope.launch {
                    state.refreshSuspend()
                }
            }
        }
    }
