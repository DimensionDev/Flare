package dev.dimension.flare.ui.presenter.home.mastodon

import androidx.compose.runtime.Composable
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase

class DeleteListPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<DeleteListState>() {
    @Composable
    override fun body(): DeleteListState {
        val serviceState = accountServiceProvider(accountType = accountType)

        return object : DeleteListState {
            override suspend fun deleteList() {
                serviceState.onSuccess {
                    require(it is MastodonDataSource)
                    it.deleteList(listId)
                }
            }
        }
    }
}

interface DeleteListState {
    suspend fun deleteList()
}
