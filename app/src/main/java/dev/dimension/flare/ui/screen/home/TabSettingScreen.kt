package dev.dimension.flare.ui.screen.home

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.PinnableTimelineTabPresenter
import dev.dimension.flare.ui.screen.settings.ListTabItem
import dev.dimension.flare.ui.screen.settings.TabTitle
import dev.dimension.flare.ui.screen.settings.toTabItem
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun TabSettingRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
) {
    TabSettingScreen(
        accountType = accountType,
        onBack = navigator::navigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TabSettingScreen(
    accountType: AccountType,
    onBack: () -> Unit,
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
            items(state.currentTabs, key = { it.key }) { item ->
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
                                enableDismissFromEndToStart = state.canSwipeToDelete,
                                enableDismissFromStartToEnd = state.canSwipeToDelete,
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
                                        TabTitle(item.metaData.title)
                                    },
                                    trailingContent = {
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
            fun TabItem(tabItem: TimelineTabItem) {
                ListTabItem(
                    data = tabItem,
                    isAdded = state.currentTabs.any { tab -> tabItem.key == tab.key },
                    modifier =
                        Modifier.clickable {
                            if (state.currentTabs.any { tab -> tabItem.key == tab.key }) {
                                state.deleteTab(tabItem.key)
                            } else {
                                state.addTab(tabItem)
                            }
                            state.setAddTab(false)
                        },
                )
            }
            LazyColumn {
                state.tabs.onSuccess { tabs ->
                    items(tabs) {
                        TabItem(it)
                    }
                }
                state.extraTabs.onSuccess { extraTabs ->
                    state.accountState.user.onSuccess { user ->
                        extraTabs.forEach { tab ->
                            items(tab.data) { item ->
                                TabItem(remember(item) { item.toTabItem(accountKey = user.key) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    settingsRepository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val accountState =
        remember(accountType) {
            UserPresenter(
                accountType = accountType,
                userKey = null,
            )
        }.invoke()
    val tabSettings by settingsRepository.tabSettings.collectAsUiState()
    val cacheTabs =
        remember {
            mutableStateListOf<TimelineTabItem>()
        }
    val currentTabs =
        accountState.user.flatMap { user ->
            tabSettings.map {
                it.homeTabs
                    .getOrDefault(user.key, listOf(HomeTimelineTabItem(accountType = AccountType.Specific(user.key))))
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
    val tabs =
        accountState
            .user
            .map { user ->
                remember(user.key) {
                    TimelineTabItem
                        .defaultPrimary(user)
                        .plus(
                            TimelineTabItem.defaultSecondary(
                                user,
                            ),
                        ).filterIsInstance<TimelineTabItem>()
                        .toImmutableList()
                }
            }
    val extraTabs =
        remember(accountType) {
            PinnableTimelineTabPresenter(accountType = accountType)
        }.invoke().tabs.map {
            it.toImmutableList()
        }
    var showAddTab by remember { mutableStateOf(false) }
    object {
        val accountState = accountState
        val currentTabs = cacheTabs.toImmutableList()
        val tabs = tabs
        val extraTabs = extraTabs
        val canSwipeToDelete = cacheTabs.size > 1
        val showAddTab = showAddTab

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
                            homeTabs = homeTabs + (user.key to cacheTabs),
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
