package dev.dimension.flare.ui.screen.bluesky

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Rss
import compose.icons.fontawesomeicons.solid.SquareRss
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.ThumbtackSlash
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.status.ListComponent
import dev.dimension.flare.ui.component.status.StatusPlaceholder
import dev.dimension.flare.ui.component.uiListItemComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedsPresenter
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedsState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

// @OptIn(ExperimentalMaterial3AdaptiveApi::class)
// @Destination<RootGraph>(
//    wrappers = [ThemeWrapper::class],
// )
// @Composable
// internal fun BlueskyFeedsRoute(
//    navigator: DestinationsNavigator,
//    accountType: AccountType,
// ) {
//    val scope = rememberCoroutineScope()
//    val scaffoldNavigator =
//        rememberListDetailPaneScaffoldNavigator<BlueskyFeedUri>()
//    NavigableListDetailPaneScaffold(
//        navigator = scaffoldNavigator,
//        listPane = {
//            AnimatedPane {
//                BlueskyFeedsScreen(
//                    accountType = accountType,
//                    toFeed = {
//                        scope.launch {
//                            scaffoldNavigator.navigateTo(
//                                ListDetailPaneScaffoldRole.Detail,
//                                BlueskyFeedUri(it.id),
//                            )
//                        }
//                    },
//                )
//            }
//        },
//        detailPane = {
//            AnimatedPane {
//                scaffoldNavigator.currentDestination?.contentKey?.let {
//                    BlueskyFeedScreen(
//                        accountType = accountType,
//                        uri = it.value,
//                        onBack = {
//                            scope.launch {
//                                scaffoldNavigator.navigateBack()
//                            }
//                        },
//                    )
//                }
//            }
//        },
//    )
// }
//
// @JvmInline
// @Parcelize
// private value class BlueskyFeedUri(
//    val value: String,
// ) : Parcelable

@Composable
internal fun BlueskyFeedDetailPlaceholder(modifier: Modifier = Modifier) {
    FlareScaffold(
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterVertically),
        ) {
            FAIcon(
                FontAwesomeIcons.Solid.SquareRss,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlueskyFeedsScreen(
    accountType: AccountType,
    toFeed: (UiList) -> Unit,
) {
    val state by producePresenter {
        presenter(accountType = accountType)
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.home_tab_feeds_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = state.isRefreshing,
            onRefresh = state::refresh,
            content = {
                LazyColumn(
                    contentPadding = contentPadding,
                ) {
                    item {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(id = R.string.feeds_my_feeds_title))
                            },
                        )
                    }
                    uiListItemComponent(
                        state.myFeeds,
                        trailingContent = { item ->
                            state.currentTabs.onSuccess { currentTabs ->
                                val isPinned =
                                    remember(
                                        item,
                                        currentTabs,
                                    ) {
                                        currentTabs.contains(item.id)
                                    }
                                IconButton(
                                    onClick = {
                                        if (isPinned) {
                                            state.unpinFeed(item)
                                        } else {
                                            state.pinFeed(item)
                                        }
                                    },
                                ) {
                                    AnimatedContent(isPinned) {
                                        if (it) {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.ThumbtackSlash,
                                                contentDescription = stringResource(id = R.string.tab_settings_add),
                                            )
                                        } else {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.Thumbtack,
                                                contentDescription = stringResource(id = R.string.tab_settings_remove),
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )

                    item {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(id = R.string.feeds_discover_feeds_title))
                            },
                        )
                    }
                    items(
                        state.popularFeeds,
                        loadingCount = 5,
                        loadingContent = {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                StatusPlaceholder(
                                    modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                            }
                        },
                    ) { (item, subscribed) ->
                        Column(
                            modifier =
                                Modifier
                                    .clickable {
                                        toFeed.invoke(item)
                                    },
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ListComponent(
                                modifier =
                                    Modifier
                                        .padding(
                                            horizontal = screenHorizontalPadding,
                                        ),
                                headlineContent = {
                                    Text(text = item.title)
                                },
                                leadingContent = {
                                    if (item.avatar != null) {
                                        NetworkImage(
                                            model = item.avatar,
                                            contentDescription = item.title,
                                            modifier =
                                                Modifier
                                                    .size(AvatarComponentDefaults.size)
                                                    .clip(MaterialTheme.shapes.medium),
                                        )
                                    } else {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Rss,
                                            contentDescription = null,
                                            modifier =
                                                Modifier
                                                    .size(AvatarComponentDefaults.size)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = MaterialTheme.shapes.medium,
                                                    ),
                                        )
                                    }
                                },
                                supportingContent = {
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.feeds_discover_feeds_created_by,
                                                item.creator?.handle ?: "Unknown",
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier =
                                            Modifier
                                                .alpha(MediumAlpha),
                                    )
                                },
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            if (subscribed) {
                                                state.unsubscribe(item)
                                                state.unpinFeed(item)
                                            } else {
                                                state.subscribe(item)
                                                state.pinFeed(item)
                                            }
                                        },
                                    ) {
                                        if (subscribed) {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.Trash,
                                                contentDescription = null,
                                            )
                                        } else {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.Plus,
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                },
                            )
                            item.description?.takeIf { it.isNotEmpty() }?.let {
                                Text(
                                    text = it,
                                    modifier =
                                        Modifier
                                            .padding(
                                                horizontal = screenHorizontalPadding,
                                            ),
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    settingsRepository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val tabSettings by settingsRepository.tabSettings.collectAsUiState()
    val accountState =
        remember(accountType) {
            UserPresenter(
                accountType = accountType,
                userKey = null,
            )
        }.invoke()
    val currentTabs =
        accountState.user.flatMap { user ->
            tabSettings.map {
                it.homeTabs
                    .getOrDefault(
                        user.key,
                        listOf(HomeTimelineTabItem(accountType = AccountType.Specific(user.key))),
                    ).filterIsInstance<Bluesky.FeedTabItem>()
                    .map { it.uri }
                    .toImmutableList()
            }
        }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val state =
        remember(accountType) {
            BlueskyFeedsPresenter(accountType = accountType)
        }.invoke()

    object : BlueskyFeedsState by state {
        val isRefreshing: Boolean
            get() = isRefreshing

        fun refresh() {
            isRefreshing = true
            scope.launch {
                state.refreshSuspend()
                isRefreshing = false
            }
        }

        val currentTabs = currentTabs

        fun pinFeed(item: UiList) {
            accountState.user.onSuccess { user ->
                appScope.launch {
                    settingsRepository.updateTabSettings {
                        copy(
                            homeTabs =
                                homeTabs + (
                                    user.key to
                                        homeTabs[user.key].orEmpty().plus(
                                            Bluesky.FeedTabItem(
                                                account = AccountType.Specific(user.key),
                                                uri = item.id,
                                                metaData =
                                                    TabMetaData(
                                                        title = TitleType.Text(item.title),
                                                        icon = IconType.Material(IconType.Material.MaterialIcon.Feeds),
                                                    ),
                                            ),
                                        )
                                ),
                        )
                    }
                }
            }
        }

        fun unpinFeed(item: UiList) {
            accountState.user.onSuccess { user ->
                appScope.launch {
                    settingsRepository.updateTabSettings {
                        copy(
                            homeTabs =
                                homeTabs + (
                                    user.key to
                                        homeTabs[user.key].orEmpty().filter {
                                            if (it is Bluesky.FeedTabItem) {
                                                it.uri != item.id
                                            } else {
                                                true
                                            }
                                        }
                                ),
                        )
                    }
                }
            }
        }
    }
}
