package dev.dimension.flare.ui.screen.misskey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.TabRowIndicator
import dev.dimension.flare.ui.component.uiListItemComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.home.misskey.MisskeyChannelListPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelListScreen(
    accountType: AccountType,
    toTimeline: (UiList) -> Unit,
    onBack: () -> Unit,
) {
    val state by producePresenter {
        remember {
            MisskeyChannelListPresenter(accountType)
        }.invoke()
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    FlareScaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.home_tab_channels_title))
                },
                subtitle = {
                    SecondaryScrollableTabRow(
                        containerColor = Color.Transparent,
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        indicator = {
                            TabRowIndicator(
                                selectedIndex = state.allTypes.indexOf(state.type),
                            )
                        },
                        minTabWidth = 48.dp,
                        selectedTabIndex = state.allTypes.indexOf(state.type),
                        divider = {},
                        edgePadding = screenHorizontalPadding,
                    ) {
                        state.allTypes.forEach { type ->
                            Tab(
                                selected = state.type == type,
                                onClick = {
                                    state.setType(type)
                                },
                                text = {
                                    Text(
                                        stringResource(
                                            when (type) {
                                                MisskeyChannelListPresenter.State.Type.Following ->
                                                    R.string.misskey_channel_tab_following
                                                MisskeyChannelListPresenter.State.Type.Favorites ->
                                                    R.string.misskey_channel_tab_favorites
                                                MisskeyChannelListPresenter.State.Type.Owned ->
                                                    R.string.misskey_channel_tab_owned
                                                MisskeyChannelListPresenter.State.Type.Featured ->
                                                    R.string.misskey_channel_tab_featured
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
    ) { contentPadding ->
        RefreshContainer(
            isRefreshing = state.data.isRefreshing,
            onRefresh = { state.refresh() },
            indicatorPadding = contentPadding,
            content = {
                LazyColumn(
                    contentPadding =
                        contentPadding +
                            PaddingValues(
                                vertical = 16.dp,
                            ),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    uiListItemComponent(
                        items = state.data,
                        onClicked = toTimeline,
                    )
                }
            },
        )
    }
}
