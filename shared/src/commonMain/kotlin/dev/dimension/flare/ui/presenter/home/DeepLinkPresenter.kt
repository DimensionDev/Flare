package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class DeepLinkPresenter :
    PresenterBase<DeepLinkPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    public interface State

    private val patternFlow: Flow<ImmutableMap<UiAccount, ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>>>> by lazy {
        accountRepository.allAccounts.map {
            it
                .associateWith {
                    DeepLinkMapping
                        .generatePattern(
                            platformType = it.platformType,
                            host = it.accountKey.host,
                        ).toImmutableList()
                }.toImmutableMap()
        }
    }

    @Composable
    override fun body(): State =
        object : State {
        }
}
