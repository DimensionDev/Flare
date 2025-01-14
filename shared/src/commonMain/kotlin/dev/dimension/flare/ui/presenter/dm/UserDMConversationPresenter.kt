package dev.dimension.flare.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class UserDMConversationPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey,
) : PresenterBase<UserDMConversationPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Immutable
    public interface State {
        public val roomKey: UiState<MicroBlogKey>
    }

    @Composable
    override fun body(): State {
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val roomKey =
            serviceState.flatMap {
                require(it is DirectMessageDataSource)
                remember {
                    it.createDirectMessageRoom(userKey)
                }.collectAsUiState().value.flatMap { it }
            }
        return object : State {
            override val roomKey = roomKey
        }
    }
}
