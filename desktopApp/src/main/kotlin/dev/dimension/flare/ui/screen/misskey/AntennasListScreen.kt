package dev.dimension.flare.ui.screen.misskey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.component.ScrollbarContainer
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun AntennasListScreen(
    accountType: AccountType,
    toTimeline: (UiList) -> Unit,
) {
    val state by producePresenter("antennas_list_$accountType") {
        presenter(accountType)
    }

    val listState = rememberLazyListState()
    val scrollbarAdapter = rememberScrollbarAdapter(listState)
    RegisterTabCallback(listState, onRefresh = state::refresh)
    ScrollbarContainer(
        adapter = scrollbarAdapter,
    ) {
        LazyColumn(
            contentPadding = LocalWindowPadding.current,
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            state = listState,
        ) {
            misskeyAntennasWithTabs(
                state = state,
                onClick = toTimeline,
            )
        }
    }
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        remember(accountType) { MisskeyAntennasListWithTabsPresenter(accountType) }.invoke()
    }
