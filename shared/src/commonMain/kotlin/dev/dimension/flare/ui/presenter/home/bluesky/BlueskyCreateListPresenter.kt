package dev.dimension.flare.ui.presenter.home.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase

class BlueskyCreateListPresenter(
    private val accountType: AccountType,
) : PresenterBase<BlueskyCreateListState>() {
    @Composable
    override fun body(): BlueskyCreateListState {
        val serviceState = accountServiceProvider(accountType = accountType)
        return object : BlueskyCreateListState {
            override suspend fun createList(
                name: String,
                description: String?,
                avatar: FileItem?,
            ) {
                serviceState.onSuccess {
                    require(it is BlueskyDataSource)
                    it.createList(name, description, avatar)
                }
            }
        }
    }
}

@Immutable
interface BlueskyCreateListState {
    suspend fun createList(
        name: String,
        description: String?,
        avatar: FileItem?,
    )
}
