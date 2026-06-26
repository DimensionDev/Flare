package dev.dimension.flare.ui.presenter.home.vvo

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dev.dimension.flare.di.koinInject

public class VVOLikeTimelinePresenter(
    private val accountType: AccountType,
) : TimelinePresenter() {
    private val accountService: AccountService by koinInject()

    override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
        accountService.accountServiceFlow(accountType).map { service ->
            require(service is VVODataSource)
            service.likeRemoteMediator()
        }
    }
}
