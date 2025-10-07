package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.Res
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.profile_tab_likes
import dev.dimension.flare.profile_tab_media
import dev.dimension.flare.profile_tab_timeline
import dev.dimension.flare.profile_tab_timeline_with_reply
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.ProfileHeader
import dev.dimension.flare.ui.component.ProfileHeaderLoading
import dev.dimension.flare.ui.component.ProfileMenu
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.platform.placeholder
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.component.status.StatusPlaceholder
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.profile.ProfileMedia
import dev.dimension.flare.ui.presenter.profile.ProfilePresenter
import dev.dimension.flare.ui.presenter.profile.ProfileState
import dev.dimension.flare.ui.presenter.profile.ProfileWithUserNameAndHostPresenter
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProfileWithUserNameAndHostDeeplinkRoute(
    userName: String,
    host: String,
    accountType: AccountType,
    toEditAccountList: (userKey: MicroBlogKey) -> Unit,
    toSearchUserUsingAccount: (String, MicroBlogKey) -> Unit,
    toStartMessage: (MicroBlogKey) -> Unit,
    onFollowListClick: (userKey: MicroBlogKey) -> Unit,
    onFansListClick: (userKey: MicroBlogKey) -> Unit,
    onBack: () -> Unit = {},
) {
    val state by producePresenter(key = "acct_${accountType}_$userName@$host") {
        profileWithUserNameAndHostPresenter(
            userName = userName,
            host = host,
            accountType = accountType,
        )
    }
    state
        .onSuccess {
            ProfileScreen(
                accountType = accountType,
                toEditAccountList = {
                    toEditAccountList(it.key)
                },
                toSearchUserUsingAccount = toSearchUserUsingAccount,
                toStartMessage = toStartMessage,
                onFollowListClick = onFollowListClick,
                onFansListClick = onFansListClick,
                userKey = it.key,
            )
        }.onLoading {
            ProfileLoadingScreen(
                onBack = onBack,
            )
        }.onError {
            ProfileErrorScreen(
                onBack = onBack,
            )
        }
}

@Composable
private fun ProfileErrorScreen(onBack: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Error")
    }
}

@Composable
private fun ProfileLoadingScreen(onBack: () -> Unit) {
    LazyColumn(
        contentPadding = LocalWindowPadding.current,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ProfileHeaderLoading(withStatusBarHeight = false)
        }
        items(5) {
            StatusPlaceholder(
                modifier = Modifier.padding(horizontal = screenHorizontalPadding),
            )
        }
    }
}

@Composable
private fun profileWithUserNameAndHostPresenter(
    userName: String,
    host: String,
    accountType: AccountType,
) = run {
    remember(
        userName,
        host,
    ) {
        ProfileWithUserNameAndHostPresenter(
            userName = userName,
            host = host,
            accountType = accountType,
        )
    }.invoke().user
}

@Composable
internal fun ProfileScreen(
    accountType: AccountType,
    userKey: MicroBlogKey?,
    toEditAccountList: () -> Unit = {},
    toSearchUserUsingAccount: (String, MicroBlogKey) -> Unit = { _, _ -> },
    toStartMessage: (MicroBlogKey) -> Unit = {},
    onFollowListClick: (userKey: MicroBlogKey) -> Unit = {},
    onFansListClick: (userKey: MicroBlogKey) -> Unit = {},
) {
    val state by producePresenter(
        key = "profile_${accountType}_$userKey",
    ) {
        presenter(accountType, userKey)
    }
    val listState = rememberLazyStaggeredGridState()
    RegisterTabCallback(listState, state::refresh)
    val isBigScreen = isBigScreen()
    Box {
        Row {
            if (isBigScreen) {
                Column(
                    modifier =
                        Modifier
                            .padding(LocalWindowPadding.current)
                            .padding(
                                vertical = 16.dp,
                            ).padding(
                                start = 16.dp,
                            ).width(332.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    Card(
                        modifier = Modifier,
                    ) {
                        ProfileHeader(
                            state = state.state,
                            menu = {
                                ProfileMenu(
                                    profileState = state.state,
                                    setShowMoreMenus = state::setShowMoreMenus,
                                    showMoreMenus = state.showMoreMenus,
                                    toEditAccountList = toEditAccountList,
                                    accountsState = state.allAccountsState,
                                    toSearchUserUsingAccount = toSearchUserUsingAccount,
                                    toStartMessage = toStartMessage,
                                )
                            },
                            onAvatarClick = {
                            },
                            onBannerClick = {
                            },
                            isBigScreen = true,
                            onFollowListClick = onFollowListClick,
                            onFansListClick = onFansListClick,
                        )
                    }
                }
            }
            LazyStatusVerticalStaggeredGrid(
                contentPadding =
                    PaddingValues(
                        vertical =
                            if (isBigScreen) {
                                16.dp
                            } else {
                                0.dp
                            },
                    ) +
                        if (isBigScreen) {
                            LocalWindowPadding.current
                        } else {
                            PaddingValues()
                        },
                state = listState,
            ) {
                if (!isBigScreen) {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        Column {
                            ProfileHeader(
                                modifier =
                                    Modifier
                                        .ignoreHorizontalParentPadding(screenHorizontalPadding),
                                state = state.state,
                                menu = {
                                    ProfileMenu(
                                        profileState = state.state,
                                        setShowMoreMenus = state::setShowMoreMenus,
                                        showMoreMenus = state.showMoreMenus,
                                        toEditAccountList = toEditAccountList,
                                        accountsState = state.allAccountsState,
                                        toSearchUserUsingAccount = toSearchUserUsingAccount,
                                        toStartMessage = toStartMessage,
                                    )
                                },
                                onAvatarClick = {
                                },
                                onBannerClick = {
                                },
                                isBigScreen = false,
                                onFollowListClick = onFollowListClick,
                                onFansListClick = onFansListClick,
                            )
                            state.state.tabs.onSuccess { tabs ->
                                LiteFilter {
                                    repeat(tabs.size) { index ->
                                        val tab = tabs.get(index)
                                        PillButton(
                                            selected = state.selectedTabIndex == index,
                                            onSelectedChanged = {
                                                state.setSelectedTab(index)
                                            },
                                        ) {
                                            Text(
                                                stringResource(tab.title),
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
                state.state.tabs.onSuccess { tabs ->
                    if (tabs.size > 1 && isBigScreen) {
                        item(
                            span = StaggeredGridItemSpan.FullLine,
                        ) {
                            LiteFilter {
                                repeat(tabs.size) { index ->
                                    val tab = tabs.get(index)
                                    PillButton(
                                        selected = state.selectedTabIndex == index,
                                        onSelectedChanged = {
                                            state.setSelectedTab(index)
                                        },
                                    ) {
                                        Text(
                                            stringResource(tab.title),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                state.selectedTabItem.onSuccess { tab ->
                    when (tab) {
                        is ProfileTabItem.Media -> {
                            items(
                                tab.data,
                                loadingContent = {
                                    Card(
                                        modifier = Modifier,
                                    ) {
                                        Box(modifier = Modifier.size(120.dp).placeholder(true))
                                    }
                                },
                            ) { item ->
                                CompositionLocalProvider(
                                    LocalComponentAppearance provides
                                        LocalComponentAppearance.current.copy(
                                            videoAutoplay = ComponentAppearance.VideoAutoplay.NEVER,
                                        ),
                                ) {
                                    val media = item.media
                                    MediaItem(
                                        media = media,
                                        showCountdown = false,
                                        modifier =
                                            Modifier
                                                .clip(FluentTheme.shapes.control)
                                                .padding(
                                                    vertical = 4.dp,
                                                ).clipToBounds()
                                                .clickable {
                                                    val content = item.status.content
                                                    if (content is UiTimeline.ItemContent.Status) {
//                                                onItemClicked(
//                                                    content.statusKey,
//                                                    item.index,
//                                                    when (media) {
//                                                        is UiMedia.Image -> media.previewUrl
//                                                        is UiMedia.Video -> media.thumbnailUrl
//                                                        is UiMedia.Gif -> media.previewUrl
//                                                        else -> null
//                                                    },
//                                                )
                                                    }
                                                },
                                    )
                                }
                            }
                        }

                        is ProfileTabItem.Timeline -> {
                            status(tab.data)
                        }
                    }
                }
            }
        }

        state.selectedTabItem.onSuccess {
            val isRefreshing =
                when (it) {
                    is ProfileTabItem.Media -> it.data.isRefreshing
                    is ProfileTabItem.Timeline -> it.data.isRefreshing
                }
            AnimatedVisibility(
                isRefreshing,
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

private val ProfileState.Tab.title: StringResource
    get() =
        when (this) {
            is ProfileState.Tab.Media -> Res.string.profile_tab_media
            is ProfileState.Tab.Timeline ->
                when (type) {
                    ProfileTab.Timeline.Type.Status -> Res.string.profile_tab_timeline
                    ProfileTab.Timeline.Type.StatusWithReplies -> Res.string.profile_tab_timeline_with_reply
                    ProfileTab.Timeline.Type.Likes -> Res.string.profile_tab_likes
                }
        }

@Composable
private fun presenter(
    accountType: AccountType,
    userKey: MicroBlogKey?,
) = run {
    val scope = rememberCoroutineScope()
    val state =
        remember(accountType, userKey) {
            ProfilePresenter(accountType, userKey)
        }.invoke()
    var selectedTabIndex by remember {
        mutableStateOf(0)
    }
    var showMoreMenus by remember {
        mutableStateOf(false)
    }

    val allAccounts =
        remember {
            AccountsPresenter()
        }.invoke()

    val tabs =
        state.tabs.map {
            it.map {
                when (val tab = it) {
                    is ProfileState.Tab.Media ->
                        ProfileTabItem.Media(tab.presenter.body().mediaState)

                    is ProfileState.Tab.Timeline ->
                        ProfileTabItem.Timeline(tab.type, tab.presenter.body().listState)
                }
            }
        }

    val selectedTab =
        tabs.map {
            it[selectedTabIndex]
        }

    object {
        val state = state
        val allAccountsState =
            allAccounts.accounts.map {
                it
                    .toImmutableList()
                    .groupBy { it.first.platformType }
                    .map { it.key to (it.value.map { it.second }.toImmutableList()) }
                    .toMap()
            }
        val selectedTabIndex = selectedTabIndex
        val selectedTabItem = selectedTab
        val showMoreMenus = showMoreMenus

        fun setShowMoreMenus(value: Boolean) {
            showMoreMenus = value
        }

        fun refresh() {
            scope.launch {
                selectedTabItem.onSuccess { tab ->
                    when (tab) {
                        is ProfileTabItem.Media -> {
                            tab.data.refreshSuspend()
                        }

                        is ProfileTabItem.Timeline -> {
                            tab.data.refreshSuspend()
                        }
                    }
                }
            }
        }

        fun setSelectedTab(value: Int) {
            if (selectedTabIndex == value) {
                refresh()
            }
            selectedTabIndex = value
        }
    }
}

private sealed interface ProfileTabItem {
    data class Timeline(
        val type: ProfileTab.Timeline.Type,
        val data: PagingState<UiTimeline>,
    ) : ProfileTabItem

    data class Media(
        val data: PagingState<ProfileMedia>,
    ) : ProfileTabItem
}

fun Modifier.ignoreHorizontalParentPadding(horizontal: Dp): Modifier =
    this.layout { measurable, constraints ->
        val overridenWidth = constraints.maxWidth + 2 * horizontal.roundToPx()
        val placeable = measurable.measure(constraints.copy(maxWidth = overridenWidth))
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
