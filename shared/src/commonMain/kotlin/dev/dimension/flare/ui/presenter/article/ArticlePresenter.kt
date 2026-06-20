package dev.dimension.flare.ui.presenter.article

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.datasource.microblog.datasource.ArticleDataSource
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.STATUS_HISTORY_PAGING_KEY
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
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

public class ArticlePresenter(
    private val accountType: AccountType,
    private val articleKey: MicroBlogKey,
) : PresenterBase<ArticlePresenter.State>(),
    KoinComponent {
    private val accountService: AccountService by inject()
    private val cacheDatabase: CacheDatabase by inject()

    private val articleStateFlow by lazy {
        accountService
            .accountServiceFlow(accountType)
            .map { service ->
                require(service is ArticleDataSource)
                service.article(articleKey)
            }
    }

    @Immutable
    public interface State {
        public val article: UiState<UiArticle>
    }

    @Composable
    override fun body(): State {
        val articleState by articleStateFlow.collectAsUiState()
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
        }
    }
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
