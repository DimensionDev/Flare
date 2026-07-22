package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AnglesUp
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.CaretDown
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.Sliders
import dev.dimension.flare.R
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.datastore.model.TimelineAutoRefreshInterval
import dev.dimension.flare.data.model.BottomBarBehavior
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.data.model.tab.resolveTimelineAppearance
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.Glassify
import dev.dimension.flare.ui.component.LocalAppSettings
import dev.dimension.flare.ui.component.LocalBottomBarShowing
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.platform.LocalWindowSizeClass
import dev.dimension.flare.ui.component.platform.WindowSizeClass
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.platform.isCompatScreen
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.HomeTimelineWithTabsPresenter
import dev.dimension.flare.ui.presenter.home.DeepLinkPresenter
import dev.dimension.flare.ui.presenter.home.LoggedInPresenter
import dev.dimension.flare.ui.presenter.home.LoggedInState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.rememberTimelineItemPresenterWithLazyListState
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.route.Router
import dev.dimension.flare.ui.theme.isLightTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import dev.dimension.flare.ui.component.Text as FlareText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun HomeTimelineScreen(
    toQuickMenu: () -> Unit,
    toLogin: () -> Unit,
    toTabSettings: () -> Unit,
    uriHandler: UriHandler,
) {
    val state by producePresenter(key = "home_timeline") {
        timelinePresenter()
    }
    val scope = rememberCoroutineScope()

    if (LocalGlobalAppearance.current.deckMode && isBigScreen()) {
        LazyRow(
            modifier =
                Modifier
                    .consumeWindowInsets(
                        WindowInsets.systemBars
                            .union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Horizontal),
                    ),
            contentPadding =
                WindowInsets.systemBars
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues(),
        ) {
            state.tabState.onSuccess { tabs ->
                items(tabs) { tab ->
                    val tabItemState by producePresenter("tabItemState_${tab.id}") {
                        val backStack =
                            remember {
                                mutableStateListOf<Route>(Route.DeckTimeline(tab.id))
                            }
                        val state =
                            DeepLinkPresenter(
                                onRoute = {
                                    Route.from(it)?.let { route ->
                                        backStack.add(route)
                                    }
                                },
                                onLink = {
                                    uriHandler.openUri(it)
                                },
                            ).invoke()
                        object : DeepLinkPresenter.State by state {
                            val backStack = backStack
                        }
                    }
                    CompositionLocalProvider(
                        LocalUriHandler provides
                            remember(tabItemState) {
                                object : UriHandler {
                                    override fun openUri(uri: String) {
                                        tabItemState.handle(uri)
                                    }
                                }
                            },
                        LocalWindowSizeClass provides WindowSizeClass.Compact,
                    ) {
                        Row {
                            Router(
                                modifier =
                                    Modifier
                                        .fillMaxHeight()
                                        .width(360.dp),
                                backStack = tabItemState.backStack,
                                openDrawer = {
                                    toQuickMenu.invoke()
                                },
                                navigate = {
                                    when (it) {
                                        is Route.TabSettings -> {
                                            toTabSettings.invoke()
                                        }

                                        is Route.ServiceSelect.Selection -> {
                                            toLogin.invoke()
                                        }

                                        else -> {
                                            tabItemState.backStack.add(it)
                                        }
                                    }
                                },
                                onBack = {
                                    if (tabItemState.backStack.size > 1) {
                                        tabItemState.backStack.removeAt(tabItemState.backStack.lastIndex)
                                    }
                                },
                            )
                            if (tab != tabs.last()) {
                                VerticalDivider(
                                    modifier =
                                        Modifier
                                            .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        val topAppBarScrollBehavior =
            if (LocalGlobalAppearance.current.bottomBarBehavior == BottomBarBehavior.AlwaysShow) {
                TopAppBarDefaults.pinnedScrollBehavior()
            } else {
                TopAppBarDefaults.enterAlwaysScrollBehavior()
            }
        FlareScaffold(
            topBar = {
                val appearance = LocalTimelineAppearance.current
                val backgroundColor = MaterialTheme.colorScheme.background
                val plainColor =
                    if (isLightTheme() && isCompatScreen()) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        backgroundColor
                    }
                val tabs = state.tabState.takeSuccess()
                val tabBackgroundColors =
                    remember(tabs, appearance, plainColor, backgroundColor) {
                        tabs
                            ?.map { tab ->
                                when (tab.resolveTimelineAppearance(appearance).timelineDisplayMode) {
                                    TimelineDisplayMode.Plain -> plainColor
                                    else -> backgroundColor
                                }
                            }.orEmpty()
                    }
                val pagerState = state.pagerState.takeSuccess()
                val color by remember(pagerState, tabBackgroundColors, backgroundColor) {
                    derivedStateOf {
                        if (pagerState == null || tabBackgroundColors.isEmpty()) {
                            backgroundColor
                        } else {
                            val pagePosition =
                                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                    .coerceIn(0f, tabBackgroundColors.lastIndex.toFloat())
                            val startIndex = pagePosition.toInt().coerceIn(0, tabBackgroundColors.lastIndex)
                            val endIndex = (startIndex + 1).coerceAtMost(tabBackgroundColors.lastIndex)
                            val fraction = (pagePosition - startIndex).coerceIn(0f, 1f)
                            lerp(
                                tabBackgroundColors[startIndex],
                                tabBackgroundColors[endIndex],
                                fraction,
                            )
                        }
                    }
                }
                FlareTopAppBar(
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = color,
                            scrolledContainerColor = color,
                            actionIconContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    title = {
                        state.pagerState.onSuccess { pagerState ->
                            state.tabState.onSuccess { tabs ->
                                if (tabs.any()) {
                                    if (isCompatScreen() && tabs.size > MAX_SCROLLABLE_HOME_TABS) {
                                        HomeTabDropdown(
                                            tabs = tabs,
                                            selectedTabIndex =
                                                minOf(
                                                    pagerState.currentPage,
                                                    tabs.lastIndex,
                                                ),
                                            onTabSelected = { index ->
                                                scope.launch {
                                                    pagerState.scrollToPage(index)
                                                }
                                            },
                                        )
                                    } else {
                                        SecondaryScrollableTabRow(
                                            containerColor = Color.Transparent,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth(),
                                            selectedTabIndex =
                                                minOf(
                                                    pagerState.currentPage,
                                                    tabs.lastIndex,
                                                ),
                                            edgePadding = 0.dp,
                                            divider = { },
                                            indicator = {
                                                TabRowDefaults.SecondaryIndicator(
                                                    Modifier.tabIndicatorOffset(
                                                        minOf(
                                                            pagerState.currentPage,
                                                            tabs.lastIndex,
                                                        ),
                                                        matchContentSize = false,
                                                    ),
                                                )
                                            },
                                            minTabWidth = 48.dp,
                                        ) {
                                            tabs.forEachIndexed { index, tab ->
                                                LeadingIconTab(
                                                    modifier = Modifier,
                                                    selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    unselectedContentColor = LocalContentColor.current,
                                                    selected = index == pagerState.currentPage,
                                                    onClick = {
                                                        scope.launch {
                                                            pagerState.scrollToPage(index)
                                                        }
                                                    },
                                                    text = {
                                                        FlareText(
                                                            tab.title,
                                                        )
                                                    },
                                                    icon = {
                                                        TabIcon(tab)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        if (LocalBottomBarShowing.current) {
                            state.user
                                .onSuccess {
                                    IconButton(
                                        onClick = {
                                            toQuickMenu.invoke()
                                        },
                                    ) {
                                        AvatarComponent(
                                            it.avatar,
                                            size = 24.dp,
                                        )
                                    }
                                }.onError {
                                    IconButton(
                                        onClick = {
                                            toQuickMenu.invoke()
                                        },
                                    ) {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Bars,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }.onLoading {
                                    IconButton(
                                        onClick = {
                                            toQuickMenu.invoke()
                                        },
                                    ) {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Bars,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                toTabSettings.invoke()
                            },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Sliders,
                                contentDescription = stringResource(R.string.edit_tab_title),
                            )
                        }
                        if (state.isLoggedIn.takeSuccess() == false) {
                            TextButton(onClick = toLogin) {
                                Text(text = stringResource(id = R.string.login_button))
                            }
                        }
                    },
                )
            },
            modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        ) { contentPadding ->
            state.pagerState.onSuccess { pagerState ->
                state.tabState.onSuccess { tabState ->
                    LaunchedEffect(pagerState.currentPage >= tabState.size) {
                        if (pagerState.currentPage >= tabState.size) {
                            scope.launch {
                                pagerState.scrollToPage(0)
                            }
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        key = { index ->
                            tabState.getOrNull(index)?.id ?: "timeline_$index"
                        },
                    ) { index ->
                        val item = tabState.getOrNull(index)
                        if (item != null) {
                            val timelineAppearance = LocalTimelineAppearance.current
                            CompositionLocalProvider(
                                LocalTimelineAppearance provides
                                    remember(
                                        item.appearancePatch,
                                        timelineAppearance,
                                    ) {
                                        item.resolveTimelineAppearance(timelineAppearance)
                                    },
                            ) {
                                TimelineItemContent(
                                    item = item,
                                    contentPadding = contentPadding,
                                    modifier = Modifier.fillMaxWidth(),
                                    changeLogState = state,
                                    isHomeTimeline = true,
                                    isCurrentlyVisible = pagerState.currentPage == index,
                                    autoRefreshInterval =
                                        LocalAppSettings.current.homeTimelineAutoRefreshInterval,
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
private fun HomeTabDropdown(
    tabs: List<UiTimelineTabItem>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTab = tabs[selectedTabIndex]

    Box {
        AnimatedContent(selectedTab) { selectedTab ->
            Row(
                modifier =
                    Modifier
                        .clickable { expanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabIcon(selectedTab)
                FlareText(selectedTab.title, style = MaterialTheme.typography.bodyLarge)
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.CaretDown,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        FlareDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 200.dp, max = 320.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                DropdownMenuItem(
                    text = {
                        FlareText(tab.title)
                    },
                    leadingIcon = {
                        TabIcon(tab)
                    },
                    trailingIcon =
                        if (index == selectedTabIndex) {
                            {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                    onClick = {
                        expanded = false
                        onTabSelected(index)
                    },
                )
            }
        }
    }
}

private const val MAX_SCROLLABLE_HOME_TABS = 10

@Composable
internal fun TimelineItemContent(
    item: UiTimelineTabItem,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    changeLogState: ChangeLogState? = null,
    isHomeTimeline: Boolean = false,
    isCurrentlyVisible: Boolean = true,
    autoRefreshInterval: TimelineAutoRefreshInterval = TimelineAutoRefreshInterval.DISABLED,
    lazyStaggeredGridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    val isBigScreen = isBigScreen()
    val layoutDirection = LocalLayoutDirection.current
    val paddingWithStatusBar =
        PaddingValues(
            top =
                maxOf(
                    WindowInsets.safeContent.asPaddingValues().calculateTopPadding(),
                    contentPadding.calculateTopPadding(),
                ),
            bottom = contentPadding.calculateBottomPadding(),
            start = contentPadding.calculateStartPadding(layoutDirection),
            end = contentPadding.calculateEndPadding(layoutDirection),
        )
    val state =
        rememberTimelineItemPresenterWithLazyListState(
            item = item,
            isHomeTimeline = isHomeTimeline,
            lazyStaggeredGridState = lazyStaggeredGridState,
        )
    val latestState by rememberUpdatedState(state)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isHomeTimeline, autoRefreshInterval, isCurrentlyVisible, lifecycleOwner) {
        if (!isHomeTimeline || !isCurrentlyVisible || autoRefreshInterval == TimelineAutoRefreshInterval.DISABLED) {
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(autoRefreshInterval.minutes * 60_000L)
                if (!latestState.isRefreshing) {
                    latestState.refreshSuspend()
                }
            }
        }
    }
    if (isCurrentlyVisible) {
        RegisterTabCallback(
            lazyListState = state.lazyListState,
            onRefresh = {
                state.refreshSync()
                changeLogState?.dismissChangeLog()
            },
        )
    }
    val scope = rememberCoroutineScope()
    RefreshContainer(
        modifier = modifier,
        onRefresh = {
            state.refreshSync()
            changeLogState?.dismissChangeLog()
        },
        isRefreshing = state.isRefreshing,
        indicatorPadding = paddingWithStatusBar,
        content = {
            LazyStatusVerticalStaggeredGrid(
                state = state.lazyListState,
                contentPadding = contentPadding,
                allowGalleryMode = true,
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                changeLogState?.shouldShowChangeLog?.onSuccess {
                    changeLogState.changeLog?.let { changelog ->
                        if (it) {
                            item {
                                Column {
                                    AdaptiveCard {
                                        Column(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        horizontal = screenHorizontalPadding,
                                                    ).padding(top = 16.dp, bottom = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                stringResource(R.string.changelog_title),
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Text(
                                                stringResource(R.string.changelog_message),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(changelog)
                                            Button(
                                                onClick = {
                                                    changeLogState.dismissChangeLog()
                                                },
                                            ) {
                                                Text(
                                                    stringResource(android.R.string.ok),
                                                )
                                            }
                                        }
                                    }
                                    if (!isBigScreen) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                status(state.listState)
            }
            state.listState.onSuccess {
                AnimatedVisibility(
                    state.showNewToots,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                    modifier =
                        Modifier
                            .padding(paddingWithStatusBar)
                            .align(Alignment.TopCenter),
                ) {
                    Glassify(
                        onClick = {
                            state.onNewTootsShown()
                            scope.launch {
                                state.lazyListState.scrollToItem(0)
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.AnglesUp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            val ctx = LocalContext.current
                            val newTootsText =
                                ctx.resources.getQuantityString(
                                    R.plurals.home_timeline_new_toots,
                                    state.newPostsCount,
                                    state.newPostsCount,
                                )
                            Text(text = newTootsText)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun timelinePresenter() =
    run {
        val state = remember { HomeTimelineWithTabsPresenter() }.invoke()
        val loginState = remember { LoggedInPresenter() }.invoke()

        val pagerState =
            state.tabState.map {
                rememberPagerState { it.size }
            }

        val changeLogState = changeLogPresenter()

        object :
            HomeTimelineWithTabsPresenter.State by state,
            LoggedInState by loginState,
            ChangeLogState by changeLogState {
            val pagerState = pagerState
        }
    }
