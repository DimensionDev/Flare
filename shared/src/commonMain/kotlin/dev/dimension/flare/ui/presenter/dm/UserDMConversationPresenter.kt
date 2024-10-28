package dev.dimension.flare.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.presenter.PresenterBase

class UserDMConversationPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey,
) : PresenterBase<UserDMConversationPresenter.State>() {
    interface State {
        val roomKey: UiState<MicroBlogKey>
    }

    @Composable
    override fun body(): State {
        val serviceState = accountServiceProvider(accountType = accountType)
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
