package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class SearchStatusTimelinePresenter(
    private val accountType: AccountType,
    private val initialQuery: String,
) : TimelinePresenter(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()
    private val queryFlow by lazy {
        MutableStateFlow(initialQuery)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val loader: Flow<BaseTimelineLoader> by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).flatMapLatest { service ->
            queryFlow.map { query ->
                if (query.isEmpty()) {
                    BaseTimelineLoader.NotSupported
                } else {
                    service.searchStatus(query)
                }
            }
        }
    }

    public fun setQuery(query: String) {
        queryFlow.value = query
    }
}
