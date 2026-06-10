package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.R
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.model.tab.UiGroupTimelineTabItem
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.data.model.tab.isSystemHomeMixedTimeline
import dev.dimension.flare.data.model.tab.withSystemHomeMixedTimelineEnabled
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.HomeTabSettingsPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.AllTabsPresenter
import dev.dimension.flare.ui.screen.settings.EditTabDialog
import dev.dimension.flare.ui.screen.settings.TabAddBottomSheet
import dev.dimension.flare.ui.screen.settings.TabCustomItem
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.segmentedShapes2
import dev.dimension.flare.ui.theme.single
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun TabSettingScreen(
    onBack: () -> Unit,
    toAddRssSource: () -> Unit,
    toGroupConfig: (UiGroupTimelineTabItem?) -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = LocalHapticFeedback.current
    val state by producePresenter {
        presenter()
    }
    DisposableEffect(Unit) {
        onDispose {
            state.commit()
        }
    }
    state.selectedEditTab?.let {
        EditTabDialog(
            tabItem = it,
            onDismissRequest = {
                state.setEditTab(null)
            },
            onConfirm = { updatedTab ->
                state.setEditTab(null)
                state.updateTab(updatedTab)
            },
        )
    }
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.tab_settings_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                showMenu = true
                            },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Plus,
                                contentDescription = stringResource(id = R.string.tab_settings_add),
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tab_settings_add_group)) },
                                onClick = {
                                    showMenu = false
                                    toGroupConfig(null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tab_settings_add_tab)) },
                                onClick = {
                                    showMenu = false
                                    state.setAddTab(true)
                                },
                            )
                        }
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        val lazyListState = rememberLazyListState()
        val reorderableLazyColumnState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                state.moveTab(from.key, to.key)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        LazyColumn(
            state = lazyListState,
            contentPadding = it,
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            if (state.canShowMixedTimelineSetting) {
                item("header") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        modifier =
                            Modifier
                                .animateItem(),
                    ) {
                        SegmentedListItem(
                            onClick = {
                                state.setEnableMixedTimeline(!state.enableMixedTimeline)
                            },
                            shapes =
                                if (state.enableMixedTimeline) {
                                    ListItemDefaults.first()
                                } else {
                                    ListItemDefaults.single()
                                },
                            content = {
                                Text(stringResource(R.string.tab_settings_mixed_timeline))
                            },
                            trailingContent = {
                                Switch(
                                    checked = state.enableMixedTimeline,
                                    onCheckedChange = {
                                        state.setEnableMixedTimeline(it)
                                    },
                                )
                            },
                            supportingContent = {
                                Text(stringResource(R.string.tab_settings_mixed_timeline_desc))
                            },
                        )
                        if (state.enableMixedTimeline) {
                            MergePolicySettingsItem(
                                selected = state.systemHomeMergePolicy,
                                onSelected = state::setSystemHomeMergePolicy,
                                shapes = ListItemDefaults.last(),
                            )
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
            itemsIndexed(state.currentTabs, key = { _, item -> item.id }) { index, item ->
                TabCustomItem(
                    item = item,
                    shapes = ListItemDefaults.segmentedShapes2(index, state.currentTabs.size),
                    deleteTab = { state.deleteTab(it) },
                    editTab = {
                        if (it is UiGroupTimelineTabItem && !it.isSystemHomeMixedTimeline) {
                            toGroupConfig(it)
                        } else {
                            state.setEditTab(it)
                        }
                    },
                    reorderableLazyColumnState = reorderableLazyColumnState,
                    canSwipeToDelete = state.canSwipeToDelete,
                    isEditing = state.selectedEditTab == item,
                )
            }
        }
    }

    if (state.showAddTab) {
        TabAddBottomSheet(
            tabs = state.currentTabs.toImmutableList(),
            allTabs = state.allTabsState,
            onDismissRequest = {
                state.setAddTab(false)
            },
            onAddTab = { tabItem ->
                state.addTab(tabItem)
            },
            onDeleteTab = { key ->
                state.deleteTab(key)
            },
            toAddRssSource = toAddRssSource,
        )
    }
}

@Composable
private fun presenter() =
    run {
        var selectedEditTab by remember { mutableStateOf<UiTimelineTabItem?>(null) }
        val tabSettingsState = remember { HomeTabSettingsPresenter() }.invoke()
        val allTabsState = remember { AllTabsPresenter() }.invoke()
        val cacheTabs =
            remember {
                mutableStateListOf<UiTimelineTabItem>()
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
            val canSwipeToDelete = true
            val showAddTab = showAddTab
            val selectedEditTab = selectedEditTab
            val enableMixedTimeline = cacheTabs.any { it.isSystemHomeMixedTimeline }
            val canShowMixedTimelineSetting = cacheTabs.filterNot { it.isSystemHomeMixedTimeline }.size > 1
            val systemHomeMergePolicy =
                cacheTabs
                    .filterIsInstance<UiGroupTimelineTabItem>()
                    .firstOrNull { it.isSystemHomeMixedTimeline }
                    ?.mergePolicy
                    ?: TimelineMergePolicy.TimePerPage

            fun setEnableMixedTimeline(enable: Boolean) {
                replaceTabs(cacheTabs.toList().withSystemHomeMixedTimelineEnabled(enable))
            }

            fun setSystemHomeMergePolicy(mergePolicy: TimelineMergePolicy) {
                replaceTabs(
                    cacheTabs
                        .toList()
                        .withSystemHomeMixedTimelineEnabled(
                            enabled = true,
                            mergePolicy = mergePolicy,
                        ),
                )
            }

            fun setEditTab(tab: UiTimelineTabItem?) {
                selectedEditTab = tab
            }

            fun updateTab(tab: UiTimelineTabItem) {
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

            fun deleteTab(tab: UiTimelineTabItem) {
                cacheTabs.removeIf { it.id == tab.id }
                syncSystemHomeMixedTimeline()
            }

            fun deleteTab(key: String) {
                cacheTabs.removeIf { it.id == key }
                syncSystemHomeMixedTimeline()
            }

            fun addTab(tab: UiTimelineTabItem) {
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

            private fun replaceTabs(tabs: List<UiTimelineTabItem>) {
                cacheTabs.clear()
                cacheTabs.addAll(tabs)
            }
        }
    }
