package dev.dimension.flare.ui.presenter.home.mastodon

import androidx.compose.runtime.Composable
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase

class CreateListPresenter(
    private val accountType: AccountType,
) : PresenterBase<CreateListState>() {
    @Composable
    override fun body(): CreateListState {
        val serviceState = accountServiceProvider(accountType = accountType)

        return object : CreateListState {
            override suspend fun createList(name: String) {
                serviceState.onSuccess {
                    require(it is MastodonDataSource)
                    it.createList(name)
                }
            }
        }
    }
}

interface CreateListState {
    suspend fun createList(name: String)
}
