package dev.dimension.flare.ui.screen.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.MasterDetailView
import dev.dimension.flare.ui.component.MasterDetailViewState
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.screen.home.TimelineScreen
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun ListScreen(accountType: AccountType) {
    val state by producePresenter {
        presenter()
    }
    MasterDetailView(
        state =
            if (state.selectedList == null) {
                MasterDetailViewState.Master
            } else {
                MasterDetailViewState.Detail
            },
        master = {
            AllListScreen(
                accountType = accountType,
                onAddList = {
                },
                toList = {
                    state.setSelectedList(it)
                },
            )
        },
        detail = {
            state.selectedList?.let {
                TimelineScreen(
                    tabItem =
                        remember(it, accountType) {
                            ListTimelineTabItem(
                                account = accountType,
                                listId = it.id,
                                metaData =
                                    TabMetaData(
                                        title = TitleType.Text(it.title),
                                        icon = IconType.Material(IconType.Material.MaterialIcon.List),
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
        var selectedList by remember { mutableStateOf<UiList?>(null) }
        object {
            val selectedList = selectedList

            fun setSelectedList(list: UiList?) {
                selectedList = list
            }
        }
    }
