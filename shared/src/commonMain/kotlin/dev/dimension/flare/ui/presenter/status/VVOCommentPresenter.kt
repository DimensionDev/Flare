package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class VVOCommentPresenter(
    private val accountType: AccountType,
    private val commentKey: MicroBlogKey,
) : PresenterBase<VVOCommentState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): VVOCommentState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val root =
            serviceState
                .flatMap { service ->
                    remember(commentKey, accountType) {
                        require(service is VVODataSource)
                        service.comment(commentKey)
                    }.collectAsState().toUi()
                }
        val list =
            serviceState
                .map { service ->
                    remember(service) {
                        require(service is VVODataSource)
                        service.commentChild(scope = scope, commentKey = commentKey)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
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
    public val root: UiState<UiTimeline>
    public val list: PagingState<UiTimeline>

    public suspend fun refresh()
}
