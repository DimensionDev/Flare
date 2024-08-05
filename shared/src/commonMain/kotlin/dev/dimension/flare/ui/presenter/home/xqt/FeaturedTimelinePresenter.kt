package dev.dimension.flare.ui.presenter.home.xqt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.home.TimelinePresenter

class FeaturedTimelinePresenter(
    private val accountType: AccountType,
) : TimelinePresenter() {
    @Composable
    override fun listState(): PagingState<UiTimeline> {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType)
        return serviceState
            .map { service ->
                remember(service) {
                    require(service is XQTDataSource)
                    service.featuredTimeline(scope = scope)
                }.collectAsLazyPagingItems()
            }.toPagingState()
    }
}
