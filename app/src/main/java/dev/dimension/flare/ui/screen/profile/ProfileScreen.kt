package dev.dimension.flare.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.window.core.layout.WindowWidthSizeClass
import com.eygraber.compose.placeholder.material3.placeholder
import com.fleeksoft.ksoup.nodes.Element
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.generated.destinations.EditAccountListRouteDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileMediaRouteDestination
import com.ramcosta.composedestinations.generated.destinations.SearchRouteDestination
import com.ramcosta.composedestinations.generated.destinations.StatusMediaRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Cat
import compose.icons.fontawesomeicons.solid.CircleCheck
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.LocationDot
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.Robot
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isLoading
import dev.dimension.flare.common.isSuccess
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.component.MatricesDisplay
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.UserFields
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.component.status.StatusPlaceholder
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.profile.ProfileMedia
import dev.dimension.flare.ui.presenter.profile.ProfilePresenter
import dev.dimension.flare.ui.presenter.profile.ProfileState
import dev.dimension.flare.ui.presenter.profile.ProfileWithUserNameAndHostPresenter
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.presenter.settings.AccountsState
import dev.dimension.flare.ui.screen.home.RegisterTabCallback
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch
import moe.tlaster.nestedscrollview.VerticalNestedScrollView
import moe.tlaster.nestedscrollview.rememberNestedScrollViewState
import kotlin.math.max

@Composable
@Destination<RootGraph>(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.ProfileWithNameAndHost.ROUTE,
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
internal fun ProfileWithUserNameAndHostDeeplinkRoute(
    userName: String,
    host: String,
    navigator: DestinationsNavigator,
    accountKey: MicroBlogKey?,
) {
    val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
    val state by producePresenter(key = "acct_${accountKey}_$userName@$host") {
        profileWithUserNameAndHostPresenter(
            userName = userName,
            host = host,
            accountType = accountType,
        )
    }
    state
        .onSuccess {
            ProfileScreen(
                userKey = it.key,
                onBack = {
                    navigator.navigateUp()
                },
                onProfileMediaClick = {
                    navigator.navigate(
                        ProfileMediaRouteDestination(
                            it.key,
                            accountType = accountType,
                        ),
                    )
                },
                onMediaClick = { statusKey, index, preview ->
                    navigator.navigate(
                        StatusMediaRouteDestination(
                            statusKey = statusKey,
                            mediaIndex = index,
                            preview = preview,
                            accountType = accountType,
                        ),
                    )
                },
                accountType = accountType,
                toEditAccountList = {
                    navigator.navigate(
                        EditAccountListRouteDestination(
                            accountType,
                            it.key,
                        ),
                    )
                },
                toSearchUserUsingAccount = { handle, accountKey ->
                    navigator.navigate(
                        SearchRouteDestination(
                            handle,
                            AccountType.Specific(accountKey),
                        ),
                    )
                },
            )
        }.onLoading {
            ProfileLoadingScreen(
                onBack = {
                    navigator.navigateUp()
                },
            )
        }.onError {
            ProfileErrorScreen(
                onBack = {
                    navigator.navigateUp()
                },
            )
        }
}

@Composable
@Destination<RootGraph>(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
internal fun ProfileWithUserNameAndHostRoute(
    userName: String,
    host: String,
    navigator: DestinationsNavigator,
    accountType: AccountType,
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
                userKey = it.key,
                onBack = {
                    navigator.navigateUp()
                },
                onProfileMediaClick = {
                    navigator.navigate(
                        ProfileMediaRouteDestination(
                            it.key,
                            accountType = accountType,
                        ),
                    )
                },
                onMediaClick = { statusKey, index, preview ->
                    navigator.navigate(
                        StatusMediaRouteDestination(
                            statusKey = statusKey,
                            mediaIndex = index,
                            preview = preview,
                            accountType = accountType,
                        ),
                    )
                },
                accountType = accountType,
                toEditAccountList = {
                    navigator.navigate(EditAccountListRouteDestination(accountType, it.key))
                },
                toSearchUserUsingAccount = { handle, accountKey ->
                    navigator.navigate(
                        SearchRouteDestination(
                            handle,
                            AccountType.Specific(accountKey),
                        ),
                    )
                },
            )
        }.onLoading {
            ProfileLoadingScreen(
                onBack = {
                    navigator.navigateUp()
                },
            )
        }.onError {
            ProfileErrorScreen(
                onBack = {
                    navigator.navigateUp()
                },
            )
        }
}

@Composable
@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
internal fun MeRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
) {
    ProfileRoute(
        null,
        navigator,
        accountType,
        showBackButton = false,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileErrorScreen(onBack: () -> Unit) {
    FlareScaffold(
        topBar = {
            TopAppBar(
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

@Composable
@Destination<RootGraph>(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.Profile.ROUTE,
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
internal fun ProfileDeeplinkRoute(
    userKey: MicroBlogKey,
    navigator: DestinationsNavigator,
    accountKey: MicroBlogKey?,
) {
    val accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest
    ProfileScreen(
        userKey = userKey,
        onBack = {
            navigator.navigateUp()
        },
        onProfileMediaClick = {
            navigator.navigate(
                ProfileMediaRouteDestination(
                    userKey,
                    accountType = accountType,
                ),
            )
        },
        onMediaClick = { statusKey, index, preview ->
            navigator.navigate(
                StatusMediaRouteDestination(
                    statusKey = statusKey,
                    mediaIndex = index,
                    preview = preview,
                    accountType = accountType,
                ),
            )
        },
        accountType = accountType,
        toEditAccountList = {
            navigator.navigate(
                EditAccountListRouteDestination(
                    accountType,
                    userKey,
                ),
            )
        },
        toSearchUserUsingAccount = { handle, accountKey ->
            navigator.navigate(
                SearchRouteDestination(
                    handle,
                    AccountType.Specific(accountKey),
                ),
            )
        },
    )
}

@Composable
@Destination<RootGraph>(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
internal fun ProfileRoute(
    userKey: MicroBlogKey?,
    navigator: DestinationsNavigator,
    accountType: AccountType,
    showBackButton: Boolean = true,
) {
    ProfileScreen(
        userKey = userKey,
        onBack = {
            navigator.navigateUp()
        },
        showBackButton = showBackButton,
        onProfileMediaClick = {
            navigator.navigate(
                ProfileMediaRouteDestination(
                    userKey,
                    accountType = accountType,
                ),
            )
        },
        onMediaClick = { statusKey, index, preview ->
            navigator.navigate(
                StatusMediaRouteDestination(
                    statusKey = statusKey,
                    mediaIndex = index,
                    preview = preview,
                    accountType = accountType,
                ),
            )
        },
        accountType = accountType,
        toEditAccountList = {
            if (userKey != null) {
                navigator.navigate(EditAccountListRouteDestination(accountType, userKey))
            }
        },
        toSearchUserUsingAccount = { handle, accountKey ->
            navigator.navigate(
                SearchRouteDestination(
                    handle,
                    AccountType.Specific(accountKey),
                ),
            )
        },
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
)
@Composable
private fun ProfileScreen(
    accountType: AccountType,
    toEditAccountList: () -> Unit,
    toSearchUserUsingAccount: (String, MicroBlogKey) -> Unit,
    userKey: MicroBlogKey? = null,
    onBack: () -> Unit = {},
    showBackButton: Boolean = true,
    onProfileMediaClick: () -> Unit = {},
    onMediaClick: (statusKey: MicroBlogKey, index: Int, preview: String?) -> Unit,
) {
    val state by producePresenter(key = "${accountType}_$userKey") {
        profilePresenter(userKey = userKey, accountType = accountType)
    }
    val listState = rememberLazyStaggeredGridState()
    val nestedScrollState = rememberNestedScrollViewState()
    RegisterTabCallback(lazyListState = listState)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInfo = currentWindowAdaptiveInfo()
    val windowSize =
        with(LocalDensity.current) {
            currentWindowSize().toSize().toDpSize()
        }
    val bigScreen = windowInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
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
                TopAppBar(
                    title = {
                        state.state.userState.onSuccess {
                            HtmlText(
                                element = it.name.data,
                                modifier =
                                    Modifier.graphicsLayer {
                                        alpha = titleAlpha
                                    },
                            )
                        }
                    },
                    colors =
                        if (!bigScreen) {
                            TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent,
                            )
                        } else {
                            TopAppBarDefaults.centerAlignedTopAppBarColors()
                        },
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
                            .padding(it + PaddingValues(horizontal = 16.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Card {
                        ProfileHeader(
                            state.state.userState,
                            state.state.relationState,
                            onFollowClick = state.state::follow,
                            isMe = state.state.isMe,
                            menu = {
                                ProfileMenu(
                                    profileState = state.state,
                                    setShowMoreMenus = state::setShowMoreMenus,
                                    showMoreMenus = state.showMoreMenus,
                                    toEditAccountList = toEditAccountList,
                                    accountsState = state.allAccountsState,
                                    toSearchUserUsingAccount = toSearchUserUsingAccount,
                                )
                            },
                            expandMatrices = true,
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
                            isBigScreen = bigScreen,
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
                    val pagerState = rememberPagerState { state.profileTabs.size }
                    val content = @Composable {
                        Column {
                            Box {
                                if (state.profileTabs.size > 1) {
                                    SecondaryScrollableTabRow(
                                        selectedTabIndex = pagerState.currentPage,
                                        modifier = Modifier.fillMaxWidth(),
                                        edgePadding = screenHorizontalPadding,
                                        divider = {},
                                    ) {
                                        state.profileTabs.forEachIndexed { index, profileTab ->
                                            Tab(
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
                                HorizontalDivider(
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth(),
                                )
                            }
                            HorizontalPager(
                                state = pagerState,
                            ) { index ->
                                val type = state.profileTabs[index]
                                when (type) {
                                    ProfileTab.Timeline ->
                                        LazyStatusVerticalStaggeredGrid(
                                            state = listState,
                                            contentPadding =
                                                PaddingValues(
                                                    top = 8.dp,
                                                    bottom = 8.dp + it.calculateBottomPadding(),
                                                ),
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            status(state.state.listState)
                                        }
                                    ProfileTab.Media -> {
                                        ProfileMediaTab(
                                            mediaState = state.state.mediaState,
                                            onItemClicked = { statusKey, index, preview ->
                                                onMediaClick(statusKey, index, preview)
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
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
                                        state.state.userState,
                                        state.state.relationState,
                                        onFollowClick = state.state::follow,
                                        isMe = state.state.isMe,
                                        menu = {
                                            Spacer(modifier = Modifier.width(screenHorizontalPadding))
                                        },
                                        expandMatrices = false,
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
                                        isBigScreen = bigScreen,
                                    )
                                }
                            },
                            content = content,
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
                            Box(modifier = Modifier.size(120.dp).placeholder(true))
                        }
                    }
                }
            }.onLoading {
                items(10) {
                    Box(modifier = Modifier.size(120.dp).placeholder(true))
                }
            }
    }
}

@Composable
private fun ProfileMenu(
    profileState: ProfileState,
    accountsState: AccountsState,
    setShowMoreMenus: (Boolean) -> Unit,
    showMoreMenus: Boolean,
    toEditAccountList: () -> Unit,
    toSearchUserUsingAccount: (String, MicroBlogKey) -> Unit,
) {
    profileState.isMe.onSuccess { isMe ->
        if (!isMe) {
            profileState.userState.onSuccess { user ->
                IconButton(onClick = {
                    setShowMoreMenus(true)
                }) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                        contentDescription = null,
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenus,
                    onDismissRequest = { setShowMoreMenus(false) },
                ) {
                    profileState.relationState.onSuccess { relation ->
                        if (!profileState.isGuestMode && relation.following) {
                            profileState.userState.onSuccess { user ->
                                profileState.isListDataSource.onSuccess { isListDataSource ->
                                    if (isListDataSource) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text =
                                                        stringResource(
                                                            id = R.string.user_follow_edit_list,
                                                        ),
                                                )
                                            },
                                            onClick = {
                                                setShowMoreMenus(false)
                                                toEditAccountList.invoke()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        accountsState.accounts.onSuccess { accounts ->
                            profileState.myAccountKey.onSuccess { myKey ->
                                if (accounts.size > 1) {
                                    for (i in 0 until accounts.size) {
                                        val account = accounts[i]
                                        account.second.onSuccess { accountData ->
                                            if (accountData.key != user.key &&
                                                accountData.key != myKey &&
                                                accountData.platformType != user.platformType
                                            ) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text =
                                                                stringResource(
                                                                    id = R.string.profile_search_user_using_account,
                                                                    user.handleWithoutAtAndHost,
                                                                    accountData.platformType.name,
                                                                    accountData.handleWithoutAt,
                                                                ),
                                                        )
                                                    },
                                                    onClick = {
                                                        setShowMoreMenus(false)
                                                        toSearchUserUsingAccount(
                                                            user.handleWithoutAtAndHost,
                                                            accountData.key,
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        profileState.actions.onSuccess { actions ->
                            for (i in 0..<actions.size) {
                                val action = actions[i]
                                DropdownMenuItem(
                                    text = {
                                        val text =
                                            when (action) {
                                                is ProfileAction.Block ->
                                                    if (action.relationState(relation)) {
                                                        stringResource(
                                                            id = R.string.user_unblock,
                                                            user.handle,
                                                        )
                                                    } else {
                                                        stringResource(
                                                            id = R.string.user_block,
                                                            user.handle,
                                                        )
                                                    }

                                                is ProfileAction.Mute ->
                                                    if (action.relationState(relation)) {
                                                        stringResource(
                                                            id = R.string.user_unmute,
                                                            user.handle,
                                                        )
                                                    } else {
                                                        stringResource(
                                                            id = R.string.user_mute,
                                                            user.handle,
                                                        )
                                                    }
                                            }
                                        Text(text = text)
                                    },
                                    onClick = {
                                        setShowMoreMenus(false)
                                        profileState.onProfileActionClick(
                                            userKey = user.key,
                                            relation = relation,
                                            action = action,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text =
                                    stringResource(
                                        id = R.string.user_report,
                                        user.handle,
                                    ),
                            )
                        },
                        onClick = {
                            setShowMoreMenus(false)
                            profileState.report(user.key)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    userState: UiState<UiProfile>,
    relationState: UiState<UiRelation>,
    onFollowClick: (userKey: MicroBlogKey, UiRelation) -> Unit,
    onAvatarClick: () -> Unit,
    onBannerClick: () -> Unit,
    isMe: UiState<Boolean>,
    menu: @Composable RowScope.() -> Unit,
    expandMatrices: Boolean,
    isBigScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    when (userState) {
        is UiState.Loading -> {
            ProfileHeaderLoading(modifier = modifier, withStatusBarHeight = true)
        }

        is UiState.Error -> {
            ProfileHeaderError()
        }

        is UiState.Success -> {
            ProfileHeaderSuccess(
                modifier = modifier,
                user = userState.data,
                relationState = relationState,
                onFollowClick = onFollowClick,
                isMe = isMe,
                menu = menu,
                expandMatrices = expandMatrices,
                onAvatarClick = onAvatarClick,
                onBannerClick = onBannerClick,
                isBigScreen = isBigScreen,
            )
        }
    }
}

@Composable
private fun ProfileHeaderSuccess(
    user: UiProfile,
    relationState: UiState<UiRelation>,
    onFollowClick: (userKey: MicroBlogKey, UiRelation) -> Unit,
    onAvatarClick: () -> Unit,
    onBannerClick: () -> Unit,
    isMe: UiState<Boolean>,
    menu: @Composable RowScope.() -> Unit,
    isBigScreen: Boolean,
    modifier: Modifier = Modifier,
    expandMatrices: Boolean = false,
) {
    CommonProfileHeader(
        modifier = modifier,
        bannerUrl = user.banner,
        avatarUrl = user.avatar,
        displayName = user.name.data,
        userKey = user.key,
        handle = user.handle,
        isBigScreen = isBigScreen,
        headerTrailing = {
            isMe.onSuccess {
                if (!it) {
                    when (relationState) {
                        is UiState.Error -> Unit
                        is UiState.Loading -> {
                            FilledTonalButton(
                                onClick = {
                                    // No-op
                                },
                                modifier =
                                    Modifier.placeholder(
                                        true,
                                        shape = ButtonDefaults.filledTonalShape,
                                    ),
                            ) {
                                Text(text = stringResource(R.string.profile_header_button_follow))
                            }
                        }

                        is UiState.Success -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                FilledTonalButton(onClick = {
                                    onFollowClick.invoke(user.key, relationState.data)
                                }) {
                                    Text(
                                        text =
                                            stringResource(
                                                id =
                                                    when {
                                                        relationState.data.blocking ->
                                                            R.string.profile_header_button_blocked

                                                        relationState.data.following ->
                                                            R.string.profile_header_button_following

                                                        relationState.data.hasPendingFollowRequestFromYou ->
                                                            R.string.profile_header_button_requested

                                                        else ->
                                                            R.string.profile_header_button_follow
                                                    },
                                            ),
                                    )
                                }
                                if (relationState.data.isFans) {
                                    Text(
                                        text = stringResource(R.string.profile_header_button_is_fans),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            menu.invoke(this)
        },
        onAvatarClick = onAvatarClick,
        onBannerClick = onBannerClick,
        handleTrailing = {
            user.mark.forEach {
                when (it) {
                    UiProfile.Mark.Verified ->
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.CircleCheck,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .alpha(MediumAlpha),
                            tint = Color.Blue,
                        )

                    UiProfile.Mark.Cat ->
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Cat,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .alpha(MediumAlpha),
                        )

                    UiProfile.Mark.Bot ->
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Robot,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .alpha(MediumAlpha),
                        )

                    UiProfile.Mark.Locked ->
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Lock,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .alpha(MediumAlpha),
                        )
                }
            }
        },
        content = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                user.description?.let {
                    HtmlText(
                        element = it.data,
                        layoutDirection = it.direction,
                    )
                }
                when (val content = user.bottomContent) {
                    is UiProfile.BottomContent.Fields ->
                        UserFields(
                            fields = content.fields,
                        )

                    is UiProfile.BottomContent.Iconify -> {
                        content.items.forEach { (key, value) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                val icon =
                                    when (key) {
                                        UiProfile.BottomContent.Iconify.Icon.Location -> FontAwesomeIcons.Solid.LocationDot
                                        UiProfile.BottomContent.Iconify.Icon.Url -> FontAwesomeIcons.Solid.Globe
                                        UiProfile.BottomContent.Iconify.Icon.Verify -> FontAwesomeIcons.Solid.CircleCheck
                                    }
                                FAIcon(icon, contentDescription = null)
                                HtmlText(element = value.data, layoutDirection = value.direction)
                            }
                        }
                    }

                    null -> Unit
                }
                MatricesDisplay(
                    matrices =
                        remember(user.matrices) {
                            persistentMapOf(
                                R.string.profile_misskey_header_status_count to user.matrices.statusesCountHumanized,
                                R.string.profile_header_following_count to user.matrices.followsCountHumanized,
                                R.string.profile_header_fans_count to user.matrices.fansCountHumanized,
                            )
                        },
                    expanded = expandMatrices,
                )
            }
        },
    )
}

@Composable
internal fun CommonProfileHeader(
    bannerUrl: String?,
    avatarUrl: String?,
    displayName: Element,
    userKey: MicroBlogKey,
    handle: String,
    isBigScreen: Boolean,
    modifier: Modifier = Modifier,
    onAvatarClick: (() -> Unit)? = null,
    onBannerClick: (() -> Unit)? = null,
    headerTrailing: @Composable RowScope.() -> Unit = {},
    handleTrailing: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val statusBarHeight =
        with(LocalDensity.current) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
    val actualBannerHeight =
        remember(statusBarHeight) {
            ProfileHeaderConstants.BANNER_HEIGHT.dp + statusBarHeight
        }
    Box(
        modifier =
            modifier
//                .sharedBounds(
//                    rememberSharedContentState(key = "header-$userKey"),
//                    animatedVisibilityScope = this@AnimatedVisibilityScope,
//                    renderInOverlayDuringTransition = false,
//                    enter = EnterTransition.None,
//                    exit = ExitTransition.None,
//                    resizeMode =
//                        SharedTransitionScope.ResizeMode.ScaleToBounds(
//                            contentScale = ContentScale.FillWidth,
//                            alignment = Alignment.TopStart,
//                        ),
//                    placeHolderSize = SharedTransitionScope.PlaceHolderSize.animatedSize,
//                )
//                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                .padding(bottom = 8.dp),
    ) {
        bannerUrl?.let {
            NetworkImage(
                model = it,
                contentDescription = null,
                modifier =
                    Modifier
//                        .sharedElement(
//                            rememberSharedContentState(key = "profile-banner-$userKey"),
//                            animatedVisibilityScope = this@AnimatedVisibilityScope,
//                        )
                        .clipToBounds()
                        .fillMaxWidth()
                        .height(actualBannerHeight)
                        .let {
                            if (onBannerClick != null) {
                                it.clickable {
                                    onBannerClick.invoke()
                                }
                            } else {
                                it
                            }
                        },
            )
        } ?: Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(actualBannerHeight)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)),
        )
        // avatar
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = screenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(
                                top = (actualBannerHeight - ProfileHeaderConstants.AVATAR_SIZE.dp / 2),
                            ),
                ) {
                    AvatarComponent(
                        data = avatarUrl,
                        size = ProfileHeaderConstants.AVATAR_SIZE.dp,
//                        beforeModifier =
//                            Modifier
//                                .sharedElement(
//                                    rememberSharedContentState(key = "profile-avatar-$userKey"),
//                                    animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                ),
                        modifier =
                            Modifier
                                .let {
                                    if (onAvatarClick != null) {
                                        it.clickable {
                                            onAvatarClick.invoke()
                                        }
                                    } else {
                                        it
                                    }
                                },
                    )
                }
                if (!isBigScreen) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(top = actualBannerHeight),
                    ) {
                        HtmlText(
                            element = displayName,
                            textStyle = MaterialTheme.typography.titleMedium,
//                        modifier =
//                            Modifier
//                                .sharedElement(
//                                    rememberSharedContentState(key = "profile-display-name-$userKey"),
//                                    animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                ),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = handle,
                                style = MaterialTheme.typography.bodySmall,
//                            modifier =
//                                Modifier
//                                    .sharedElement(
//                                        rememberSharedContentState(key = "profile-handle-$userKey"),
//                                        animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                    ),
                            )
                            handleTrailing.invoke(this)
                        }
                    }
                } else {
                    Spacer(
                        modifier =
                            Modifier
                                .weight(1f),
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .padding(top = actualBannerHeight),
                ) {
                    headerTrailing()
                }
            }
            if (isBigScreen) {
                Column(
                    modifier =
                        Modifier
                            .padding(horizontal = screenHorizontalPadding),
                ) {
                    HtmlText(
                        element = displayName,
                        textStyle = MaterialTheme.typography.titleMedium,
//                        modifier =
//                            Modifier
//                                .sharedElement(
//                                    rememberSharedContentState(key = "profile-display-name-$userKey"),
//                                    animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                ),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = handle,
                            style = MaterialTheme.typography.bodySmall,
//                            modifier =
//                                Modifier
//                                    .sharedElement(
//                                        rememberSharedContentState(key = "profile-handle-$userKey"),
//                                        animatedVisibilityScope = this@AnimatedVisibilityScope,
//                                    ),
                        )
                        handleTrailing.invoke(this)
                    }
                }
            }
            // content
            Box {
                content()
            }
        }
    }
}

private object ProfileHeaderConstants {
    const val BANNER_HEIGHT = 150
    const val AVATAR_SIZE = 96
}

@Composable
private fun ProfileHeaderError() {
}

@Composable
internal fun ProfileHeaderLoading(
    withStatusBarHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val statusBarHeight =
        with(LocalDensity.current) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
    val actualBannerHeight =
        remember(
            statusBarHeight,
            withStatusBarHeight,
        ) {
            ProfileHeaderConstants.BANNER_HEIGHT.dp + if (withStatusBarHeight) statusBarHeight else 0.dp
        }
    Box(
        modifier =
            modifier
//                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                .padding(bottom = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(actualBannerHeight)
                    .placeholder(true),
        )
        // avatar
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = screenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(
                                top = (actualBannerHeight - ProfileHeaderConstants.AVATAR_SIZE.dp / 2),
                            ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(ProfileHeaderConstants.AVATAR_SIZE.dp)
                                .clip(CircleShape)
                                .placeholder(true),
                    )
                }
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(top = actualBannerHeight),
                ) {
                    Text(
                        text = "Loading user",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.placeholder(true),
                    )
                    Text(
                        text = "Loading",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.placeholder(true),
                    )
                }
            }
            Text(
                text = "Lorem Ipsum is simply dummy text",
                modifier =
                    Modifier
                        .placeholder(true)
                        .padding(horizontal = screenHorizontalPadding),
            )
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
    val mediaState = state.mediaState

    val profileTabs =
        listOfNotNull(
            ProfileTab.Timeline,
            if (mediaState.isSuccess() && mediaState.itemCount > 0 || mediaState.isLoading) {
                ProfileTab.Media
            } else {
                null
            },
        )
    val allAccounts =
        remember {
            AccountsPresenter()
        }.invoke()
    object {
        val state = state
        val allAccountsState = allAccounts
        val showMoreMenus = showMoreMenus
        val isRefreshing = isRefreshing
        val profileTabs = profileTabs

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

private enum class ProfileTab {
    Timeline,
    Media,
}

private val ProfileTab.title: String
    @Composable
    get() =
        when (this) {
            ProfileTab.Timeline -> stringResource(R.string.profile_tab_timeline)
            ProfileTab.Media -> stringResource(R.string.profile_tab_media)
        }
