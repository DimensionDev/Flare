package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.Pager
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapper.renderVVOText
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
public class VVOStatusDetailPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<VVOStatusDetailState>() {
    private val accountService: AccountService by koinInject()
    private val serviceFlow by lazy {
        accountService.accountServiceFlow(accountType)
    }
    private val rawStatusFlow by lazy {
        serviceFlow.flatMapLatest { service ->
            require(service is PostDataSource)
            service.postHandler.post(statusKey).toUi()
        }
    }
    private val extendedTextFlow by lazy {
        serviceFlow.flatMapLatest { service ->
            require(service is VVODataSource)
            service.statusExtendedText(statusKey)
        }
    }
    private val statusFlow by lazy {
        combine(
            rawStatusFlow,
            extendedTextFlow,
            serviceFlow,
        ) { status, extendedText, service ->
            status.map { item ->
                if (extendedText is UiState.Success) {
                    require(service is VVODataSource)
                    item.withVvoExtendedText(
                        renderVVOText(
                            extendedText.data,
                            service.accountKey,
                        ),
                    )
                } else {
                    item
                }
            }
        }
    }
    private val repostFlow by lazy {
        serviceFlow.flatMapLatest { service ->
            require(service is VVODataSource)
            Pager(config = pagingConfig) {
                service.statusRepost(statusKey = statusKey).toPagingSource()
            }.flow.map { data ->
                data.map { item ->
                    item.withoutVvoQuotes()
                }
            }
        }
    }
    private val commentFlow by lazy {
        serviceFlow.flatMapLatest { service ->
            require(service is VVODataSource)
            Pager(config = pagingConfig) {
                service.statusComment(statusKey = statusKey).toPagingSource()
            }.flow
        }
    }

    @Composable
    override fun body(): VVOStatusDetailState {
        val scope = rememberCoroutineScope()
        val status by statusFlow.flattenUiState()
        remember { LogStatusHistoryPresenter(accountType = accountType, statusKey = statusKey) }.body()
        val repost =
            remember {
                repostFlow.cachedIn(scope)
            }.collectAsLazyPagingItems().toPagingState()
        val comment =
            remember {
                commentFlow.cachedIn(scope)
            }.collectAsLazyPagingItems().toPagingState()
        return object : VVOStatusDetailState {
            override val status = status
            override val comment = comment
            override val repost = repost

            override suspend fun refreshComment() {
                comment.refreshSuspend()
            }

            override suspend fun refreshRepost() {
                repost.refreshSuspend()
            }
        }
    }
}

@Immutable
public interface VVOStatusDetailState {
    public val status: UiState<UiTimelineV2>
    public val comment: PagingState<UiTimelineV2>
    public val repost: PagingState<UiTimelineV2>

    public suspend fun refreshComment()

    public suspend fun refreshRepost()
}

private fun UiTimelineV2.withoutVvoQuotes(): UiTimelineV2 {
    val item = asTimelinePostItem() ?: return this
    return item.copy(
        presentation =
            item.presentation.copy(
                quotes = persistentListOf(),
            ),
    )
}

private fun UiTimelineV2.withVvoExtendedText(originalContent: UiRichText): UiTimelineV2 =
    when (this) {
        is UiTimelineV2.Post -> {
            copy(content = content.copy(original = originalContent))
        }

        is UiTimelineV2.TimelinePostItem -> {
            val repost = presentation.repost
            if (repost != null) {
                copy(
                    presentation =
                        presentation.copy(
                            repost = repost.copy(content = repost.content.copy(original = originalContent)),
                        ),
                )
            } else {
                copy(post = post.copy(content = post.content.copy(original = originalContent)))
            }
        }

        else -> {
            this
        }
    }
