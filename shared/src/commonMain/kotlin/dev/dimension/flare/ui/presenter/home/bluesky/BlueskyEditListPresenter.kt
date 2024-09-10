package dev.dimension.flare.ui.presenter.home.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase

class BlueskyEditListPresenter(
    private val accountType: AccountType,
    private val listUri: String,
) : PresenterBase<BlueskyEditListState>() {
    @Composable
    override fun body(): BlueskyEditListState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType)
        val listInfo =
            serviceState.flatMap {
                require(it is BlueskyDataSource)
                remember(it) {
                    it.listInfo(listUri)
                }.collectAsState().toUi()
            }
        val memberInfo =
            serviceState
                .map {
                    require(it is BlueskyDataSource)
                    remember(it) {
                        it.listMembers(listUri, scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        return object : BlueskyEditListState {
            override val info = listInfo
            override val memberInfo = memberInfo

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
interface BlueskyEditListState {
    val info: UiState<UiList>
    val memberInfo: PagingState<UiUserV2>

    suspend fun editList(
        name: String,
        description: String?,
        avatar: FileItem?,
    )
}
