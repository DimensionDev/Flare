package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.vvo.VVOPlatformDetector
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.platform.VVoCredential
import dev.dimension.flare.data.platform.VvoPlatformSpec
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.addAccount
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.ui.model.UiAccount
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

public data object VVOLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.VVo
    override val metadata: PlatformTypeMetadata = VvoPlatformSpec.metadata
    override val detector: PlatformDetector = VVOPlatformDetector
    override val methods: List<LoginMethodSpec> =
        listOf(
            LoginMethodSpec(
                type = LoginMethodType.WebCookie,
                title = UiStrings.WebCookieLogin,
            ),
        )

    override fun agreementUrl(host: String): String? = null

    override suspend fun recommendInstances(): List<RecommendedInstance> = emptyList()

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${platformType.name} is not supported yet")

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        require(context.methodType == LoginMethodType.WebCookie) {
            "Unsupported VVO login method: ${context.methodType}"
        }
        return VVOWebCookieLoginHandler(context)
    }
}

private class VVOWebCookieLoginHandler(
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
        _effects.emit(LoginEffect.OpenWebCookieLogin("https://$vvoHost/login?backURL=https://$vvoHost/"))
    }

    override suspend fun resume(value: String) {
        _state.value = state(loading = true)
        runCatching {
            require(value.isNotBlank()) { "Cookie is empty" }
            require(VVOService.checkChocolates(value)) { "Invalid cookie" }
            vvoLoginUseCase(value)
            context.onSuccess()
        }.onFailure {
            _state.value = state(error = it.message)
        }
    }

    override fun canResume(value: String): Boolean = value.isNotBlank() && VVOService.checkChocolates(value)

    override fun clear() {
        _state.value = state()
    }

    private suspend fun vvoLoginUseCase(chocolate: String) {
        val service = VVOService(flowOf(chocolate))
        val config = service.config()
        val uid = config.data?.uid
        requireNotNull(uid) { "uid is null" }
        val st = config.data.st
        requireNotNull(st) { "st is null" }
        val profile = service.profileInfo(uid, st)
        requireNotNull(profile.data) { "profile is null" }
        accountService.addAccount(
            UiAccount(
                accountKey =
                    MicroBlogKey(
                        id = uid,
                        host = vvoHost,
                    ),
                platformType = PlatformType.VVo,
            ),
            credential =
                VVoCredential(
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
