package dev.dimension.flare.ui.presenter.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.Pager
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.postEventOrNull
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.status.LogStatusHistoryPresenter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class GalleryDetailPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<GalleryDetailPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Immutable
    public interface State {
        public val detail: UiState<GalleryDetail>
        public val comments: PagingState<UiTimelineV2>
        public val recommendations: PagingState<UiTimelineV2>

        public fun performAction(action: ActionMenu.Item)

        public fun refresh()
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val postCache =
            serviceState.map { service ->
                val postDataSource =
                    service as? PostDataSource
                        ?: error("Current service does not support post data source")
                remember(service, statusKey) {
                    postDataSource.postHandler.post(statusKey)
                }.collectAsState()
            }
        val detail =
            postCache.flatMap { cacheState ->
                cacheState
                    .toUi()
                    .map { it.toGalleryDetail() }
            }
        val comments =
            serviceState
                .map { service ->
                    val galleryDataSource =
                        service as? GalleryDataSource
                            ?: error("Current service does not support gallery data source")
                    remember(service, statusKey) {
                        Pager(config = pagingConfig) {
                            galleryDataSource.galleryComments(statusKey).toPagingSource()
                        }.flow
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        val recommendations =
            serviceState
                .map { service ->
                    val galleryDataSource =
                        service as? GalleryDataSource
                            ?: error("Current service does not support gallery data source")
                    remember(service, statusKey) {
                        Pager(config = pagingConfig) {
                            galleryDataSource.galleryRecommendations(statusKey).toPagingSource()
                        }.flow
                    }.collectAsLazyPagingItems()
                }.toPagingState()

        remember { LogStatusHistoryPresenter(accountType = accountType, statusKey = statusKey) }.body()

        return object : State {
            override val detail: UiState<GalleryDetail> = detail
            override val comments: PagingState<UiTimelineV2> = comments
            override val recommendations: PagingState<UiTimelineV2> = recommendations

            override fun performAction(action: ActionMenu.Item) {
                val event = action.clickEvent.postEventOrNull() ?: return
                scope.launch {
                    accountServiceFlow(
                        accountType = AccountType.Specific(event.accountKey),
                        repository = accountRepository,
                    ).firstOrNull()?.let { service ->
                        (service as? PostDataSource)?.postEventHandler?.handleEvent(event.postEvent)
                    }
                }
            }

            override fun refresh() {
                if (postCache is UiState.Success) {
                    postCache.data.refresh()
                }
                scope.launch {
                    comments
                        .onSuccess {
                            refreshSuspend()
                        }.onEmpty {
                            refresh()
                        }.onError {
                            onRetry()
                        }
                    recommendations
                        .onSuccess {
                            refreshSuspend()
                        }.onEmpty {
                            refresh()
                        }.onError {
                            onRetry()
                        }
                }
            }
        }
    }
}

private fun UiTimelineV2.toGalleryDetail(): GalleryDetail {
    val post = this as? UiTimelineV2.Post ?: error("Gallery detail should be a post")
    val firstImage = post.images.filterIsInstance<UiMedia.Image>().firstOrNull()
    return GalleryDetail(
        post = post,
        orientation =
            if ((firstImage?.aspectRatio ?: 1f) >= 1f) {
                GalleryOrientation.Horizontal
            } else {
                GalleryOrientation.Vertical
            },
    )
}
