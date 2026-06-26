package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.Pager
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import dev.dimension.flare.di.koinInject

@OptIn(ExperimentalCoroutinesApi::class)
public class VVOCommentPresenter(
    private val accountType: AccountType,
    private val commentKey: MicroBlogKey,
) : PresenterBase<VVOCommentState>() {
    private val accountService: AccountService by koinInject()
    private val serviceFlow by lazy {
        accountService.accountServiceFlow(accountType)
    }
    private val rootFlow by lazy {
        serviceFlow.flatMapLatest { service ->
            require(service is VVODataSource)
            service.comment(commentKey).toUi().map { state ->
                state.map {
                    if (it is UiTimelineV2.Post) {
                        it.copy(
                            quote = persistentListOf(),
                        )
                    } else {
                        it
                    }
                }
            }
        }
    }
    private val listFlow by lazy {
        serviceFlow.flatMapLatest { service ->
            require(service is VVODataSource)
            Pager(config = pagingConfig) {
                service.commentChild(commentKey = commentKey).toPagingSource()
            }.flow
        }
    }

    @Composable
    override fun body(): VVOCommentState {
        val scope = rememberCoroutineScope()
        val root by rootFlow.flattenUiState()
        val list =
            remember {
                listFlow.cachedIn(scope)
            }.collectAsLazyPagingItems().toPagingState()
        return object : VVOCommentState {
            override val root = root
            override val list = list

            override suspend fun refresh() {
                list.onSuccess {
                    refreshSuspend()
                }
            }
        }
    }
}

@Immutable
public interface VVOCommentState {
    public val root: UiState<UiTimelineV2>
    public val list: PagingState<UiTimelineV2>

    public suspend fun refresh()
}
