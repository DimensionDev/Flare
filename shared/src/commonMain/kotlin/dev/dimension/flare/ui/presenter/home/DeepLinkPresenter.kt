package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class DeepLinkPresenter :
    PresenterBase<DeepLinkPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    public interface State {
        public suspend fun match(url: String): ImmutableList<MatchResult>

        public data class MatchResult(
            val account: UiAccount,
            val deepLink: String,
        )
    }

    private val patternFlow by lazy {
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
    override fun body(): State {
        return object : State {
            override suspend fun match(url: String): ImmutableList<State.MatchResult> {
                val pattern = patternFlow.first()
                return DeepLinkMapping
                    .matches(url, pattern)
                    .map {
                        State.MatchResult(
                            account = it.key,
                            deepLink = it.value.deepLink(it.key.accountKey),
                        )
                    }.toImmutableList()
            }
        }
    }
}
