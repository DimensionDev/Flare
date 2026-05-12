package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Rss
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.data.model.tab.GroupTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.model.tab.isSystemHomeMixedTimeline
import dev.dimension.flare.data.model.tab.withSystemHomeMixedTimelineEnabled
import dev.dimension.flare.tab_settings_add
import dev.dimension.flare.tab_settings_add_group
import dev.dimension.flare.tab_settings_add_tab
import dev.dimension.flare.tab_settings_drag
import dev.dimension.flare.tab_settings_edit
import dev.dimension.flare.tab_settings_mixed_timeline
import dev.dimension.flare.tab_settings_mixed_timeline_desc
import dev.dimension.flare.tab_settings_remove
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.HomeTabSettingsPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.AllTabsPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.MenuFlyout
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import dev.dimension.flare.ui.component.Text as UiText

@Composable
internal fun TabSettingScreen(
    toAddRssSource: () -> Unit,
    toGroupConfig: (GroupTimelineTabItemV2?) -> Unit,
) {
    val state by producePresenter {
        presenter()
    }

    DisposableEffect(Unit) {
        onDispose {
            state.commit()
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableLazyColumnState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            state.moveTab(from.key, to.key)
        }
    FlareScrollBar(lazyListState) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalWindowPadding.current + PaddingValues(vertical = 24.dp),
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (state.canShowMixedTimelineSetting) {
                item("header") {
                    CardExpanderItem(
                        heading = {
                            Text(stringResource(Res.string.tab_settings_mixed_timeline))
                        },
                        trailing = {
                            Switcher(
                                checked = state.enableMixedTimeline,
                                onCheckStateChange = {
                                    state.setEnableMixedTimeline(it)
                                },
                            )
                        },
                        icon = {
                            FAIcon(
                                FontAwesomeIcons.Solid.Rss,
                                contentDescription = null,
                            )
                        },
                        caption = {
                            Text(stringResource(Res.string.tab_settings_mixed_timeline_desc))
                        },
                        modifier =
                            Modifier
                                .animateItem(),
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    var showMenu by remember { mutableStateOf(false) }
                    AccentButton(
                        onClick = {
                            showMenu = true
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(Res.string.tab_settings_add),
                        )
                        Text(
                            text = stringResource(Res.string.tab_settings_add),
                        )
                    }
                    MenuFlyout(
                        visible = showMenu,
                        onDismissRequest = { showMenu = false },
                        placement = FlyoutPlacement.BottomAlignedEnd,
                    ) {
                        MenuFlyoutItem(
                            text = { Text(stringResource(Res.string.tab_settings_add_group)) },
                            onClick = {
                                showMenu = false
                                toGroupConfig(null)
                            },
                        )
                        MenuFlyoutItem(
                            text = { Text(stringResource(Res.string.tab_settings_add_tab)) },
                            onClick = {
                                showMenu = false
                                state.setAddTab(true)
                            },
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
            itemsIndexed(state.currentTabs, key = { _, item -> item.id }) { index, item ->
                ReorderableItem(reorderableLazyColumnState, key = item.id) { isDragging ->
                    CardExpanderItem(
                        heading = {
                            UiText(item.title)
                        },
                        icon = {
                            TabIcon(item)
                        },
                        trailing = {
                            Row {
                                SubtleButton(
                                    onClick = {
                                        if (item is GroupTimelineTabItemV2 && !item.isSystemHomeMixedTimeline) {
                                            toGroupConfig(item)
                                        } else {
                                            state.setEditTab(item)
                                        }
                                    },
                                    iconOnly = true,
                                ) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Pen,
                                        contentDescription =
                                            stringResource(
                                                Res.string.tab_settings_edit,
                                            ),
                                    )
                                }
                                SubtleButton(
                                    onClick = {
                                        state.deleteTab(item)
                                    },
                                    iconOnly = true,
                                ) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Trash,
                                        contentDescription =
                                            stringResource(
                                                Res.string.tab_settings_remove,
                                            ),
                                        tint = FluentTheme.colors.system.critical,
                                    )
                                }
                                SubtleButton(
                                    modifier =
                                        Modifier.draggableHandle(),
                                    onClick = {},
                                    iconOnly = true,
                                ) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Bars,
                                        contentDescription =
                                            stringResource(
                                                Res.string.tab_settings_drag,
                                            ),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    AddTabDialog(
        visible = state.showAddTab,
        onDismiss = {
            state.setAddTab(false)
        },
        tabs = state.currentTabs.toImmutableList(),
        allTabs = state.allTabsState,
        onAddTab = { tabItem ->
            state.addTab(tabItem)
        },
        onDeleteTab = { key ->
            state.deleteTab(key)
        },
        toAddRssSource = toAddRssSource,
    )

    state.selectedEditTab?.let {
        EditTabDialog(
            tabItem = it,
            onDismissRequest = {
                state.setEditTab(null)
            },
            onConfirm = {
                state.setEditTab(null)
                state.updateTab(it)
            },
            visible = true,
        )
    }
}

@Composable
private fun presenter() =
    run {
        var selectedEditTab by remember { mutableStateOf<TimelineTabItemV2?>(null) }
        val tabSettingsState = remember { HomeTabSettingsPresenter() }.invoke()
        val allTabsState = remember { AllTabsPresenter() }.invoke()
        val cacheTabs =
            remember {
                mutableStateListOf<TimelineTabItemV2>()
            }
        var loadedTabs by remember { mutableStateOf(false) }
        tabSettingsState.homeTimelineTabs
            .onSuccess {
                LaunchedEffect(it) {
                    cacheTabs.clear()
                    cacheTabs.addAll(it)
                    loadedTabs = true
                }
            }
        var showAddTab by remember { mutableStateOf(false) }
        object {
            val currentTabs = cacheTabs

            val allTabsState = allTabsState
            val canSwipeToDelete = cacheTabs.size > 1
            val showAddTab = showAddTab
            val selectedEditTab = selectedEditTab
            val enableMixedTimeline = cacheTabs.any { it.isSystemHomeMixedTimeline }
            val canShowMixedTimelineSetting = cacheTabs.filterNot { it.isSystemHomeMixedTimeline }.size > 1

            fun setEnableMixedTimeline(enable: Boolean) {
                replaceTabs(cacheTabs.toList().withSystemHomeMixedTimelineEnabled(enable))
            }

            fun setEditTab(tab: TimelineTabItemV2?) {
                selectedEditTab = tab
            }

            fun updateTab(tab: TimelineTabItemV2) {
                val index = cacheTabs.indexOfFirst { it.id == tab.id }
                if (index != -1) {
                    cacheTabs[index] = tab
                    syncSystemHomeMixedTimeline()
                }
            }

            fun moveTab(
                from: Any,
                to: Any,
            ) {
                val fromIndex = cacheTabs.indexOfFirst { it.id == from }
                val toIndex = cacheTabs.indexOfFirst { it.id == to }
                if (fromIndex != -1 && toIndex != -1) {
                    cacheTabs.add(toIndex, cacheTabs.removeAt(fromIndex))
                    syncSystemHomeMixedTimeline()
                }
            }

            fun commit() {
                if (!loadedTabs) return
                tabSettingsState.replaceHomeTimelineTabs(cacheTabs)
            }

            fun deleteTab(tab: TimelineTabItemV2) {
                if (cacheTabs.size <= 1) {
                    return
                }
                cacheTabs.removeIf { it.id == tab.id }
                syncSystemHomeMixedTimeline()
            }

            fun deleteTab(key: String) {
                if (cacheTabs.size <= 1) {
                    return
                }
                cacheTabs.removeIf { it.id == key }
                syncSystemHomeMixedTimeline()
            }

            fun addTab(tab: TimelineTabItemV2) {
                if (cacheTabs.none { it.id == tab.id }) {
                    cacheTabs.add(tab)
                    syncSystemHomeMixedTimeline()
                }
            }

            fun setAddTab(value: Boolean) {
                showAddTab = value
            }

            private fun syncSystemHomeMixedTimeline() {
                if (cacheTabs.any { it.isSystemHomeMixedTimeline }) {
                    replaceTabs(cacheTabs.toList().withSystemHomeMixedTimelineEnabled(true))
                }
            }

            private fun replaceTabs(tabs: List<TimelineTabItemV2>) {
                cacheTabs.clear()
                cacheTabs.addAll(tabs)
            }
        }
    }
