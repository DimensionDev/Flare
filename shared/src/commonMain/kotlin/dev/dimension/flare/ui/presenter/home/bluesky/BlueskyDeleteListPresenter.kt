package dev.dimension.flare.ui.presenter.home.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase

class BlueskyDeleteListPresenter(
    private val accountType: AccountType,
    private val listUri: String,
) : PresenterBase<BlueskyDeleteListState>() {
    @Composable
    override fun body(): BlueskyDeleteListState {
        val serviceState = accountServiceProvider(accountType = accountType)
        return object : BlueskyDeleteListState {
            override suspend fun deleteList() {
                serviceState.onSuccess {
                    require(it is BlueskyDataSource)
                    it.deleteList(listUri)
                }
            }
        }
    }
}

@Immutable
interface BlueskyDeleteListState {
    suspend fun deleteList()
}
