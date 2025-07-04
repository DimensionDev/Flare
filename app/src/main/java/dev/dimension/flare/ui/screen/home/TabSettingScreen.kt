package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.R
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.EditTabDialog
import dev.dimension.flare.ui.screen.settings.TabAddBottomSheet
import dev.dimension.flare.ui.screen.settings.TabCustomItem
import dev.dimension.flare.ui.screen.settings.allTabsPresenter
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun TabSettingScreen(
    accountType: AccountType,
    onBack: () -> Unit,
    toAddRssSource: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val state by producePresenter {
        presenter(accountType = accountType)
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
            onConfirm = {
                state.setEditTab(null)
                if (it is TimelineTabItem) {
                    state.updateTab(it)
                }
            },
        )
    }
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(text = stringResource(R.string.tab_settings_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
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
            )
        },
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
        ) {
            state.enableMixedTimeline.onSuccess { enabled ->
                item("header") {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.tab_settings_mixed_timeline))
                        },
                        trailingContent = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    state.setEnableMixedTimeline(it)
                                },
                            )
                        },
                        supportingContent = {
                            Text(stringResource(R.string.tab_settings_mixed_timeline_desc))
                        },
                        modifier =
                            Modifier.clickable {
                                state.setEnableMixedTimeline(!enabled)
                            },
                    )
                }
            }
            items(state.currentTabs, key = { it.key }) { item ->
                TabCustomItem(
                    item = item,
                    deleteTab = {
                        if (it is TimelineTabItem) {
                            state.deleteTab(item)
                        }
                    },
                    editTab = {
                        if (it is TimelineTabItem) {
                            state.setEditTab(it)
                        }
                    },
                    reorderableLazyColumnState = reorderableLazyColumnState,
                    canSwipeToDelete = state.canSwipeToDelete,
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
                if (tabItem is TimelineTabItem) {
                    state.addTab(tabItem)
                }
                state.setAddTab(false)
            },
            onDeleteTab = { key ->
                state.deleteTab(key)
                state.setAddTab(false)
            },
            toAddRssSource = toAddRssSource,
        )
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    settingsRepository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val scope = rememberCoroutineScope()
    val accountState =
        remember(accountType) {
            UserPresenter(
                accountType = accountType,
                userKey = null,
            )
        }.invoke()
    var selectedEditTab by remember { mutableStateOf<TabItem?>(null) }
    val allTabsState = allTabsPresenter(filterIsTimeline = true)
    val tabSettings by settingsRepository.tabSettings.collectAsUiState()
    val cacheTabs =
        remember {
            mutableStateListOf<TimelineTabItem>()
        }
    val currentTabs =
        accountState.user.flatMap { user ->
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
            accountState.user.onSuccess { user ->
                appScope.launch {
                    settingsRepository.updateTabSettings {
                        copy(
                            mainTabs = cacheTabs,
                        )
                    }
                }
            }
        }

        fun deleteTab(tab: TimelineTabItem) {
            cacheTabs.removeIf { it.key == tab.key }
        }

        fun deleteTab(key: String) {
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
