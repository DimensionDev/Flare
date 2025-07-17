package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.CircleMinus
import compose.icons.fontawesomeicons.solid.CirclePlus
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.TableList
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.data.model.Bluesky.FeedTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.IconType.Mixed
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.Misskey
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.model.resId
import dev.dimension.flare.data.model.toIcon
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.AccountType.Specific
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.PinnableTimelineTabPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.fornewid.placeholder.material3.placeholder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import soup.compose.material.motion.animation.materialFadeIn
import soup.compose.material.motion.animation.materialFadeOut

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
internal fun TabCustomizeScreen(
    onBack: () -> Unit,
    toAddRssSource: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
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
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_side_panel))
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
        AnimatedVisibility(
            state.tabs.isEmpty(),
            enter = materialFadeIn(),
            exit = materialFadeOut(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                FAIcon(
                    FontAwesomeIcons.Solid.TableList,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                )
                Text(
                    stringResource(R.string.settings_side_panel_empty),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
        LazyColumn(
            state = lazyListState,
            contentPadding = it,
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(
                state.tabs,
                key = { _, item -> item.key },
            ) { index, item ->
                TabCustomItem(
                    item = item,
                    deleteTab = state::deleteTab,
                    editTab = {
                        state.setEditTab(it)
                    },
                    reorderableLazyColumnState = reorderableLazyColumnState,
                    canSwipeToDelete = state.canSwipeToDelete,
                    modifier =
                        Modifier
                            .listCard(
                                index = index,
                                totalCount = state.tabs.size,
                            ),
                )
            }
        }
    }
    if (state.showAddTab) {
        TabAddBottomSheet(
            tabs = state.tabs.toImmutableList(),
            allTabs = state.allTabs,
            onDismissRequest = {
                state.setAddTab(false)
            },
            onAddTab = { tabItem ->
                state.addTab(tabItem)
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

internal fun UiList.toTabItem(accountKey: MicroBlogKey) =
    when (type) {
        UiList.Type.Feed -> {
            FeedTabItem(
                account = Specific(accountKey),
                uri = id,
                metaData =
                    TabMetaData(
                        title = TitleType.Text(title),
                        icon =
                            Mixed(
                                icon = IconType.Material.MaterialIcon.List,
                                userKey = accountKey,
                            ),
                    ),
            )
        }

        UiList.Type.List ->
            ListTimelineTabItem(
                account = AccountType.Specific(accountKey),
                listId = id,
                metaData =
                    TabMetaData(
                        title = TitleType.Text(title),
                        icon =
                            IconType.Mixed(
                                icon = IconType.Material.MaterialIcon.List,
                                userKey = accountKey,
                            ),
                    ),
            )

        UiList.Type.Antenna ->
            Misskey.AntennasTimelineTabItem(
                account = AccountType.Specific(accountKey),
                id = id,
                metaData =
                    TabMetaData(
                        title = TitleType.Text(title),
                        icon =
                            IconType.Mixed(
                                icon = IconType.Material.MaterialIcon.Rss,
                                userKey = accountKey,
                            ),
                    ),
            )
    }

@Composable
internal fun ListTabItem(
    data: TabItem,
    isAdded: Boolean,
    modifier: Modifier = Modifier,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            TabTitle(data.metaData.title)
        },
        leadingContent = {
            TabIcon(data.account, data.metaData.icon, data.metaData.title)
        },
        modifier = modifier,
        trailingContent = {
            if (isAdded) {
                FAIcon(
                    FontAwesomeIcons.Solid.CircleMinus,
                    contentDescription = stringResource(id = R.string.tab_settings_remove),
                )
            } else {
                FAIcon(
                    FontAwesomeIcons.Solid.CirclePlus,
                    contentDescription = stringResource(id = R.string.tab_settings_add),
                )
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LazyItemScope.TabCustomItem(
    item: TabItem,
    deleteTab: (TabItem) -> Unit,
    editTab: (TabItem) -> Unit,
    reorderableLazyColumnState: ReorderableLazyListState,
    canSwipeToDelete: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val swipeState =
        rememberSwipeToDismissBoxState()

    LaunchedEffect(swipeState.settledValue) {
        if (swipeState.settledValue != SwipeToDismissBoxValue.Settled) {
            delay(AnimationConstants.DefaultDurationMillis.toLong())
            deleteTab(item)
        }
    }
    ReorderableItem(reorderableLazyColumnState, key = item.key, modifier = modifier) { isDragging ->
        AnimatedVisibility(
            visible = swipeState.settledValue == SwipeToDismissBoxValue.Settled,
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
                        if (swipeState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.error)
                                        .padding(16.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Trash,
                                    contentDescription = stringResource(id = R.string.tab_settings_remove),
                                    modifier =
                                        Modifier
//                                            .size(24.dp)
                                            .align(alignment),
                                    tint = MaterialTheme.colorScheme.onError,
                                )
                            }
                        }
                    },
                ) {
                    ListItem(
                        headlineContent = {
                            TabTitle(item.metaData.title)
                        },
                        leadingContent = {
                            TabIcon(
                                item.account,
                                item.metaData.icon,
                                item.metaData.title,
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = {
                                        editTab.invoke(item)
                                    },
                                ) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Pen,
                                        contentDescription = stringResource(id = R.string.tab_settings_edit),
                                    )
                                }
                                IconButton(
                                    modifier =
                                        Modifier.draggableHandle(
                                            onDragStarted = {
                                                haptics.performHapticFeedback(
                                                    HapticFeedbackType.Confirm,
                                                )
                                            },
                                            onDragStopped = {
                                                haptics.performHapticFeedback(
                                                    HapticFeedbackType.Confirm,
                                                )
                                            },
                                        ),
                                    onClick = {},
                                ) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Bars,
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
            FAIcon(
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

        is Mixed -> {
            if (iconOnly) {
                FAIcon(
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
                            AvatarComponent(
                                null,
                                size = 24.dp,
                                modifier = Modifier.placeholder(true),
                            )
                        }
                    FAIcon(
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
                                .background(
                                    MaterialTheme.colorScheme.background,
                                    shape = CircleShape,
                                ).padding(2.dp),
                    )
                }
            }
        }

        is IconType.Url -> {
            NetworkImage(icon.url, contentDescription = null, modifier = modifier.size(24.dp))
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
            mutableStateListOf<TabItem>()
        }
    var showAddTab by remember { mutableStateOf(false) }
    var selectedEditTab by remember { mutableStateOf<TabItem?>(null) }
    tabSettings
        .onSuccess {
            LaunchedEffect(it.secondaryItems?.size) {
                cacheTabs.clear()
                it.secondaryItems?.let { secondaryItems ->
                    cacheTabs.addAll(secondaryItems)
                }
            }
        }
//    val except = remember(cacheTabs) {
//        cacheTabs.map { it.key }.toImmutableList()
//    }
    val allTabs = allTabsPresenter()

    object {
        val tabs = cacheTabs
        val allTabs = allTabs
        val showAddTab = showAddTab
        val canSwipeToDelete = true
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
                        secondaryItems =
                            cacheTabs
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

        fun deleteTab(tab: TabItem) {
            cacheTabs.removeIf { it.key == tab.key }
        }

        fun deleteTab(key: String) {
            cacheTabs.removeIf { it.key == key }
        }

        fun addTab(tab: TabItem) {
            cacheTabs.add(tab)
        }

        fun setEditTab(tab: TabItem?) {
            selectedEditTab = tab
        }

        fun updateTab(tab: TabItem) {
            val index = cacheTabs.indexOfFirst { it.key == tab.key }
            cacheTabs[index] = tab
        }
    }
}

@Immutable
internal data class AccountTabs(
    val profile: UiProfile,
    val tabs: ImmutableList<TabItem>,
    val extraTabs: ImmutableList<PinnableTimelineTabPresenter.State.Tab>,
)
