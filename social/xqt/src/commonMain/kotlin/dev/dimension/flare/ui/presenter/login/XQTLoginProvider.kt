package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.datasource.xqt.userById
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.xqt.XQTPlatformDetector
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.platform.XQTCredential
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.addAccount
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val LOGIN_ACTION = "login"

public data object XQTLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.xQt
    override val metadata: PlatformTypeMetadata = XqtPlatformSpec.metadata
    override val detector: PlatformDetector = XQTPlatformDetector
    override val methods: List<LoginMethodSpec> =
        listOf(
            LoginMethodSpec(
                type = LoginMethodType.WebCookie,
                title = UiStrings.WebCookieLogin,
            ),
        )

    override fun agreementUrl(host: String): String? = "https://help.x.com/en/rules-and-policies/x-rules"

    override suspend fun recommendInstances(): List<RecommendedInstance> =
        listOf(
            RecommendedInstance(
                instance =
                    UiInstance(
                        name = metadata.displayName,
                        description =
                            "From breaking news and entertainment to sports and politics," +
                                " get the full story with all the live commentary.",
                        iconUrl = null,
                        domain = xqtHost,
                        type = platformType,
                        bannerUrl = null,
                        usersCount = 0,
                    ),
                priority = 80,
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${platformType.name} is not supported yet")

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        require(context.methodType == LoginMethodType.WebCookie) {
            "Unsupported X login method: ${context.methodType}"
        }
        return XQTWebCookieLoginHandler(context)
    }
}

private class XQTWebCookieLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler,
    KoinComponent {
    private val accountService: AccountService by inject()
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
        _effects.emit(LoginEffect.OpenWebCookieLogin("https://$xqtHost/i/flow/login"))
    }

    override suspend fun resume(value: String) {
        _state.value = state(loading = true)
        runCatching {
            require(value.isNotBlank()) { "Cookie is empty" }
            require(XQTService.checkChocolate(value)) { "Invalid cookie" }
            xqtLoginUseCase(value)
            context.onSuccess()
        }.onFailure {
            _state.value = state(error = it.message)
        }
    }

    override fun clear() {
        _state.value = state()
    }

    private suspend fun xqtLoginUseCase(chocolate: String) {
        val xqtService = XQTService(flowOf(chocolate))
        val userId = xqtService.getInitialUserId(chocolate = chocolate)
        requireNotNull(userId)
        val account =
            xqtService
                .userById(userId)
                .body()
                ?.data
                ?.user
                ?.result
        requireNotNull(account)
        require(account is User)
        accountService.addAccount(
            UiAccount(
                accountKey =
                    MicroBlogKey(
                        id = account.restId,
                        host = xqtHost,
                    ),
                platformType = PlatformType.xQt,
            ),
            credential =
                XQTCredential(
                    chocolate = chocolate,
                ),
        )
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
                        label = UiStrings.Login,
                        enabled = !loading,
                    ),
                ),
            loading = loading,
            error = error,
        )
}
