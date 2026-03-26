package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class NostrRelaysPresenter(
    private val accountKey: MicroBlogKey,
) : PresenterBase<NostrRelaysPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Immutable
    public interface State {
        public val relays: UiState<ImmutableList<String>>

        public fun addRelay(relay: String)

        public fun removeRelay(relay: String)
    }

    @Composable
    override fun body(): State {
        val credential by remember {
            accountRepository.credentialFlow<UiAccount.Nostr.Credential>(accountKey)
        }.collectAsUiState()

        return object : State {
            override val relays = credential.map { it.relays.toImmutableList() }

            override fun addRelay(relay: String) {
                credential.onSuccess { credential ->
                    if (relay in credential.relays) return
                    val newRelays = credential.relays + relay
                    val newCredential = credential.copy(relays = newRelays)
                    accountRepository.updateCredential(
                        accountKey = accountKey,
                        credential = newCredential,
                    )
                }
            }

            override fun removeRelay(relay: String) {
                credential.onSuccess { credential ->
                    if (relay !in credential.relays) return
                    val newRelays = credential.relays - relay
                    val newCredential = credential.copy(relays = newRelays)
                    accountRepository.updateCredential(
                        accountKey = accountKey,
                        credential = newCredential,
                    )
                }
            }
        }
    }
}
