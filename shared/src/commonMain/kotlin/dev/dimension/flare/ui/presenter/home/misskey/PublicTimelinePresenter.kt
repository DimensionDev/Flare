package dev.dimension.flare.ui.presenter.home.misskey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.collectPagingProxy
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.home.TimelinePresenter

class PublicTimelinePresenter(
    private val accountType: AccountType,
) : TimelinePresenter() {
    @Composable
    override fun listState(): UiState<LazyPagingItemsProxy<UiStatus>> {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType)
        return serviceState.map { service ->
            remember(service) {
                require(service is MisskeyDataSource)
                service.publicTimeline(scope = scope)
            }.collectPagingProxy()
        }
    }
}
