package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class DeleteStatusPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<DeleteStatusState>(),
    KoinComponent {
    // using io scope because it's a long-running operation
    private val scope by inject<CoroutineScope>()
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): DeleteStatusState =
        object : DeleteStatusState {
            override fun delete() {
                scope.launch {
                    accountServiceFlow(
                        accountType = accountType,
                        repository = accountRepository,
                    ).map {
                        require(it is AuthenticatedMicroblogDataSource)
                        it
                    }.first()
                        .deleteStatus(statusKey)
                }
            }
        }
}

@Immutable
public interface DeleteStatusState {
    public fun delete()
}
