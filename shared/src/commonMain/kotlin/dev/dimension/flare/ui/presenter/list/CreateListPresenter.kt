package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
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

class CreateListPresenter(
    private val accountType: AccountType,
) : PresenterBase<CreateListState>() {
    @Composable
    override fun body(): CreateListState {
        val serviceState = accountServiceProvider(accountType = accountType)

        return object : CreateListState {
            override val supportedMetaData =
                serviceState.map {
                    require(it is ListDataSource)
                    it.supportedMetaData
                }

            override suspend fun createList(listMetaData: ListMetaData) {
                serviceState.onSuccess {
                    require(it is ListDataSource)
                    it.createList(listMetaData)
                }
            }
        }
    }
}

interface CreateListState {
    val supportedMetaData: UiState<ImmutableList<ListMetaDataType>>

    suspend fun createList(listMetaData: ListMetaData)
}
