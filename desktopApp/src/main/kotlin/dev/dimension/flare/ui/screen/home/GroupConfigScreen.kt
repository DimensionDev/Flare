package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.TableList
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.MixedTimelineTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.tab_settings_add
import dev.dimension.flare.tab_settings_drag
import dev.dimension.flare.tab_settings_group_default_name
import dev.dimension.flare.tab_settings_group_empty
import dev.dimension.flare.tab_settings_group_name_placeholder
import dev.dimension.flare.tab_settings_remove
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.TabTitle
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.AllTabsPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.Flyout
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun GroupConfigScreen(
    item: MixedTimelineTabItem? = null,
    toAddRssSource: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val defaultGroupName = stringResource(Res.string.tab_settings_group_default_name)
    val state by producePresenter(key = item?.key ?: "new_group") {
        GroupConfigPresenter(item, defaultGroupName)
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
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalWindowPadding.current,
        modifier =
            Modifier
                .padding(horizontal = screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        stickyHeader {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
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
                    Text(stringResource(Res.string.tab_settings_add))
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TabIcon(
                        accountType = AccountType.Guest,
                        icon = state.icon,
                        title = TitleType.Text(state.name.text.toString()),
                        size = 36.dp,
                        modifier = Modifier.clickable { state.setShowIconPicker(true) },
                    )
                    Flyout(
                        visible = state.showIconPicker,
                        onDismissRequest = { state.setShowIconPicker(false) },
                    ) {
                        LazyHorizontalGrid(
                            rows = GridCells.FixedSize(48.dp),
                            modifier = Modifier.heightIn(max = 120.dp),
                        ) {
                            items(state.availableIcons) { icon ->
                                TabIcon(
                                    accountType = AccountType.Guest,
                                    icon = icon,
                                    title = TitleType.Text(state.name.text.toString()),
                                    modifier =
                                        Modifier.padding(4.dp).clickable {
                                            state.setIcon(icon)
                                            state.setShowIconPicker(false)
                                        },
                                    size = 48.dp,
                                )
                            }
                        }
                    }
                }

                TextField(
                    state = state.name,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(Res.string.tab_settings_group_name_placeholder)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
        }

        if (state.tabs.isEmpty()) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FAIcon(
                        FontAwesomeIcons.Solid.TableList,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = FluentTheme.colors.text.text.secondary,
                    )
                    Text(
                        text = stringResource(Res.string.tab_settings_group_empty),
                        style = FluentTheme.typography.body,
                        textAlign = TextAlign.Center,
                        color = FluentTheme.colors.text.text.secondary,
                    )
                }
            }
        }

        itemsIndexed(state.tabs, key = { _, item -> item.key }) { index, item ->
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
        onDismiss = { state.setAddTab(false) },
        tabs = state.tabs.toImmutableList(),
        allTabs = state.allTabs,
        onAddTab = { tabItem ->
            if (tabItem is TimelineTabItem) {
                state.addTab(tabItem)
            }
        },
        onDeleteTab = { key -> state.deleteTab(key) },
        toAddRssSource = toAddRssSource,
    )
}

@Composable
private fun GroupConfigPresenter(
    initialItem: MixedTimelineTabItem?,
    defaultGroupName: String,
    repository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val name =
        rememberTextFieldState(
            initialItem?.metaData?.title?.let {
                when (it) {
                    is TitleType.Text -> it.content
                    is TitleType.Localized -> ""
                }
            } ?: "",
        )

    var icon by remember {
        mutableStateOf<IconType>(
            initialItem?.metaData?.icon ?: IconType.Material(IconType.Material.MaterialIcon.Rss),
        )
    }

    val tabs =
        remember {
            mutableStateListOf<TimelineTabItem>().apply {
                initialItem?.subTimelineTabItem?.let { addAll(it) }
            }
        }

    var showAddTab by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    val allTabs = remember { AllTabsPresenter(filterIsTimeline = true) }.invoke()

    object {
        val name = name
        val icon = icon
        val tabs = tabs
        val showAddTab = showAddTab
        val showIconPicker = showIconPicker
        val allTabs = allTabs
        val availableIcons =
            IconType.Material.MaterialIcon.entries
                .map { IconType.Material(it) }

        fun setIcon(newIcon: IconType) {
            icon = newIcon
        }

        fun setShowIconPicker(show: Boolean) {
            showIconPicker = show
        }

        fun setAddTab(show: Boolean) {
            showAddTab = show
        }

        fun addTab(tab: TimelineTabItem) {
            if (tabs.none { it.key == tab.key }) {
                tabs.add(tab)
            }
        }

        fun deleteTab(tab: TabItem) {
            tabs.removeIf { it.key == tab.key }
        }

        fun deleteTab(key: String) {
            tabs.removeIf { it.key == key }
        }

        fun moveTab(
            from: Any,
            to: Any,
        ) {
            val fromIndex = tabs.indexOfFirst { it.key == from }
            val toIndex = tabs.indexOfFirst { it.key == to }
            if (fromIndex != -1 && toIndex != -1) {
                tabs.add(toIndex, tabs.removeAt(fromIndex))
            }
        }

        fun commit() {
            appScope.launch {
                val groupName = name.text.toString()

                if (tabs.isEmpty()) {
                    if (initialItem != null) {
                        repository.updateTabSettings {
                            val currentTabs = mainTabs.toMutableList()
                            currentTabs.removeIf { it.key == initialItem.key }
                            copy(mainTabs = currentTabs)
                        }
                    } else {
                        // New group is empty, don't create it
                    }
                    return@launch
                }

                val newGroup =
                    MixedTimelineTabItem(
                        subTimelineTabItem = tabs.toList(),
                        metaData =
                            TabMetaData(
                                title = TitleType.Text(groupName.ifEmpty { defaultGroupName }),
                                icon = icon,
                            ),
                    )

                repository.updateTabSettings {
                    val currentTabs = mainTabs.toMutableList()
                    if (initialItem != null) {
                        // Edit existing
                        val index = currentTabs.indexOfFirst { it.key == initialItem.key }
                        if (index != -1) {
                            currentTabs[index] = newGroup
                        }
                    } else {
                        // Create new
                        currentTabs.add(newGroup)
                    }
                    copy(mainTabs = currentTabs)
                }
            }
        }
    }
}
