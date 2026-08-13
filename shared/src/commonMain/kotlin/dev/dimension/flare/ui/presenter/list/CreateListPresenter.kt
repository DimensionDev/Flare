package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.capabilities
import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.data.datasource.microblog.list.ListMetaDataType
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList

/**
 * Presenter for creating lists.
 */
public class CreateListPresenter(
    private val accountType: AccountType,
) : PresenterBase<CreateListState>() {
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): CreateListState {
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)

        return object : CreateListState {
            override val supportedMetaData =
                serviceState.map {
                    requireNotNull(it.capabilities.list).listHandler.supportedMetaData
                }

            override suspend fun createList(listMetaData: ListMetaData) {
                serviceState.onSuccess {
                    requireNotNull(it.capabilities.list).listHandler.create(listMetaData)
                }
            }
        }
    }
}

@Immutable
public interface CreateListState {
    public val supportedMetaData: UiState<ImmutableList<ListMetaDataType>>

    public suspend fun createList(listMetaData: ListMetaData)
}
