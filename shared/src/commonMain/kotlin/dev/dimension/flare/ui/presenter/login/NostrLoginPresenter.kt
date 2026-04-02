package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.network.nostr.AmberSignerBridge
import dev.dimension.flare.data.network.nostr.NostrService
import dev.dimension.flare.data.network.nostr.defaultNostrRelays
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes

public class NostrLoginPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<NostrLoginState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()
    private val amberSignerBridge: AmberSignerBridge by inject()

    @Composable
    override fun body(): NostrLoginState {
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<Throwable?>(null) }
        var qrConnectUri by remember { mutableStateOf<String?>(null) }
        var qrWaiting by remember { mutableStateOf(false) }
        var pendingQrLogin by remember { mutableStateOf<NostrService.Companion.PendingQrLogin?>(null) }
        val scope = rememberCoroutineScope()
        DisposableEffect(Unit) {
            onDispose {
                pendingQrLogin?.close()
            }
        }
        return object : NostrLoginState {
            override val loading: Boolean = loading
            override val error: Throwable? = error
            override val amberAvailable: Boolean = amberSignerBridge.isAvailable()
            override val qrConnectUri: String? = qrConnectUri
            override val qrWaitingForApproval: Boolean = qrWaiting

            override fun login(input: String) {
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        loginWith(
                            NostrService.importAccount(
                                input = input,
                            ),
                        )
                    }.onFailure {
                        error = it
                    }
                    loading = false
                }
            }

            override fun connectAmber() {
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        val connection = amberSignerBridge.connect()
                        val relays =
                            runCatching {
                                NostrService.resolvePublicRelays(connection.pubkeyHex)
                            }.getOrDefault(defaultNostrRelays)
                        accountRepository.addAccount(
                            account =
                                UiAccount.Nostr(
                                    accountKey =
                                        MicroBlogKey(
                                            id = connection.pubkeyHex,
                                            host = NostrService.NOSTR_HOST,
                                        ),
                                ),
                            credential =
                                UiAccount.Nostr.Credential(
                                    pubkeyHex = connection.pubkeyHex,
                                    relays = relays,
                                    signer = connection.credential,
                                ),
                        )
                        toHome.invoke()
                    }.onFailure {
                        error = it
                    }
                    loading = false
                }
            }

            override fun startQrLogin() {
                scope.launch {
                    runCatching {
                        loading = true
                        error = null
                        pendingQrLogin?.close()
                        val session = NostrService.beginQrLogin()
                        pendingQrLogin = session
                        qrConnectUri = session.connectUri
                        qrWaiting = true
                        loading = false
                        runCatching {
                            withTimeout(2.minutes) {
                                session.awaitAccount()
                            }
                        }.onSuccess { imported ->
                            if (pendingQrLogin === session) {
                                loading = true
                                pendingQrLogin = null
                                qrConnectUri = null
                                qrWaiting = false
                                loginWith(imported)
                                session.close()
                                loading = false
                            }
                        }.onFailure {
                            if (pendingQrLogin === session) {
                                error = it
                                pendingQrLogin = null
                                qrConnectUri = null
                                qrWaiting = false
                                session.close()
                            }
                        }
                    }.onFailure {
                        it.printStackTrace()
                    }
                }
            }

            override fun cancelQrLogin() {
                pendingQrLogin?.close()
                pendingQrLogin = null
                qrConnectUri = null
                qrWaiting = false
            }

            private suspend fun loginWith(imported: NostrService.ImportedAccount) {
                val relays =
                    runCatching {
                        NostrService.resolvePublicRelays(imported.pubkeyHex)
                    }.getOrDefault(defaultNostrRelays)
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
                            pubkeyHex = imported.pubkeyHex,
                            relays = relays,
                            signer = imported.signerCredential,
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
    public val amberAvailable: Boolean
    public val qrConnectUri: String?
    public val qrWaitingForApproval: Boolean

    public fun login(input: String)

    public fun connectAmber()

    public fun startQrLogin()

    public fun cancelQrLogin()
}
