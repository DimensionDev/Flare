package dev.dimension.flare.ui.screen.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CaretUp
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.ShareNodes
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.ErrorContent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.ignoreHorizontalParentPadding
import dev.dimension.flare.ui.component.placeholder
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.GalleryTimelineItem
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.StatusActionButton
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.component.toImageVector
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.gallery.GalleryDetailPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

private val GalleryGridSpacing = 8.dp
private val CompactTimelineSpacing = 2.dp
private val CompactRecommendationBottomSpacing = GalleryGridSpacing - CompactTimelineSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryDetailScreen(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    val state by producePresenter("gallery_detail_$accountType-$statusKey") {
        GalleryDetailPresenter(accountType = accountType, statusKey = statusKey).invoke()
    }
    val isBigScreen =
        dev.dimension.flare.ui.component.platform
            .isBigScreen()
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val shareRoute =
        remember(state.detail) {
            state.detail.takePost()?.shareRoute()
        }
    var showCompactInfoSheet by remember { mutableStateOf(false) }
    GalleryCardTimeline {
        FlareScaffold(
            topBar = {
                if (!isBigScreen) {
                    GalleryTopAppBar(
                        isBigScreen = false,
                        post = state.detail.takePost(),
                        navigate = navigate,
                        onBack = onBack,
                        onShare = {
                            shareRoute?.let(navigate)
                        },
                        onExpand = {
                            showCompactInfoSheet = true
                        },
                        scrollBehavior = topAppBarScrollBehavior,
                    )
                }
            },
            contentWindowInsets = WindowInsets(),
            containerColor = MaterialTheme.colorScheme.background,
            modifier =
                if (isBigScreen) {
                    Modifier
                } else {
                    Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                },
        ) { contentPadding ->
            state.detail
                .onSuccess { detail ->
                    if (isBigScreen) {
                        BigScreenGalleryContent(
                            detail = detail,
                            comments = state.comments,
                            recommendations = state.recommendations,
                            navigate = navigate,
                            onBack = onBack,
                            onShare = {
                                shareRoute?.let(navigate)
                            },
                            scrollBehavior = topAppBarScrollBehavior,
                            onAction = state::performAction,
                        )
                    } else {
                        CompactGalleryContent(
                            detail = detail,
                            comments = state.comments,
                            recommendations = state.recommendations,
                            navigate = navigate,
                            onAction = state::performAction,
                            contentPadding = contentPadding,
                            showInfoSheet = showCompactInfoSheet,
                            onDismissInfoSheet = {
                                showCompactInfoSheet = false
                            },
                        )
                    }
                }.onLoading {
                    GalleryLoading()
                }.onError { error ->
                    ErrorContent(
                        error = error,
                        onRetry = state::refresh,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryCommentsScreen(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    onBack: () -> Unit,
) {
    val state by producePresenter("gallery_comments_$accountType-$statusKey") {
        GalleryDetailPresenter(accountType = accountType, statusKey = statusKey).invoke()
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    GalleryCardTimeline {
        FlareScaffold(
            topBar = {
                FlareTopAppBar(
                    title = {},
                    navigationIcon = {
                        BackButton(onBack = onBack)
                    },
                    colors = transparentTopAppBarColors(),
                    scrollBehavior = topAppBarScrollBehavior,
                )
            },
            modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        ) { contentPadding ->
            LazyStatusVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                forceCardMode = true,
            ) {
                status(state.comments)
            }
        }
    }
}

@Composable
private fun GalleryCardTimeline(content: @Composable () -> Unit) {
    val appearance = LocalTimelineAppearance.current
    CompositionLocalProvider(
        LocalTimelineAppearance provides appearance.copy(timelineDisplayMode = TimelineDisplayMode.Card),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactGalleryContent(
    detail: GalleryDetail,
    comments: PagingState<UiTimelineV2>,
    recommendations: PagingState<UiTimelineV2>,
    navigate: (Route) -> Unit,
    onAction: (ActionMenu.Item) -> Unit,
    contentPadding: PaddingValues,
    showInfoSheet: Boolean,
    onDismissInfoSheet: () -> Unit,
) {
    val gridState = rememberLazyStaggeredGridState()
    if (showInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissInfoSheet,
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(164.dp),
                contentPadding =
                    PaddingValues(
                        start = screenHorizontalPadding,
                        end = screenHorizontalPadding,
                        bottom = 32.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(GalleryGridSpacing),
                verticalItemSpacing = CompactTimelineSpacing,
                modifier = Modifier.fillMaxHeight(0.9f),
            ) {
                galleryAfterImagesItems(
                    detail = detail,
                    comments = comments,
                    recommendations = recommendations,
                    navigate = { route ->
                        onDismissInfoSheet()
                        navigate(route)
                    },
                    onAction = onAction,
                )
            }
        }
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(164.dp),
        state = gridState,
        contentPadding =
            PaddingValues(
                start = screenHorizontalPadding,
                top = contentPadding.calculateTopPadding(),
                end = screenHorizontalPadding,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(GalleryGridSpacing),
        verticalItemSpacing = CompactTimelineSpacing,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            val configuration = LocalConfiguration.current
            GalleryImages(
                detail = detail,
                onMediaClick = { media ->
                    navigate(detail.post.statusMediaRoute(media))
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .ignoreHorizontalParentPadding(screenHorizontalPadding)
                        .let {
                            if (detail.orientation == GalleryOrientation.Horizontal) {
                                it.height(configuration.screenHeightDp.dp)
                            } else {
                                it
                            }
                        },
            )
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            Spacer(Modifier.height(12.dp))
        }
        galleryAfterImagesItems(
            detail = detail,
            comments = comments,
            recommendations = recommendations,
            navigate = navigate,
            onAction = onAction,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BigScreenGalleryContent(
    detail: GalleryDetail,
    comments: PagingState<UiTimelineV2>,
    recommendations: PagingState<UiTimelineV2>,
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onAction: (ActionMenu.Item) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            if (detail.orientation == GalleryOrientation.Vertical) {
                val images = detail.post.galleryImages()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement =
                        if (images.size == 1) {
                            Arrangement.Center
                        } else {
                            Arrangement.Top
                        },
                ) {
                    items(images) { image ->
                        GalleryImage(
                            image = image,
                            onClick = {
                                navigate(detail.post.statusMediaRoute(image))
                            },
                        )
                    }
                }
            } else {
                GalleryImages(
                    detail = detail,
                    onMediaClick = { media ->
                        navigate(detail.post.statusMediaRoute(media))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            GalleryTopAppBar(
                isBigScreen = true,
                post = detail.post,
                navigate = navigate,
                onBack = onBack,
                onShare = onShare,
                onExpand = {},
                scrollBehavior = scrollBehavior,
            )
        }
        GallerySideBar(
            post = detail.post,
            comments = comments,
            recommendations = recommendations,
            navigate = navigate,
            onAction = onAction,
            modifier =
                Modifier
                    .width(380.dp)
                    .fillMaxHeight()
                    .systemBarsPadding(),
        )
    }
}

@Composable
private fun GalleryImages(
    detail: GalleryDetail,
    onMediaClick: (UiMedia.Image) -> Unit,
    modifier: Modifier = Modifier,
) {
    val images = detail.post.galleryImages()
    if (images.isEmpty()) {
        Box(
            modifier =
                modifier
                    .height(320.dp)
                    .placeholder(true),
        )
        return
    }
    when (detail.orientation) {
        GalleryOrientation.Vertical -> {
            Column(modifier = modifier) {
                images.forEach { image ->
                    GalleryImage(
                        image = image,
                        onClick = { onMediaClick(image) },
                    )
                }
            }
        }

        GalleryOrientation.Horizontal -> {
            val pagerState =
                rememberPagerState(
                    pageCount = { images.size },
                )
            HorizontalPager(
                state = pagerState,
                modifier = modifier,
            ) { index ->
                val image = images[index]
                Box(Modifier.fillMaxSize()) {
                    NetworkImage(
                        model = image.url,
                        contentDescription = image.description,
                        customHeaders = image.customHeaders,
                        contentScale = ContentScale.Fit,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clickable { onMediaClick(image) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryImage(
    image: UiMedia.Image,
    onClick: () -> Unit,
) {
    NetworkImage(
        model = image.url,
        contentDescription = image.description,
        customHeaders = image.customHeaders,
        contentScale = ContentScale.FillWidth,
        modifier =
            Modifier
                .aspectRatio(image.aspectRatio)
                .fillMaxWidth()
                .clickable(onClick = onClick),
    )
}

@Composable
private fun GallerySideBar(
    post: UiTimelineV2.Post,
    comments: PagingState<UiTimelineV2>,
    recommendations: PagingState<UiTimelineV2>,
    navigate: (Route) -> Unit,
    onAction: (ActionMenu.Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
    ) {
        Column {
            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Info") },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Comments") },
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text("Recommend") },
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> {
                        GalleryInfoTab(
                            post = post,
                            navigate = navigate,
                            onAction = onAction,
                        )
                    }

                    1 -> {
                        LazyStatusVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(1),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.fillMaxSize(),
                            forceCardMode = true,
                        ) {
                            status(comments)
                        }
                    }

                    2 -> {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Adaptive(132.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(GalleryGridSpacing),
                            verticalItemSpacing = GalleryGridSpacing,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            recommendationItems(recommendations)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryInfoTab(
    post: UiTimelineV2.Post,
    navigate: (Route) -> Unit,
    onAction: (ActionMenu.Item) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
    ) {
        item {
            GalleryAuthorCard(
                post = post,
                navigate = navigate,
                onAction = onAction,
                index = 0,
                totalCount = 2,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            GalleryDetailInfoCard(
                post = post,
                onAction = onAction,
                index = 1,
                totalCount = 2,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun GalleryAuthorCard(
    post: UiTimelineV2.Post,
    navigate: (Route) -> Unit,
    onAction: (ActionMenu.Item) -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    totalCount: Int = 0,
) {
    val user = post.user
    AdaptiveCard(
        modifier = modifier.fillMaxWidth(),
        index = index,
        totalCount = totalCount,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AvatarComponent(
                data = user?.avatar,
                modifier =
                    Modifier.clickable(enabled = user != null) {
                        if (user != null) {
                            navigate(Route.Profile.User(post.accountType, user.key))
                        }
                    },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = post.contentWarning?.raw ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = user?.handleWithoutAtAndHost.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            post.bookmarkAction()?.let { action ->
                StatusActionButton(
                    icon = action.icon?.toImageVector() ?: FontAwesomeIcons.Solid.EllipsisVertical,
                    number = null,
                    color = action.color.toComposeColor(),
                    onClicked = { onAction(action) },
                )
            }
        }
    }
}

@Composable
private fun GalleryDetailInfoCard(
    post: UiTimelineV2.Post,
    onAction: (ActionMenu.Item) -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    totalCount: Int = 0,
) {
    AdaptiveCard(
        modifier = modifier.fillMaxWidth(),
        index = index,
        totalCount = totalCount,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            GalleryMetadata(
                post = post,
                onAction = onAction,
            )
            GalleryBody(post = post)
        }
    }
}

@Composable
private fun GalleryMetadata(
    post: UiTimelineV2.Post,
    onAction: (ActionMenu.Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadataTextStyle = MaterialTheme.typography.bodySmall
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DateTimeText(
            data = post.createdAt,
            fullTime = true,
            style = metadataTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        post.countedActions().forEach { action ->
            GalleryMetadataActionButton(
                action = action,
                onAction = onAction,
                textStyle = metadataTextStyle,
            )
        }
    }
}

@Composable
private fun GalleryMetadataActionButton(
    action: ActionMenu.Item,
    onAction: (ActionMenu.Item) -> Unit,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    val color = action.color.toComposeColor()
    Row(
        modifier =
            modifier
                .clickable { onAction(action) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FAIcon(
            imageVector = action.icon?.toImageVector() ?: FontAwesomeIcons.Solid.EllipsisVertical,
            contentDescription = null,
            tint = color,
            modifier = Modifier.height(textStyle.fontSize.value.dp + 2.dp),
        )
        action.count?.humanized?.takeIf { it.isNotEmpty() }?.let {
            Text(
                text = it,
                style = textStyle,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GalleryBody(
    post: UiTimelineV2.Post,
    modifier: Modifier = Modifier,
) {
    if (post.content.isEmpty) return
    RichText(
        text = post.content,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
    )
}

private fun LazyStaggeredGridScope.galleryAfterImagesItems(
    detail: GalleryDetail,
    comments: PagingState<UiTimelineV2>,
    recommendations: PagingState<UiTimelineV2>,
    navigate: (Route) -> Unit,
    onAction: (ActionMenu.Item) -> Unit,
) {
    item(span = StaggeredGridItemSpan.FullLine) {
        GalleryAuthorCard(
            post = detail.post,
            navigate = navigate,
            onAction = onAction,
            index = 0,
            totalCount = 2,
            modifier =
                Modifier
                    .fillMaxWidth(),
        )
    }
    item(span = StaggeredGridItemSpan.FullLine) {
        GalleryDetailInfoCard(
            post = detail.post,
            onAction = onAction,
            index = 1,
            totalCount = 2,
            modifier =
                Modifier
                    .fillMaxWidth(),
        )
    }
    compactCommentsPreviewItems(
        statusKey = detail.post.statusKey,
        accountType = detail.post.accountType,
        comments = comments,
        navigate = navigate,
    )
    item(span = StaggeredGridItemSpan.FullLine) {
        SectionTitle("Recommendations")
    }
    recommendationItems(
        recommendations = recommendations,
        itemModifier = Modifier.padding(bottom = CompactRecommendationBottomSpacing),
    )
}

private fun LazyStaggeredGridScope.compactCommentsPreviewItems(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    comments: PagingState<UiTimelineV2>,
    navigate: (Route) -> Unit,
) {
    with(comments) {
        onSuccess {
            val visibleCount = minOf(itemCount, 3)
            if (visibleCount > 0) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    SectionTitle("Comments")
                }
                repeat(visibleCount) { index ->
                    item(span = StaggeredGridItemSpan.FullLine) {
                        AdaptiveCard(
                            index = index,
                            totalCount = visibleCount,
                            respectTimelineMode = true,
                        ) {
                            StatusItem(get(index))
                        }
                    }
                }
            }
            if (
                visibleCount > 0 &&
                (
                    itemCount > 3 ||
                        (
                            appendState is LoadState.NotLoading &&
                                !(appendState as LoadState.NotLoading).endOfPaginationReached
                        )
                )
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Button(
                        onClick = {
                            navigate(Route.Gallery.Comments(statusKey = statusKey, accountType = accountType))
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    ) {
                        Text("View more")
                    }
                }
            }
        }
        onLoading {
            item(span = StaggeredGridItemSpan.FullLine) {
                SectionTitle("Comments")
            }
            repeat(3) { index ->
                item(span = StaggeredGridItemSpan.FullLine) {
                    AdaptiveCard(
                        index = index,
                        totalCount = 3,
                        respectTimelineMode = true,
                    ) {
                        StatusItem(null)
                    }
                }
            }
        }
        onEmpty {
            Unit
        }
        onError { error ->
            item(span = StaggeredGridItemSpan.FullLine) {
                SectionTitle("Comments")
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                ErrorContent(error = error, onRetry = onRetry)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope.recommendationItems(
    recommendations: PagingState<UiTimelineV2>,
    itemModifier: Modifier = Modifier,
) {
    with(recommendations) {
        onSuccess {
            items(
                count = itemCount,
                key = itemKey { it.itemKey ?: it.hashCode() },
            ) { index ->
                GalleryTimelineItem(
                    item = peek(index),
                    modifier = itemModifier,
                )
            }
        }
        onLoading {
            items(8) {
                GalleryTimelineItem(
                    item = null,
                    modifier = itemModifier,
                )
            }
        }
        onError {
            item(span = StaggeredGridItemSpan.FullLine) {
                ErrorContent(error = it, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTopAppBar(
    isBigScreen: Boolean,
    post: UiTimelineV2.Post?,
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onExpand: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    FlareTopAppBar(
        title = {
            if (!isBigScreen) {
                CompactAppBarTitle(
                    post = post,
                    navigate = navigate,
                )
            }
        },
        navigationIcon = {
            BackButton(onBack = onBack)
        },
        actions = {
            if (isBigScreen) {
                IconButton(onClick = onShare) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.ShareNodes,
                        contentDescription = "Share",
                    )
                }
                IconButton(onClick = {}) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                        contentDescription = "More",
                    )
                }
            } else {
                IconButton(
                    enabled = post != null,
                    onClick = onExpand,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.CaretUp,
                        contentDescription = "Expand",
                    )
                }
            }
        },
        colors =
            if (isBigScreen) {
                transparentTopAppBarColors()
            } else {
                compactTopAppBarColors()
            },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun CompactAppBarTitle(
    post: UiTimelineV2.Post?,
    navigate: (Route) -> Unit,
) {
    val user = post?.user
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarComponent(
            data = user?.avatar,
            size = 36.dp,
            modifier =
                Modifier.clickable(enabled = post != null && user != null) {
                    if (post != null && user != null) {
                        navigate(Route.Profile.User(post.accountType, user.key))
                    }
                },
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = post?.contentWarning?.raw.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = user?.handleWithoutAtAndHost.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun transparentTopAppBarColors() =
    TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        navigationIconContentColor = MaterialTheme.colorScheme.primary,
        actionIconContentColor = MaterialTheme.colorScheme.primary,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun compactTopAppBarColors() =
    TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.background,
        navigationIconContentColor = MaterialTheme.colorScheme.primary,
        actionIconContentColor = MaterialTheme.colorScheme.primary,
    )

@Composable
private fun GalleryLoading() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .placeholder(true),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .placeholder(true),
        )
    }
}

private fun UiState<GalleryDetail>.takePost(): UiTimelineV2.Post? = (this as? UiState.Success)?.data?.post

private fun UiTimelineV2.Post.shareRoute(): Route.Status.ShareSheet? =
    actions
        .asSequence()
        .filterIsInstance<ActionMenu.Item>()
        .firstNotNullOfOrNull { action ->
            val text = action.text as? ActionMenu.Item.Text.Localized
            if (text?.type != ActionMenu.Item.Text.Localized.Type.Share) return@firstNotNullOfOrNull null
            val url = (action.clickEvent as? ClickEvent.Deeplink)?.url ?: return@firstNotNullOfOrNull null
            Route.parse(url) as? Route.Status.ShareSheet
        }

private fun UiTimelineV2.Post.bookmarkAction(): ActionMenu.Item? =
    actions
        .asSequence()
        .filterIsInstance<ActionMenu.Item>()
        .firstOrNull { action ->
            val text = action.text as? ActionMenu.Item.Text.Localized
            text?.type == ActionMenu.Item.Text.Localized.Type.Bookmark ||
                text?.type == ActionMenu.Item.Text.Localized.Type.Unbookmark
        }

private fun UiTimelineV2.Post.countedActions(): List<ActionMenu.Item> =
    actions
        .asSequence()
        .filterIsInstance<ActionMenu.Item>()
        .filter { it.count != null }
        .toList()

private fun UiTimelineV2.Post.galleryImages(): List<UiMedia.Image> = images.filterIsInstance<UiMedia.Image>()

private fun UiTimelineV2.Post.statusMediaRoute(media: UiMedia): Route.Media.StatusMedia =
    Route.Media.StatusMedia(
        statusKey = statusKey,
        accountType = accountType,
        index = images.indexOfFirst { it.url == media.url }.coerceAtLeast(0),
        preview =
            when (media) {
                is UiMedia.Image -> media.previewUrl
                is UiMedia.Video -> media.thumbnailUrl
                is UiMedia.Gif -> media.previewUrl
                is UiMedia.Audio -> null
            },
    )

@Composable
private fun ActionMenu.Item.Color?.toComposeColor(): Color =
    when (this) {
        ActionMenu.Item.Color.Red -> MaterialTheme.colorScheme.error
        ActionMenu.Item.Color.PrimaryColor -> MaterialTheme.colorScheme.primary
        ActionMenu.Item.Color.ContentColor -> LocalContentColor.current
        null -> LocalContentColor.current
    }
