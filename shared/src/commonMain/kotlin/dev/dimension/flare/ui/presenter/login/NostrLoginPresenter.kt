package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.network.nostr.NostrService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class NostrLoginPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<NostrLoginState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): NostrLoginState {
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<Throwable?>(null) }
        val scope = rememberCoroutineScope()
        return object : NostrLoginState {
            override val loading: Boolean = loading
            override val error: Throwable? = error

            override fun login(secretKey: String) {
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        loginWith(
                            NostrService.importAccount(
                                secretKeyInput = secretKey,
                            ),
                        )
                    }.onFailure {
                        error = it
                    }
                    loading = false
                }
            }

            private fun loginWith(imported: NostrService.ImportedAccount) {
                accountRepository.addAccount(
                    account =
                        UiAccount.Nostr(
                            accountKey =
                                MicroBlogKey(
                                    id = imported.pubkeyHex,
                                    host = NostrService.NOSTR_HOST,
                                ),
                        ),
                    credential =
                        UiAccount.Nostr.Credential(
                            pubkey = imported.pubkeyHex,
                            nsec = imported.nsec,
                            relays = dev.dimension.flare.data.network.nostr.defaultNostrRelays,
                        ),
                )
                toHome.invoke()
            }
        }
    }
}

@Immutable
public interface NostrLoginState {
    public val loading: Boolean
    public val error: Throwable?

    public fun login(secretKey: String)
}
