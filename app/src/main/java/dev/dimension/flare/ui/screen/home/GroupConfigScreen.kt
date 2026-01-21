package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.TableList
import dev.dimension.flare.R
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.MixedTimelineTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.AllTabsPresenter
import dev.dimension.flare.ui.screen.settings.TabAddBottomSheet
import dev.dimension.flare.ui.screen.settings.TabCustomItem
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.segmentedShapes2
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun GroupConfigScreen(
    item: MixedTimelineTabItem?,
    onBack: () -> Unit,
    toAddRssSource: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = LocalHapticFeedback.current
    val state by producePresenter(key = item?.key ?: "new_group") { GroupConfigPresenter(item) }

    DisposableEffect(Unit) {
        onDispose {
            state.commit()
        }
    }

    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(
                        text =
                            if (item ==
                                null
                            ) {
                                stringResource(R.string.tab_settings_add_group)
                            } else {
                                stringResource(R.string.tab_settings_edit_group)
                            },
                    )
                },
                navigationIcon = {
                    BackButton(onBack = {
                        onBack()
                    })
                },
                actions = {
                    IconButton(
                        onClick = {
                            state.setAddTab(true)
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(id = R.string.tab_settings_add),
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { padding ->
        val lazyListState = rememberLazyListState()
        val reorderableLazyColumnState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                state.moveTab(from.key, to.key)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }

        LazyColumn(
            state = lazyListState,
            contentPadding = padding,
            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        TabIcon(
                            accountType = AccountType.Guest,
                            icon = state.icon,
                            title = TitleType.Text(state.name.text.toString()),
                            size = 64.dp,
                            modifier = Modifier.clickable { state.setShowIconPicker(true) },
                        )

                        if (state.showIconPicker) {
                            Popup(
                                onDismissRequest = { state.setShowIconPicker(false) },
                                alignment = Alignment.BottomCenter,
                                properties = PopupProperties(usePlatformDefaultWidth = true, focusable = true),
                            ) {
                                Card(
                                    modifier = Modifier.sizeIn(maxHeight = 256.dp, maxWidth = 384.dp),
                                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                                ) {
                                    LazyHorizontalGrid(rows = GridCells.FixedSize(48.dp)) {
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
                        }
                    }

                    OutlinedTextField(
                        state = state.name,
                        label = { Text(stringResource(R.string.tab_settings_group_name_placeholder)) },
                        modifier = Modifier.weight(1f),
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Text(
                            text = stringResource(R.string.tab_settings_group_empty),
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            itemsIndexed(state.tabs, key = { _, item -> item.key }) { index, item ->
                TabCustomItem(
                    item = item,
                    shapes = ListItemDefaults.segmentedShapes2(index, state.tabs.size),
                    deleteTab = { state.deleteTab(item) },
                    editTab = { }, // Child tabs in a group are intentionally not editable from this screen; only group-level configuration is supported.
                    reorderableLazyColumnState = reorderableLazyColumnState,
                    canSwipeToDelete = true,
                    isEditing = false,
                )
            }
        }
    }

    if (state.showAddTab) {
        TabAddBottomSheet(
            tabs = state.tabs.toImmutableList(),
            allTabs = state.allTabs,
            onDismissRequest = { state.setAddTab(false) },
            onAddTab = { tabItem ->
                if (tabItem is TimelineTabItem) {
                    state.addTab(tabItem)
                }
            },
            onDeleteTab = { key -> state.deleteTab(key) },
            toAddRssSource = toAddRssSource,
        )
    }
}

@Composable
private fun GroupConfigPresenter(
    initialItem: MixedTimelineTabItem?,
    repository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val name =
        rememberTextFieldState(
            initialItem?.metaData?.title?.let {
                when (it) {
                    is TitleType.Text -> it.content
                    is TitleType.Localized -> "" // Or resolve string resource if possible
                }
            } ?: "",
        )

    var icon by remember {
        mutableStateOf<IconType>(initialItem?.metaData?.icon ?: IconType.Material(IconType.Material.MaterialIcon.Rss))
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
                if (tabs.isEmpty()) {
                    if (initialItem != null) {
                        repository.updateTabSettings {
                            val currentTabs = mainTabs.toMutableList()
                            currentTabs.removeIf { it.key == initialItem.key }
                            copy(mainTabs = currentTabs)
                        }
                    }
                    return@launch
                }
                val newGroup =
                    MixedTimelineTabItem(
                        subTimelineTabItem = tabs.toList(),
                        metaData =
                            TabMetaData(
                                title = TitleType.Text(name.text.toString().ifEmpty { "Group" }),
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
