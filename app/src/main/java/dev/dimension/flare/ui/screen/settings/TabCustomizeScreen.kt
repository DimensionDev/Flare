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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.material3.placeholder
import com.ramcosta.composedestinations.annotation.Destination
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
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
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
import sh.calvin.reorderable.rememberReorderableLazyColumnState

@Destination(
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
            rememberReorderableLazyColumnState(lazyListState) { from, to ->
                state.moveTab(from.index, to.index)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        LazyColumn(
            state = lazyListState,
            contentPadding = it,
        ) {
            items(state.tabs, key = { it.key }) { item ->
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
                        state.deleteTab(item)
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
                                enableDismissFromEndToStart = state.tabs.size > 1,
                                enableDismissFromStartToEnd = state.tabs.size > 1,
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
                                                onClick = {},
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
                            AccountItem(userState = userState, onClick = {})
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
            userState.user.onSuccess {
                AvatarComponent(it.avatarUrl, size = 24.dp, modifier = modifier)
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
                modifier = modifier,
            )
        }

        is IconType.Mixed -> {
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
                userState.user.onSuccess {
                    AvatarComponent(it.avatarUrl, size = 24.dp)
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

    tabSettings.onSuccess {
        LaunchedEffect(it.items.size) {
            cacheTabs.clear()
            cacheTabs.addAll(it.items)
        }
    }
    val allTabs = allTabsPresenter()

    object {
        val tabs = cacheTabs
        val allTabs = allTabs
        val showAddTab = showAddTab

        fun moveTab(
            from: Int,
            to: Int,
        ) {
            cacheTabs.add(to, cacheTabs.removeAt(from))
        }

        fun commit() {
            appScope.launch {
                repository.updateTabSettings {
                    copy(items = cacheTabs.toImmutableList())
                }
            }
        }

        fun setAddTab(value: Boolean) {
            showAddTab = value
        }

        fun deleteTab(tab: TabItem) {
            cacheTabs.remove(tab)
        }

        fun addTab(tab: TabItem) {
            if (tab !in cacheTabs) {
                cacheTabs.add(tab)
            }
        }
    }
}

@Composable
private fun allTabsPresenter() =
    run {
        val accountState = remember { AccountsPresenter() }.invoke()
        val accountTabs =
            accountState.accounts.map {
                it.toImmutableList().associateWith { userState ->
                    userState.map { user ->
                        when (user) {
                            is UiUser.Bluesky -> TimelineTabItem.bluesky(user.userKey)
                            is UiUser.Mastodon -> TimelineTabItem.mastodon(user.userKey)
                            is UiUser.Misskey -> TimelineTabItem.misskey(user.userKey)
                            is UiUser.XQT -> TimelineTabItem.xqt(user.userKey)
                        }.toImmutableList()
                    }
                }.toImmutableMap()
            }

        object {
            val defaultTabs = TimelineTabItem.default.toImmutableList()
            val accountTabs = accountTabs
        }
    }
