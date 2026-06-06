package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowsRotate
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.data.model.tab.resolveTimelineAppearance
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.refresh
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.floatingToolbarVerticalNestedScroll
import dev.dimension.flare.ui.component.platform.LocalWindowSizeClass
import dev.dimension.flare.ui.component.platform.WindowSizeClass
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.HomeTimelineWithTabsPresenter
import dev.dimension.flare.ui.presenter.home.CanComposePresenter
import dev.dimension.flare.ui.presenter.home.DeepLinkPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.rememberTimelineItemPresenterWithLazyListState
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.route.Router
import dev.dimension.flare.ui.screen.compose.ComposeDialog
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.NavigationDefaults
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.SubtleButton
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import dev.dimension.flare.ui.component.Text as UiText

@Composable
internal fun HomeTimelineScreen(
    accountType: AccountType,
    onAddTab: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val state by producePresenter(key = "home_timeline_$accountType") {
        presenter(accountType)
    }
    val canComposeState = remember { CanComposePresenter() }.invoke()
    if (LocalGlobalAppearance.current.deckMode && isBigScreen()) {
        val lazyRowState = rememberLazyListState()
        FlareScrollBar(
            state = lazyRowState,
            isVertical = false,
        ) {
            LazyRow(
                state = lazyRowState,
            ) {
                state.tabState.onSuccess { tabs ->
                    items(
                        items = tabs,
                        key = { it.id },
                    ) { tab ->
                        val deepLinkState by producePresenter("desktop_deck_timeline_${tab.id}") {
                            val backStack =
                                remember(tab.id) {
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
                                remember(deepLinkState) {
                                    object : UriHandler {
                                        override fun openUri(uri: String) {
                                            deepLinkState.handle(uri)
                                        }
                                    }
                                },
                            LocalWindowSizeClass provides WindowSizeClass.Compact,
                        ) {
                            Row {
                                Box {
                                    Router(
                                        modifier =
                                            Modifier
                                                .fillMaxHeight()
                                                .width(360.dp),
                                        backStack = deepLinkState.backStack.toImmutableList(),
                                        navigate = { route ->
                                            when (route) {
                                                Route.TabSetting -> {
                                                    onAddTab.invoke()
                                                }

                                                else -> {
                                                    deepLinkState.backStack.add(route)
                                                }
                                            }
                                        },
                                        onBack = {
                                            if (deepLinkState.backStack.size > 1) {
                                                deepLinkState.backStack.removeAt(deepLinkState.backStack.lastIndex)
                                            }
                                        },
                                        enableDeepLinkHandler = false,
                                    )
                                    if (deepLinkState.backStack.size > 1) {
                                        NavigationDefaults.BackButton(
                                            onClick = {
                                                if (deepLinkState.backStack.size > 1) {
                                                    deepLinkState.backStack.removeAt(deepLinkState.backStack.lastIndex)
                                                }
                                            },
                                            modifier =
                                                Modifier
                                                    .align(Alignment.TopStart),
                                        )
                                    }
                                }
                                if (tab != tabs.last()) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxHeight()
                                                .width(1.dp)
                                                .background(FluentTheme.colors.stroke.divider.default),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        state.tabState.onSuccess { tabState ->
            state.selectedTab.onSuccess { currentTab ->
                val currentTabTimelineState =
                    key(currentTab.id) {
                        rememberTimelineItemPresenterWithLazyListState(currentTab)
                    }
                val timelineAppearance = LocalTimelineAppearance.current
                CompositionLocalProvider(
                    LocalTimelineAppearance provides
                        remember(
                            currentTab.appearancePatch,
                            timelineAppearance,
                        ) {
                            currentTab.resolveTimelineAppearance(timelineAppearance)
                        },
                ) {
                    Box {
                        TimelineContent(
                            state = currentTabTimelineState,
                            modifier =
                                Modifier
                                    .floatingToolbarVerticalNestedScroll(
                                        expanded = state.isTopBarExpanded,
                                        onExpand = {
                                            state.setTopBarExpanded(true)
                                        },
                                        onCollapse = {
                                            state.setTopBarExpanded(false)
                                        },
                                    ),
                            contentPadding = PaddingValues(top = 48.dp),
                            allowGalleryMode = true,
                            onScrollToTop = {
                                state.setTopBarExpanded(true)
                            },
                            header =
                                if (LocalGlobalAppearance.current.showComposeInHomeTimeline &&
                                    canComposeState.canCompose.takeSuccess() == true
                                ) {
                                    {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AdaptiveCard(
                                                modifier =
                                                    Modifier
                                                        .widthIn(max = 600.dp),
                                            ) {
                                                ComposeDialog(
                                                    onBack = null,
                                                    accountType = accountType,
                                                    focusOnOpen = false,
                                                    modifier = Modifier.padding(top = 16.dp),
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    null
                                },
                        )
                        AnimatedVisibility(
                            visible = state.isTopBarExpanded,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // prevent click through
                                    },
                            enter = slideInVertically { -it },
                            exit = slideOutVertically { -it },
                        ) {
                            Box {
                                Box(
                                    modifier =
                                        Modifier
                                            .matchParentSize()
                                            .background(FluentTheme.colors.background.mica.base)
                                            .blur(32.dp),
                                )
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth(1f)
                                            .padding(LocalWindowPadding.current)
                                            .padding(horizontal = screenHorizontalPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LiteFilter(
                                        modifier =
                                            Modifier
                                                .weight(1f),
                                    ) {
                                        tabState.forEachIndexed { index, tab ->
                                            PillButton(
                                                selected = tab.id == currentTab.id,
                                                onSelectedChanged = {
                                                    if (tab.id == currentTab.id) {
                                                        if (currentTabTimelineState.lazyListState.firstVisibleItemIndex == 0) {
                                                            currentTabTimelineState.refreshSync()
                                                        } else {
                                                            currentTabTimelineState.lazyListState.requestScrollToItem(
                                                                0,
                                                            )
                                                        }
                                                    } else {
                                                        state.setSelectedIndex(index)
                                                    }
                                                },
                                            ) {
                                                TabIcon(
                                                    tabItem = tab,
                                                )
                                                UiText(tab.title)
                                            }
                                        }
                                        PillButton(
                                            selected = false,
                                            onSelectedChanged = {
                                                onAddTab.invoke()
                                            },
                                        ) {
                                            FAIcon(
                                                FontAwesomeIcons.Solid.Plus,
                                                contentDescription = null,
                                            )
                                        }
                                    }

                                    SubtleButton(onClick = {
                                        currentTabTimelineState.refreshSync()
                                    }) {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.ArrowsRotate,
                                            contentDescription = stringResource(Res.string.refresh),
                                        )
                                    }
                                }
                            }
                        }
                        AnimatedVisibility(
                            currentTabTimelineState.isRefreshing,
                            enter = slideInVertically { -it },
                            exit = slideOutVertically { -it },
                        ) {
                            ProgressBar(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        var isTopBarExpanded by remember { mutableStateOf(true) }
        val state = remember { HomeTimelineWithTabsPresenter() }.invoke()
        var selectedIndex by remember {
            mutableStateOf(0)
        }

        state.tabState.onSuccess {
            LaunchedEffect(it.size) {
                selectedIndex = 0
            }
        }

        val selectedTab =
            remember(
                state.tabState,
                selectedIndex,
            ) {
                state.tabState.map { it.elementAt(selectedIndex) }
            }

        object : HomeTimelineWithTabsPresenter.State by state {
            val selectedTab = selectedTab

            fun setSelectedIndex(index: Int) {
                selectedIndex = index
            }

            val isTopBarExpanded: Boolean
                get() = isTopBarExpanded

            fun setTopBarExpanded(expanded: Boolean) {
                isTopBarExpanded = expanded
            }
        }
    }
