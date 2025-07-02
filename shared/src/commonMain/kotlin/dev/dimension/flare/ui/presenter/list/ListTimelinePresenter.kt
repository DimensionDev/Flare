package dev.dimension.flare.ui.presenter.list

import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Presenter for retrieving list timeline.
 */
public class ListTimelinePresenter(
    private val accountType: AccountType,
    private val listId: String,
) : TimelinePresenter(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    override val loader: Flow<BaseTimelineLoader> by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).map { service ->
            require(service is ListDataSource)
            service.listTimeline(listId = listId)
        }
    }
}
