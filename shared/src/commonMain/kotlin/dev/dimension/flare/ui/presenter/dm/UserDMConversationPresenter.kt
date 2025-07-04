package dev.dimension.flare.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val roomKeyFlow by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).flatMapLatest {
            require(it is DirectMessageDataSource)
            it.createDirectMessageRoom(userKey)
        }
    }

    @Composable
    override fun body(): State {
        val roomKey by roomKeyFlow.flattenUiState()
        return object : State {
            override val roomKey = roomKey
        }
    }
}
