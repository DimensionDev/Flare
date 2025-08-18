package dev.dimension.flare.ui.screen.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.VVOCommentPresenter
import io.github.composefluent.component.ProgressBar
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun VVOCommentScreen(
    commentKey: MicroBlogKey,
    onBack: () -> Unit,
    accountType: AccountType,
) {
    val state by producePresenter("comment_$commentKey") {
        presenter(
            commentKey = commentKey,
            accountType = accountType,
        )
    }

    val listState = rememberLazyStaggeredGridState()
    RegisterTabCallback(listState, onRefresh = state::refresh)
    Box(
        modifier =
            Modifier
                .fillMaxSize(),
    ) {
        LazyStatusVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(1),
            contentPadding =
                PaddingValues(
                    vertical = 8.dp,
                ),
            state = listState,
        ) {
            item {
                state.state.root
                    .onSuccess {
                        StatusItem(item = it)
                    }.onLoading {
                        StatusItem(item = null)
                    }
            }
            status(state.state.list)
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
private fun presenter(
    commentKey: MicroBlogKey,
    accountType: AccountType,
) = run {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val state =
        remember(
            commentKey,
            accountType,
        ) {
            VVOCommentPresenter(
                commentKey = commentKey,
                accountType = accountType,
            )
        }.invoke()

    object {
        val state = state
        val isRefreshing = isRefreshing

        fun refresh() {
            scope.launch {
                isRefreshing = true
                state.list.onSuccess {
                    refreshSuspend()
                }
                isRefreshing = false
            }
        }
    }
}
