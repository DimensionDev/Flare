package dev.dimension.flare.ui.screen.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusContextPresenter
import io.github.composefluent.component.ProgressBar
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun StatusScreen(
    statusKey: MicroBlogKey,
    accountType: AccountType,
) {
    val state by producePresenter(statusKey.toString()) {
        statusPresenter(accountType = accountType, statusKey = statusKey)
    }

    val listState = rememberLazyStaggeredGridState()
    RegisterTabCallback(listState, onRefresh = state::refresh)
    Box(
        modifier =
            Modifier
                .fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyStatusVerticalStaggeredGrid(
            modifier = Modifier.widthIn(max = 480.dp),
            columns = StaggeredGridCells.Fixed(1),
            contentPadding = LocalWindowPadding.current,
            state = listState,
        ) {
            status(
                state.state.listState,
                detailStatusKey = statusKey,
            )
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
private fun statusPresenter(
    statusKey: MicroBlogKey,
    accountType: AccountType,
) = run {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val state =
        remember(statusKey) {
            StatusContextPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()

    object {
        val state = state
        val isRefreshing = isRefreshing

        fun refresh() {
            scope.launch {
                isRefreshing = true
                state.refresh()
                isRefreshing = false
            }
        }
    }
}
