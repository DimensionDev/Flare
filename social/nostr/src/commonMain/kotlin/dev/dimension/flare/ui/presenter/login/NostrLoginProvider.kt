package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.nostr.AmberSignerBridge
import dev.dimension.flare.data.network.nostr.NostrPlatformDetector
import dev.dimension.flare.data.network.nostr.NostrService
import dev.dimension.flare.data.network.nostr.defaultNostrRelays
import dev.dimension.flare.data.platform.NostrCredential
import dev.dimension.flare.data.platform.NostrPlatformSpec
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.addAccount
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes

private const val LOGIN_ACTION = "login"
private const val START_QR_ACTION = "start_qr"
private const val CANCEL_ACTION = "cancel"
private const val INPUT_FIELD = "credential"

public data object NostrLoginProvider :
    LoginPlatformProvider,
    KoinComponent {
    private val amberSignerBridge: AmberSignerBridge by inject()

    override val platformType: PlatformType = PlatformType.Nostr
    override val metadata: PlatformTypeMetadata
        get() = NostrPlatformSpec.metadata
    override val detector: PlatformDetector = NostrPlatformDetector
    override val methods: List<LoginMethodSpec>
        get() =
            buildList {
                add(
                    LoginMethodSpec(
                        type = LoginMethodType.CredentialImport,
                        title = UiStrings.CredentialImport,
                        priority = 20,
                    ),
                )
                add(
                    LoginMethodSpec(
                        type = LoginMethodType.QrConnect,
                        title = UiStrings.QrConnect,
                        priority = 10,
                    ),
                )
                if (amberSignerBridge.isAvailable()) {
                    add(
                        LoginMethodSpec(
                            type = LoginMethodType.ExternalSigner,
                            title = UiStrings.ExternalSigner,
                        ),
                    )
                }
            }

    override fun agreementUrl(host: String): String? = null

    override suspend fun recommendInstances(): List<RecommendedInstance> =
        listOf(
            RecommendedInstance(
                instance =
                    UiInstance(
                        name = metadata.displayName,
                        description =
                            "A decentralized network based on cryptographic keypairs and that is not peer-to-peer, " +
                                "it is super simple and scalable and therefore has a chance of working.",
                        iconUrl = null,
                        domain = "nostr",
                        type = platformType,
                        bannerUrl = null,
                        usersCount = 0,
                    ),
                priority = 60,
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${platformType.name} is not supported yet")

    override fun createHandler(context: LoginContext): LoginMethodHandler =
        when (context.methodType) {
            LoginMethodType.CredentialImport -> NostrCredentialLoginHandler(context)
            LoginMethodType.QrConnect -> NostrQrLoginHandler(context)
            LoginMethodType.ExternalSigner -> NostrExternalSignerLoginHandler(context)
            else -> error("Unsupported Nostr login method: ${context.methodType}")
        }
}

private class NostrCredentialLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler,
    KoinComponent {
    private val accountService: AccountService by inject()
    private var input = ""
    private val _state = MutableStateFlow(state())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects

    override fun updateField(
        id: String,
        value: String,
    ) {
        if (id == INPUT_FIELD) {
            input = value
            _state.value = state()
        }
    }

    override suspend fun perform(actionId: String) {
        if (actionId != LOGIN_ACTION) return
        _state.value = state(loading = true)
        runCatching {
            loginWith(
                accountService = accountService,
                imported =
                    NostrService.importAccount(
                        input = input,
                    ),
            )
            context.onSuccess()
        }.onFailure {
            _state.value = state(error = it.message)
        }
    }

    override suspend fun resume(value: String) = Unit

    override fun clear() {
        input = ""
        _state.value = state()
    }

    private fun state(
        loading: Boolean = false,
        error: String? = null,
    ): LoginFlowState =
        LoginFlowState(
            fields =
                listOf(
                    LoginField(
                        id = INPUT_FIELD,
                        type = LoginFieldType.TextInput,
                        label = UiStrings.NostrLoginAccount,
                        placeholder = UiStrings.NostrLoginAccount,
                        value = input,
                    ),
                ),
            actions =
                listOf(
                    LoginAction(
                        id = LOGIN_ACTION,
                        label = UiStrings.Login,
                        enabled = !loading && input.isNotBlank(),
                    ),
                ),
            loading = loading,
            error = error,
        )
}

private class NostrQrLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler,
    KoinComponent {
    private val accountService: AccountService by inject()
    private var pendingQrLogin: NostrService.Companion.PendingQrLogin? = null
    private var waiting = false
    private val _state = MutableStateFlow(state())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects

    override fun updateField(
        id: String,
        value: String,
    ) = Unit

    override suspend fun perform(actionId: String) {
        when (actionId) {
            START_QR_ACTION -> startQrLogin()
            CANCEL_ACTION -> cancelQrLogin()
        }
    }

    override suspend fun resume(value: String) = Unit

    override fun clear() {
        cancelQrLogin()
    }

    override fun close() {
        cancelQrLogin()
    }

    private suspend fun startQrLogin() {
        _state.value = state(loading = true)
        runCatching {
            pendingQrLogin?.close()
            val session = NostrService.beginQrLogin()
            pendingQrLogin = session
            waiting = true
            _effects.emit(LoginEffect.ShowQr(session.connectUri))
            _state.value = state()
            val imported =
                withTimeout(2.minutes) {
                    session.awaitAccount()
                }
            if (pendingQrLogin === session) {
                _state.value = state(loading = true)
                pendingQrLogin = null
                waiting = false
                loginWith(accountService, imported)
                session.close()
                context.onSuccess()
            }
        }.onFailure {
            pendingQrLogin?.close()
            pendingQrLogin = null
            waiting = false
            _state.value = state(error = it.message)
        }
    }

    private fun cancelQrLogin() {
        pendingQrLogin?.close()
        pendingQrLogin = null
        waiting = false
        _state.value = state()
    }

    private fun state(
        loading: Boolean = false,
        error: String? = null,
    ): LoginFlowState =
        LoginFlowState(
            actions =
                listOf(
                    if (waiting) {
                        LoginAction(
                            id = CANCEL_ACTION,
                            label = UiStrings.Cancel,
                            enabled = !loading,
                        )
                    } else {
                        LoginAction(
                            id = START_QR_ACTION,
                            label = UiStrings.QrConnect,
                            enabled = !loading,
                        )
                    },
                ),
            loading = loading,
            error = error,
        )
}

private class NostrExternalSignerLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler,
    KoinComponent {
    private val accountService: AccountService by inject()
    private val amberSignerBridge: AmberSignerBridge by inject()
    private val _state = MutableStateFlow(state())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects

    override fun updateField(
        id: String,
        value: String,
    ) = Unit

    override suspend fun perform(actionId: String) {
        if (actionId != LOGIN_ACTION) return
        _state.value = state(loading = true)
        runCatching {
            val connection = amberSignerBridge.connect()
            val relays =
                runCatching {
                    NostrService.resolvePublicRelays(connection.pubkeyHex)
                }.getOrDefault(defaultNostrRelays)
            accountService.addAccount(
                account =
                    UiAccount(
                        accountKey =
                            MicroBlogKey(
                                id = connection.pubkeyHex,
                                host = NostrService.NOSTR_HOST,
                            ),
                        platformType = PlatformType.Nostr,
                    ),
                credential =
                    NostrCredential(
                        pubkeyHex = connection.pubkeyHex,
                        relays = relays,
                        signer = connection.credential,
                    ),
            )
            context.onSuccess()
        }.onFailure {
            _state.value = state(error = it.message)
        }
    }

    override suspend fun resume(value: String) = Unit

    override fun clear() {
        _state.value = state()
    }

    private fun state(
        loading: Boolean = false,
        error: String? = null,
    ): LoginFlowState =
        LoginFlowState(
            actions =
                listOf(
                    LoginAction(
                        id = LOGIN_ACTION,
                        label = UiStrings.ExternalSigner,
                        enabled = !loading && amberSignerBridge.isAvailable(),
                    ),
                ),
            loading = loading,
            error = error,
        )
}

private suspend fun loginWith(
    accountService: AccountService,
    imported: NostrService.ImportedAccount,
) {
    val relays =
        runCatching {
            NostrService.resolvePublicRelays(imported.pubkeyHex)
        }.getOrDefault(defaultNostrRelays)
    accountService.addAccount(
        account =
            UiAccount(
                accountKey =
                    MicroBlogKey(
                        id = imported.pubkeyHex,
                        host = NostrService.NOSTR_HOST,
                    ),
                platformType = PlatformType.Nostr,
            ),
        credential =
            NostrCredential(
                pubkeyHex = imported.pubkeyHex,
                relays = relays,
                signer = imported.signerCredential,
            ),
    )
}
