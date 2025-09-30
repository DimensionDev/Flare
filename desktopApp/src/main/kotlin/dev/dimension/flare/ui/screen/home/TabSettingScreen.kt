package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.tab_settings_add
import dev.dimension.flare.tab_settings_drag
import dev.dimension.flare.tab_settings_edit
import dev.dimension.flare.tab_settings_mixed_timeline
import dev.dimension.flare.tab_settings_mixed_timeline_desc
import dev.dimension.flare.tab_settings_remove
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.TabTitle
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.AllTabsPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun TabSettingScreen(toAddRssSource: () -> Unit) {
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
    LazyColumn(
        state = lazyListState,
        contentPadding = LocalWindowPadding.current + PaddingValues(vertical = 24.dp),
        modifier =
            Modifier
                .padding(horizontal = screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        state.enableMixedTimeline.onSuccess { enabled ->
            if (state.currentTabs.size > 1) {
                item("header") {
                    CardExpanderItem(
                        heading = {
                            Text(stringResource(Res.string.tab_settings_mixed_timeline))
                        },
                        trailing = {
                            Switcher(
                                checked = enabled,
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
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.fillParentMaxWidth(),
            ) {
                AccentButton(
                    onClick = {
                        state.setAddTab(true)
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
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        itemsIndexed(state.currentTabs, key = { _, item -> item.key }) { index, item ->
            ReorderableItem(reorderableLazyColumnState, key = item.key) { isDragging ->
                CardExpanderItem(
                    heading = {
                        TabTitle(item.metaData.title)
                    },
                    icon = {
                        TabIcon(item)
                    },
                    trailing = {
                        Row {
                            SubtleButton(
                                onClick = {
                                    state.setEditTab(item)
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

    AddTabDialog(
        visible = state.showAddTab,
        onDismiss = {
            state.setAddTab(false)
        },
        tabs = state.currentTabs.toImmutableList(),
        allTabs = state.allTabsState,
        onAddTab = { tabItem ->
            if (tabItem is TimelineTabItem) {
                state.addTab(tabItem)
            }
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
                if (it is TimelineTabItem) {
                    state.updateTab(it)
                }
            },
            visible = true,
        )
    }
}

@Composable
private fun presenter(
    settingsRepository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val scope = rememberCoroutineScope()
    var selectedEditTab by remember { mutableStateOf<TabItem?>(null) }
    val allTabsState = remember { AllTabsPresenter(filterIsTimeline = true) }.invoke()
    val tabSettings by settingsRepository.tabSettings.collectAsUiState()
    val cacheTabs =
        remember {
            mutableStateListOf<TimelineTabItem>()
        }
    val currentTabs =
        remember(tabSettings) {
            tabSettings.map {
                it.mainTabs
                    .toImmutableList()
            }
        }
    currentTabs
        .onSuccess {
            LaunchedEffect(it.size) {
                cacheTabs.clear()
                cacheTabs.addAll(it)
            }
        }
    val enableMixedTimeline by remember {
        settingsRepository.tabSettings.map { it.enableMixedTimeline }
    }.collectAsUiState()
    var showAddTab by remember { mutableStateOf(false) }
    object {
        val currentTabs = cacheTabs

        val allTabsState = allTabsState
        val canSwipeToDelete = cacheTabs.size > 1
        val showAddTab = showAddTab
        val selectedEditTab = selectedEditTab
        val enableMixedTimeline = enableMixedTimeline

        fun setEnableMixedTimeline(enable: Boolean) {
            scope.launch {
                settingsRepository.updateTabSettings {
                    copy(enableMixedTimeline = enable)
                }
            }
        }

        fun setEditTab(tab: TimelineTabItem?) {
            selectedEditTab = tab
        }

        fun updateTab(tab: TimelineTabItem) {
            val index = cacheTabs.indexOfFirst { it.key == tab.key }
            cacheTabs[index] = tab
        }

        fun moveTab(
            from: Any,
            to: Any,
        ) {
            val fromIndex = cacheTabs.indexOfFirst { it.key == from }
            val toIndex = cacheTabs.indexOfFirst { it.key == to }
            cacheTabs.add(toIndex, cacheTabs.removeAt(fromIndex))
        }

        fun commit() {
            appScope.launch {
                settingsRepository.updateTabSettings {
                    copy(
                        mainTabs = cacheTabs,
                    )
                }
            }
        }

        fun deleteTab(tab: TimelineTabItem) {
            if (cacheTabs.size <= 1) {
                return
            }
            cacheTabs.removeIf { it.key == tab.key }
        }

        fun deleteTab(key: String) {
            if (cacheTabs.size <= 1) {
                return
            }
            cacheTabs.removeIf { it.key == key }
        }

        fun addTab(tab: TimelineTabItem) {
            cacheTabs.add(tab)
        }

        fun setAddTab(value: Boolean) {
            showAddTab = value
        }
    }
}
