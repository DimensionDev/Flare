package dev.dimension.flare.ui.screen.dm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.dm.dmList
import dev.dimension.flare.ui.presenter.dm.DMListPresenter
import dev.dimension.flare.ui.presenter.dm.DMListState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.component.ProgressBar
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun DmListScreen(
    accountType: AccountType,
    onItemClicked: (MicroBlogKey) -> Unit,
) {
    val state by producePresenter("dm_list_$accountType") {
        presenter(accountType)
    }
    val listState = rememberLazyListState()

    RegisterTabCallback(listState, onRefresh = state::refresh)

    Box {
        LazyColumn(
            contentPadding = LocalWindowPadding.current + PaddingValues(top = 8.dp),
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            state = listState,
        ) {
            dmList(
                data = state.items,
                onItemClicked = onItemClicked,
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
private fun presenter(accountType: AccountType) =
    run {
        val scope = rememberCoroutineScope()
        val state =
            remember(accountType) {
                DMListPresenter(accountType)
            }.invoke()
        object : DMListState by state {
            fun refresh() {
                scope.launch {
                    state.refreshSuspend()
                }
            }
        }
    }
