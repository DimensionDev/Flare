package dev.dimension.flare.ui.presenter.home.mastodon

import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public class MastodonLocalTimelinePresenter(
    private val accountType: AccountType,
) : TimelinePresenter() {
    private val accountService: AccountService by koinInject()

    override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
        accountService.accountServiceFlow(accountType).map { service ->
            require(service is MastodonDataSource)
            service.publicTimelineLoader(local = true)
        }
    }
}
