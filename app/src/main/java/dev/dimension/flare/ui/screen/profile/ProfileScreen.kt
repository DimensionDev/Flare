package dev.dimension.flare.ui.screen.profile

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.material3.placeholder
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.onNotEmptyOrLoading
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.AdaptiveGrid
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.HtmlText2
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.mastodon.StatusPlaceholder
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.AccountData
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.profile.ProfileMedia
import dev.dimension.flare.ui.presenter.profile.ProfilePresenter
import dev.dimension.flare.ui.presenter.profile.ProfileState
import dev.dimension.flare.ui.presenter.profile.ProfileWithUserNameAndHostPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.ktml.dom.Element
import org.koin.compose.koinInject
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KFunction1

@Composable
@Destination(
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
fun ProfileWithUserNameAndHostRoute(
    userName: String,
    host: String,
    navigator: DestinationsNavigator,
    accountData: AccountData,
) {
    val state by producePresenter(key = "acct_${accountData.data}_$userName@$host") {
        profileWithUserNameAndHostPresenter(
            userName = userName,
            host = host,
            accountData = accountData,
        )
    }
    state.onSuccess {
        ProfileScreen(
            userKey = it.userKey,
            onBack = {
                navigator.navigateUp()
            },
            onProfileMediaClick = {
                navigator.navigate(
                    dev.dimension.flare.ui.screen.destinations.ProfileMediaRouteDestination(
                        it.userKey,
                    ),
                )
            },
            onMediaClick = {
                navigator.navigate(
                    dev.dimension.flare.ui.screen.destinations.MediaRouteDestination(
                        it,
                    ),
                )
            },
            accountData = accountData,
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
@Destination(
    wrappers = [ThemeWrapper::class],
)
internal fun MeRoute(
    navigator: DestinationsNavigator,
    accountData: AccountData,
) {
    ProfileRoute(null, navigator, accountData)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileErrorScreen(onBack: () -> Unit) {
    Scaffold(
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
    Scaffold(
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
    accountData: AccountData,
) = run {
    remember(
        userName,
        host,
    ) {
        ProfileWithUserNameAndHostPresenter(
            userName = userName,
            host = host,
            accountKey = accountData.data,
        )
    }.invoke()
}

@Composable
@Destination(
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
fun ProfileRoute(
    userKey: MicroBlogKey?,
    navigator: DestinationsNavigator,
    accountData: AccountData,
) {
    ProfileScreen(
        userKey = userKey,
        onBack = {
            navigator.navigateUp()
        },
        onProfileMediaClick = {
            navigator.navigate(
                dev.dimension.flare.ui.screen.destinations.ProfileMediaRouteDestination(
                    userKey,
                ),
            )
        },
        onMediaClick = {
            navigator.navigate(
                dev.dimension.flare.ui.screen.destinations.MediaRouteDestination(
                    it,
                ),
            )
        },
        accountData = accountData,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun ProfileScreen(
    // null means current user
    accountData: AccountData,
    userKey: MicroBlogKey? = null,
    onBack: () -> Unit = {},
    onProfileMediaClick: () -> Unit = {},
    onMediaClick: (url: String) -> Unit = {},
    showTopBar: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by producePresenter(key = "${accountData.data}_$userKey") {
        profilePresenter(userKey = userKey, accountData = accountData)
    }
    val listState = rememberLazyStaggeredGridState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInfo = currentWindowAdaptiveInfo()
    val bigScreen = windowInfo.windowSizeClass.widthSizeClass > WindowWidthSizeClass.Medium
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets =
            ScaffoldDefaults
                .contentWindowInsets.exclude(WindowInsets.statusBars),
        topBar = {
            if (showTopBar) {
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
                                (listState.firstVisibleItemScrollOffset / listState.layoutInfo.visibleItemsInfo[0].size.height.toFloat()),
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
                                HtmlText2(
                                    element = it.nameElement,
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
            }
        },
    ) {
        Row {
            if (bigScreen) {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .width(432.dp)
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
                                    onMediaClick(it.avatarUrl)
                                }
                            },
                            onBannerClick = {
                                state.state.userState.onSuccess {
                                    it.bannerUrl?.let { it1 -> onMediaClick(it1) }
                                }
                            },
                        )
                    }
                    Card {
                        ProfileMeidasPreview(
                            mediaState = state.state.mediaState,
                            orientation = Orientation.Vertical,
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
                onRefresh = state.state::refresh,
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
                                            onMediaClick(it.avatarUrl)
                                        }
                                    },
                                    onBannerClick = {
                                        state.state.userState.onSuccess {
                                            it.bannerUrl?.let { it1 -> onMediaClick(it1) }
                                        }
                                    },
                                )
                            }
                            item {
                                ProfileMeidasPreview(
                                    mediaState = state.state.mediaState,
                                    orientation = Orientation.Horizontal,
                                    modifier =
                                        Modifier.clickable {
                                            onProfileMediaClick.invoke()
                                        },
                                )
                            }
                            state.state.mediaState.onSuccess {
                                it.onNotEmptyOrLoading {
                                    item {
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                        with(state.state.listState) {
                            with(state.statusEvent) {
                                status()
                            }
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
                        when (relation) {
                            is UiRelation.Bluesky ->
                                BlueskyUserMenu(
                                    user = user,
                                    relation = relation,
                                    onBlockClick = {
                                        setShowMoreMenus(false)
                                        profileState.block(user, relation)
                                    },
                                    onMuteClick = {
                                        setShowMoreMenus(false)
                                        profileState.mute(user, relation)
                                    },
                                )

                            is UiRelation.Mastodon ->
                                MastodonUserMenu(
                                    user = user,
                                    relation = relation,
                                    onBlockClick = {
                                        setShowMoreMenus(false)
                                        profileState.block(user, relation)
                                    },
                                    onMuteClick = {
                                        setShowMoreMenus(false)
                                        profileState.mute(user, relation)
                                    },
                                )

                            is UiRelation.Misskey ->
                                MisskeyUserMenu(
                                    user = user,
                                    relation = relation,
                                    onBlockClick = {
                                        setShowMoreMenus(false)
                                        profileState.block(user, relation)
                                    },
                                    onMuteClick = {
                                        setShowMoreMenus(false)
                                        profileState.mute(user, relation)
                                    },
                                )

                            is UiRelation.XQT ->
                                XQTUserMenu(
                                    user = user,
                                    relation = relation,
                                    onBlockClick = {
                                        setShowMoreMenus(false)
                                        profileState.block(user, relation)
                                    },
                                    onMuteClick = {
                                        setShowMoreMenus(false)
                                        profileState.mute(user, relation)
                                    },
                                )
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
                            profileState.report(user)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    userState: UiState<UiUser>,
    relationState: UiState<UiRelation>,
    onFollowClick: (UiUser, UiRelation) -> Unit,
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
        contentKey = {
            when (it) {
                is UiState.Loading -> "Loading"
                is UiState.Error -> "Error"
                is UiState.Success -> "Success"
            }
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
    user: UiUser,
    relationState: UiState<UiRelation>,
    onFollowClick: (UiUser, UiRelation) -> Unit,
    onAvatarClick: () -> Unit,
    onBannerClick: () -> Unit,
    isMe: UiState<Boolean>,
    menu: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    expandMatrices: Boolean = false,
) {
    when (user) {
        is UiUser.Mastodon -> {
            MastodonProfileHeader(
                user = user,
                relationState = relationState,
                modifier = modifier,
                isMe = isMe,
                onFollowClick = {
                    onFollowClick(user, it)
                },
                menu = menu,
                expandMatrices = expandMatrices,
                onAvatarClick = onAvatarClick,
                onBannerClick = onBannerClick,
            )
        }

        is UiUser.Misskey -> {
            MisskeyProfileHeader(
                user = user,
                relationState = relationState,
                modifier = modifier,
                isMe = isMe,
                onFollowClick = {
                    onFollowClick(user, it)
                },
                menu = menu,
                expandMatrices = expandMatrices,
                onAvatarClick = onAvatarClick,
                onBannerClick = onBannerClick,
            )
        }

        is UiUser.Bluesky ->
            BlueskyProfileHeader(
                user = user,
                relationState = relationState,
                modifier = modifier,
                isMe = isMe,
                onFollowClick = {
                    onFollowClick(user, it)
                },
                menu = menu,
                expandMatrices = expandMatrices,
                onAvatarClick = onAvatarClick,
                onBannerClick = onBannerClick,
            )

        is UiUser.XQT ->
            XQTProfileHeader(
                user = user,
                relationState = relationState,
                modifier = modifier,
                isMe = isMe,
                onFollowClick = {
                    onFollowClick(user, it)
                },
                menu = menu,
                expandMatrices = expandMatrices,
                onAvatarClick = onAvatarClick,
                onBannerClick = onBannerClick,
            )
    }
}

@Composable
internal fun CommonProfileHeader(
    bannerUrl: String?,
    avatarUrl: String?,
    displayName: Element,
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
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                .padding(bottom = 8.dp),
    ) {
        bannerUrl?.let {
            NetworkImage(
                model = it,
                contentDescription = null,
                modifier =
                    Modifier
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
                    HtmlText2(
                        element = displayName,
                        textStyle = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = handle,
                            style = MaterialTheme.typography.bodySmall,
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
        }
    }
}

@Composable
private fun ProfileMeidasPreview(
    mediaState: UiState<LazyPagingItemsProxy<ProfileMedia>>,
    orientation: Orientation,
    modifier: Modifier = Modifier,
) {
    when (orientation) {
        Orientation.Vertical -> {
            AdaptiveGrid(
                modifier = modifier,
                content = {
                    mediaState.onSuccess { media ->
                        if (media.itemCount > 0) {
                            val count = min(6, media.itemCount)
                            repeat(count) {
                                val item = media[it]
                                if (item == null) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .aspectRatio(1f)
                                                .placeholder(true),
                                    )
                                } else {
                                    Box {
                                        MediaItem(
                                            media = item.media,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        if (it == count - 1) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .matchParentSize()
                                                        .background(
                                                            color =
                                                                MaterialTheme.colorScheme
                                                                    .surfaceColorAtElevation(
                                                                        3.dp,
                                                                    )
                                                                    .copy(alpha = 0.25f),
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
                                        }
                                    }
                                }
                            }
                        }
                    }.onLoading {
                        repeat(6) {
                            Box(
                                modifier =
                                    Modifier
                                        .aspectRatio(1f)
                                        .fillMaxSize()
                                        .placeholder(true),
                            )
                        }
                    }
                },
            )
        }

        Orientation.Horizontal -> {
            LazyRow(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
            ) {
                mediaState.onSuccess { media ->
                    if (media.itemCount > 0) {
                        val count = min(6, media.itemCount)
                        items(count) {
                            val item = media[it]
                            if (item == null) {
                                Box(
                                    modifier =
                                        Modifier
                                            .aspectRatio(1f)
                                            .size(64.dp)
                                            .placeholder(true),
                                )
                            } else {
                                Box {
                                    MediaItem(
                                        media = item.media,
                                        modifier =
                                            Modifier
                                                .size(64.dp)
                                                .let {
                                                    if (item.media is UiMedia.Image &&
                                                        (item.media as UiMedia.Image).sensitive &&
                                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                                    ) {
                                                        it.blur(32.dp)
                                                    } else {
                                                        it
                                                    }
                                                },
                                    )
                                    Box(
                                        modifier =
                                            Modifier
                                                .matchParentSize()
                                                .let {
                                                    if (item.media is UiMedia.Image &&
                                                        (item.media as UiMedia.Image).sensitive &&
                                                        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                                                    ) {
                                                        it.background(MaterialTheme.colorScheme.surfaceContainer)
                                                    } else {
                                                        it
                                                    }
                                                },
                                    )
                                    if (it == count - 1) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .matchParentSize()
                                                    .background(
                                                        color =
                                                            MaterialTheme.colorScheme
                                                                .surfaceColorAtElevation(
                                                                    3.dp,
                                                                )
                                                                .copy(alpha = 0.25f),
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
                                    }
                                }
                            }
                        }
                    }
                }.onLoading {
                    items(6) {
                        Box(
                            modifier =
                                Modifier
                                    .aspectRatio(1f)
                                    .size(64.dp)
                                    .placeholder(true),
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
    accountData: AccountData,
    statusEvent: StatusEvent = koinInject(),
) = run {
    val state =
        remember(userKey) {
            ProfilePresenter(
                userKey = userKey,
                accountKey = accountData.data,
            )
        }.invoke()
    var showMoreMenus by remember {
        mutableStateOf(false)
    }
    object {
        val state = state
        val statusEvent = statusEvent
        val showMoreMenus = showMoreMenus

        fun setShowMoreMenus(value: Boolean) {
            showMoreMenus = value
        }
    }
}
