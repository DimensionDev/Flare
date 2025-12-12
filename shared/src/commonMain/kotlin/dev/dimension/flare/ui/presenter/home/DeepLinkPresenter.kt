package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class DeepLinkPresenter(
    private val onRoute: (DeeplinkRoute) -> Unit,
    private val onLink: (String) -> Unit,
) : PresenterBase<DeepLinkPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    public interface State {
        public fun handle(url: String)
        public data class MatchResult(
            val account: UiAccount,
            val deepLink: DeeplinkRoute,
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
        var pendingUrl by remember { mutableStateOf<String?>(null) }
        pendingUrl?.let { url ->
            LaunchedEffect(url) {
                patternFlow.collect { pattern ->
                    val matches = DeepLinkMapping.matches(url, pattern)
                    if (matches.isEmpty()) {
                        onLink.invoke(url)
                    } else if (matches.size == 1) {
                        val match = matches.toList().first()
                        onRoute.invoke(match.second.deepLink(match.first.accountKey))
                    } else {
                        onRoute.invoke(
                            DeeplinkRoute.DeepLinkAccountPicker(
                                data = matches.map {
                                    it.key.accountKey to it.value.deepLink(it.key.accountKey)
                                }.toMap().toImmutableMap()
                            )
                        )
                    }
                    pendingUrl = null
                }
            }
        }

        return object : State {
            override fun handle(url: String) {
                pendingUrl = url
            }
        }
    }
}
