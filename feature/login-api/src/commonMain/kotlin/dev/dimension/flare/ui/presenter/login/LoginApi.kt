package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
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
    val platformType: PlatformType,
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
    public val platformType: PlatformType
    public val metadata: PlatformTypeMetadata
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
    @Provided data: PlatformRuntimeData,
) {
    public val all: List<LoginPlatformProvider> = data.platformSpecs.filterIsInstance<LoginPlatformProvider>()
    private val byType: Map<PlatformType, LoginPlatformProvider> =
        all
            .also { providers ->
                val duplicateTypes =
                    providers
                        .groupBy { it.platformType }
                        .filterValues { it.size > 1 }
                        .keys
                require(duplicateTypes.isEmpty()) {
                    "Duplicate login platform providers: ${duplicateTypes.joinToString()}"
                }
            }.associateBy { it.platformType }

    public fun get(platformType: PlatformType): LoginPlatformProvider? = byType[platformType]

    public fun require(platformType: PlatformType): LoginPlatformProvider =
        get(platformType) ?: throw UnsupportedLoginPlatformException(platformType)

    public fun methods(platformType: PlatformType): List<LoginMethodSpec> = require(platformType).methods.sortedByDescending { it.priority }

    public suspend fun detectPlatformType(host: String): NodeData {
        val hostCleaned = normalizeHost(host)
        require(hostCleaned.isNotBlank()) { "Host is blank" }
        return all
            .map { it.detector }
            .distinct()
            .sortedByDescending { it.priority }
            .firstNotNullOfOrNull { detector ->
                runCatching {
                    detector.detect(hostCleaned)
                }.getOrElse {
                    if (it is CancellationException) {
                        throw it
                    }
                    null
                }
            }
            ?: throw IllegalArgumentException("Unsupported platform: $hostCleaned")
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
    public val platformType: PlatformType,
) : IllegalArgumentException("Login platform is not registered: $platformType")
