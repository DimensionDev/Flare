package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DeleteStatusPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<DeleteStatusState>(),
    KoinComponent {
    // using io scope because it's a long-running operation
    private val scope by inject<CoroutineScope>()
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): DeleteStatusState {
        val service =
            accountServiceProvider(accountType = accountType, repository = accountRepository).map {
                require(it is AuthenticatedMicroblogDataSource)
                it
            }
        return object : DeleteStatusState {
            override fun delete() {
                service.onSuccess {
                    scope.launch {
                        it.deleteStatus(statusKey)
                    }
                }
            }
        }
    }
}

interface DeleteStatusState {
    fun delete()
}
