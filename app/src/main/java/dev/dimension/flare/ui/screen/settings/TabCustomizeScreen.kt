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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.CircleMinus
import compose.icons.fontawesomeicons.solid.CirclePlus
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.data.model.Bluesky.FeedTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.IconType.Mixed
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.Misskey
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.model.resId
import dev.dimension.flare.data.model.toIcon
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.AccountType.Specific
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.PinnableTimelineTabPresenter
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.theme.MediumAlpha
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

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
internal fun TabCustomizeScreen(onBack: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val state by producePresenter { presenter() }
    val scope = rememberCoroutineScope()
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
            FlareTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_tab_customization))
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
            @Composable
            fun TabItem(tabItem: TabItem) {
                ListTabItem(
                    data = tabItem,
                    isAdded = state.tabs.any { tab -> tabItem.key == tab.key },
                    modifier =
                        Modifier.clickable {
                            if (state.tabs.any { tab -> tabItem.key == tab.key }) {
                                state.deleteTab(tabItem.key)
                            } else {
                                state.addTab(tabItem)
                            }
                            state.setAddTab(false)
                        },
                )
            }
            Column(
                modifier = Modifier.fillMaxHeight(),
            ) {
                state.allTabs.accountTabs.onSuccess { tabs ->
                    val pagerState =
                        rememberPagerState {
                            tabs.size + 1
                        }
                    SecondaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        Tab(
                            selected = pagerState.currentPage == 0,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                            text = {
                                Text(text = stringResource(id = R.string.tab_settings_default))
                            },
                        )
                        tabs.forEachIndexed { index, tabState ->
                            LeadingIconTab(
                                selected = pagerState.currentPage == index + 1,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index + 1)
                                    }
                                },
                                text = {
                                    tabState.onSuccess { tab ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            RichText(
                                                text = tab.profile.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = tab.profile.handle,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier =
                                                    Modifier
                                                        .alpha(MediumAlpha),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                },
                                icon = {
                                    tabState.onSuccess { tab ->
                                        AvatarComponent(
                                            tab.profile.avatar,
                                            size = 24.dp,
                                        )
                                    }
                                },
                            )
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (it == 0) {
                            LazyColumn {
                                items(state.allTabs.defaultTabs) {
                                    TabItem(it)
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val tabState = tabs[it - 1]
                                tabState.onSuccess { tab ->
                                    var selectedIndex by remember { mutableStateOf(0) }
                                    if (tab.extraTabs.any()) {
                                        val items =
                                            listOf(
                                                stringResource(id = R.string.tab_settings_default),
                                            ) +
                                                tab.extraTabs
                                                    .map {
                                                        when (it) {
                                                            is PinnableTimelineTabPresenter.State.Tab.Feed ->
                                                                R.string.tab_settings_feed
                                                            is PinnableTimelineTabPresenter.State.Tab.List ->
                                                                R.string.tab_settings_list
                                                            is PinnableTimelineTabPresenter.State.Tab.Antenna ->
                                                                R.string.home_tab_antennas_title
                                                        }
                                                    }.map { stringResource(id = it) }
                                        ButtonGroup(
                                            overflowIndicator = {},
                                        ) {
                                            items.forEachIndexed { index, text ->
                                                toggleableItem(
                                                    checked = selectedIndex == index,
                                                    onCheckedChange = {
                                                        selectedIndex = index
                                                    },
                                                    label = text,
                                                )
                                            }
                                        }
                                    }
                                    when (selectedIndex) {
                                        0 -> {
                                            LazyColumn {
                                                items(tab.tabs) {
                                                    TabItem(it)
                                                }
                                            }
                                        }

                                        else -> {
                                            LazyColumn {
                                                val data = tab.extraTabs.elementAtOrNull(selectedIndex - 1)?.data
                                                if (data != null) {
                                                    items(data) { item ->
                                                        TabItem(remember(item) { item.toTabItem(accountKey = tab.profile.key) })
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
private fun LazyListScope.tabItem(
    item: ActualTabItem,
    deleteTab: (ActualTabItem) -> Unit,
    editTab: (ActualTabItem) -> Unit,
    reorderableLazyColumnState: ReorderableLazyListState,
    haptics: HapticFeedback,
    canSwipeToDelete: Boolean,
) {
    item(key = item.tabItem.key) {
        val swipeState =
            rememberSwipeToDismissBoxState()

        LaunchedEffect(swipeState.settledValue) {
            if (swipeState.settledValue != SwipeToDismissBoxValue.Settled) {
                delay(AnimationConstants.DefaultDurationMillis.toLong())
                deleteTab(item)
            }
        }
        ReorderableItem(reorderableLazyColumnState, key = item.key) { isDragging ->
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

        is IconType.Mixed -> {
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
//    val except = remember(cacheTabs) {
//        cacheTabs.map { it.key }.toImmutableList()
//    }
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

        fun deleteTab(key: String) {
            cacheTabs.removeIf { it.key == key }
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
                    .map { (_, userState) ->
                        userState.flatMap { user ->
                            val tabs =
                                remember(user.key) {
                                    TimelineTabItem.defaultPrimary(user) +
                                        TimelineTabItem.defaultSecondary(
                                            user,
                                        )
                                }
                            userState
                                .flatMap { user ->
                                    listTabPresenter(accountKey = user.key).tabs.map {
                                        it.toImmutableList()
                                    }
                                }.map { extraTabs ->
                                    AccountTabs(
                                        profile = user,
                                        tabs = tabs.toImmutableList(),
                                        extraTabs = extraTabs,
                                    )
                                }
                        }
                    }.toImmutableList()
            }

        object {
            val defaultTabs = TimelineTabItem.default.toImmutableList()
            val accountTabs: UiState<ImmutableList<UiState<AccountTabs>>> = accountTabs
        }
    }

@Immutable
internal data class AccountTabs(
    val profile: UiProfile,
    val tabs: ImmutableList<TabItem>,
    val extraTabs: ImmutableList<PinnableTimelineTabPresenter.State.Tab>,
)

@Composable
private fun listTabPresenter(accountKey: MicroBlogKey) =
    run {
        remember(accountKey) {
            PinnableTimelineTabPresenter(accountType = AccountType.Specific(accountKey))
        }.invoke()
    }
