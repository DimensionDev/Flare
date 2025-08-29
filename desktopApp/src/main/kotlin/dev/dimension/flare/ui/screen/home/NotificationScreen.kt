package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.NotificationPresenter
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.SegmentedButton
import io.github.composefluent.component.SegmentedControl
import io.github.composefluent.component.SegmentedItemPosition
import io.github.composefluent.component.Text
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun NotificationScreen(accountType: AccountType) {
    val state by producePresenter(
        key = "notification_$accountType",
    ) {
        presenter(accountType)
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
            state.state.allTypes.onSuccess { types ->
                if (types.size > 1) {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        SegmentedControl {
                            types.forEachIndexed { index, type ->
                                SegmentedButton(
                                    checked = state.state.notificationType == type,
                                    onCheckedChanged = {
                                        state.state.onNotificationTypeChanged(type)
                                    },
                                    position =
                                        when (index) {
                                            0 -> SegmentedItemPosition.Start
                                            types.size - 1 -> SegmentedItemPosition.End
                                            else -> SegmentedItemPosition.Center
                                        },
                                ) {
                                    Text(text = type.name)
                                }
                            }
                        }
                    }
                }
            }
            status(state.state.listState)
        }
        if (state.isRefreshing) {
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
private fun presenter(accountType: AccountType) =
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
