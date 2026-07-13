package dev.dimension.flare.ui.screen.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.ErrorContent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
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
import dev.dimension.flare.ui.component.status.appendStateUI
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.component.toImageVector
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.gallery.GalleryDetailPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.HorizontalFlipView
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter

private val GalleryGridSpacing = 8.dp
private val CompactTimelineSpacing = 2.dp
private val CompactRecommendationBottomSpacing = GalleryGridSpacing - CompactTimelineSpacing
private val SideBarWidth = 380.dp

@Composable
internal fun GalleryDetailScreen(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    navigate: (Route) -> Unit,
) {
    val state by producePresenter("desktop_gallery_detail_$accountType-$statusKey") {
        remember(accountType, statusKey) {
            GalleryDetailPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()
    }
    GalleryCardTimeline {
        state.detail
            .onSuccess { detail ->
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth < 720.dp) {
                        CompactGalleryContent(
                            detail = detail,
                            comments = state.comments,
                            recommendations = state.recommendations,
                            navigate = navigate,
                        )
                    } else {
                        BigScreenGalleryContent(
                            detail = detail,
                            comments = state.comments,
                            recommendations = state.recommendations,
                            navigate = navigate,
                        )
                    }
                }
            }.onLoading {
                GalleryLoading()
            }.onError { error ->
                ErrorContent(
                    error = error,
                    onRetry = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
    }
}

@Composable
internal fun GalleryCommentsScreen(
    statusKey: MicroBlogKey,
    accountType: AccountType,
) {
    val state by producePresenter("desktop_gallery_comments_$accountType-$statusKey") {
        remember(accountType, statusKey) {
            GalleryDetailPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()
    }
    val listState = rememberLazyStaggeredGridState()
    GalleryCardTimeline {
        Box(Modifier.fillMaxSize()) {
            FlareScrollBar(listState) {
                LazyStatusVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(1),
                    contentPadding = LocalWindowPadding.current,
                    state = listState,
                    forceCardMode = true,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    status(state.comments)
                }
            }
            if (state.comments.isRefreshing) {
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

@Composable
private fun GalleryCardTimeline(content: @Composable () -> Unit) {
    val appearance = LocalTimelineAppearance.current
    CompositionLocalProvider(
        LocalTimelineAppearance provides appearance.copy(timelineDisplayMode = TimelineDisplayMode.Card),
    ) {
        content()
    }
}

@Composable
private fun CompactGalleryContent(
    detail: GalleryDetail,
    comments: PagingState<UiTimelineV2>,
    recommendations: PagingState<UiTimelineV2>,
    navigate: (Route) -> Unit,
) {
    val gridState = rememberLazyStaggeredGridState()
    FlareScrollBar(gridState) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(164.dp),
            state = gridState,
            contentPadding =
                PaddingValues(
                    start = screenHorizontalPadding,
                    top = LocalWindowPadding.current.calculateTopPadding(),
                    end = screenHorizontalPadding,
                    bottom = LocalWindowPadding.current.calculateBottomPadding() + 24.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(GalleryGridSpacing),
            verticalItemSpacing = CompactTimelineSpacing,
            modifier = Modifier.fillMaxSize(),
        ) {
            galleryImageItems(
                detail = detail,
                onMediaClick = { media ->
                    navigate(detail.statusMediaRoute(media))
                },
            )
            item(span = StaggeredGridItemSpan.FullLine) {
                Spacer(Modifier.height(12.dp))
            }
            galleryAfterImagesItems(
                detail = detail,
                comments = comments,
                recommendations = recommendations,
                navigate = navigate,
            )
        }
    }
}

@Composable
private fun BigScreenGalleryContent(
    detail: GalleryDetail,
    comments: PagingState<UiTimelineV2>,
    recommendations: PagingState<UiTimelineV2>,
    navigate: (Route) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        GalleryImagePane(
            detail = detail,
            onMediaClick = { media ->
                navigate(detail.statusMediaRoute(media))
            },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        )
        GallerySideBar(
            detail = detail,
            comments = comments,
            recommendations = recommendations,
            navigate = navigate,
            modifier =
                Modifier
                    .width(SideBarWidth)
                    .fillMaxHeight(),
        )
    }
}

@Composable
private fun GalleryImagePane(
    detail: GalleryDetail,
    onMediaClick: (UiMedia.Image) -> Unit,
    modifier: Modifier = Modifier,
) {
    val images = detail.images
    Box(
        modifier =
            modifier
                .background(FluentTheme.colors.background.solid.base)
                .clipToBounds(),
    ) {
        when {
            images.isEmpty() -> {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(320.dp)
                            .placeholder(true),
                )
            }

            detail.orientation == GalleryOrientation.Vertical -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement =
                        if (images.size == 1) {
                            Arrangement.Center
                        } else {
                            Arrangement.Top
                        },
                ) {
                    items(
                        items = images,
                        key = { it.url },
                    ) { image ->
                        GalleryImage(
                            image = image,
                            onClick = { onMediaClick(image) },
                        )
                    }
                }
            }

            else -> {
                val pagerState =
                    rememberPagerState(
                        pageCount = { images.size },
                    )
                HorizontalFlipView(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    val image = images[index]
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

private fun LazyStaggeredGridScope.galleryImageItems(
    detail: GalleryDetail,
    onMediaClick: (UiMedia.Image) -> Unit,
) {
    val images = detail.images
    if (images.isEmpty()) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .placeholder(true),
            )
        }
        return
    }
    when (detail.orientation) {
        GalleryOrientation.Vertical -> {
            items(
                items = images,
                key = { it.url },
                span = { StaggeredGridItemSpan.FullLine },
            ) { image ->
                GalleryImage(
                    image = image,
                    onClick = { onMediaClick(image) },
                    modifier = Modifier.ignoreHorizontalParentPadding(screenHorizontalPadding),
                )
            }
        }

        GalleryOrientation.Horizontal -> {
            item(span = StaggeredGridItemSpan.FullLine) {
                GalleryHorizontalImages(
                    images = images,
                    onMediaClick = onMediaClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(520.dp)
                            .ignoreHorizontalParentPadding(screenHorizontalPadding),
                )
            }
        }
    }
}

@Composable
private fun GalleryHorizontalImages(
    images: List<UiMedia.Image>,
    onMediaClick: (UiMedia.Image) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState =
        rememberPagerState(
            pageCount = { images.size },
        )
    HorizontalFlipView(
        state = pagerState,
        modifier = modifier,
    ) { index ->
        val image = images[index]
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

@Composable
private fun GalleryImage(
    image: UiMedia.Image,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NetworkImage(
        model = image.url,
        contentDescription = image.description,
        customHeaders = image.customHeaders,
        contentScale = ContentScale.FillWidth,
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(image.aspectRatio)
                .clickable(onClick = onClick),
    )
}

@Composable
private fun GallerySideBar(
    detail: GalleryDetail,
    comments: PagingState<UiTimelineV2>,
    recommendations: PagingState<UiTimelineV2>,
    navigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Column(
        modifier =
            modifier
                .background(FluentTheme.colors.background.layer.default)
                .padding(
                    top = LocalWindowPadding.current.calculateTopPadding(),
                    bottom = LocalWindowPadding.current.calculateBottomPadding(),
                ),
    ) {
        LiteFilter(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            GallerySideBarTab(
                text = "Info",
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
            )
            GallerySideBarTab(
                text = "Comments",
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
            )
            GallerySideBarTab(
                text = "Recommend",
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
            )
        }
        when (selectedTab) {
            0 -> {
                GalleryInfoTab(
                    detail = detail,
                    navigate = navigate,
                    modifier = Modifier.weight(1f),
                )
            }

            1 -> {
                val listState = rememberLazyStaggeredGridState()
                FlareScrollBar(listState, modifier = Modifier.weight(1f)) {
                    LazyStatusVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(1),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        state = listState,
                        forceCardMode = true,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        status(comments)
                    }
                }
            }

            2 -> {
                val gridState = rememberLazyStaggeredGridState()
                FlareScrollBar(gridState, modifier = Modifier.weight(1f)) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(132.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(GalleryGridSpacing),
                        verticalItemSpacing = GalleryGridSpacing,
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        recommendationItems(recommendations)
                    }
                }
            }
        }
    }
}

@Composable
private fun GallerySideBarTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    PillButton(
        selected = selected,
        onSelectedChanged = {
            if (it) {
                onClick()
            }
        },
    ) {
        Text(text)
    }
}

@Composable
private fun GalleryInfoTab(
    detail: GalleryDetail,
    navigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    FlareScrollBar(listState, modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
        ) {
            item {
                GalleryAuthorCard(
                    detail = detail,
                    navigate = navigate,
                    index = 0,
                    totalCount = 2,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                GalleryDetailInfoCard(
                    detail = detail,
                    index = 1,
                    totalCount = 2,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun GalleryAuthorCard(
    detail: GalleryDetail,
    navigate: (Route) -> Unit,
    index: Int = 0,
    totalCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val user = detail.author
    val uriHandler = LocalUriHandler.current
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
                            navigate(Route.Profile(detail.accountType, user.key))
                        }
                    },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = detail.title,
                    style = FluentTheme.typography.bodyStrong,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
                user?.let {
                    RichText(
                        text = it.name,
                        textStyle = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            StatusActionButton(
                icon =
                    if (detail.isBookmarked) {
                        UiIcon.Unlike.toImageVector()
                    } else {
                        UiIcon.Like.toImageVector()
                    },
                number = null,
                color =
                    if (detail.isBookmarked) {
                        FluentTheme.colors.system.critical
                    } else {
                        FluentTheme.colors.text.text.secondary
                    },
                onClicked = {
                    detail.onBookmark.invoke(ClickContext(uriHandler::openUri))
                },
            )
        }
    }
}

@Composable
private fun GalleryDetailInfoCard(
    detail: GalleryDetail,
    index: Int = 0,
    totalCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    AdaptiveCard(
        modifier = modifier.fillMaxWidth(),
        index = index,
        totalCount = totalCount,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            GalleryMetadata(detail = detail)
            GalleryBody(detail = detail)
        }
    }
}

@Composable
private fun GalleryMetadata(
    detail: GalleryDetail,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DateTimeText(
            data = detail.createdAt,
            fullTime = true,
            style = FluentTheme.typography.caption,
            color = FluentTheme.colors.text.text.secondary,
            modifier = Modifier.weight(1f, fill = false),
        )
        detail.matrix.forEach { matrix ->
            GalleryMetadataItem(matrix = matrix)
        }
    }
}

@Composable
private fun GalleryMetadataItem(
    matrix: GalleryDetail.Matrix,
    modifier: Modifier = Modifier,
) {
    val color = FluentTheme.colors.text.text.secondary
    Row(
        modifier =
            modifier
                .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FAIcon(
            imageVector = matrix.icon.toImageVector(),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(FluentTheme.typography.caption.fontSize.value.dp + 2.dp),
        )
        matrix.humanizedCount.takeIf { it.isNotEmpty() }?.let {
            Text(
                text = it,
                style = FluentTheme.typography.caption,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GalleryBody(
    detail: GalleryDetail,
    modifier: Modifier = Modifier,
) {
    val content = detail.content ?: return
    RichText(
        text = content,
        textStyle = FluentTheme.typography.body,
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
) {
    item(span = StaggeredGridItemSpan.FullLine) {
        GalleryAuthorCard(
            detail = detail,
            navigate = navigate,
            index = 0,
            totalCount = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item(span = StaggeredGridItemSpan.FullLine) {
        GalleryDetailInfoCard(
            detail = detail,
            index = 1,
            totalCount = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    compactCommentsPreviewItems(
        statusKey = detail.statusKey,
        accountType = detail.accountType,
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
                    SubtleButton(
                        onClick = {
                            navigate(Route.Gallery.Comments(statusKey = statusKey, accountType = accountType))
                        },
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
        onEmpty {}
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

private fun LazyStaggeredGridScope.recommendationItems(
    recommendations: PagingState<UiTimelineV2>,
    itemModifier: Modifier = Modifier,
) {
    with(recommendations) {
        onSuccess {
            items(
                count = itemCount,
                key = itemKey { it.itemKey ?: it.hashCode() },
                contentType = itemContentType { it.itemType },
            ) { index ->
                GalleryTimelineItem(
                    item = get(index),
                    modifier = itemModifier,
                )
            }
            appendStateUI(this)
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
        style = FluentTheme.typography.bodyStrong,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

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

private fun GalleryDetail.statusMediaRoute(media: UiMedia): Route.StatusMedia =
    Route.StatusMedia(
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
