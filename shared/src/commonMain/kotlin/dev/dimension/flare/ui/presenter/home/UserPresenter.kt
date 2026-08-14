package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.datasource.microblog.accountKeyOrNull
import dev.dimension.flare.data.datasource.microblog.capabilities
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.NoActiveAccountException
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

public open class UserPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey?,
) : PresenterBase<UserState>() {
    private val accountRepository: AccountRepository by koinInject()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dataFlow by lazy {
        accountServiceFlow(accountType, accountRepository)
            .flatMapLatest { service ->
                val userId =
                    userKey?.id
                        ?: service.accountKeyOrNull?.id
                val profile = service.capabilities.profile
                if (userId == null || profile == null) {
                    flowOf(UiState.Error(NoActiveAccountException))
                } else {
                    profile.userHandler.userById(userId).toUi()
                }
            }
    }

    @Composable
    override fun body(): UserState {
        val user by dataFlow.flattenUiState()

        return object : UserState {
            override val user = user
        }
    }
}
