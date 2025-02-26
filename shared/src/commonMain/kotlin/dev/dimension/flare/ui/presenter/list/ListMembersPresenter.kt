package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ListMembersPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<ListMembersState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): ListMembersState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val memberInfo =
            serviceState
                .map {
                    remember(it, listId) {
                        require(it is ListDataSource)
                        it.listMembers(listId, scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()

        return object : ListMembersState {
            override val memberInfo = memberInfo
        }
    }
}

@Immutable
public interface ListMembersState {
    public val memberInfo: PagingState<UiUserV2>
}
