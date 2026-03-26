package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.presenter.settings.AccountsState
import org.koin.core.component.KoinComponent

public class AccountManagementPresenter :
    PresenterBase<AccountManagementPresenter.State>(),
    KoinComponent {
    public interface State : AccountsState {
        public fun logout(accountKey: MicroBlogKey)

        public fun setOrder(value: List<MicroBlogKey>)
    }

    @Composable
    override fun body(): State {
        val state =
            remember {
                AccountsPresenter()
            }.invoke()

        val order = remember { mutableStateListOf<MicroBlogKey>() }
        state.accounts.onSuccess {
            LaunchedEffect(Unit) {
                order.clear()
                order.addAll(it.map { it.account.accountKey })
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                state.updateOrder(order.toList())
            }
        }

        return object : State, AccountsState by state {
            override fun logout(accountKey: MicroBlogKey) {
                removeAccount(accountKey)
            }

            override fun setOrder(value: List<MicroBlogKey>) {
                order.clear()
                order.addAll(value)
            }
        }
    }
}
