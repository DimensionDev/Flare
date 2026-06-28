package dev.dimension.flare.ui.presenter.list

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public class ChannelTimelinePresenter(
    private val accountType: AccountType,
    private val id: String,
) : TimelinePresenter() {
    private val accountService: AccountService by koinInject()

    override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
        accountService.accountServiceFlow(accountType).map { service ->
            require(service is MisskeyDataSource)
            service.channelTimelineLoader(id)
        }
    }
}
