package dev.dimension.flare.ui.screen.article

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Chrome
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.File
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.ShareNodes
import dev.dimension.flare.R
import dev.dimension.flare.common.MediaFileNamePolicy
import dev.dimension.flare.common.VideoDownloadHelper
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.ErrorContent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FavIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.VideoPlayer
import dev.dimension.flare.ui.component.placeholder
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiArticle
import dev.dimension.flare.ui.model.UiArticleAuthor
import dev.dimension.flare.ui.model.UiArticleBlock
import dev.dimension.flare.ui.model.UiArticleContentGateReason
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.article.ArticlePresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.theme.isLightTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

private val ArticleCoverHeight = 260.dp
private const val ARTICLE_COVER_KEY = "cover"
private const val ARTICLE_HEADER_KEY = "header"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArticleScreen(
    accountType: AccountType,
    articleKey: MicroBlogKey,
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    var refreshKey by remember(accountType, articleKey) { mutableIntStateOf(0) }
    val state by producePresenter("article_$accountType-$articleKey-$refreshKey") {
        remember {
            ArticlePresenter(
                accountType = accountType,
                articleKey = articleKey,
            )
        }.invoke()
    }
    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val downloadScope = koinInject<CoroutineScope>()
    val videoDownloadHelper = koinInject<VideoDownloadHelper>()
    val article = state.article.takeSuccess()
    val articleTitle = article?.title
    var titleHeightPx by remember(article?.key) { mutableIntStateOf(0) }
    val sourceUrl = article?.sourceUrl?.takeIf { it.isNotBlank() }
    val hasCover = article?.cover != null
    val appBarBottomPx =
        with(LocalDensity.current) {
            TopAppBarDefaults.TopAppBarExpandedHeight.toPx() +
                WindowInsets.statusBars.getTop(this)
        }
    val titleAppBarAlpha by remember(
        article?.key,
        articleTitle,
        hasCover,
        listState,
        titleHeightPx,
        appBarBottomPx,
    ) {
        derivedStateOf {
            if (articleTitle == null) {
                0f
            } else {
                val headerIndex = if (hasCover) 1 else 0
                val headerItem =
                    listState.layoutInfo.visibleItemsInfo.firstOrNull {
                        it.key == ARTICLE_HEADER_KEY
                    }
                headerItem?.offset?.let { offset ->
                    if (titleHeightPx > 0) {
                        ((appBarBottomPx - offset) / titleHeightPx).coerceIn(0f, 1f)
                    } else {
                        if (offset < appBarBottomPx) {
                            1f
                        } else {
                            0f
                        }
                    }
                } ?: if (listState.firstVisibleItemIndex > headerIndex) {
                    1f
                } else {
                    0f
                }
            }
        }
    }
    val coverScrollRangePx =
        with(LocalDensity.current) {
            (
                ArticleCoverHeight.toPx() -
                    TopAppBarDefaults.TopAppBarExpandedHeight.toPx() -
                    WindowInsets.statusBars.getTop(this)
            ).coerceAtLeast(1f)
        }
    val appBarAlpha by remember(hasCover, listState, coverScrollRangePx) {
        derivedStateOf {
            if (!hasCover) {
                1f
            } else if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / coverScrollRangePx).coerceIn(0f, 1f)
            }
        }
    }
    val color =
        if (isLightTheme()) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.background
        }
    val appBarColor =
        if (hasCover) {
            color.copy(alpha = appBarAlpha)
        } else {
            color
        }
    FlareScaffold(
        containerColor = color,
        topBar = {
            FlareTopAppBar(
                title = {
                    if (titleAppBarAlpha > 0f && articleTitle != null) {
                        Text(
                            text = articleTitle,
                            modifier =
                                Modifier.graphicsLayer {
                                    alpha = titleAppBarAlpha
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = appBarColor,
                        scrolledContainerColor = appBarColor,
                        navigationIconContentColor = MaterialTheme.colorScheme.primary,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                actions = {
                    Row(
                        modifier =
                            Modifier.background(
                                color,
                                MaterialTheme.shapes.medium,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            enabled = sourceUrl != null,
                            onClick = {
                                sourceUrl?.let(uriHandler::openUri)
                            },
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Brands.Chrome,
                                contentDescription = stringResource(R.string.rss_detail_open_in_browser),
                            )
                        }
                        IconButton(
                            enabled = sourceUrl != null,
                            onClick = {
                                sourceUrl?.let {
                                    navigate(
                                        Route.Status.ShareSheet(
                                            statusKey = articleKey,
                                            accountType = accountType,
                                            shareUrl = it,
                                        ),
                                    )
                                }
                            },
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.ShareNodes,
                                contentDescription = stringResource(R.string.rss_detail_share),
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        when (val articleState = state.article) {
            is UiState.Success -> {
                ArticleSuccessContent(
                    article = articleState.data,
                    contentPadding = contentPadding,
                    listState = listState,
                    onProfileClick = { profile ->
                        profile.onClicked(
                            ClickContext(
                                launcher = uriHandler::openUri,
                            ),
                        )
                    },
                    onTitleMeasured = {
                        titleHeightPx = it
                    },
                    onOpenUrl = uriHandler::openUri,
                    onDownloadFile = { file ->
                        downloadArticleFile(
                            block = file,
                            context = context,
                            scope = downloadScope,
                            videoDownloadHelper = videoDownloadHelper,
                        )
                    },
                    onOpenMedia = { media ->
                        val articleMedias = articleState.data.articleMedias()
                        val index =
                            articleMedias.indexOf(media).takeIf { it >= 0 }
                                ?: articleMedias.indexOfFirst { it.url == media.url }.coerceAtLeast(0)
                        navigate(
                            Route.Media.RawMedia(
                                medias = articleMedias,
                                index = index,
                                preview = media.previewUrl(),
                            ),
                        )
                    },
                )
            }

            is UiState.Loading -> {
                ArticleLoadingContent(
                    contentPadding = contentPadding,
                    listState = listState,
                )
            }

            is UiState.Error -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorContent(
                        error = articleState.throwable,
                        onRetry = {
                            refreshKey++
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleSuccessContent(
    article: UiArticle,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onProfileClick: (UiProfile) -> Unit,
    onTitleMeasured: (Int) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDownloadFile: (UiArticleBlock.File) -> Unit,
    onOpenMedia: (UiMedia) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val listContentPadding =
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top =
                if (article.cover == null) {
                    contentPadding.calculateTopPadding()
                } else {
                    0.dp
                },
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding(),
        )
    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = listContentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            article.cover?.let { cover ->
                item(key = ARTICLE_COVER_KEY) {
                    ArticleCover(
                        cover = cover,
                        title = article.title,
                        onOpenMedia = onOpenMedia,
                    )
                }
            }
            item(key = ARTICLE_HEADER_KEY) {
                ArticleBodyContainer {
                    ArticleHeader(
                        article = article,
                        onProfileClick = onProfileClick,
                        onTitleMeasured = onTitleMeasured,
                    )
                }
            }
            item(key = "divider") {
                ArticleBodyContainer {
                    HorizontalDivider()
                }
            }
            items(
                items = article.content.blocks,
                key = UiArticleBlock::key,
            ) { block ->
                ArticleBodyContainer {
                    ArticleBlock(
                        block = block,
                        onOpenUrl = onOpenUrl,
                        onDownloadFile = onDownloadFile,
                        onOpenMedia = onOpenMedia,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleCover(
    cover: UiMedia.Image,
    title: String,
    onOpenMedia: (UiMedia) -> Unit,
) {
    NetworkImage(
        model = cover.url,
        contentDescription = title,
        customHeaders = cover.customHeaders,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(ArticleCoverHeight)
                .clickable {
                    onOpenMedia(cover)
                },
    )
}

@Composable
private fun ArticleHeader(
    article: UiArticle,
    onProfileClick: (UiProfile) -> Unit,
    onTitleMeasured: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = article.title,
            style = MaterialTheme.typography.headlineMedium,
            modifier =
                Modifier.onSizeChanged {
                    onTitleMeasured(it.height)
                },
        )
        when (val author = article.author) {
            is UiArticleAuthor.Profile -> {
                CommonStatusHeaderComponent(
                    data = author.profile,
                    onUserClick = {
                        onProfileClick(author.profile)
                    },
                    trailing = {
                        article.publishDate?.let {
                            DateTimeText(
                                data = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fullTime = true,
                            )
                        }
                    },
                )
            }

            is UiArticleAuthor.Rss -> {
                ArticleRssAuthor(
                    author = author,
                    sourceUrl = article.sourceUrl,
                    publishDate = article.publishDate,
                )
            }

            null -> {
                article.publishDate?.let {
                    DateTimeText(
                        data = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fullTime = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleRssAuthor(
    author: UiArticleAuthor.Rss,
    sourceUrl: String?,
    publishDate: UiDateTime?,
) {
    if (author.siteName == null && author.byline == null && publishDate == null) {
        return
    }
    val host =
        remember(sourceUrl) {
            sourceUrl?.let {
                runCatching { Url(it).host }.getOrNull()
            }
        }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        author.siteName?.let { siteName ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (host != null) {
                    FavIcon(
                        host = host,
                        size = 16.dp,
                    )
                } else {
                    author.iconUrl?.let {
                        NetworkImage(
                            model = it,
                            contentDescription = siteName,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = siteName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            author.byline?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            publishDate?.let {
                DateTimeText(
                    data = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fullTime = true,
                )
            }
        }
    }
}

@Composable
private fun ArticleBlock(
    block: UiArticleBlock,
    onOpenUrl: (String) -> Unit,
    onDownloadFile: (UiArticleBlock.File) -> Unit,
    onOpenMedia: (UiMedia) -> Unit,
) {
    when (block) {
        is UiArticleBlock.Text -> {
            RichText(
                text = block.richText,
                overflow = TextOverflow.Clip,
            )
        }

        is UiArticleBlock.Image -> {
            ArticleImageBlock(
                media = block.media,
                onOpenMedia = onOpenMedia,
            )
        }

        is UiArticleBlock.Video -> {
            ArticleVideoBlock(
                media = block.media,
                onOpenMedia = onOpenMedia,
            )
        }

        is UiArticleBlock.File -> {
            ArticleFileBlock(
                block = block,
                onDownloadFile = onDownloadFile,
            )
        }

        is UiArticleBlock.Embed -> {
            ArticleEmbedBlock(
                block = block,
                onOpenUrl = onOpenUrl,
            )
        }

        is UiArticleBlock.ContentGate -> {
            ArticleContentGateBlock(
                block = block,
                onOpenUrl = onOpenUrl,
            )
        }
    }
}

@Composable
private fun ArticleImageBlock(
    media: UiMedia.Image,
    onOpenMedia: (UiMedia) -> Unit,
) {
    NetworkImage(
        model = media.url,
        contentDescription = media.description,
        customHeaders = media.customHeaders,
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(media.aspectRatio.coerceIn(0.2f, 4f))
                .clip(MaterialTheme.shapes.medium)
                .clickable {
                    onOpenMedia(media)
                },
    )
}

@Composable
private fun ArticleVideoBlock(
    media: UiMedia.Video,
    onOpenMedia: (UiMedia) -> Unit,
) {
    ElevatedCard(
        onClick = {
            onOpenMedia(media)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(media.aspectRatio.coerceIn(0.2f, 4f)),
            contentAlignment = Alignment.Center,
        ) {
            VideoPlayer(
                uri = media.url,
                customHeaders = media.customHeaders,
                previewUri = media.thumbnailUrl,
                contentDescription = media.description,
                modifier = Modifier.fillMaxSize(),
                muted = false,
                showControls = true,
                keepScreenOn = false,
                aspectRatio = media.aspectRatio.coerceIn(0.2f, 4f),
                contentScale = ContentScale.Fit,
                onClick = {
                    onOpenMedia(media)
                },
                autoPlay = false,
            )
        }
    }
}

@Composable
private fun ArticleFileBlock(
    block: UiArticleBlock.File,
    onDownloadFile: (UiArticleBlock.File) -> Unit,
) {
    ElevatedCard(
        onClick = {
            onDownloadFile(block)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FAIcon(
                FontAwesomeIcons.Solid.File,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = block.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                block.extension?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FAIcon(
                FontAwesomeIcons.Solid.Download,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ArticleEmbedBlock(
    block: UiArticleBlock.Embed,
    onOpenUrl: (String) -> Unit,
) {
    val url = block.url
    if (url == null) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            ArticleEmbedBlockContent(block = block)
        }
    } else {
        ElevatedCard(
            onClick = {
                onOpenUrl(url)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            ArticleEmbedBlockContent(block = block)
        }
    }
}

@Composable
private fun ArticleEmbedBlockContent(block: UiArticleBlock.Embed) {
    Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        block.imageUrl?.let {
            NetworkImage(
                model = it,
                contentDescription = block.title,
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.small),
            )
        } ?: FAIcon(
            FontAwesomeIcons.Solid.Globe,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = block.title ?: block.url ?: block.htmlFallback.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            block.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            block.url?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun UiArticle.articleMedias(): ImmutableList<UiMedia> =
    buildList {
        cover?.let(::add)
        content.blocks.forEach { block ->
            when (block) {
                is UiArticleBlock.Image -> add(block.media)
                is UiArticleBlock.Video -> add(block.media)
                else -> Unit
            }
        }
    }.toImmutableList()

private fun UiMedia.previewUrl(): String? =
    when (this) {
        is UiMedia.Audio -> previewUrl
        is UiMedia.Gif -> previewUrl
        is UiMedia.Image -> previewUrl
        is UiMedia.Video -> thumbnailUrl
    }

@Composable
private fun ArticleContentGateBlock(
    block: UiArticleBlock.ContentGate,
    onOpenUrl: (String) -> Unit,
) {
    val description =
        when (val reason = block.reason) {
            is UiArticleContentGateReason.SubscriptionRequired -> {
                reason.feeRequired?.let {
                    stringResource(R.string.article_content_gate_subscription_description_with_fee, it)
                } ?: stringResource(R.string.article_content_gate_subscription_description)
            }
        }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.Lock,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.article_content_gate_subscription_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                block.actionUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    FilledTonalButton(
                        onClick = {
                            onOpenUrl(url)
                        },
                    ) {
                        Text(text = stringResource(R.string.rss_detail_open_in_browser))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleLoadingContent(
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "loading-header") {
            ArticleBodyContainer {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .placeholder(true),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(AvatarComponentDefaults.size)
                                    .clip(MaterialTheme.shapes.medium)
                                    .placeholder(true),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.5f)
                                        .height(14.dp)
                                        .placeholder(true),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.35f)
                                        .height(12.dp)
                                        .placeholder(true),
                            )
                        }
                    }
                }
            }
        }
        item(key = "loading-divider") {
            ArticleBodyContainer {
                HorizontalDivider()
            }
        }
        items(5) { index ->
            ArticleBodyContainer {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(if (index == 0) 4 else 3) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(if (it == 2) 0.8f else 1f)
                                    .height(16.dp)
                                    .placeholder(true),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleBodyContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = screenHorizontalPadding),
            contentAlignment = Alignment.TopStart,
        ) {
            content()
        }
    }
}

private fun downloadArticleFile(
    block: UiArticleBlock.File,
    context: Context,
    scope: CoroutineScope,
    videoDownloadHelper: VideoDownloadHelper,
) {
    scope.launch {
        runCatching {
            videoDownloadHelper.downloadVideo(
                uri = block.url,
                fileName =
                    MediaFileNamePolicy.articleFileName(
                        name = block.name,
                        url = block.url,
                        extensionName = block.extension,
                    ),
                customHeaders = block.customHeaders,
                callback =
                    object : VideoDownloadHelper.DownloadCallback {
                        override fun onDownloadStarted(downloadId: Long) {
                            context.showArticleDownloadToast(R.string.media_download_started)
                        }

                        override fun onDownloadSuccess(downloadId: Long) {
                            context.showArticleDownloadToast(R.string.media_save_success)
                        }

                        override fun onDownloadFailed(downloadId: Long) {
                            context.showArticleDownloadToast(R.string.media_save_fail)
                        }
                    },
            )
        }.onFailure {
            withContext(Dispatchers.Main) {
                context.showArticleDownloadToast(R.string.media_save_fail)
            }
        }
    }
}

private fun Context.showArticleDownloadToast(messageRes: Int) {
    Toast
        .makeText(this, getString(messageRes), Toast.LENGTH_SHORT)
        .show()
}
