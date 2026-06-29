package dev.dimension.flare.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

public class UnblockUserPresenter(
    private val accountType: AccountType?,
    private val userKey: MicroBlogKey,
) : PresenterBase<UnblockUserPresenter.State>() {
    private val accountRepository by koinInject<AccountRepository>()
    private val scope by koinInject<CoroutineScope>()

    @Immutable
    public interface State {
        public fun confirm()
    }

    @Composable
    override fun body(): State =
        object : State {
            override fun confirm() {
                if (accountType != null) {
                    scope.launch {
                        accountServiceFlow(accountType, repository = accountRepository)
                            .firstOrNull()
                            ?.let {
                                if (it is RelationDataSource) {
                                    it.relationHandler.unblock(userKey)
                                }
                            }
                    }
                }
            }
        }
}
