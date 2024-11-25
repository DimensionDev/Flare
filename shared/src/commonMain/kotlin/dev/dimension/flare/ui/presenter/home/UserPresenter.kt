package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.NoActiveAccountException
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UserPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey?,
) : PresenterBase<UserState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): UserState {
        val user =
            accountServiceProvider(accountType = accountType, repository = accountRepository).flatMap { service ->
                val userId =
                    userKey?.id
                        ?: if (service is AuthenticatedMicroblogDataSource) {
                            service.accountKey.id
                        } else {
                            null
                        } ?: throw NoActiveAccountException
                remember(service, userKey) {
                    service.userById(userId)
                }.collectAsState()
                    .toUi()
                    .map {
                        it as UiUserV2
                    }
            }

        return object : UserState {
            override val user = user
        }
    }
}
