package dev.dimension.flare.ui.screen.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moriatsushi.koject.compose.rememberInject
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.HtmlText2
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.placeholder.placeholder
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.mastodon.StatusPlaceholder
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.profile.ProfilePresenter
import dev.dimension.flare.ui.presenter.profile.ProfileWithUserNameAndHostPresenter
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.ktml.dom.Element
import kotlin.math.max

@Composable
@Destination(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.ProfileWithNameAndHost.route,
        ),
    ],
)
fun ProfileWithUserNameAndHostRoute(
    userName: String,
    host: String,
    navigator: DestinationsNavigator,
) {
    val state by producePresenter(key = "acct_$userName@$host") {
        profileWithUserNameAndHostPresenter(
            userName = userName,
            host = host,
        )
    }
    state.onSuccess {
        ProfileScreen(
            userKey = it.userKey,
            onBack = {
                navigator.navigateUp()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileErrorScreen(onBack: () -> Unit) {
    FlareTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = "Error")
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileLoadingScreen(onBack: () -> Unit) {
    FlareTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = "Loading...")
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
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
}

@Composable
private fun profileWithUserNameAndHostPresenter(
    userName: String,
    host: String,
) = run {
    remember(
        userName,
        host,
    ) {
        ProfileWithUserNameAndHostPresenter(
            userName = userName,
            host = host,
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
            uriPattern = AppDeepLink.Profile.route,
        ),
    ],
)
fun ProfileRoute(
    userKey: MicroBlogKey,
    navigator: DestinationsNavigator,
) {
    ProfileScreen(
        userKey = userKey,
        onBack = {
            navigator.navigateUp()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    // null means current user
    userKey: MicroBlogKey? = null,
    onBack: () -> Unit = {},
    showTopBar: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by producePresenter(key = userKey.toString()) {
        profilePresenter(userKey)
    }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    FlareTheme {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets =
                ScaffoldDefaults
                    .contentWindowInsets.exclude(WindowInsets.statusBars),
            topBar = {
                if (showTopBar) {
                    val titleAlpha by remember {
                        derivedStateOf {
                            if (listState.firstVisibleItemIndex > 0 || listState.layoutInfo.visibleItemsInfo.isEmpty()) {
                                1f
                            } else {
                                max(
                                    0f,
                                    (listState.firstVisibleItemScrollOffset / listState.layoutInfo.visibleItemsInfo[0].size.toFloat()),
                                )
                            }
                        }
                    }
                    Box {
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
                                TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = Color.Transparent,
                                ),
                            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
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
                                state.state.userState.onSuccess {
                                    IconButton(onClick = { /*TODO*/ }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            },
        ) {
            RefreshContainer(
                modifier = Modifier.fillMaxSize(),
                refreshing = state.state.refreshing,
                onRefresh = state.state::refresh,
                indicatorPadding = it + contentPadding,
                content = {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        state = listState,
                        contentPadding = contentPadding,
                    ) {
                        item {
                            ProfileHeader(
                                state.state.userState,
                                state.state.relationState,
                            )
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
private fun ProfileHeader(
    userState: UiState<UiUser>,
    relationState: UiState<UiRelation>,
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
                )
            }
        }
    }
}

@Composable
private fun ProfileHeaderSuccess(
    user: UiUser,
    relationState: UiState<UiRelation>,
    modifier: Modifier = Modifier,
) {
    when (user) {
        is UiUser.Mastodon -> {
            MastodonProfileHeader(
                user = user,
                relationState = relationState,
                modifier = modifier,
            )
        }

        is UiUser.Misskey -> {
            MisskeyProfileHeader(
                user = user,
                relationState = relationState,
                modifier = modifier,
            )
        }

//        is UiUser.Bluesky -> BlueskyProfileHeader(
//            user = user,
//            relationState = relationState,
//            modifier = modifier,
//        )
    }
}

@Composable
internal fun CommonProfileHeader(
    bannerUrl: String?,
    avatarUrl: String?,
    displayName: Element,
    handle: String,
    modifier: Modifier = Modifier,
    headerTrailing: @Composable () -> Unit = {},
    handleTrailing: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val statusBarHeight =
        with(LocalDensity.current) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
    val actualBannerHeight =
        remember(statusBarHeight) {
            ProfileHeaderConstants.BannerHeight.dp + statusBarHeight
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
                        .height(actualBannerHeight),
            )
        }
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
                                top = (actualBannerHeight - ProfileHeaderConstants.AvatarSize.dp / 2),
                            ),
                ) {
                    AvatarComponent(data = avatarUrl, size = ProfileHeaderConstants.AvatarSize.dp)
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
                Box(
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
    const val BannerHeight = 150
    const val AvatarSize = 96
}

@Composable
private fun ProfileHeaderError(modifier: Modifier = Modifier) {
}

@Composable
private fun ProfileHeaderLoading(modifier: Modifier = Modifier) {
    val statusBarHeight =
        with(LocalDensity.current) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
    val actualBannerHeight =
        remember(statusBarHeight) {
            ProfileHeaderConstants.BannerHeight.dp + statusBarHeight
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
                                top = (actualBannerHeight - ProfileHeaderConstants.AvatarSize.dp / 2),
                            ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(ProfileHeaderConstants.AvatarSize.dp)
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
private fun profilePresenter(
    userKey: MicroBlogKey?,
    statusEvent: StatusEvent = rememberInject(),
) = run {
    val state =
        remember(userKey) {
            ProfilePresenter(
                userKey = userKey,
            )
        }.invoke()
    object {
        val state = state
        val statusEvent = statusEvent
    }
}
