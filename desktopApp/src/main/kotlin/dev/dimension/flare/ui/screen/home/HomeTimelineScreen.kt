package dev.dimension.flare.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.TabTitle
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.HomeTimelineWithTabsPresenter
import dev.dimension.flare.ui.presenter.invoke
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.PillButton
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@Composable
internal fun HomeTimelineScreen(
    accountType: AccountType,
    onAddTab: () -> Unit,
) {
    val state by producePresenter(key = "home_timeline_$accountType") {
        presenter(accountType)
    }

    state.tabState.onSuccess { tabState ->
        state.selectedTab.onSuccess { currentTab ->
            val lazyListState = currentTab.lazyListState
            RegisterTabCallback(
                lazyListState = lazyListState,
                onRefresh = {
                    currentTab.refreshSync()
                },
            )

            TimelineScreen(
                tabItem = currentTab.timelineTabItem,
                header = {
                    LiteFilter {
                        tabState.forEachIndexed { index, tab ->
                            PillButton(
                                selected = tab.timelineTabItem.key == currentTab.timelineTabItem.key,
                                onSelectedChanged = {
                                    state.setSelectedIndex(index)
                                },
                            ) {
                                TabIcon(
                                    tabItem = tab.timelineTabItem,
                                )
                                TabTitle(
                                    title = tab.timelineTabItem.metaData.title,
                                )
                            }
                        }
                        PillButton(
                            selected = false,
                            onSelectedChanged = {
                                onAddTab.invoke()
                            },
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.Plus,
                                contentDescription = null,
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    settingsRepository: SettingsRepository = koinInject(),
) = run {
    val state = remember(accountType) { HomeTimelineWithTabsPresenter(accountType) }.invoke()
    var selectedIndex by remember {
        mutableStateOf(0)
    }

    state.tabState.onSuccess {
        LaunchedEffect(it.size) {
            selectedIndex = 0
        }
    }

    val selectedTab =
        remember(
            state.tabState,
            selectedIndex,
        ) {
            state.tabState.map { it.elementAt(selectedIndex) }
        }

    object : HomeTimelineWithTabsPresenter.State by state {
        val selectedTab = selectedTab

        fun setSelectedIndex(index: Int) {
            selectedIndex = index
        }
    }
}
