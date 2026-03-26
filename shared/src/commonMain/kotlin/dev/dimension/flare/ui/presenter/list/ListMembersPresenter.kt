package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.emptyFlow
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.datasource.ListDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Immutable
public interface ListMembersState {
    public val memberInfo: PagingState<UiProfile>
}

public class ListMembersPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<ListMembersState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val membersFlow by lazy {
        accountServiceFlow(accountType, accountRepository).flatMapLatest { service ->
            if (service is ListDataSource) {
                service.listMemberHandler.listMembers(listId)
            } else {
                PagingData.emptyFlow<UiProfile>()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): ListMembersState {
        val scope = rememberCoroutineScope()
        val memberInfo =
            remember {
                membersFlow.cachedIn(scope)
            }.collectAsLazyPagingItems().toPagingState()

        return object : ListMembersState {
            override val memberInfo: PagingState<UiProfile> = memberInfo
        }
    }
}
