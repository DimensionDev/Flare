package dev.dimension.flare.ui.presenter.dm

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiDMList
import dev.dimension.flare.ui.presenter.PresenterBase

class DMListPresenter(
    private val accountType: AccountType,
) : PresenterBase<DMListState>() {
    @Composable
    override fun body(): DMListState {
        TODO()
    }
}

interface DMListState {
    val items: PagingState<UiDMList>
    val isRefreshing: Boolean

    fun refresh()
}
