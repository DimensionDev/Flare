package dev.dimension.flare.ui.presenter.home.misskey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.map
import kotlinx.coroutines.CoroutineScope

public class MisskeyFavoriteChannelsPresenter(
    accountType: AccountType,
) : MisskeyBaseChannelPresenter(accountType) {
    @Composable
    internal override fun getPagingData(
        scope: CoroutineScope,
        serviceState: UiState<MicroblogDataSource>,
    ): PagingState<UiList> =
        serviceState
            .map<MicroblogDataSource, LazyPagingItems<UiList>> { service ->
                require(service is MisskeyDataSource)
                remember(service) {
                    service.myFavoriteChannelHandler.data.cachedIn(scope)
                }.collectAsLazyPagingItems()
            }.toPagingState()
}
