package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AnglesUp
import compose.icons.fontawesomeicons.solid.Plus
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.dimension.flare.R
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.Glassify
import dev.dimension.flare.ui.component.LocalBottomBarShowing
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.TabRowIndicator
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.HomeTimelineWithTabsPresenter
import dev.dimension.flare.ui.presenter.TimelineItemPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.TabIcon
import dev.dimension.flare.ui.screen.settings.TabTitle
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun HomeTimelineScreen(
    accountType: AccountType,
    toCompose: () -> Unit,
    toQuickMenu: () -> Unit,
    toLogin: () -> Unit,
    toTabSettings: () -> Unit,
) {
    val state by producePresenter(key = "home_timeline_$accountType") {
        timelinePresenter(accountType)
    }
    val scope = rememberCoroutineScope()
    state.pagerState.onSuccess { pagerState ->
        state.tabState.onSuccess { tabState ->
            LaunchedEffect(pagerState.currentPage >= tabState.size) {
                if (pagerState.currentPage >= tabState.size) {
                    scope.launch {
                        pagerState.scrollToPage(0)
                    }
                }
            }
            val currentTab = tabState.elementAtOrNull(pagerState.currentPage)
            if (currentTab != null) {
                val lazyListState = currentTab.lazyListState
                RegisterTabCallback(
                    lazyListState = lazyListState,
                    onRefresh = {
                        currentTab.refreshSync()
                        state.changeLogState.dismissChangeLog()
                    },
                )
            }
        }
    }

    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    state.pagerState.onSuccess { pagerState ->
                        state.tabState.onSuccess { tabs ->
                            if (tabs.any()) {
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
                                    divider = {},
                                    indicator = {
                                        TabRowIndicator(
                                            selectedIndex =
                                                minOf(
                                                    pagerState.currentPage,
                                                    tabs.lastIndex,
                                                ),
                                        )
                                    },
                                    minTabWidth = 48.dp,
                                ) {
                                    state.tabState.onSuccess { tabs ->
                                        tabs.forEachIndexed { index, tab ->
                                            LeadingIconTab(
                                                modifier = Modifier.clip(CircleShape),
                                                selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                unselectedContentColor = LocalContentColor.current,
                                                selected = index == pagerState.currentPage,
                                                onClick = {
                                                    scope.launch {
                                                        pagerState.scrollToPage(index)
                                                    }
                                                },
                                                text = {
                                                    TabTitle(
                                                        tab.timelineTabItem.metaData.title,
//                                                        modifier =
//                                                            Modifier
//                                                                .padding(8.dp),
                                                    )
                                                },
                                                icon = {
                                                    TabIcon(
                                                        accountType = tab.timelineTabItem.account,
                                                        icon = tab.timelineTabItem.metaData.icon,
                                                        title = tab.timelineTabItem.metaData.title,
                                                    )
                                                },
//                                                colors = FilterChipDefaults.filterChipColors(
//                                                    containerColor = MaterialTheme.colorScheme.surface,
//                                                ),
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            toTabSettings.invoke()
                                        },
                                    ) {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Plus,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    if (LocalBottomBarShowing.current) {
                        state.user.onSuccess {
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
                        }
                    }
                },
                actions = {
                    state.user
                        .onError {
                            TextButton(onClick = toLogin) {
                                Text(text = stringResource(id = R.string.login_button))
                            }
                        }.onSuccess {
                        }
                },
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        state.pagerState.onSuccess { pagerState ->
            state.tabState.onSuccess { tabs ->
                HorizontalPager(
                    state = pagerState,
                    key = { index ->
                        tabs.getOrNull(index)?.timelineTabItem?.key ?: "timeline_$index"
                    },
                ) { index ->
                    TimelineItemContent(
                        state = tabs[index],
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxWidth(),
                        changeLogState = state.changeLogState,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TimelineItemContent(
    state: TimelineItemPresenter.State,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    changeLogState: ChangeLogState? = null,
) {
    val hazeState = rememberHazeState()
    val scope = rememberCoroutineScope()
    RefreshContainer(
        modifier = modifier,
        onRefresh = {
            state.refreshSync()
            changeLogState?.dismissChangeLog()
        },
        isRefreshing = state.isRefreshing,
        indicatorPadding = contentPadding,
        content = {
            LazyStatusVerticalStaggeredGrid(
                state = state.lazyListState,
                contentPadding = contentPadding,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState),
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
                                    if (!isBigScreen()) {
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
                            .padding(contentPadding)
                            .align(Alignment.TopCenter),
                ) {
                    Glassify(
                        onClick = {
                            state.onNewTootsShown()
                            scope.launch {
                                state.lazyListState.scrollToItem(0)
                            }
                        },
                        hazeState = hazeState,
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
                            Text(text = stringResource(id = R.string.home_timeline_new_toots))
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun timelinePresenter(accountType: AccountType) =
    run {
        val state = remember(accountType) { HomeTimelineWithTabsPresenter(accountType) }.invoke()

        val pagerState =
            state.tabState.map {
                rememberPagerState { it.size }
            }

        val changeLogState = changeLogPresenter()

        object : HomeTimelineWithTabsPresenter.State by state {
            val pagerState = pagerState
            val changeLogState = changeLogState
        }
    }
