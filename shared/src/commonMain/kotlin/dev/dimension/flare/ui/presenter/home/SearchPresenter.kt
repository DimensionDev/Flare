package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class SearchPresenter(
    private val accountType: AccountType,
    private val initialQuery: String = "",
) : PresenterBase<SearchState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): SearchState {
        val scope = rememberCoroutineScope()
        val accountState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        var query by remember { mutableStateOf(initialQuery) }

        val user =
            accountState
                .flatMap { service ->
                    if (query.isEmpty()) {
                        UiState.Error(IllegalStateException("Query is empty"))
                    } else {
                        UiState.Success(
                            remember(service, query) {
                                service.searchUser(query, scope = scope)
                            }.collectAsLazyPagingItems(),
                        )
                    }
                }.toPagingState()

        val status =
            remember {
                SearchStatusTimelinePresenter(
                    accountType = accountType,
                    initialQuery = query,
                )
            }
        val stateState = status.body().listState
        LaunchedEffect(query) {
            status.setQuery(query)
        }

        val isSearching = query.isNotEmpty()

        return object : SearchState {
            override val users = user
            override val status = stateState
            override val searching = isSearching

            override fun search(new: String) {
                query = new
            }
        }
    }
}

@Immutable
public interface SearchState {
    public val users: PagingState<UiUserV2>
    public val status: PagingState<UiTimeline>
    public val searching: Boolean

    public fun search(new: String)
}
