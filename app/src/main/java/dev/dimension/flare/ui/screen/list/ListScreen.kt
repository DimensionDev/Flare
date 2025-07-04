package dev.dimension.flare.ui.screen.list

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.ThumbtackSlash
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.uiListItemComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.AllListPresenter
import dev.dimension.flare.ui.presenter.list.AllListState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@Composable
internal fun ListDetailPlaceholder(modifier: Modifier = Modifier) {
    FlareScaffold(
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterVertically),
        ) {
            FAIcon(
                FontAwesomeIcons.Solid.List,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListScreen(
    accountType: AccountType,
    toList: (UiList) -> Unit,
    createList: () -> Unit,
    editList: (UiList) -> Unit,
    deleteList: (UiList) -> Unit,
) {
    val state by producePresenter {
        presenter(accountType)
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.home_tab_list_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            createList.invoke()
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(id = R.string.list_add),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = state.isRefreshing,
            onRefresh = state::refresh,
            content = {
                LazyColumn(
                    contentPadding = contentPadding,
                ) {
                    uiListItemComponent(
                        state.items,
                        toList,
                        trailingContent = { item ->
                            state.currentTabs.onSuccess { currentTabs ->
                                val isPinned =
                                    remember(
                                        item,
                                        currentTabs,
                                    ) {
                                        currentTabs.contains(item.id)
                                    }
                                IconButton(
                                    onClick = {
                                        if (isPinned) {
                                            state.unpinList(item)
                                        } else {
                                            state.pinList(item)
                                        }
                                    },
                                ) {
                                    AnimatedContent(isPinned) {
                                        if (it) {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.ThumbtackSlash,
                                                contentDescription = stringResource(id = R.string.tab_settings_add),
                                            )
                                        } else {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.Thumbtack,
                                                contentDescription = stringResource(id = R.string.tab_settings_remove),
                                            )
                                        }
                                    }
                                }
                            }
                            if (!item.readonly) {
                                var showDropdown by remember {
                                    mutableStateOf(false)
                                }
                                IconButton(onClick = { showDropdown = true }) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                                        contentDescription = stringResource(id = R.string.more),
                                    )
                                    DropdownMenu(
                                        expanded = showDropdown,
                                        onDismissRequest = { showDropdown = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(id = R.string.list_edit),
                                                )
                                            },
                                            onClick = {
                                                editList(item)
                                                showDropdown = false
                                            },
                                            leadingIcon = {
                                                FAIcon(
                                                    imageVector = FontAwesomeIcons.Solid.Pen,
                                                    contentDescription = stringResource(id = R.string.list_edit),
                                                )
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(id = R.string.list_delete),
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            },
                                            onClick = {
                                                deleteList(item)
                                                showDropdown = false
                                            },
                                            leadingIcon = {
                                                FAIcon(
                                                    imageVector = FontAwesomeIcons.Solid.Trash,
                                                    contentDescription = stringResource(id = R.string.list_delete),
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    settingsRepository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val tabSettings by settingsRepository.tabSettings.collectAsUiState()
    val accountState =
        remember(accountType) {
            UserPresenter(
                accountType = accountType,
                userKey = null,
            )
        }.invoke()
    val currentTabs =
        accountState.user.flatMap { user ->
            tabSettings.map {
                it.mainTabs
                    .filterIsInstance<ListTimelineTabItem>()
                    .map { it.listId }
                    .toImmutableList()
            }
        }
    val state =
        remember(accountType) {
            AllListPresenter(accountType)
        }.invoke()

    object : AllListState by state {
        val currentTabs = currentTabs

        fun pinList(item: UiList) {
            accountState.user.onSuccess { user ->
                appScope.launch {
                    settingsRepository.updateTabSettings {
                        copy(
                            mainTabs =
                                mainTabs +
                                    ListTimelineTabItem(
                                        account = AccountType.Specific(user.key),
                                        listId = item.id,
                                        metaData =
                                            TabMetaData(
                                                title = TitleType.Text(item.title),
                                                icon = IconType.Material(IconType.Material.MaterialIcon.List),
                                            ),
                                    ),
                        )
                    }
                }
            }
        }

        fun unpinList(item: UiList) {
            accountState.user.onSuccess { user ->
                appScope.launch {
                    settingsRepository.updateTabSettings {
                        copy(
                            mainTabs =
                                mainTabs.filter {
                                    if (it is ListTimelineTabItem) {
                                        it.listId != item.id
                                    } else {
                                        true
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}
