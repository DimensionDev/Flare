package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dimension.flare.LocalContentPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.Res
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
import dev.dimension.flare.ui.component.ProfileMenu
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.platform.placeholder
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.profile.ProfilePresenter
import dev.dimension.flare.ui.presenter.profile.ProfileState
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.SegmentedButton
import io.github.composefluent.component.SegmentedControl
import io.github.composefluent.component.SegmentedItemPosition
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
    Row {
        if (isBigScreen) {
            Column(
                modifier =
                    Modifier
                        .padding(LocalContentPadding.current)
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
                ) + LocalContentPadding.current,
            state = listState,
        ) {
            if (!isBigScreen) {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    ProfileHeader(
                        modifier =
                            Modifier.let {
                                if (isBigScreen) {
                                    it
                                } else {
                                    it.ignoreHorizontalParentPadding(screenHorizontalPadding)
                                }
                            },
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
                }
            }
            state.state.tabs.onSuccess { tabs ->
                if (tabs.size > 1) {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        SegmentedControl {
                            repeat(tabs.size) { index ->
                                val tab = tabs.get(index)
                                SegmentedButton(
                                    checked = state.selectedTab == index,
                                    onCheckedChanged = {
                                        state.setSelectedTab(index)
                                    },
                                    position =
                                        when (index) {
                                            0 -> SegmentedItemPosition.Start
                                            tabs.size - 1 -> SegmentedItemPosition.End
                                            else -> SegmentedItemPosition.Center
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
                when (val tab = tabs.get(state.selectedTab)) {
                    is ProfileState.Tab.Media -> {
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
                    is ProfileState.Tab.Timeline -> {
                        status(tab.data)
                    }
                }
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
    var selectedTab by remember {
        mutableStateOf(0)
    }
    var showMoreMenus by remember {
        mutableStateOf(false)
    }

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
        val selectedTab = selectedTab
        val showMoreMenus = showMoreMenus

        fun setShowMoreMenus(value: Boolean) {
            showMoreMenus = value
        }

        fun refresh() {
            scope.launch {
                state.refresh()
            }
        }

        fun setSelectedTab(value: Int) {
            selectedTab = value
        }
    }
}

fun Modifier.ignoreHorizontalParentPadding(horizontal: Dp): Modifier =
    this.layout { measurable, constraints ->
        val overridenWidth = constraints.maxWidth + 2 * horizontal.roundToPx()
        val placeable = measurable.measure(constraints.copy(maxWidth = overridenWidth))
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
