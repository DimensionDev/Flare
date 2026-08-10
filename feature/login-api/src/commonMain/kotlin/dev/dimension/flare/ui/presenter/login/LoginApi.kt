package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

public enum class LoginMethodType {
    OAuth,
    Password,
    CredentialImport,
    QrConnect,
    ExternalSigner,
    WebCookie,
}

public enum class LoginFieldType {
    TextInput,
    PasswordInput,
    OtpInput,
    DisplayText,
}

public data class LoginMethodSpec(
    val type: LoginMethodType,
    val title: UiStrings,
    val priority: Int = 0,
)

public data class LoginField(
    val id: String,
    val type: LoginFieldType,
    val label: UiStrings,
    val placeholder: UiStrings? = null,
    val value: String = "",
    val readOnly: Boolean = false,
    val error: String? = null,
)

public data class LoginAction(
    val id: String,
    val label: UiStrings,
    val enabled: Boolean = true,
)

public data class LoginFlowState(
    val fields: List<LoginField> = emptyList(),
    val actions: List<LoginAction> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

public data class ReloginTarget(
    val accountKey: MicroBlogKey,
    val platformId: String,
)

public sealed interface LoginEffect {
    public data class OpenUrl(
        val url: String,
    ) : LoginEffect

    public data class ShowQr(
        val content: String,
    ) : LoginEffect

    public data class OpenWebCookieLogin(
        val url: String,
    ) : LoginEffect
}

public interface LoginPlatformProvider {
    public val platformId: String
    public val metadata: PlatformMetadata
    public val detector: PlatformDetector
    public val methods: List<LoginMethodSpec>

    public fun agreementUrl(host: String): String?

    public suspend fun recommendInstances(): List<RecommendedInstance>

    public suspend fun instanceMetadata(host: String): UiInstanceMetadata

    public fun createHandler(context: LoginContext): LoginMethodHandler
}

public data class LoginContext(
    val host: String,
    val methodType: LoginMethodType,
    val onSuccess: suspend () -> Unit,
    val redirectUri: String? = null,
    val reloginTarget: ReloginTarget? = null,
)

public fun LoginContext.requireReloginAccount(accountKey: MicroBlogKey) {
    val target = reloginTarget ?: return
    if (accountKey != target.accountKey) {
        throw ReloginAccountMismatchException(
            expected = target.accountKey,
            actual = accountKey,
        )
    }
}

public class ReloginAccountMismatchException(
    public val expected: MicroBlogKey,
    public val actual: MicroBlogKey,
) : IllegalArgumentException(
        "Relogin account mismatch: expected $expected, got $actual",
    )

public interface LoginMethodHandler : AutoCloseable {
    public val state: StateFlow<LoginFlowState>
    public val effects: Flow<LoginEffect>

    public fun updateField(
        id: String,
        value: String,
    )

    public suspend fun perform(actionId: String)

    public suspend fun resume(value: String)

    public fun canResume(value: String): Boolean = true

    public fun onExternalAuthenticationDismissed(error: String?) {
        clear()
    }

    public fun clear()

    override fun close() {
    }
}

@Single
@HiddenFromObjC
public class LoginPlatformRegistry(
    @Provided platformRegistry: PlatformRegistry,
) {
    public val all: List<LoginPlatformProvider> = platformRegistry.all.filterIsInstance<LoginPlatformProvider>()
    private val byId: Map<String, LoginPlatformProvider> = all.associateBy { it.platformId }

    public fun get(platformId: String): LoginPlatformProvider? = byId[platformId]

    public fun require(platformId: String): LoginPlatformProvider = get(platformId) ?: throw UnsupportedLoginPlatformException(platformId)

    public fun methods(platformId: String): List<LoginMethodSpec> = require(platformId).methods.sortedByDescending { it.priority }

    public suspend fun detectPlatformId(host: String): NodeData {
        val hostCleaned = normalizeHost(host)
        require(hostCleaned.isNotBlank()) { "Host is blank" }
        return all
            .sortedByDescending { it.detector.priority }
            .firstNotNullOfOrNull { provider ->
                runCatching {
                    provider.detector.detect(hostCleaned)
                }.getOrElse {
                    if (it is CancellationException) {
                        throw it
                    }
                    null
                }?.let { detected ->
                    NodeData(
                        host = detected.host,
                        platformId = provider.platformId,
                        software = detected.software,
                        compatibleMode = detected.compatibleMode,
                        platformDisplayName = provider.metadata.displayName,
                        platformIcon = provider.metadata.icon,
                        loginMethods = provider.methods.sortedByDescending { it.priority },
                    )
                }
            } ?: throw IllegalArgumentException("Unsupported platform: $hostCleaned")
    }

    private fun normalizeHost(host: String): String =
        host
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore("?")
            .removeSuffix("/")
            .lowercase()
}

@HiddenFromObjC
public class UnsupportedLoginPlatformException(
    public val platformId: String,
) : IllegalArgumentException("Login platform is not registered: $platformId")
