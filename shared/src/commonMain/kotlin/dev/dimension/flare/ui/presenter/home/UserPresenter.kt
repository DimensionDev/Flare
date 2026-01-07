package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.NoActiveAccountException
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public open class UserPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey?,
) : PresenterBase<UserState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dataFlow by lazy {
        accountServiceFlow(accountType, accountRepository)
            .flatMapLatest { service ->
                val userId =
                    userKey?.id
                        ?: if (service is AuthenticatedMicroblogDataSource) {
                            service.accountKey.id
                        } else {
                            null
                        }
                if (userId == null) {
                    flowOf(UiState.Error(NoActiveAccountException))
                } else {
                    service.userById(userId).toUi()
                }
            }.map {
                it.map {
                    it as UiUserV2
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
