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
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CaretUp
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.ShareNodes
import dev.dimension.flare.R
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.items
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
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiIcon
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
        remember(accountType, statusKey) {
            GalleryDetailPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()
    }
    val isBigScreen =
        dev.dimension.flare.ui.component.platform
            .isBigScreen()
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showCompactInfoSheet by remember { mutableStateOf(false) }
    GalleryCardTimeline {
        FlareScaffold(
            topBar = {
                if (!isBigScreen) {
                    GalleryTopAppBar(
                        isBigScreen = false,
                        detailState = state.detail,
                        navigate = navigate,
                        onBack = onBack,
                        onShare = {
                            state.detail.onSuccess { detail ->
                                navigate(detail.shareRoute())
                            }
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
                                state.detail.onSuccess { detail ->
                                    navigate(detail.shareRoute())
                                }
                            },
                            scrollBehavior = topAppBarScrollBehavior,
                        )
                    } else {
                        CompactGalleryContent(
                            detail = detail,
                            comments = state.comments,
                            recommendations = state.recommendations,
                            navigate = navigate,
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
                        onRetry = {},
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
        remember(accountType, statusKey) {
            GalleryDetailPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()
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
        val images = detail.images
        when (detail.orientation) {
            GalleryOrientation.Vertical -> {
                items(
                    images,
                    span = {
                        StaggeredGridItemSpan.FullLine
                    },
                ) { image ->
                    GalleryImage(
                        image = image,
                        onClick = {
                            navigate(detail.galleryMediaRoute(image))
                        },
                        modifier =
                            Modifier
                                .ignoreHorizontalParentPadding(screenHorizontalPadding),
                    )
                }
            }

            GalleryOrientation.Horizontal -> {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    val containerSize = LocalWindowInfo.current.containerSize
                    val screenHeightDp = with(LocalDensity.current) { containerSize.height.toDp() }
                    val pagerState =
                        rememberPagerState(
                            pageCount = { images.size },
                        )
                    HorizontalPager(
                        state = pagerState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .ignoreHorizontalParentPadding(screenHorizontalPadding)
                                .height(screenHeightDp),
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
                                        .clickable {
                                            navigate(detail.galleryMediaRoute(image))
                                        },
                            )
                        }
                    }
                }
            }
        }
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
) {
    val images = detail.images
    Row(Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            if (detail.orientation == GalleryOrientation.Vertical) {
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
                                navigate(detail.galleryMediaRoute(image))
                            },
                        )
                    }
                }
            } else {
                val pagerState =
                    rememberPagerState(
                        pageCount = { images.size },
                    )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
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
                                    .clickable {
                                        navigate(detail.galleryMediaRoute(image))
                                    },
                        )
                    }
                }
            }
            GalleryTopAppBar(
                isBigScreen = true,
                detailState = UiState.Success(detail),
                navigate = navigate,
                onBack = onBack,
                onShare = onShare,
                onExpand = {},
                scrollBehavior = scrollBehavior,
            )
        }
        GallerySideBar(
            detail = detail,
            comments = comments,
            recommendations = recommendations,
            navigate = navigate,
            modifier =
                Modifier
                    .width(380.dp)
                    .fillMaxHeight()
                    .systemBarsPadding(),
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
                    text = { Text(stringResource(R.string.gallery_detail_tab_info)) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.gallery_detail_tab_comments)) },
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text(stringResource(R.string.gallery_detail_tab_recommend)) },
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> {
                        GalleryInfoTab(
                            detail = detail,
                            navigate = navigate,
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
    detail: GalleryDetail,
    navigate: (Route) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
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

@Composable
private fun GalleryAuthorCard(
    detail: GalleryDetail,
    navigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    totalCount: Int = 0,
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
                            navigate(Route.Profile.User(detail.accountType, user.key))
                        }
                    },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                user?.let {
                    RichText(
                        text = it.name,
                        textStyle = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            StatusActionButton(
                icon =
                    if (detail.isBookmarked) {
                        UiIcon.Unbookmark.toImageVector()
                    } else {
                        UiIcon.Bookmark.toImageVector()
                    },
                number = null,
                color =
                    if (detail.isBookmarked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
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
            data = detail.createdAt,
            fullTime = true,
            style = metadataTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        detail.matrix.forEach { matrix ->
            GalleryMetadataItem(
                matrix = matrix,
                textStyle = metadataTextStyle,
            )
        }
    }
}

@Composable
private fun GalleryMetadataItem(
    matrix: GalleryDetail.Matrix,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
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
            modifier = Modifier.height(textStyle.fontSize.value.dp + 2.dp),
        )
        matrix.humanizedCount.takeIf { it.isNotEmpty() }?.let {
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
    detail: GalleryDetail,
    modifier: Modifier = Modifier,
) {
    val content = detail.content ?: return
    RichText(
        text = content,
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
            modifier =
                Modifier
                    .fillMaxWidth(),
        )
    }
    item(span = StaggeredGridItemSpan.FullLine) {
        GalleryDetailInfoCard(
            detail = detail,
            index = 1,
            totalCount = 2,
            modifier =
                Modifier
                    .fillMaxWidth(),
        )
    }
    compactCommentsPreviewItems(
        statusKey = detail.statusKey,
        accountType = detail.accountType,
        comments = comments,
        navigate = navigate,
    )
    item(span = StaggeredGridItemSpan.FullLine) {
        SectionTitle(stringResource(R.string.gallery_detail_recommendations_title))
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
                    SectionTitle(stringResource(R.string.gallery_detail_comments_title))
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
                            navigate(
                                Route.Gallery.Comments(
                                    statusKey = statusKey,
                                    accountType = accountType,
                                ),
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.gallery_detail_view_more))
                    }
                }
            }
        }
        onLoading {
            item(span = StaggeredGridItemSpan.FullLine) {
                SectionTitle(stringResource(R.string.gallery_detail_comments_title))
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
        }
        onError { error ->
            item(span = StaggeredGridItemSpan.FullLine) {
                SectionTitle(stringResource(R.string.gallery_detail_comments_title))
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
    items(
        recommendations,
        loadingContent = {
            GalleryTimelineItem(
                item = null,
                modifier = itemModifier,
            )
        },
        errorContent = {
            ErrorContent(
                error = it,
                onRetry = {
                    recommendations.onError {
                        onRetry.invoke()
                    }
                },
            )
        },
    ) {
        GalleryTimelineItem(
            item = it,
            modifier = itemModifier,
        )
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
    detailState: UiState<GalleryDetail>,
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
                    detailState = detailState,
                    navigate = navigate,
                )
            }
        },
        navigationIcon = {
            BackButton(onBack = onBack)
        },
        actions = {
            if (isBigScreen) {
                val shareEnabled =
                    when (detailState) {
                        is UiState.Success -> true

                        is UiState.Loading,
                        is UiState.Error,
                        -> false
                    }
                IconButton(
                    enabled = shareEnabled,
                    onClick = onShare,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.ShareNodes,
                        contentDescription = stringResource(R.string.gallery_detail_share_content_description),
                    )
                }
                IconButton(onClick = {}) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                        contentDescription = stringResource(R.string.more),
                    )
                }
            } else {
                val expandEnabled =
                    when (detailState) {
                        is UiState.Success -> true

                        is UiState.Loading,
                        is UiState.Error,
                        -> false
                    }
                IconButton(
                    enabled = expandEnabled,
                    onClick = onExpand,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.CaretUp,
                        contentDescription = stringResource(R.string.gallery_detail_expand_content_description),
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
    detailState: UiState<GalleryDetail>,
    navigate: (Route) -> Unit,
) {
    when (detailState) {
        is UiState.Success -> {
            CompactAppBarTitleContent(
                detail = detailState.data,
                navigate = navigate,
            )
        }

        is UiState.Loading -> {
            CompactAppBarTitleLoading()
        }

        is UiState.Error -> {
            Spacer(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CompactAppBarTitleContent(
    detail: GalleryDetail,
    navigate: (Route) -> Unit,
) {
    val user = detail.author
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarComponent(
            data = user?.avatar,
            size = 36.dp,
            modifier =
                Modifier.clickable(enabled = user != null) {
                    if (user != null) {
                        navigate(Route.Profile.User(detail.accountType, user.key))
                    }
                },
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            user?.let {
                RichText(
                    text = it.name,
                    textStyle = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompactAppBarTitleLoading() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarComponent(
            data = null,
            size = 36.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(16.dp)
                        .placeholder(true),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.38f)
                        .height(12.dp)
                        .placeholder(true),
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

private fun GalleryDetail.shareRoute(): Route.Status.ShareSheet =
    Route.Status.ShareSheet(
        statusKey = statusKey,
        accountType = accountType,
        shareUrl = url,
    )

private fun GalleryDetail.galleryMediaRoute(media: UiMedia.Image): Route.Media.RawMedia =
    Route.Media.RawMedia(
        medias = images,
        index = images.indexOfFirst { it.url == media.url }.coerceAtLeast(0),
        preview = media.previewUrl,
    )
