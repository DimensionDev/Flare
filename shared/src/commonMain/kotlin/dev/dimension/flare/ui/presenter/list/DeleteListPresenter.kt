package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class DeleteListPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<DeleteListState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): DeleteListState {
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)

        return object : DeleteListState {
            override suspend fun deleteList() {
                serviceState.onSuccess {
                    require(it is ListDataSource)
                    it.deleteList(listId)
                }
            }
        }
    }
}

@Immutable
public interface DeleteListState {
    public suspend fun deleteList()
}
