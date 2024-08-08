package dev.dimension.flare.ui.screen.profile

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ContextualFlowRow
import androidx.compose.foundation.layout.ContextualFlowRowOverflow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.window.core.layout.WindowWidthSizeClass
import com.eygraber.compose.placeholder.material3.placeholder
import com.fleeksoft.ksoup.nodes.Element
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.generated.destinations.MediaRouteDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileMediaRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Cat
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.AvatarComponent
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
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.profile.ProfileMedia
import dev.dimension.flare.ui.presenter.profile.ProfilePresenter
import dev.dimension.flare.ui.presenter.profile.ProfileState
import dev.dimension.flare.ui.presenter.profile.ProfileWithUserNameAndHostPresenter
import dev.dimension.flare.ui.screen.home.RegisterTabCallback
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.reflect.KFunction1

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
    accountKey: MicroBlogKey,
) {
    val state by producePresenter(key = "acct_${accountKey}_$userName@$host") {
        profileWithUserNameAndHostPresenter(
            userName = userName,
            host = host,
            accountType = AccountType.Specific(accountKey),
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
                            accountType = AccountType.Specific(accountKey),
                        ),
                    )
                },
                onMediaClick = {
                    navigator.navigate(
                        MediaRouteDestination(
                            it,
                        ),
                    )
                },
                accountType = AccountType.Specific(accountKey),
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
                onMediaClick = {
                    navigator.navigate(
                        MediaRouteDestination(
                            it,
                        ),
                    )
                },
                accountType = accountType,
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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileLoadingScreen(onBack: () -> Unit) {
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Loading...")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) {
        LazyColumn(
            contentPadding = it,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ProfileHeaderLoading()
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
    userKey: MicroBlogKey?,
    navigator: DestinationsNavigator,
    accountKey: MicroBlogKey,
) {
    ProfileScreen(
        userKey = userKey,
        onBack = {
            navigator.navigateUp()
        },
        onProfileMediaClick = {
            navigator.navigate(
                ProfileMediaRouteDestination(
                    userKey,
                    accountType = AccountType.Specific(accountKey),
                ),
            )
        },
        onMediaClick = {
            navigator.navigate(
                MediaRouteDestination(
                    it,
                ),
            )
        },
        accountType = AccountType.Specific(accountKey),
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
) {
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
        onMediaClick = {
            navigator.navigate(
                MediaRouteDestination(
                    it,
                ),
            )
        },
        accountType = accountType,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
)
@Composable
private fun ProfileScreen(
    // null means current user
    accountType: AccountType,
    userKey: MicroBlogKey? = null,
    onBack: () -> Unit = {},
    onProfileMediaClick: () -> Unit = {},
    onMediaClick: (url: String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by producePresenter(key = "${accountType}_$userKey") {
        profilePresenter(userKey = userKey, accountType = accountType)
    }
    val listState = rememberLazyStaggeredGridState()
    RegisterTabCallback(lazyListState = listState)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInfo = currentWindowAdaptiveInfo()
    val windowSize =
        with(LocalDensity.current) {
            currentWindowSize().toSize().toDpSize()
        }
    val bigScreen = windowInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    FlareScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets =
            ScaffoldDefaults
                .contentWindowInsets
                .exclude(WindowInsets.statusBars),
        topBar = {
            val titleAlpha by remember {
                derivedStateOf {
                    if (listState.firstVisibleItemIndex > 0 ||
                        listState.layoutInfo.visibleItemsInfo.isEmpty() ||
                        bigScreen
                    ) {
                        1f
                    } else {
                        max(
                            0f,
                            (
                                listState.firstVisibleItemScrollOffset /
                                    listState.layoutInfo.visibleItemsInfo[0]
                                        .size.height
                                        .toFloat()
                            ),
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
                                    .background(
                                        color =
                                            MaterialTheme.colorScheme.surfaceColorAtElevation(
                                                3.dp,
                                            ),
                                    ),
                        )
                        Spacer(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .background(
                                        color =
                                            MaterialTheme.colorScheme.surfaceColorAtElevation(
                                                3.dp,
                                            ),
                                    ),
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
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigate_back),
                            )
                        }
                    },
                    actions = {
                        if (!bigScreen) {
                            ProfileMenu(
                                profileState = state.state,
                                setShowMoreMenus = state::setShowMoreMenus,
                                showMoreMenus = state.showMoreMenus,
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
                                )
                            },
                            expandMatrices = true,
                            onAvatarClick = {
                                state.state.userState.onSuccess {
                                    onMediaClick(it.avatar)
                                }
                            },
                            onBannerClick = {
                                state.state.userState.onSuccess {
                                    it.banner?.let { it1 -> onMediaClick(it1) }
                                }
                            },
                        )
                    }
                    Card {
                        ProfileMeidasPreview(
                            mediaState = state.state.mediaState,
                            maxLines = 2,
                            itemSize = 128.dp,
                            modifier =
                                Modifier.clickable {
                                    onProfileMediaClick.invoke()
                                },
                        )
                    }
                }
            }
            RefreshContainer(
                modifier = Modifier.fillMaxSize(),
                onRefresh = state::refresh,
                isRefreshing = state.isRefreshing,
                indicatorPadding = it + contentPadding,
                content = {
                    LazyStatusVerticalStaggeredGrid(
                        state = listState,
                        contentPadding =
                            contentPadding +
                                if (bigScreen) {
                                    it
                                } else {
                                    PaddingValues(0.dp)
                                },
                    ) {
                        if (!bigScreen) {
                            item(
                                span = StaggeredGridItemSpan.FullLine,
                            ) {
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
                                            onMediaClick(it.avatar)
                                        }
                                    },
                                    onBannerClick = {
                                        state.state.userState.onSuccess {
                                            it.banner?.let { it1 -> onMediaClick(it1) }
                                        }
                                    },
                                )
                            }
                            item {
                                ProfileMeidasPreview(
                                    mediaState = state.state.mediaState,
                                    maxLines = 1,
                                    itemSize = 64.dp,
                                    modifier =
                                        Modifier.clickable {
                                            onProfileMediaClick.invoke()
                                        },
                                )
                            }
                            state.state.mediaState.onSuccess {
                                item {
                                    HorizontalDivider()
                                }
                            }
                        }
                        with(state.state.listState) {
                            status()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ProfileMenu(
    profileState: ProfileState,
    setShowMoreMenus: KFunction1<Boolean, Unit>,
    showMoreMenus: Boolean,
) {
    profileState.userState.onSuccess { user ->
        IconButton(onClick = {
            setShowMoreMenus(true)
        }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = showMoreMenus,
            onDismissRequest = { setShowMoreMenus(false) },
        ) {
            profileState.isMe.onSuccess { isMe ->
                if (!isMe) {
                    profileState.relationState.onSuccess { relation ->
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
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = userState,
        modifier = modifier.animateContentSize(),
        label = "ProfileHeader",
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
    ) { state ->
        when (state) {
            is UiState.Loading -> {
                ProfileHeaderLoading()
            }

            is UiState.Error -> {
                ProfileHeaderError()
            }

            is UiState.Success -> {
                ProfileHeaderSuccess(
                    user = state.data,
                    relationState = relationState,
                    onFollowClick = onFollowClick,
                    isMe = isMe,
                    menu = menu,
                    expandMatrices = expandMatrices,
                    onAvatarClick = onAvatarClick,
                    onBannerClick = onBannerClick,
                )
            }
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
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .alpha(MediumAlpha),
                            tint = Color.Blue,
                        )

                    UiProfile.Mark.Cat ->
                        Icon(
                            imageVector = FontAwesomeIcons.Solid.Cat,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .alpha(MediumAlpha),
                        )

                    UiProfile.Mark.Bot ->
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .alpha(MediumAlpha),
                        )

                    UiProfile.Mark.Locked ->
                        Icon(
                            imageVector = Icons.Default.Lock,
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
                                        UiProfile.BottomContent.Iconify.Icon.Location -> Icons.Default.LocationOn
                                        UiProfile.BottomContent.Iconify.Icon.Url -> Icons.Default.Public
                                        UiProfile.BottomContent.Iconify.Icon.Verify -> Icons.Default.CheckCircle
                                    }
                                Icon(icon, contentDescription = null)
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
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
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
                Row(
                    modifier =
                        Modifier
                            .padding(top = actualBannerHeight),
                ) {
                    headerTrailing()
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
internal fun ProfileHeaderLoading(modifier: Modifier = Modifier) {
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
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
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
                modifier = Modifier.placeholder(true),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileMeidasPreview(
    mediaState: PagingState<ProfileMedia>,
    maxLines: Int,
    itemSize: Dp,
    modifier: Modifier = Modifier,
) {
    val appearanceSettings = LocalAppearanceSettings.current
    mediaState.onSuccess {
        if (itemCount > 0) {
            ContextualFlowRow(
                modifier =
                    modifier
                        .padding(horizontal = screenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                overflow =
                    ContextualFlowRowOverflow.expandIndicator {
                        Box(
                            modifier =
                                Modifier
                                    .size(itemSize)
                                    .background(
                                        color =
                                            MaterialTheme.colorScheme
                                                .surfaceColorAtElevation(
                                                    3.dp,
                                                ).copy(alpha = 0.25f),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.mastodon_item_show_more),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                    },
                itemCount = itemCount,
                maxLines = maxLines,
            ) {
                val item = get(it)
                if (item == null) {
                    Box(
                        modifier =
                            Modifier
                                .aspectRatio(1f)
                                .size(itemSize)
                                .placeholder(true),
                    )
                } else {
                    Box {
                        MediaItem(
                            media = item.media,
                            modifier =
                                Modifier
                                    .clipToBounds()
                                    .size(itemSize)
                                    .clip(MaterialTheme.shapes.medium)
                                    .let {
                                        if (item.media is UiMedia.Image &&
                                            (item.media as UiMedia.Image).sensitive &&
                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                            !appearanceSettings.showSensitiveContent
                                        ) {
                                            it.blur(32.dp)
                                        } else {
                                            it
                                        }
                                    },
                            showCountdown = false,
                        )
                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .let {
                                        if (item.media is UiMedia.Image &&
                                            (item.media as UiMedia.Image).sensitive &&
                                            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                                            !appearanceSettings.showSensitiveContent
                                        ) {
                                            it.background(MaterialTheme.colorScheme.surfaceContainer)
                                        } else {
                                            it
                                        }
                                    },
                        )
                    }
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
    object {
        val state = state
        val showMoreMenus = showMoreMenus
        val isRefreshing = isRefreshing

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
