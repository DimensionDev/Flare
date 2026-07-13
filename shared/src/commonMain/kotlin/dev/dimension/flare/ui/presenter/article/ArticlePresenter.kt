package dev.dimension.flare.ui.presenter.article

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.datasource.microblog.datasource.ArticleDataSource
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.STATUS_HISTORY_PAGING_KEY
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiArticle
import dev.dimension.flare.ui.model.UiArticleAuthor
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
public class ArticlePresenter(
    private val accountType: AccountType,
    private val articleKey: MicroBlogKey,
) : PresenterBase<ArticlePresenter.State>() {
    private val accountService: AccountService by koinInject()
    private val cacheDatabase: CacheDatabase by koinInject()

    private val serviceFlow by lazy {
        accountService.accountServiceFlow(accountType)
    }

    private val articleStateFlow by lazy {
        serviceFlow.map { service ->
            val articleDataSource =
                service as? ArticleDataSource
                    ?: error("Current service does not support article data source")
            articleDataSource.article(articleKey)
        }
    }

    private val commentsFlow by lazy {
        articleCommentsFlow(
            dataSources = serviceFlow,
            articleKey = articleKey,
        )
    }

    @Immutable
    public interface State {
        public val article: UiState<UiArticle>
        public val comments: PagingState<UiTimelineV2>
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val articleState by articleStateFlow.collectAsUiState()
        val comments =
            remember(commentsFlow, scope) {
                commentsFlow.cachedIn(scope)
            }.collectAsLazyPagingItems()
                .toPagingState()
        LaunchedEffect(accountType, articleKey, articleState) {
            articleState
                .takeSuccess()
                ?.toHistoryFeed(
                    accountType = accountType,
                    articleKey = articleKey,
                )?.let { feed ->
                    saveToDatabase(
                        database = cacheDatabase,
                        items =
                            listOf(
                                TimelinePagingMapper.toDb(
                                    data = feed,
                                    pagingKey = STATUS_HISTORY_PAGING_KEY,
                                    sortId = Clock.System.now().toEpochMilliseconds(),
                                ),
                            ),
                    )
                }
        }
        return object : State {
            override val article: UiState<UiArticle> = articleState
            override val comments: PagingState<UiTimelineV2> = comments
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun articleCommentsFlow(
    dataSources: Flow<Any>,
    articleKey: MicroBlogKey,
): Flow<PagingData<UiTimelineV2>> =
    dataSources.flatMapLatest { dataSource ->
        val articleDataSource =
            dataSource as? ArticleDataSource
                ?: error("Current service does not support article data source")
        Pager(config = pagingConfig) {
            articleDataSource.articleComments(articleKey).toPagingSource()
        }.flow
    }

private fun UiArticle.toHistoryFeed(
    accountType: AccountType,
    articleKey: MicroBlogKey,
): UiTimelineV2.Feed? {
    val url = sourceUrl?.takeIf { it.isNotBlank() } ?: return null
    return UiTimelineV2.Feed(
        title = title.takeIf { it.isNotBlank() },
        description = content.rawText.takeIf { it.isNotBlank() },
        url = url,
        createdAt = publishDate ?: Clock.System.now().toUi(),
        source = author.toHistoryFeedSource(fallbackName = url),
        media = cover,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Article(
                    accountType = accountType,
                    articleKey = articleKey,
                ),
            ),
        accountType = accountType,
    )
}

private fun UiArticleAuthor?.toHistoryFeedSource(fallbackName: String): UiTimelineV2.Feed.Source =
    when (this) {
        is UiArticleAuthor.Profile -> {
            UiTimelineV2.Feed.Source(
                name =
                    profile.name.raw.takeIf { it.isNotBlank() }
                        ?: profile.handle.raw.takeIf { it.isNotBlank() }
                        ?: fallbackName,
                icon = profile.avatar?.url,
            )
        }

        is UiArticleAuthor.Rss -> {
            UiTimelineV2.Feed.Source(
                name =
                    listOfNotNull(siteName, byline)
                        .firstOrNull { it.isNotBlank() }
                        ?: fallbackName,
                icon = iconUrl,
            )
        }

        null -> {
            UiTimelineV2.Feed.Source(
                name = fallbackName,
                icon = null,
            )
        }
    }
