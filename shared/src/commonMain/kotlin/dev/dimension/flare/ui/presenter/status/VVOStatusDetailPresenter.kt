package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase

class VVOStatusDetailPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<VVOStatusDetailState>() {
    @Composable
    override fun body(): VVOStatusDetailState {
        val service = accountServiceProvider(accountType)
        val status =
            service
                .flatMap {
                    remember(statusKey, accountType) {
                        it.status(statusKey)
                    }.collectAsState().toUi()
                }.map {
                    it as UiStatus.VVO
                }

        val extendedText =
            service.flatMap {
                require(it is VVODataSource)
                remember(statusKey, accountType) {
                    it.statusExtendedText(statusKey)
                }.collectAsState(UiState.Loading()).value
            }

        val actualStatus =
            status.map { item ->
                when (extendedText) {
                    is UiState.Error -> item
                    is UiState.Loading -> item
                    is UiState.Success -> {
                        item.copy(content = extendedText.data)
                    }
                }
            }

        val repost =
            service.map {
                require(it is VVODataSource)
                remember(statusKey, accountType) {
                    it.statusRepost(statusKey)
                }.collectAsLazyPagingItems()
            }

        val comment =
            service.map {
                require(it is VVODataSource)
                remember(statusKey, accountType) {
                    it.statusComment(statusKey)
                }.collectAsLazyPagingItems()
            }

        return object : VVOStatusDetailState {
            override val status = actualStatus
            override val comment = comment
            override val repost = repost
        }
    }
}

@Immutable
interface VVOStatusDetailState {
    val status: UiState<UiStatus.VVO>
    val comment: UiState<LazyPagingItems<UiStatus.VVONotification>>
    val repost: UiState<LazyPagingItems<UiStatus.VVO>>
}
