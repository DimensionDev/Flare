package dev.dimension.flare.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.dimension.flare.R
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.ProfileHeader
import dev.dimension.flare.ui.component.ProfileHeaderLoading
import dev.dimension.flare.ui.component.ProfileMenu
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.TabRowIndicator
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.component.status.StatusPlaceholder
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiMedia
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
import dev.dimension.flare.ui.screen.home.RegisterTabCallback
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.fornewid.placeholder.material3.placeholder
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import moe.tlaster.nestedscrollview.VerticalNestedScrollView
import moe.tlaster.nestedscrollview.rememberNestedScrollViewState
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.math.max

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
    showBackButton: Boolean = true,
    onMediaClick: (statusKey: MicroBlogKey, index: Int, preview: String?) -> Unit,
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
                onBack = onBack,
                showBackButton = showBackButton,
                onMediaClick = onMediaClick,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileErrorScreen(onBack: () -> Unit) {
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(text = "Error")
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Error")
        }
    }
}

@Composable
private fun ProfileLoadingScreen(onBack: () -> Unit) {
    FlareScaffold {
        LazyColumn(
            contentPadding = it,
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

@OptIn(
    ExperimentalMaterial3Api::class,
)
@Composable
internal fun ProfileScreen(
    accountType: AccountType,
    toEditAccountList: () -> Unit,
    toSearchUserUsingAccount: (String, MicroBlogKey) -> Unit,
    toStartMessage: (MicroBlogKey) -> Unit,
    onFollowListClick: (userKey: MicroBlogKey) -> Unit,
    onFansListClick: (userKey: MicroBlogKey) -> Unit,
    userKey: MicroBlogKey? = null,
    onBack: () -> Unit = {},
    showBackButton: Boolean = true,
    onMediaClick: (statusKey: MicroBlogKey, index: Int, preview: String?) -> Unit,
) {
    val state by producePresenter(key = "${accountType}_$userKey") {
        profilePresenter(userKey = userKey, accountType = accountType)
    }
    val nestedScrollState = rememberNestedScrollViewState()
    val windowSize =
        with(LocalDensity.current) {
            currentWindowSize().toSize().toDpSize()
        }
    val bigScreen = isBigScreen()
    val scrollBehavior =
        if (bigScreen) {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        } else {
            TopAppBarDefaults.pinnedScrollBehavior()
        }
    val scope = rememberCoroutineScope()
    FlareScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets =
            ScaffoldDefaults
                .contentWindowInsets
                .exclude(WindowInsets.statusBars),
        topBar = {
            val titleAlpha by remember {
                derivedStateOf {
                    if (nestedScrollState.offset == nestedScrollState.maxOffset ||
                        bigScreen
                    ) {
                        1f
                    } else {
                        max(
                            0f,
                            nestedScrollState.offset / nestedScrollState.maxOffset,
                        )
                    }
                }
            }
            Box {
                if (!bigScreen) {
                    Column(
                        modifier =
                            Modifier
                                .graphicsLayer {
                                    alpha = titleAlpha
                                },
                    ) {
                        Spacer(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .windowInsetsTopHeight(WindowInsets.statusBars)
                                    .background(MaterialTheme.colorScheme.background),
                        )
                        Spacer(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .background(MaterialTheme.colorScheme.background),
                        )
                    }
                }
                FlareTopAppBar(
                    title = {
                        state.state.userState.onSuccess {
                            RichText(
                                text = it.name,
                                modifier =
                                    Modifier.graphicsLayer {
                                        alpha = titleAlpha
                                    },
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                    modifier =
                        Modifier.let {
                            if (!bigScreen) {
                                it.windowInsetsPadding(WindowInsets.statusBars)
                            } else {
                                it
                            }
                        },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        if (showBackButton) {
                            BackButton(onBack = onBack)
                        }
                    },
                    actions = {
                        if (!bigScreen) {
                            ProfileMenu(
                                profileState = state.state,
                                setShowMoreMenus = state::setShowMoreMenus,
                                showMoreMenus = state.showMoreMenus,
                                toEditAccountList = toEditAccountList,
                                accountsState = state.allAccountsState,
                                toSearchUserUsingAccount = toSearchUserUsingAccount,
                                toStartMessage = toStartMessage,
                            )
                        }
                    },
                )
            }
        },
    ) {
        Row {
            if (bigScreen) {
                val width =
                    when (windowSize.width) {
                        in 840.dp..1024.dp -> 332.dp
                        else -> 432.dp
                    }
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .width(width)
                            .padding(horizontal = 16.dp)
                            .padding(it),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AdaptiveCard {
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
                                state.state.userState.onSuccess {
//                                    onMediaClick(it.avatar)
                                }
                            },
                            onBannerClick = {
                                state.state.userState.onSuccess {
//                                    it.banner?.let { it1 -> onMediaClick(it1) }
                                }
                            },
                            isBigScreen = true,
                            onFollowListClick = onFollowListClick,
                            onFansListClick = onFansListClick,
                        )
                    }
                }
            }
            RefreshContainer(
                modifier = Modifier.fillMaxSize(),
                onRefresh = state::refresh,
                isRefreshing = state.isRefreshing,
                indicatorPadding = it,
                content = {
                    val content = @Composable {
                        state.state.tabs.onSuccess { tabs ->
                            val pagerState = rememberPagerState { tabs.size }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (tabs.size > 1) {
                                    SecondaryScrollableTabRow(
                                        containerColor = Color.Transparent,
                                        selectedTabIndex = pagerState.currentPage,
                                        modifier = Modifier.fillMaxWidth(),
                                        edgePadding = screenHorizontalPadding,
                                        divider = {},
                                        indicator = {
                                            TabRowIndicator(
                                                selectedIndex = minOf(pagerState.currentPage, tabs.size - 1),
                                            )
                                        },
                                    ) {
                                        tabs
                                            .toImmutableList()
                                            .forEachIndexed { index, profileTab ->
                                                Tab(
                                                    modifier = Modifier.clip(CircleShape),
                                                    selected = pagerState.currentPage == index,
                                                    onClick = {
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(index)
                                                        }
                                                    },
                                                ) {
                                                    Text(
                                                        profileTab.title,
                                                        modifier =
                                                            Modifier
                                                                .padding(8.dp),
                                                    )
                                                }
                                            }
                                    }
                                }
                                HorizontalPager(
                                    state = pagerState,
                                ) { index ->
                                    val type = tabs[index]
                                    when (type) {
                                        is ProfileState.Tab.Media -> {
                                            ProfileMediaTab(
                                                mediaState = type.data,
                                                onItemClicked = { statusKey, index, preview ->
                                                    onMediaClick(statusKey, index, preview)
                                                },
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }

                                        is ProfileState.Tab.Timeline -> {
                                            val listState = rememberLazyStaggeredGridState()
                                            if (index == pagerState.currentPage) {
                                                RegisterTabCallback(
                                                    lazyListState = listState,
                                                    onRefresh = {
                                                        state.refresh()
                                                    },
                                                )
                                            }
                                            LazyStatusVerticalStaggeredGrid(
                                                state = listState,
                                                contentPadding =
                                                    PaddingValues(
                                                        top = 8.dp,
                                                        bottom = 8.dp + it.calculateBottomPadding(),
                                                    ),
                                                modifier = Modifier.fillMaxSize(),
                                            ) {
                                                status(type.data)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (bigScreen) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(
                                        top = it.calculateTopPadding(),
                                    ),
                        ) {
                            content.invoke()
                        }
                    } else {
                        VerticalNestedScrollView(
                            state = nestedScrollState,
                            contentTopPadding = it.calculateTopPadding(),
                            header = {
                                Column {
                                    ProfileHeader(
                                        state = state.state,
                                        menu = {
                                            Spacer(modifier = Modifier.width(screenHorizontalPadding))
                                        },
                                        onAvatarClick = {
                                            state.state.userState.onSuccess {
//                                                    onMediaClick(it.avatar)
                                            }
                                        },
                                        onBannerClick = {
                                            state.state.userState.onSuccess {
//                                                    it.banner?.let { it1 -> onMediaClick(it1) }
                                            }
                                        },
                                        isBigScreen = false,
                                        onFollowListClick = onFollowListClick,
                                        onFansListClick = onFansListClick,
                                    )
                                }
                            },
                            content = {
                                content.invoke()
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun ProfileMediaTab(
    mediaState: PagingState<ProfileMedia>,
    onItemClicked: (statusKey: MicroBlogKey, index: Int, preview: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(
        LocalComponentAppearance provides
            LocalComponentAppearance.current.copy(
                videoAutoplay = ComponentAppearance.VideoAutoplay.NEVER,
            ),
    ) {
        LazyVerticalStaggeredGrid(
            modifier = modifier,
            columns = StaggeredGridCells.Adaptive(120.dp),
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = screenHorizontalPadding),
        ) {
            mediaState
                .onSuccess {
                    items(itemCount) { index ->
                        val item = get(index)
                        if (item != null) {
                            val media = item.media
                            MediaItem(
                                media = media,
                                showCountdown = false,
                                modifier =
                                    Modifier
                                        .clip(MaterialTheme.shapes.medium)
                                        .clipToBounds()
                                        .clickable {
                                            val content = item.status.content
                                            if (content is UiTimeline.ItemContent.Status) {
                                                onItemClicked(
                                                    content.statusKey,
                                                    item.index,
                                                    when (media) {
                                                        is UiMedia.Image -> media.previewUrl
                                                        is UiMedia.Video -> media.thumbnailUrl
                                                        is UiMedia.Gif -> media.previewUrl
                                                        else -> null
                                                    },
                                                )
                                            }
                                        },
                            )
                        } else {
                            Card {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(120.dp)
                                            .placeholder(true),
                                )
                            }
                        }
                    }
                }.onLoading {
                    items(10) {
                        Box(
                            modifier =
                                Modifier
                                    .size(120.dp)
                                    .placeholder(true),
                        )
                    }
                }
        }
    }
}

@Composable
private fun profilePresenter(
    userKey: MicroBlogKey?,
    accountType: AccountType,
) = run {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val state =
        remember(userKey) {
            ProfilePresenter(
                userKey = userKey,
                accountType = accountType,
            )
        }.invoke()
    var showMoreMenus by remember {
        mutableStateOf(false)
    }
//    val mediaState = state.mediaState
//    val profileTabs =
//        listOfNotNull(
//            ProfileTab.Timeline,
//            if (mediaState.isSuccess() && mediaState.itemCount > 0 || mediaState.isLoading) {
//                ProfileTab.Media
//            } else {
//                null
//            },
//        )
    val allAccounts =
        remember {
            AccountsPresenter()
        }.invoke()
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
        val showMoreMenus = showMoreMenus
        val isRefreshing = isRefreshing
//        val profileTabs = profileTabs

        fun setShowMoreMenus(value: Boolean) {
            showMoreMenus = value
        }

        fun refresh() {
            scope.launch {
                isRefreshing = true
                state.refresh()
                isRefreshing = false
            }
        }
    }
}

private val ProfileState.Tab.title: String
    @Composable
    get() =
        when (this) {
            is ProfileState.Tab.Timeline ->
                when (type) {
                    ProfileTab.Timeline.Type.Status -> stringResource(R.string.profile_tab_timeline)
                    ProfileTab.Timeline.Type.StatusWithReplies -> stringResource(R.string.profile_tab_timeline_with_reply)
                    ProfileTab.Timeline.Type.Likes -> stringResource(R.string.profile_tab_likes)
                }

            is ProfileState.Tab.Media -> stringResource(R.string.profile_tab_media)
        }
