package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.datasource.microblog.ListMetaData
import dev.dimension.flare.data.datasource.microblog.ListMetaDataType
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

class ListEditPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<EditListState>() {
    @Composable
    override fun body(): EditListState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType)
        val listInfoState =
            remember(
                accountType,
                listId,
            ) {
                ListInfoPresenter(accountType, listId)
            }.body()
        val state =
            remember(
                accountType,
                listId,
            ) {
                EditListMemberPresenter(accountType, listId)
            }.body()
        val memberState =
            remember(
                accountType,
                listId,
            ) {
                ListMembersPresenter(accountType, listId)
            }.body()
        return object :
            EditListState,
            EditListMemberState by state,
            ListMembersState by memberState,
            ListInfoState by listInfoState {
            override val supportedMetaData =
                serviceState.map {
                    require(it is ListDataSource)
                    it.supportedMetaData
                }

            override fun refresh() {
                scope.launch {
                    memberInfo.refreshSuspend()
                }
            }

            override suspend fun updateList(listMetaData: ListMetaData) {
                serviceState.onSuccess {
                    require(it is ListDataSource)
                    it.updateList(listId, listMetaData)
                }
            }
        }
    }
}

interface EditListState :
    EditListMemberState,
    ListMembersState,
    ListInfoState {
    val supportedMetaData: UiState<ImmutableList<ListMetaDataType>>

    fun refresh()

    suspend fun updateList(listMetaData: ListMetaData)
}
