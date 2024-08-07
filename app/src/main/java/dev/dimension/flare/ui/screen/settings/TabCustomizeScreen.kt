package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.material3.placeholder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import dev.dimension.flare.R
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun TabCustomizeRoute(navigator: ProxyDestinationsNavigator) {
    TabCustomizeScreen(
        onBack = navigator::navigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TabCustomizeScreen(onBack: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val state by producePresenter { presenter() }
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
                state.updateTab(it)
            },
        )
    }
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_tab_customization))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            state.setAddTab(true)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
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
            state.tabs.forEach { item ->
                when (item) {
                    is ActualTabItem -> {
                        tabItem(
                            item = item,
                            deleteTab = state::deleteTab,
                            editTab = {
                                state.setEditTab(it.tabItem)
                            },
                            reorderableLazyColumnState = reorderableLazyColumnState,
                            haptics = haptics,
                            canSwipeToDelete = state.canSwipeToDelete,
                        )
                    }
                    PrimaryTabItemState -> {
                        stickyHeader {
                            ListItem(
                                headlineContent = {
                                    Text(text = stringResource(id = R.string.tab_settings_primary))
                                },
                            )
                        }
                    }
                    SecondaryTabItemState -> {
                        stickyHeader(key = SecondaryTabItemState.key) {
                            ReorderableItem(
                                state = reorderableLazyColumnState,
                                key = SecondaryTabItemState.key,
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(text = stringResource(id = R.string.tab_settings_secondary))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.showAddTab) {
        ModalBottomSheet(
            onDismissRequest = {
                state.setAddTab(false)
            },
        ) {
            LazyColumn {
                items(state.allTabs.defaultTabs) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            TabTitle(it.metaData.title)
                        },
                        leadingContent = {
                            TabIcon(it.account, it.metaData.icon, it.metaData.title)
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    state.addTab(it)
                                    state.setAddTab(false)
                                },
                    )
                }

                state.allTabs.accountTabs.onSuccess {
                    it.forEach { (userState, tabState) ->
                        stickyHeader {
                            AccountItem(userState = userState.second, onClick = {}, toLogin = {})
                        }
                        tabState.onSuccess {
                            items(it) { tab ->
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    headlineContent = {
                                        TabTitle(tab.metaData.title)
                                    },
                                    leadingContent = {
                                        TabIcon(tab.account, tab.metaData.icon, tab.metaData.title)
                                    },
                                    modifier =
                                        Modifier
                                            .padding(start = AvatarComponentDefaults.size + 16.dp)
                                            .clickable {
                                                state.addTab(tab)
                                                state.setAddTab(false)
                                            },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.tabItem(
    item: ActualTabItem,
    deleteTab: (ActualTabItem) -> Unit,
    editTab: (ActualTabItem) -> Unit,
    reorderableLazyColumnState: ReorderableLazyListState,
    haptics: HapticFeedback,
    canSwipeToDelete: Boolean,
) {
    item(key = item.tabItem.key) {
        var shouldDismiss by remember { mutableStateOf(false) }
        val swipeState =
            rememberSwipeToDismissBoxState(
                confirmValueChange = {
                    if (it != SwipeToDismissBoxValue.Settled) {
                        shouldDismiss = true
                    }
                    it != SwipeToDismissBoxValue.Settled
                },
            )
        LaunchedEffect(shouldDismiss) {
            if (shouldDismiss) {
                delay(AnimationConstants.DefaultDurationMillis.toLong())
                deleteTab(item)
                shouldDismiss = false
            }
        }
        ReorderableItem(reorderableLazyColumnState, key = item.key) { isDragging ->
            AnimatedVisibility(
                visible = !shouldDismiss,
                exit =
                    shrinkVertically(
                        animationSpec = tween(),
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(),
            ) {
                val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                Surface(
                    shadowElevation = elevation,
                ) {
                    SwipeToDismissBox(
                        state = swipeState,
                        enableDismissFromEndToStart = canSwipeToDelete,
                        enableDismissFromStartToEnd = canSwipeToDelete,
                        backgroundContent = {
                            val alignment =
                                when (swipeState.dismissDirection) {
                                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                    SwipeToDismissBoxValue.Settled -> Alignment.Center
                                }
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.error)
                                        .padding(16.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(id = R.string.tab_settings_remove),
                                    modifier =
                                        Modifier
                                            .size(24.dp)
                                            .align(alignment),
                                    tint = MaterialTheme.colorScheme.onError,
                                )
                            }
                        },
                    ) {
                        ListItem(
                            headlineContent = {
                                TabTitle(item.tabItem.metaData.title)
                            },
                            leadingContent = {
                                TabIcon(
                                    item.tabItem.account,
                                    item.tabItem.metaData.icon,
                                    item.tabItem.metaData.title,
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = {
                                            editTab.invoke(item)
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(id = R.string.tab_settings_edit),
                                        )
                                    }
                                    IconButton(
                                        modifier =
                                            Modifier.draggableHandle(
                                                onDragStarted = {
                                                    haptics.performHapticFeedback(
                                                        HapticFeedbackType.LongPress,
                                                    )
                                                },
                                                onDragStopped = {
                                                    haptics.performHapticFeedback(
                                                        HapticFeedbackType.LongPress,
                                                    )
                                                },
                                            ),
                                        onClick = {},
                                    ) {
                                        Icon(
                                            Icons.Rounded.DragHandle,
                                            contentDescription = stringResource(id = R.string.tab_settings_drag),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TabTitle(
    title: TitleType,
    modifier: Modifier = Modifier,
) {
    Text(
        text =
            when (title) {
                is TitleType.Localized -> stringResource(id = title.resId)
                is TitleType.Text -> title.content
            },
        modifier = modifier,
    )
}

@Composable
fun TabIcon(
    accountType: AccountType,
    icon: IconType,
    title: TitleType,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
) {
    when (icon) {
        is IconType.Avatar -> {
            val userState by producePresenter(key = "$accountType:${icon.userKey}") {
                remember(accountType, icon.userKey) {
                    UserPresenter(
                        accountType,
                        icon.userKey,
                    )
                }.invoke()
            }
            userState.user
                .onSuccess {
                    AvatarComponent(it.avatar, size = 24.dp, modifier = modifier)
                }.onLoading {
                    AvatarComponent(null, size = 24.dp, modifier = modifier.placeholder(true))
                }
        }

        is IconType.Material -> {
            Icon(
                imageVector = icon.icon.toIcon(),
                contentDescription =
                    when (title) {
                        is TitleType.Localized -> stringResource(id = title.resId)
                        is TitleType.Text -> title.content
                    },
                modifier =
                    modifier
                        .size(24.dp),
            )
        }

        is IconType.Mixed -> {
            if (iconOnly) {
                Icon(
                    imageVector = icon.icon.toIcon(),
                    contentDescription =
                        when (title) {
                            is TitleType.Localized -> stringResource(id = title.resId)
                            is TitleType.Text -> title.content
                        },
                    modifier =
                        modifier
                            .size(24.dp),
                )
            } else {
                val userState by producePresenter(key = "$accountType:${icon.userKey}") {
                    remember(accountType, icon.userKey) {
                        UserPresenter(
                            accountType,
                            icon.userKey,
                        )
                    }.invoke()
                }
                Box(
                    modifier = modifier,
                ) {
                    userState.user
                        .onSuccess {
                            AvatarComponent(it.avatar, size = 24.dp)
                        }.onLoading {
                            AvatarComponent(null, size = 24.dp, modifier = Modifier.placeholder(true))
                        }
                    Icon(
                        imageVector = icon.icon.toIcon(),
                        contentDescription =
                            when (title) {
                                is TitleType.Localized -> stringResource(id = title.resId)
                                is TitleType.Text -> title.content
                            },
                        modifier =
                            Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd)
                                .background(MaterialTheme.colorScheme.background, shape = CircleShape)
                                .padding(2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun presenter(
    repository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val tabSettings by repository.tabSettings.collectAsUiState()
    val cacheTabs =
        remember {
            mutableStateListOf<TabItemState>()
        }
    var showAddTab by remember { mutableStateOf(false) }
    var selectedEditTab by remember { mutableStateOf<TabItem?>(null) }
    tabSettings
        .onSuccess {
            LaunchedEffect(it.items.size) {
                cacheTabs.clear()
                cacheTabs.add(PrimaryTabItemState)
                cacheTabs.addAll(it.items.map { ActualTabItem(it) })
                cacheTabs.add(SecondaryTabItemState)
                it.secondaryItems?.let { secondaryItems ->
                    cacheTabs.addAll(secondaryItems.map { ActualTabItem(it) })
                }
            }
        }.onError {
            LaunchedEffect(Unit) {
                cacheTabs.clear()
                cacheTabs.add(PrimaryTabItemState)
                cacheTabs.addAll(TimelineTabItem.default.map { ActualTabItem(it) })
                cacheTabs.add(SecondaryTabItemState)
            }
        }
    val allTabs = allTabsPresenter()

    object {
        val tabs = cacheTabs
        val allTabs = allTabs
        val showAddTab = showAddTab
        val canSwipeToDelete = cacheTabs.filterIsInstance<ActualTabItem>().size > 1
        val selectedEditTab = selectedEditTab

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
                repository.updateTabSettings {
                    copy(
                        items =
                            cacheTabs
                                .subList(1, cacheTabs.indexOfFirst { it is SecondaryTabItemState })
                                .map { (it as ActualTabItem).tabItem }
                                .toImmutableList(),
                        secondaryItems =
                            cacheTabs
                                .subList(
                                    cacheTabs.indexOfFirst { it is SecondaryTabItemState } + 1,
                                    cacheTabs.size,
                                ).map { (it as ActualTabItem).tabItem }
                                .toImmutableList()
                                .takeIf {
                                    it.isNotEmpty()
                                },
                    )
                }
            }
        }

        fun setAddTab(value: Boolean) {
            showAddTab = value
        }

        fun deleteTab(tab: ActualTabItem) {
            cacheTabs.removeIf { it.key == tab.key }
        }

        fun addTab(tab: TabItem) {
            cacheTabs.add(ActualTabItem(tab))
        }

        fun setEditTab(tab: TabItem?) {
            selectedEditTab = tab
        }

        fun updateTab(tab: TabItem) {
            val index = cacheTabs.indexOfFirst { it.key == tab.key }
            cacheTabs[index] = ActualTabItem(tab)
        }
    }
}

@Immutable
private sealed interface TabItemState {
    val key: String
}

@Immutable
private data object PrimaryTabItemState : TabItemState {
    override val key: String
        get() = "primary"
}

@Immutable
private data object SecondaryTabItemState : TabItemState {
    override val key: String
        get() = "secondary"
}

@Immutable
private data class ActualTabItem(
    val tabItem: TabItem,
) : TabItemState {
    override val key: String
        get() = tabItem.key
}

@Composable
private fun allTabsPresenter() =
    run {
        val accountState = remember { AccountsPresenter() }.invoke()
        val accountTabs =
            accountState.accounts.map {
                it
                    .toImmutableList()
                    .associateWith { (_, userState) ->
                        userState.map { user ->
                            TimelineTabItem.defaultPrimary(user) + TimelineTabItem.defaultSecondary(user)
                        }
                    }.toImmutableMap()
            }

        object {
            val defaultTabs = TimelineTabItem.default.toImmutableList()
            val accountTabs = accountTabs
        }
    }
