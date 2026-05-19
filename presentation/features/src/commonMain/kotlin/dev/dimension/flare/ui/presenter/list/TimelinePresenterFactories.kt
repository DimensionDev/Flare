package dev.dimension.flare.ui.presenter.list

import dev.dimension.flare.data.datasource.microblog.datasource.ListDataSource
import dev.dimension.flare.data.platform.MisskeyTimelineDataSource
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.presenter.home.AccountTimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelinePresenter

public fun createListTimeline(
    accountType: AccountType,
    listId: String,
): TimelinePresenter =
    AccountTimelinePresenter(accountType) { service ->
        require(service is ListDataSource)
        service.listTimeline(listId = listId)
    }

public fun createMisskeyAntennaTimeline(
    accountType: AccountType,
    id: String,
): TimelinePresenter =
    AccountTimelinePresenter(accountType) { service ->
        require(service is MisskeyTimelineDataSource)
        service.antennasTimelineLoader(id)
    }

public fun createMisskeyChannelTimeline(
    accountType: AccountType,
    id: String,
): TimelinePresenter =
    AccountTimelinePresenter(accountType) { service ->
        require(service is MisskeyTimelineDataSource)
        service.channelTimelineLoader(id)
    }
