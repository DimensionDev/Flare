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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.TableList
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.GroupTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.tab_settings_add
import dev.dimension.flare.tab_settings_drag
import dev.dimension.flare.tab_settings_group_default_name
import dev.dimension.flare.tab_settings_group_empty
import dev.dimension.flare.tab_settings_group_name_placeholder
import dev.dimension.flare.tab_settings_remove
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.HomeTabSettingsPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.AllTabsPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.Flyout
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import dev.dimension.flare.ui.presenter.home.GroupConfigPresenter as SharedGroupConfigPresenter
import dev.dimension.flare.ui.component.Text as UiTextComponent

@Composable
internal fun GroupConfigScreen(
    groupId: String? = null,
    toAddRssSource: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val defaultGroupName = stringResource(Res.string.tab_settings_group_default_name)
    val state by producePresenter(key = groupId ?: "new_group") { presenter(groupId, defaultGroupName) }

    DisposableEffect(Unit) {
        onDispose {
            state.commit()
        }
    }
    state.selectedEditTab?.let {
        EditTabDialog(
            visible = true,
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

    val lazyListState = rememberLazyListState()
    val reorderableLazyColumnState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            state.moveTab(from.key, to.key)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }

    FlareScrollBar(lazyListState) {
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
                            icon = state.icon,
                            title = UiText.Raw(state.name.text.toString()),
                            size = 36.dp,
                            modifier = Modifier.clickable { state.setShowIconPicker(true) },
                        )
                        Flyout(
                            visible = state.showIconPicker,
                            onDismissRequest = { state.setShowIconPicker(false) },
                            placement = FlyoutPlacement.BottomAlignedStart,
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.FixedSize(48.dp),
                                modifier = Modifier.heightIn(max = 300.dp).widthIn(max = 300.dp),
                            ) {
                                items(state.availableIcons) { selectedIcon ->
                                    TabIcon(
                                        icon = selectedIcon,
                                        title = UiText.Raw(state.name.text.toString()),
                                        modifier =
                                            Modifier.padding(4.dp).clickable {
                                                state.setIcon(selectedIcon)
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

            itemsIndexed(state.tabs, key = { _, item -> item.id }) { _, item ->
                ReorderableItem(reorderableLazyColumnState, key = item.id) { isDragging ->
                    CardExpanderItem(
                        heading = {
                            UiTextComponent(item.title)
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
                                        contentDescription = "Edit",
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
        onDismiss = { state.setAddTab(false) },
        tabs = state.tabs.toImmutableList(),
        allTabs = state.allTabs,
        onAddTab = { tabItem ->
            state.addTab(tabItem)
        },
        onDeleteTab = state::deleteTab,
        toAddRssSource = toAddRssSource,
    )
}

@Composable
private fun presenter(
    groupId: String?,
    defaultGroupName: String,
) = run {
    val sharedState = remember { SharedGroupConfigPresenter() }.invoke()
    val tabSettingsState = remember { HomeTabSettingsPresenter() }.invoke()
    var initialItem by remember(groupId) { mutableStateOf<GroupTimelineTabItemV2?>(null) }
    tabSettingsState.homeTimelineTabs
        .onSuccess { tabs ->
            LaunchedEffect(groupId, tabs) {
                initialItem = tabs.filterIsInstance<GroupTimelineTabItemV2>().firstOrNull { it.id == groupId }
            }
        }
    val name =
        rememberTextFieldState()
    var icon by remember {
        mutableStateOf<IconType>(IconType.Material(UiIcon.Rss))
    }
    val tabs =
        remember {
            mutableStateListOf<TimelineTabItemV2>()
        }
    var initializedGroupId by remember(groupId) { mutableStateOf<String?>(null) }
    LaunchedEffect(initialItem?.id) {
        val item = initialItem ?: return@LaunchedEffect
        if (initializedGroupId == item.id) return@LaunchedEffect
        name.edit {
            replace(0, length, item.title.editableText)
        }
        icon = item.icon
        tabs.clear()
        tabs.addAll(item.children.distinctBy { it.id })
        initializedGroupId = item.id
    }
    var showAddTab by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var selectedEditTab by remember { mutableStateOf<TimelineTabItemV2?>(null) }
    val allTabs = remember { AllTabsPresenter() }.invoke()

    object {
        val name = name
        val icon = icon
        val tabs = tabs
        val showAddTab = showAddTab
        val showIconPicker = showIconPicker
        val selectedEditTab = selectedEditTab
        val allTabs = allTabs
        val availableIcons = sharedState.availableIcons

        fun setIcon(value: IconType) {
            icon = value
        }

        fun setAddTab(show: Boolean) {
            showAddTab = show
        }

        fun setShowIconPicker(show: Boolean) {
            showIconPicker = show
        }

        fun setEditTab(tab: TimelineTabItemV2?) {
            selectedEditTab = tab
        }

        fun addTab(tab: TimelineTabItemV2) {
            if (tabs.none { it.id == tab.id }) {
                tabs.add(tab)
            }
        }

        fun deleteTab(tab: TimelineTabItemV2) {
            tabs.removeIf { it.id == tab.id }
        }

        fun deleteTab(key: String) {
            tabs.removeIf { it.id == key }
        }

        fun updateTab(tab: TimelineTabItemV2) {
            val index = tabs.indexOfFirst { it.id == tab.id }
            if (index != -1) {
                tabs[index] = tab
            }
        }

        fun moveTab(
            from: Any,
            to: Any,
        ) {
            val fromIndex = tabs.indexOfFirst { it.id == from }
            val toIndex = tabs.indexOfFirst { it.id == to }
            if (fromIndex != -1 && toIndex != -1) {
                tabs.add(toIndex, tabs.removeAt(fromIndex))
            }
        }

        fun commit() {
            sharedState.commit(
                initialItem = initialItem,
                name = name.text.toString(),
                icon = icon,
                tabs = tabs.toList(),
                defaultGroupName = defaultGroupName,
            )
        }
    }
}

private val UiText.editableText: String
    get() =
        when (this) {
            is UiText.Raw -> string
            is UiText.Localized -> ""
        }
