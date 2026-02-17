package dev.dimension.flare.ui.screen.misskey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.Res
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.misskey_channel_tab_favorites
import dev.dimension.flare.misskey_channel_tab_featured
import dev.dimension.flare.misskey_channel_tab_following
import dev.dimension.flare.misskey_channel_tab_owned
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.uiListItemComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.home.misskey.MisskeyChannelListPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.ScrollbarContainer
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ChannelListScreen(
    accountType: AccountType,
    toTimeline: (UiList) -> Unit,
) {
    val state by producePresenter {
        remember {
            MisskeyChannelListPresenter(accountType)
        }.invoke()
    }

    val listState = rememberLazyListState()
    val scrollbarAdapter = rememberScrollbarAdapter(listState)
    RegisterTabCallback(listState, onRefresh = { state.refresh() })

    Box {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .padding(LocalWindowPadding.current),
        ) {
            LiteFilter(
                modifier = Modifier.padding(horizontal = screenHorizontalPadding),
            ) {
                state.allTypes.forEach { type ->
                    PillButton(
                        selected = state.type == type,
                        onSelectedChanged = {
                            state.setType(type)
                        },
                    ) {
                        Text(
                            text =
                                stringResource(
                                    when (type) {
                                        MisskeyChannelListPresenter.State.Type.Following ->
                                            Res.string.misskey_channel_tab_following
                                        MisskeyChannelListPresenter.State.Type.Favorites ->
                                            Res.string.misskey_channel_tab_favorites
                                        MisskeyChannelListPresenter.State.Type.Owned ->
                                            Res.string.misskey_channel_tab_owned
                                        MisskeyChannelListPresenter.State.Type.Featured ->
                                            Res.string.misskey_channel_tab_featured
                                    },
                                ),
                        )
                    }
                }
            }
            ScrollbarContainer(
                adapter = scrollbarAdapter,
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding =
                        PaddingValues(
                            horizontal = screenHorizontalPadding,
                        ),
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    uiListItemComponent(
                        items = state.data,
                        onClicked = toTimeline,
                    )
                }
            }
        }
        if (state.data.isRefreshing) {
            ProgressBar(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
            )
        }
    }
}
