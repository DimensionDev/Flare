package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.FluentMaterials
import dev.chrisbanes.haze.rememberHazeState
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.TabTitle
import dev.dimension.flare.ui.component.floatingToolbarVerticalNestedScroll
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.HomeTimelineWithTabsPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.ProgressBar
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun HomeTimelineScreen(
    accountType: AccountType,
    onAddTab: () -> Unit,
) {
    val hazeState = rememberHazeState()
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
            Box {
                TimelineScreen(
                    tabItem = currentTab.timelineTabItem,
                    modifier =
                        Modifier
                            .hazeSource(hazeState)
                            .floatingToolbarVerticalNestedScroll(
                                expanded = state.isTopBarExpanded,
                                onExpand = {
                                    state.setTopBarExpanded(true)
                                },
                                onCollapse = {
                                    state.setTopBarExpanded(false)
                                },
                            ),
                    contentPadding = PaddingValues(top = 48.dp),
                )
                AnimatedVisibility(
                    visible = state.isTopBarExpanded,
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                ) {
                    LiteFilter(
                        modifier =
                            Modifier
                                .hazeEffect(
                                    state = hazeState,
                                    style =
                                        FluentMaterials.mica(
                                            FluentTheme.colors.background.mica.base
                                                .luminance() < 0.5f,
                                        ),
                                )
//                            .background(FluentTheme.colors.background.solid.base)
                                .padding(LocalWindowPadding.current)
                                .padding(horizontal = screenHorizontalPadding),
                    ) {
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
                        if (accountType !is AccountType.Guest) {
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
                    }
                }
                AnimatedVisibility(
                    currentTab.isRefreshing,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                ) {
                    ProgressBar(
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        var isTopBarExpanded by remember { mutableStateOf(true) }
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

            val isTopBarExpanded: Boolean
                get() = isTopBarExpanded

            fun setTopBarExpanded(expanded: Boolean) {
                isTopBarExpanded = expanded
            }
        }
    }
