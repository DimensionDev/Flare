package dev.dimension.flare.ui.presenter.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.emptyFlow
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.status.LogStatusHistoryPresenter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
public class GalleryDetailPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<GalleryDetailPresenter.State>() {
    private val accountRepository: AccountRepository by koinInject()

    private val serviceFlow by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        )
    }

    private val detailCacheFlow by lazy {
        serviceFlow.flatMapLatest { service ->
            val galleryDataSource =
                service as? GalleryDataSource
                    ?: error("Current service does not support gallery data source")
            galleryDataSource.galleryDetail(statusKey).toUi()
        }
    }

    private val commentsFlow by lazy {
        serviceFlow.flatMapLatest { service ->
            runCatching {
                val galleryDataSource =
                    service as? GalleryDataSource
                        ?: error("Current service does not support gallery data source")
                Pager(config = pagingConfig) {
                    galleryDataSource.galleryComments(statusKey).toPagingSource()
                }.flow
            }.getOrElse {
                PagingData.emptyFlow(isError = true)
            }
        }
    }

    private val recommendationsFlow by lazy {
        serviceFlow.flatMapLatest { service ->
            runCatching {
                val galleryDataSource =
                    service as? GalleryDataSource
                        ?: error("Current service does not support gallery data source")
                Pager(config = pagingConfig) {
                    galleryDataSource.galleryRecommendations(statusKey).toPagingSource()
                }.flow
            }.getOrElse {
                PagingData.emptyFlow(isError = true)
            }
        }
    }

    @Immutable
    public interface State {
        public val detail: UiState<GalleryDetail>
        public val comments: PagingState<UiTimelineV2>
        public val recommendations: PagingState<UiTimelineV2>
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val detail by detailCacheFlow.flattenUiState()
        val comments =
            remember(commentsFlow, scope) {
                commentsFlow.cachedIn(scope)
            }.collectAsLazyPagingItems()
                .toPagingState()
        val recommendations =
            remember(recommendationsFlow, scope) {
                recommendationsFlow.cachedIn(scope)
            }.collectAsLazyPagingItems()
                .toPagingState()

        remember { LogStatusHistoryPresenter(accountType = accountType, statusKey = statusKey) }.body()

        return object : State {
            override val detail: UiState<GalleryDetail> = detail
            override val comments: PagingState<UiTimelineV2> = comments
            override val recommendations: PagingState<UiTimelineV2> = recommendations
        }
    }
}
