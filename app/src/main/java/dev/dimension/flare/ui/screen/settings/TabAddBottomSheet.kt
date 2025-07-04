package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.R
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.rss.RssSourcesPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.PinnableTimelineTabPresenter
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.theme.MediumAlpha
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TabAddBottomSheet(
    tabs: ImmutableList<TabItem>,
    allTabs: AllTabsState,
    onDismissRequest: () -> Unit,
    onAddTab: (TabItem) -> Unit,
    onDeleteTab: (String) -> Unit,
    toAddRssSource: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        @Composable
        fun TabItem(tabItem: TabItem) {
            ListTabItem(
                data = tabItem,
                isAdded = tabs.any { tab -> tabItem.key == tab.key },
                modifier =
                    Modifier.clickable {
                        if (tabs.any { tab -> tabItem.key == tab.key }) {
                            onDeleteTab(tabItem.key)
                        } else {
                            onAddTab(tabItem)
                        }
                        onDismissRequest()
                    },
            )
        }
        Column(
            modifier = Modifier.fillMaxHeight(),
        ) {
            allTabs.accountTabs.onSuccess { tabs ->
                val pagerState =
                    rememberPagerState {
                        tabs.size + 2
                    }
                val scope = rememberCoroutineScope()
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
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        text = {
                            Text(text = stringResource(id = R.string.rss_title))
                        },
                    )
                    tabs.forEachIndexed { index, tabState ->
                        LeadingIconTab(
                            selected = pagerState.currentPage == index + 2,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index + 2)
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
                            items(allTabs.defaultTabs) {
                                TabItem(it)
                            }
                        }
                    } else if (it == 1) {
                        LazyColumn {
                            items(
                                allTabs.rssTabs,
                                emptyContent = {
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
                                },
                            ) {
                                TabItem(
                                    remember(it) {
                                        RssTimelineTabItem(it)
                                    },
                                )
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
                            val tabState = tabs[it - 2]
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
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        items.forEachIndexed { index, text ->
                                            if (selectedIndex == index) {
                                                Button(
                                                    onClick = { selectedIndex = index },
                                                    modifier = Modifier.padding(4.dp),
                                                ) {
                                                    Text(text = text)
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = { selectedIndex = index },
                                                    modifier = Modifier.padding(4.dp),
                                                ) {
                                                    Text(text = text)
                                                }
                                            }
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

@Immutable
internal interface AllTabsState {
    val defaultTabs: ImmutableList<TabItem>
    val rssTabs: PagingState<UiRssSource>
    val accountTabs: UiState<ImmutableList<UiState<AccountTabs>>>
}

@Composable
internal fun allTabsPresenter(filterIsTimeline: Boolean = false): AllTabsState =
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
                                    (
                                        TimelineTabItem.defaultPrimary(user) +
                                            TimelineTabItem.defaultSecondary(
                                                user,
                                            )
                                    ).let {
                                        if (filterIsTimeline) {
                                            it.filterIsInstance<TimelineTabItem>()
                                        } else {
                                            it
                                        }
                                    }
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

        val rssTabs =
            remember {
                RssSourcesPresenter()
            }.invoke()

        object : AllTabsState {
            override val defaultTabs =
                TimelineTabItem.default
                    .let {
                        if (filterIsTimeline) {
                            it.filterIsInstance<TimelineTabItem>()
                        } else {
                            it
                        }
                    }.toImmutableList()
            override val accountTabs: UiState<ImmutableList<UiState<AccountTabs>>> = accountTabs
            override val rssTabs = rssTabs.sources
        }
    }

@Composable
private fun listTabPresenter(accountKey: MicroBlogKey) =
    run {
        remember(accountKey) {
            PinnableTimelineTabPresenter(accountType = AccountType.Specific(accountKey))
        }.invoke()
    }
