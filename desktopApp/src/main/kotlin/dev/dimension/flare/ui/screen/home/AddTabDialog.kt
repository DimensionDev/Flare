package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleMinus
import compose.icons.fontawesomeicons.solid.CirclePlus
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.Res
import dev.dimension.flare.add_rss_source
import dev.dimension.flare.antenna_title
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isEmpty
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ok
import dev.dimension.flare.rss_title
import dev.dimension.flare.tab_settings_add
import dev.dimension.flare.tab_settings_default
import dev.dimension.flare.tab_settings_feed
import dev.dimension.flare.tab_settings_list
import dev.dimension.flare.tab_settings_remove
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.TabTitle
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toTabItem
import dev.dimension.flare.ui.presenter.home.rss.RssSourcesPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.PinnableTimelineTabPresenter
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.theme.MediumAlpha
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.Text
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AddTabDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    tabs: ImmutableList<TabItem>,
    allTabs: AllTabsState,
    onAddTab: (TabItem) -> Unit,
    onDeleteTab: (String) -> Unit,
    toAddRssSource: () -> Unit,
) {
    ContentDialog(
        visible = visible,
        title = stringResource(Res.string.tab_settings_add),
        primaryButtonText = stringResource(Res.string.ok),
        onButtonClick = {
            onDismiss.invoke()
        },
        content = {
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
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allTabs.accountTabs.onSuccess { tabs ->
                    val pagerState =
                        rememberPagerState {
                            tabs.size + 2
                        }
                    val scope = rememberCoroutineScope()
                    LiteFilter {
                        PillButton(
                            selected = pagerState.currentPage == 0,
                            onSelectedChanged = {
                                if (it) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(0)
                                    }
                                }
                            },
                            content = {
                                Text(text = stringResource(Res.string.tab_settings_default))
                            },
                        )
                        PillButton(
                            selected = pagerState.currentPage == 1,
                            onSelectedChanged = {
                                if (it) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                }
                            },
                            content = {
                                Text(text = stringResource(Res.string.rss_title))
                            },
                        )
                        tabs.forEachIndexed { index, tabState ->
                            PillButton(
                                modifier = Modifier.clip(CircleShape),
                                selected = pagerState.currentPage == index + 2,
                                onSelectedChanged = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index + 2)
                                    }
                                },
                                content = {
                                    tabState.onSuccess { tab ->
                                        AvatarComponent(
                                            tab.profile.avatar,
                                            size = 24.dp,
                                        )
                                        RichText(
                                            text = tab.profile.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = tab.profile.handle,
                                            style = FluentTheme.typography.caption,
                                            modifier =
                                                Modifier
                                                    .alpha(MediumAlpha),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
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
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                itemsIndexed(allTabs.defaultTabs) { index, it ->
                                    TabItem(
                                        it,
                                        modifier =
                                            Modifier
                                                .listCard(
                                                    index = index,
                                                    totalCount = allTabs.defaultTabs.size,
                                                ),
                                    )
                                }
                            }
                        } else if (it == 1) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                itemsIndexed(
                                    allTabs.rssTabs,
                                    emptyContent = {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            AccentButton(
                                                onClick = toAddRssSource,
                                            ) {
                                                FAIcon(
                                                    FontAwesomeIcons.Solid.Plus,
                                                    contentDescription = stringResource(Res.string.add_rss_source),
                                                )
                                                Text(stringResource(Res.string.add_rss_source))
                                            }
                                        }
                                    },
                                ) { index, itemCount, it ->
                                    TabItem(
                                        remember(it) {
                                            RssTimelineTabItem(it)
                                        },
                                        modifier =
                                            Modifier
                                                .listCard(
                                                    index = index,
                                                    totalCount = itemCount,
                                                ),
                                    )
                                }
                                if (!allTabs.rssTabs.isEmpty) {
                                    item {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    item {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            AccentButton(
                                                onClick = toAddRssSource,
                                            ) {
                                                FAIcon(
                                                    FontAwesomeIcons.Solid.Plus,
                                                    contentDescription = stringResource(Res.string.add_rss_source),
                                                )
                                                Text(stringResource(Res.string.add_rss_source))
                                            }
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
                                                stringResource(Res.string.tab_settings_default),
                                            ) +
                                                tab.extraTabs
                                                    .map {
                                                        when (it) {
                                                            is PinnableTimelineTabPresenter.State.Tab.Feed ->
                                                                Res.string.tab_settings_feed

                                                            is PinnableTimelineTabPresenter.State.Tab.List ->
                                                                Res.string.tab_settings_list

                                                            is PinnableTimelineTabPresenter.State.Tab.Antenna ->
                                                                Res.string.antenna_title
                                                        }
                                                    }.map { stringResource(it) }
                                        LiteFilter(
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            items.forEachIndexed { index, text ->
                                                PillButton(
                                                    selected = selectedIndex == index,
                                                    onSelectedChanged = { selectedIndex = index },
                                                ) {
                                                    Text(text = text)
                                                }
                                            }
                                        }
                                    }
                                    when (selectedIndex) {
                                        0 -> {
                                            LazyColumn(
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
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                val data =
                                                    tab.extraTabs.elementAtOrNull(selectedIndex - 1)?.data
                                                if (data != null) {
                                                    itemsIndexed(data) { index, totalCount, item ->
                                                        TabItem(
                                                            remember(item) {
                                                                item.toTabItem(
                                                                    accountKey = tab.profile.key,
                                                                )
                                                            },
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
        },
    )
}

@Composable
internal fun ListTabItem(
    data: TabItem,
    isAdded: Boolean,
    modifier: Modifier = Modifier,
) {
    CardExpanderItem(
        heading = {
            TabTitle(data.metaData.title)
        },
        icon = {
            TabIcon(data)
        },
        modifier = modifier,
        trailing = {
            if (isAdded) {
                FAIcon(
                    FontAwesomeIcons.Solid.CircleMinus,
                    contentDescription = stringResource(Res.string.tab_settings_remove),
                )
            } else {
                FAIcon(
                    FontAwesomeIcons.Solid.CirclePlus,
                    contentDescription = stringResource(Res.string.tab_settings_add),
                )
            }
        },
    )
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
                    .filter {
                        it.second.isSuccess
                    }.map { (_, userState) ->
                        userState.flatMap { user ->
                            val tabs =
                                remember(user.key) {
                                    (
                                        TimelineTabItem.defaultPrimary(user) +
                                            TimelineTabItem.secondaryFor(
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
                TimelineTabItem.mainSidePanel
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

@Immutable
internal data class AccountTabs(
    val profile: UiProfile,
    val tabs: ImmutableList<TabItem>,
    val extraTabs: ImmutableList<PinnableTimelineTabPresenter.State.Tab>,
)
