package dev.dimension.flare.ui.screen.feeds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.MasterDetailView
import dev.dimension.flare.ui.component.MasterDetailViewState
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.screen.home.TimelineScreen
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun FeedScreen(accountType: AccountType) {
    val state by producePresenter {
        presenter()
    }
    MasterDetailView(
        state =
            if (state.selectedFeed == null) {
                MasterDetailViewState.Master
            } else {
                MasterDetailViewState.Detail
            },
        master = {
            FeedListScreen(
                accountType = accountType,
                toFeed = {
                    state.setSelectedFeed(it)
                },
            )
        },
        detail = {
            state.selectedFeed?.let {
                TimelineScreen(
                    tabItem =
                        remember(it, accountType) {
                            Bluesky.FeedTabItem(
                                account = accountType,
                                uri = it.id,
                                metaData =
                                    TabMetaData(
                                        title = TitleType.Text(it.title),
                                        icon = IconType.Material(IconType.Material.MaterialIcon.Feeds),
                                    ),
                            )
                        },
                )
            }
        },
    )
}

@Composable
private fun presenter() =
    run {
        var selectedFeed by remember { mutableStateOf<UiList?>(null) }
        object {
            val selectedFeed = selectedFeed

            fun setSelectedFeed(value: UiList?) {
                selectedFeed = value
            }
        }
    }
