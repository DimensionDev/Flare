package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Presenter for retrieving list information.
 */
public class ListInfoPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<ListInfoState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): ListInfoState {
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val listInfo =
            serviceState.flatMap {
                remember(it) {
                    require(it is ListDataSource)
                    it.listInfo(listId)
                }.collectAsState().toUi()
            }

        return object : ListInfoState {
            override val listInfo = listInfo
        }
    }
}

@Immutable
public interface ListInfoState {
    public val listInfo: UiState<UiList>
}
