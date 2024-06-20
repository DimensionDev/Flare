package dev.dimension.flare.ui.presenter.home.xqt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.home.TimelinePresenter

class BookmarkTimelinePresenter(
    private val accountType: AccountType,
) : TimelinePresenter() {
    @Composable
    override fun listState(): UiState<LazyPagingItems<UiStatus>> {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType)
        return serviceState.map { service ->
            remember(service) {
                require(service is XQTDataSource)
                service.bookmarkTimeline(scope = scope)
            }.collectAsLazyPagingItems()
        }
    }
}
