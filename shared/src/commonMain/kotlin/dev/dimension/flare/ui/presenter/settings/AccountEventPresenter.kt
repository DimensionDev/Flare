package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AccountEventPresenter :
    PresenterBase<AccountEventPresenter.State>(),
    KoinComponent {
    @Immutable
    public interface State {
        public val onAdded: Flow<UiAccount>
        public val onRemoved: Flow<MicroBlogKey>
    }

    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): State =
        object : State {
            override val onAdded = accountRepository.onAdded
            override val onRemoved = accountRepository.onRemoved
        }
}
