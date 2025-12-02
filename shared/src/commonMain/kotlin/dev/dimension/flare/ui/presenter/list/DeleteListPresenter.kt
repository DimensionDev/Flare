package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Presenter for deleting lists.
 */
public class DeleteListPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<DeleteListState>(),
    KoinComponent {
    private val scope by inject<CoroutineScope>()
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): DeleteListState =
        object : DeleteListState {
            override suspend fun deleteList() {
                scope.launch {
                    accountServiceFlow(
                        accountType = accountType,
                        repository = accountRepository,
                    ).map {
                        require(it is ListDataSource)
                        it
                    }.first()
                        .deleteList(listId)
                }
            }
        }
}

@Immutable
public interface DeleteListState {
    public suspend fun deleteList()
}
