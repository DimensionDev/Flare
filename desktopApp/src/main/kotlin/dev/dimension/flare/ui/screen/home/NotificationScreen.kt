package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.AllNotificationPresenter
import dev.dimension.flare.ui.presenter.invoke
import io.github.composefluent.component.Badge
import io.github.composefluent.component.BadgeStatus
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.ProgressBar
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
        LazyStatusVerticalStaggeredGrid(
            contentPadding = LocalWindowPadding.current,
            state = listState,
        ) {
            if (state.notifications.size > 1) {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    LiteFilter {
                        state.notifications.forEach { (profile, badge) ->
                            PillButton(
                                selected = state.selectedAccount?.key == profile.key,
                                onSelectedChanged = {
                                    if (it) {
                                        state.setAccount(profile)
                                    }
                                },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AvatarComponent(
                                        data = profile.avatar,
                                        size = AvatarComponentDefaults.compatSize,
                                    )
                                    AnimatedVisibility(state.selectedAccount?.key == profile.key) {
                                        Text(
                                            profile.handle,
                                            maxLines = 1,
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
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
                                }
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
                        LiteFilter {
                            types.forEachIndexed { index, type ->
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
