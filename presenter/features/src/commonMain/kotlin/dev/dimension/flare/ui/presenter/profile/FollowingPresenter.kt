package dev.dimension.flare.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.paging.Pager
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public abstract class UserListPresenter(
    private val accountType: AccountType,
) : PresenterBase<UserListPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Immutable
    public interface State {
        public val listState: PagingState<UiProfile>

        public suspend fun refreshSuspend()
    }

    @Composable
    override fun body(): State {
        val service = accountServiceProvider(accountType, repository = accountRepository)
        val listState =
            service
                .map {
                    remember(it) {
                        Pager(config = pagingConfig) {
                            dataSource(it).toPagingSource()
                        }.flow
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

    internal abstract fun dataSource(service: MicroblogDataSource): RemoteLoader<UiProfile>
}

public class FollowingPresenter(
    accountType: AccountType,
    private val userKey: MicroBlogKey,
) : UserListPresenter(accountType) {
    override fun dataSource(service: MicroblogDataSource) = service.following(userKey)
}

public class FansPresenter(
    accountType: AccountType,
    private val userKey: MicroBlogKey,
) : UserListPresenter(accountType) {
    override fun dataSource(service: MicroblogDataSource) = service.fans(userKey)
}
