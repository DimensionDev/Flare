package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase

class ActiveAccountPresenter : PresenterBase<UserState>() {
    @Composable
    override fun body(): UserState {
        val account by activeAccountPresenter()
        val user =
            account
                .flatMap {
                    accountServiceProvider(accountType = AccountType.Specific(it.accountKey))
                }.flatMap {
                    remember(it.account.accountKey) {
                        it.userById(it.account.accountKey.id)
                    }.collectAsState().toUi()
                }
        return object : UserState {
            override val user = user
        }
    }
}

interface UserState {
    val user: UiState<UiUser>
}
