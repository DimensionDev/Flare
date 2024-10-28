package dev.dimension.flare.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

abstract class UserListPresenter(
    private val accountType: AccountType,
) : PresenterBase<UserListPresenter.State>() {
    interface State {
        val listState: PagingState<UiUserV2>

        suspend fun refreshSuspend()
    }

    @Composable
    override fun body(): State {
        val service = accountServiceProvider(accountType)
        val scope = rememberCoroutineScope()
        val listState =
            service
                .map {
                    remember(it) {
                        dataSource(it, scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        return object : State {
            override val listState = listState

            override suspend fun refreshSuspend() {
                listState.onSuccess {
                    refreshSuspend()
                }
            }
        }
    }

    abstract fun dataSource(
        service: MicroblogDataSource,
        scope: CoroutineScope,
    ): Flow<PagingData<UiUserV2>>
}

class FollowingPresenter(
    accountType: AccountType,
    private val userKey: MicroBlogKey,
) : UserListPresenter(accountType) {
    override fun dataSource(
        service: MicroblogDataSource,
        scope: CoroutineScope,
    ) = service.following(userKey, scope)
}

class FansPresenter(
    accountType: AccountType,
    private val userKey: MicroBlogKey,
) : UserListPresenter(accountType) {
    override fun dataSource(
        service: MicroblogDataSource,
        scope: CoroutineScope,
    ) = service.fans(userKey, scope)
}
