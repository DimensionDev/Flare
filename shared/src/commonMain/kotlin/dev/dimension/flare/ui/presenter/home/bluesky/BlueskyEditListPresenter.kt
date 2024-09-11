package dev.dimension.flare.ui.presenter.home.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.list.EditListState
import dev.dimension.flare.ui.presenter.list.ListEditPresenter

class BlueskyEditListPresenter(
    private val accountType: AccountType,
    private val listUri: String,
) : PresenterBase<BlueskyEditListState>() {
    @Composable
    override fun body(): BlueskyEditListState {
        val serviceState = accountServiceProvider(accountType = accountType)
        val state =
            remember(
                accountType,
                listUri,
            ) {
                ListEditPresenter(accountType, listUri)
            }.body()

        return object : BlueskyEditListState, EditListState by state {
            override suspend fun editList(
                name: String,
                description: String?,
                avatar: FileItem?,
            ) {
                serviceState.onSuccess {
                    require(it is BlueskyDataSource)
                    it.updateList(listUri, name, description, avatar)
                }
            }
        }
    }
}

@Immutable
interface BlueskyEditListState : EditListState {
    suspend fun editList(
        name: String,
        description: String?,
        avatar: FileItem?,
    )
}
