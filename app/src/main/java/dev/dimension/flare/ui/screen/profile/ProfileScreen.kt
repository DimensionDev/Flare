package dev.dimension.flare.ui.screen.profile

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.data.datasource.mastodon.userTimelineDataSource
import dev.dimension.flare.data.repository.UiAccount
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.data.repository.mastodonUserDataPresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.status.EmptyStatusEvent
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.flatMap
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.onSuccess
import dev.dimension.flare.ui.theme.FlareTheme
import org.jsoup.nodes.Element
import kotlin.math.max

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userKey: MicroBlogKey?,
    showTopBar: Boolean = true,
) {
    val state by producePresenter {
        ProfilePresenter(userKey)
    }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    FlareTheme {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = ScaffoldDefaults
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
                    TopAppBar(
                        title = {
                            state.onSuccess {
                                it.user.onSuccess {
                                    HtmlText(
                                        element = it.nameElement,
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = titleAlpha
                            },
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = listState,
            ) {
                when (val data = state) {
                    is UiState.Error -> {

                    }

                    is UiState.Loading -> {

                    }

                    is UiState.Success -> {
                        item {
                            ProfileHeader(data.data.user)
                        }
                        with(data.data.listState) {
                            status(
                                event = data.data.eventHandler,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    userState: UiState<UiUser>,
    modifier: Modifier = Modifier,
) {
    when (userState) {
        is UiState.Loading -> {
            ProfileHeaderLoading(modifier)
        }

        is UiState.Error -> {
            ProfileHeaderError(modifier)
        }

        is UiState.Success -> {
            ProfileHeaderSuccess(
                user = userState.data,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ProfileHeaderSuccess(
    user: UiUser,
    modifier: Modifier = Modifier,
) {
    when (user) {
        is UiUser.Mastodon -> {
            MastodonProfileHeader(
                user = user,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun CommonProfileHeader(
    bannerUrl: String?,
    avatarUrl: String?,
    displayName: Element,
    handle: String,
    headerTrailing: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarHeight = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val actualBannerHeight = remember(statusBarHeight) {
        ProfileHeaderConstants.BannerHeight.dp + statusBarHeight
    }
    Box(
        modifier = modifier,
    ) {
        bannerUrl?.let {
            NetworkImage(
                model = it,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(actualBannerHeight)
            )
        }
        // avatar
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(
                            top = (actualBannerHeight - ProfileHeaderConstants.AvatarSize.dp / 2),
                        )
                ) {
                    NetworkImage(
                        model = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(ProfileHeaderConstants.AvatarSize.dp)
                            .clip(CircleShape),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = actualBannerHeight)
                ) {
                    HtmlText(
                        element = displayName,
                        textStyle = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = handle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Box {
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
private fun MastodonProfileHeader(
    user: UiUser.Mastodon,
    modifier: Modifier = Modifier,
) {
    CommonProfileHeader(
        bannerUrl = user.bannerUrl,
        avatarUrl = user.avatarUrl,
        displayName = user.nameElement,
        handle = user.displayHandle,
        headerTrailing = {
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                user.descriptionElement?.let {
                    HtmlText(
                        element = it,
                        layoutDirection = user.descriptionDirection ?: LocalLayoutDirection.current,
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ProfileHeaderError(
    modifier: Modifier = Modifier,
) {

}

@Composable
private fun ProfileHeaderLoading(
    modifier: Modifier = Modifier,
) {

}

@Composable
private fun ProfilePresenter(
    userKey: MicroBlogKey?,
): UiState<ProfileState> {
    if (userKey == null) {
        return UiState.Error(ProfileNotFoundException)
    }
    val account by activeAccountPresenter()
    val userState = account.flatMap {
        when (it) {
            is UiAccount.Mastodon -> MastodonProfilePresenter(
                userKey = userKey,
                account = it,
            )
        }
    }

    val listState = account.flatMap {
        when (it) {
            is UiAccount.Mastodon -> UiState.Success(
                userTimelineDataSource(
                    account = it,
                    userKey = userKey
                ).collectAsLazyPagingItems()
            )
        }
    }

    return UiState.Success(
        ProfileState(
            user = userState,
            listState = listState,
            eventHandler = EmptyStatusEvent,
        )
    )
}

object ProfileNotFoundException : Throwable("Profile not found")

private data class ProfileState(
    val user: UiState<UiUser>,
    val listState: UiState<LazyPagingItems<UiStatus>>,
    val eventHandler: StatusEvent,
)

@Composable
private fun MastodonProfilePresenter(
    userKey: MicroBlogKey,
    account: UiAccount.Mastodon,
): UiState<UiUser> {
    val state by mastodonUserDataPresenter(account = account, accountKey = userKey)
    return state
}
