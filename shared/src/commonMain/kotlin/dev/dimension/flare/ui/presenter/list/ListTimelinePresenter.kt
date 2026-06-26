package dev.dimension.flare.ui.presenter.list

import dev.dimension.flare.data.datasource.microblog.datasource.ListDataSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dev.dimension.flare.di.koinInject

/**
 * Presenter for retrieving list timeline.
 */
public class ListTimelinePresenter(
    private val accountType: AccountType,
    private val listId: String,
) : TimelinePresenter() {
    private val accountRepository: AccountRepository by koinInject()

    override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).map { service ->
            require(service is ListDataSource)
            service.listTimeline(listId = listId)
        }
    }
}
