package dev.dimension.flare.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

public class DeleteStatusPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<DeleteStatusState>() {
    // using io scope because it's a long-running operation
    private val scope by koinInject<CoroutineScope>()
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): DeleteStatusState =
        object : DeleteStatusState {
            override fun delete() {
                scope.launch {
                    accountServiceFlow(
                        accountType = accountType,
                        repository = accountRepository,
                    ).map {
                        require(it is PostDataSource)
                        it
                    }.first()
                        .let {
                            it.postHandler.delete(statusKey)
                        }
                }
            }
        }
}

@Immutable
public interface DeleteStatusState {
    public fun delete()
}
