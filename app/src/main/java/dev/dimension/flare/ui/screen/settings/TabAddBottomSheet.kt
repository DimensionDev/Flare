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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.TabRowIndicator
import dev.dimension.flare.ui.component.TabTitle
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toTabItem
import dev.dimension.flare.ui.presenter.list.PinnableTimelineTabPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TabAddBottomSheet(
    tabs: ImmutableList<TabItem>,
    allTabs: AllTabsPresenter.State,
    onDismissRequest: () -> Unit,
    onAddTab: (TabItem) -> Unit,
    onDeleteTab: (String) -> Unit,
    toAddRssSource: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        @Composable
        fun TabItem(
            tabItem: TabItem,
            modifier: Modifier = Modifier,
        ) {
            ListTabItem(
                data = tabItem,
                isAdded = tabs.any { tab -> tabItem.key == tab.key },
                modifier =
                    modifier.clickable {
                        if (tabs.any { tab -> tabItem.key == tab.key }) {
                            onDeleteTab(tabItem.key)
                        } else {
                            onAddTab(tabItem)
                        }
                    },
            )
        }
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            allTabs.accountTabs.onSuccess { tabs ->
                val pagerState =
                    rememberPagerState {
                        tabs.size + 1
                    }
                val scope = rememberCoroutineScope()
                SecondaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    indicator = {
                        TabRowIndicator(
                            selectedIndex = pagerState.currentPage,
                        )
                    },
                    divider = {},
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        text = {
                            Text(text = stringResource(id = R.string.rss_title))
                        },
                        modifier = Modifier.clip(CircleShape),
                    )
                    tabs.forEachIndexed { index, tab ->
                        LeadingIconTab(
                            modifier = Modifier.clip(CircleShape),
                            selected = pagerState.currentPage == index + 1,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index + 1)
                                }
                            },
                            text = {
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
                                        text = tab.profile.handle.canonical,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            icon = {
                                AvatarComponent(
                                    tab.profile.avatar,
                                    size = 24.dp,
                                )
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
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            itemsIndexed(
                                allTabs.rssTabs,
                            ) { index, it ->
                                TabItem(
                                    it,
                                    modifier =
                                        Modifier
                                            .listCard(
                                                index = index,
                                                totalCount = allTabs.rssTabs.size,
                                            ),
                                )
                            }
                            if (allTabs.rssTabs.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                            item {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Button(
                                        onClick = toAddRssSource,
                                    ) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.Plus,
                                            contentDescription = stringResource(R.string.add_rss_source),
                                        )
                                        Text(stringResource(R.string.add_rss_source))
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val tab = tabs[it - 1]
                            var selectedIndex by remember { mutableIntStateOf(0) }
                            if (tab.extraTabs.any()) {
                                val items =
                                    listOf(
                                        stringResource(id = R.string.tab_settings_default),
                                    ) +
                                        tab.extraTabs
                                            .map {
                                                when (it) {
                                                    is PinnableTimelineTabPresenter.State.Tab.Feed -> {
                                                        R.string.tab_settings_feed
                                                    }

                                                    is PinnableTimelineTabPresenter.State.Tab.List -> {
                                                        R.string.tab_settings_list
                                                    }

                                                    is PinnableTimelineTabPresenter.State.Tab.Antenna -> {
                                                        R.string.home_tab_antennas_title
                                                    }

                                                    is PinnableTimelineTabPresenter.State.Tab.Channel -> {
                                                        R.string.channel_title
                                                    }
                                                }
                                            }.map { stringResource(id = it) }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    items.forEachIndexed { index, text ->
                                        FilterChip(
                                            selected = selectedIndex == index,
                                            onClick = { selectedIndex = index },
                                            label = {
                                                Text(text)
                                            },
                                        )
                                    }
                                }
                            }
                            when (selectedIndex) {
                                0 -> {
                                    LazyColumn(
                                        contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        itemsIndexed(tab.tabs) { index, it ->
                                            TabItem(
                                                it,
                                                modifier =
                                                    Modifier
                                                        .listCard(
                                                            index = index,
                                                            totalCount = tab.tabs.size,
                                                        ),
                                            )
                                        }
                                    }
                                }

                                else -> {
                                    LazyColumn(
                                        contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        val data =
                                            tab.extraTabs.elementAtOrNull(selectedIndex - 1)?.data
                                        if (data != null) {
                                            itemsIndexed(data) { index, totalCount, item ->
                                                TabItem(
                                                    remember(item) { item.toTabItem(accountKey = tab.profile.key) },
                                                    modifier =
                                                        Modifier
                                                            .listCard(
                                                                index = index,
                                                                totalCount = totalCount,
                                                            ),
                                                )
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

@Composable
internal fun ListTabItem(
    data: TabItem,
    isAdded: Boolean,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            TabTitle(data.metaData.title)
        },
        leadingContent = {
            TabIcon(data)
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
    isEditing: Boolean,
    deleteTab: (TabItem) -> Unit,
    editTab: (TabItem) -> Unit,
    reorderableLazyColumnState: ReorderableLazyListState,
    canSwipeToDelete: Boolean,
    modifier: Modifier = Modifier,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
) {
    val haptics = LocalHapticFeedback.current
    val swipeState =
        rememberSwipeToDismissBoxState()
    LaunchedEffect(swipeState.settledValue) {
        if (swipeState.settledValue != SwipeToDismissBoxValue.Settled) {
            delay(AnimationConstants.DefaultDurationMillis.toLong())
            swipeState.reset()
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
            val isSwiping =
                swipeState.dismissDirection != SwipeToDismissBoxValue.Settled
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
                                    .background(MaterialTheme.colorScheme.error, shape = shapes.draggedShape)
                                    .padding(16.dp),
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Trash,
                                contentDescription = stringResource(id = R.string.tab_settings_remove),
                                modifier =
                                    Modifier
                                        .align(alignment),
                                tint = MaterialTheme.colorScheme.onError,
                            )
                        }
                    }
                },
            ) {
                SegmentedListItem(
                    elevation = ListItemDefaults.elevation(elevation),
                    selected = isDragging || isSwiping || isEditing,
                    onClick = {},
                    shapes = shapes,
                    content = {
                        TabTitle(item.metaData.title)
                    },
                    leadingContent = {
                        TabIcon(
                            item,
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
