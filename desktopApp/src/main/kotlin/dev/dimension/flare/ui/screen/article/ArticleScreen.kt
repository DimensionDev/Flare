package dev.dimension.flare.ui.screen.article

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
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
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.article_content_gate_subscription_description
import dev.dimension.flare.article_content_gate_subscription_description_with_fee
import dev.dimension.flare.article_content_gate_subscription_title
import dev.dimension.flare.common.DesktopDownloadManager
import dev.dimension.flare.common.DesktopSaveDialog
import dev.dimension.flare.common.MediaFileNamePolicy
import dev.dimension.flare.media_save
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.rss_detail_open_in_browser
import dev.dimension.flare.status_share
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.ErrorContent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FavIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.component.placeholder
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.model.UiArticle
import dev.dimension.flare.ui.model.UiArticleAuthor
import dev.dimension.flare.ui.model.UiArticleBlock
import dev.dimension.flare.ui.model.UiArticleContentGateReason
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.article.ArticlePresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.media.VideoItem
import dev.dimension.flare.ui.theme.LocalComposeWindow
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.ListItemSeparator
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val ArticleCoverHeight = 260.dp
private const val ARTICLE_COVER_KEY = "cover"
private const val ARTICLE_HEADER_KEY = "header"

@Composable
internal fun ArticleScreen(
    accountType: AccountType,
    articleKey: MicroBlogKey,
    navigate: (Route) -> Unit,
) {
    var refreshKey by remember(accountType, articleKey) { mutableIntStateOf(0) }
    val state by producePresenter("desktop_article_$accountType-$articleKey-$refreshKey") {
        remember(accountType, articleKey) {
            ArticlePresenter(
                accountType = accountType,
                articleKey = articleKey,
            )
        }.invoke()
    }
    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val window = LocalComposeWindow.current
    val downloadManager: DesktopDownloadManager = koinInject()
    val article = state.article.takeSuccess()
    val windowPadding = LocalWindowPadding.current
    val contentPadding =
        PaddingValues(
            start = windowPadding.calculateStartPadding(layoutDirection),
            top = windowPadding.calculateTopPadding() + 16.dp,
            end = windowPadding.calculateEndPadding(layoutDirection),
            bottom = windowPadding.calculateBottomPadding() + 16.dp,
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(FluentTheme.colors.background.card.default),
        contentAlignment = Alignment.TopCenter,
    ) {
        FlareScrollBar(state = listState) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    article?.let { currentArticle ->
                        currentArticle.sourceUrl?.takeIf { it.isNotBlank() }?.let { sourceUrl ->
                            item(key = "toolbar") {
                                ArticleBodyContainer {
                                    ArticleToolbar(
                                        sourceUrl = sourceUrl,
                                        article = currentArticle,
                                        onOpenUrl = uriHandler::openUri,
                                        onShare = {
                                            navigate(
                                                Route.StatusShareSheet(
                                                    accountType = accountType,
                                                    statusKey = articleKey,
                                                    shareUrl = sourceUrl,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    when (val articleState = state.article) {
                        is UiState.Success -> {
                            articleSuccessItems(
                                article = articleState.data,
                                accountType = accountType,
                                navigate = navigate,
                                onOpenUrl = uriHandler::openUri,
                                onDownloadFile = { file ->
                                    saveArticleFile(
                                        block = file,
                                        window = window,
                                        scope = scope,
                                        downloadManager = downloadManager,
                                    )
                                },
                            )
                        }

                        is UiState.Loading -> {
                            articleLoadingItems()
                        }

                        is UiState.Error -> {
                            item(key = "error") {
                                ArticleBodyContainer {
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
            }
        }
    }
}

@Composable
private fun ArticleToolbar(
    sourceUrl: String,
    article: UiArticle,
    onOpenUrl: (String) -> Unit,
    onShare: (UiArticle) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        SubtleButton(
            onClick = {
                onOpenUrl(sourceUrl)
            },
            iconOnly = true,
        ) {
            FAIcon(
                FontAwesomeIcons.Brands.Chrome,
                contentDescription = stringResource(Res.string.rss_detail_open_in_browser),
            )
        }
        SubtleButton(
            onClick = {
                onShare(article)
            },
            iconOnly = true,
        ) {
            FAIcon(
                FontAwesomeIcons.Solid.ShareNodes,
                contentDescription = stringResource(Res.string.status_share),
            )
        }
    }
}

private fun LazyListScope.articleSuccessItems(
    article: UiArticle,
    accountType: AccountType,
    navigate: (Route) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDownloadFile: (UiArticleBlock.File) -> Unit,
) {
    article.cover?.let { cover ->
        item(key = ARTICLE_COVER_KEY) {
            ArticleCover(
                cover = cover,
                title = article.title,
                onOpenMedia = { media ->
                    navigate(media.rawImageRoute())
                },
            )
        }
    }
    item(key = ARTICLE_HEADER_KEY) {
        ArticleBodyContainer {
            ArticleHeader(
                article = article,
                onProfileClick = { userKey ->
                    navigate(
                        Route.Profile(
                            accountType = accountType,
                            userKey = userKey,
                        ),
                    )
                },
            )
        }
    }
    item(key = "divider") {
        ArticleBodyContainer {
            ListItemSeparator(Modifier.fillMaxWidth())
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
                onOpenMedia = { media ->
                    navigate(media.rawImageRoute())
                },
            )
        }
    }
}

@Composable
private fun ArticleCover(
    cover: UiMedia.Image,
    title: String,
    onOpenMedia: (UiMedia.Image) -> Unit,
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
                .listCard()
                .clickable {
                    onOpenMedia(cover)
                },
    )
}

@Composable
private fun ArticleHeader(
    article: UiArticle,
    onProfileClick: (MicroBlogKey) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = article.title,
            style = FluentTheme.typography.title,
        )
        when (val author = article.author) {
            is UiArticleAuthor.Profile -> {
                CommonStatusHeaderComponent(
                    data = author.profile,
                    onUserClick = onProfileClick,
                    trailing = {
                        article.publishDate?.let {
                            DateTimeText(
                                data = it,
                                style = FluentTheme.typography.caption,
                                color = FluentTheme.colors.text.text.secondary,
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
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary,
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
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary,
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
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            publishDate?.let {
                DateTimeText(
                    data = it,
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary,
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
    onOpenMedia: (UiMedia.Image) -> Unit,
) {
    when (block) {
        is UiArticleBlock.Text -> {
            RichText(
                text = block.richText,
                modifier = Modifier.fillMaxWidth(),
                overflow = TextOverflow.Clip,
                textStyle = FluentTheme.typography.body,
            )
        }

        is UiArticleBlock.Image -> {
            ArticleImageBlock(
                media = block.media,
                onOpenMedia = onOpenMedia,
            )
        }

        is UiArticleBlock.Video -> {
            ArticleVideoBlock(media = block.media)
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
    onOpenMedia: (UiMedia.Image) -> Unit,
) {
    NetworkImage(
        model = media.url,
        contentDescription = media.description,
        customHeaders = media.customHeaders,
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(media.aspectRatio.coerceIn(0.2f, 4f))
                .listCard()
                .clickable {
                    onOpenMedia(media)
                },
    )
}

@Composable
private fun ArticleVideoBlock(media: UiMedia.Video) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(media.aspectRatio.coerceIn(0.2f, 4f)),
            contentAlignment = Alignment.Center,
        ) {
            VideoItem(
                url = media.url,
                thumbnailUrl = media.thumbnailUrl,
                description = media.description,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ArticleFileBlock(
    block: UiArticleBlock.File,
    onDownloadFile: (UiArticleBlock.File) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onDownloadFile(block)
                },
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
                    style = FluentTheme.typography.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                block.extension?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it.uppercase(),
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary,
                    )
                }
            }
            FAIcon(
                FontAwesomeIcons.Solid.Download,
                contentDescription = stringResource(Res.string.media_save),
                modifier = Modifier.size(18.dp),
                tint = FluentTheme.colors.text.text.secondary,
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
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .let {
                    if (url == null) {
                        it
                    } else {
                        it.clickable {
                            onOpenUrl(url)
                        }
                    }
                },
    ) {
        ArticleEmbedBlockContent(block = block)
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
                        .clip(FluentTheme.shapes.control),
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
                style = FluentTheme.typography.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            block.description?.let {
                Text(
                    text = it,
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            block.url?.let {
                Text(
                    text = it,
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.accent.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
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
                    stringResource(Res.string.article_content_gate_subscription_description_with_fee, it)
                } ?: stringResource(Res.string.article_content_gate_subscription_description)
            }
        }
    Card(
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
                tint = FluentTheme.colors.text.accent.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.article_content_gate_subscription_title),
                    style = FluentTheme.typography.bodyStrong,
                )
                Text(
                    text = description,
                    style = FluentTheme.typography.body,
                    color = FluentTheme.colors.text.text.secondary,
                )
                block.actionUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    AccentButton(
                        onClick = {
                            onOpenUrl(url)
                        },
                    ) {
                        Text(text = stringResource(Res.string.rss_detail_open_in_browser))
                    }
                }
            }
        }
    }
}

private fun LazyListScope.articleLoadingItems() {
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
                                .size(44.dp)
                                .clip(FluentTheme.shapes.control)
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
            ListItemSeparator(Modifier.fillMaxWidth())
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

private fun saveArticleFile(
    block: UiArticleBlock.File,
    window: ComposeWindow?,
    scope: CoroutineScope,
    downloadManager: DesktopDownloadManager,
) {
    val targetFile =
        DesktopSaveDialog.chooseFile(
            window = window,
            defaultName =
                MediaFileNamePolicy.articleFileName(
                    name = block.name,
                    url = block.url,
                    extensionName = block.extension,
                ),
        ) ?: return
    scope.launch {
        downloadManager.download(
            url = block.url,
            targetFile = targetFile,
            customHeaders = block.customHeaders,
        )
    }
}

private fun UiMedia.Image.rawImageRoute(): Route.RawImage =
    Route.RawImage(
        rawImage = url,
        customHeaders = customHeaders,
    )
