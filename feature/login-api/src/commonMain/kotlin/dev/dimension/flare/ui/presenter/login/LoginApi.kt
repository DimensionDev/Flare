package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asText
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
    Form,
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
    val title: UiText,
    val priority: Int = 0,
) {
    public constructor(
        type: LoginMethodType,
        title: UiStrings,
        priority: Int = 0,
    ) : this(type = type, title = title.asText(), priority = priority)
}

public data class LoginField(
    val id: String,
    val type: LoginFieldType,
    val label: UiText,
    val placeholder: UiText? = null,
    val value: String = "",
    val readOnly: Boolean = false,
    val error: String? = null,
) {
    public constructor(
        id: String,
        type: LoginFieldType,
        label: UiStrings,
        placeholder: UiStrings? = null,
        value: String = "",
        readOnly: Boolean = false,
        error: String? = null,
    ) : this(
        id = id,
        type = type,
        label = label.asText(),
        placeholder = placeholder?.asText(),
        value = value,
        readOnly = readOnly,
        error = error,
    )
}

public data class LoginAction(
    val id: String,
    val label: UiText,
    val enabled: Boolean = true,
) {
    public constructor(
        id: String,
        label: UiStrings,
        enabled: Boolean = true,
    ) : this(id = id, label = label.asText(), enabled = enabled)
}

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
        val probes: List<LoginCookieProbe> = emptyList(),
    ) : LoginEffect
}

/** Cookies the login WebView must read from one exact URL. */
public data class LoginCookieProbe(
    val url: String,
    val names: List<String>,
)

/** A cookie value associated with the exact URL from which the Host read it. */
public data class LoginCookieValue(
    val sourceUrl: String,
    val name: String,
    val value: String,
)

public data class LoginCookieSnapshot(
    val values: List<LoginCookieValue> = emptyList(),
    val rawHeader: String? = null,
) {
    internal fun legacyHeader(): String? =
        rawHeader?.takeIf(String::isNotBlank)
            ?: values
                .distinctBy(LoginCookieValue::name)
                .joinToString("; ") { "${it.name}=${it.value}" }
                .takeIf(String::isNotBlank)
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

    /**
     * Lets a login method inspect cookies before the Host closes its isolated WebView.
     * Existing built-in handlers keep receiving their legacy Cookie header.
     */
    public suspend fun checkCookies(snapshot: LoginCookieSnapshot): Boolean {
        val value = snapshot.legacyHeader() ?: return false
        if (!canResume(value)) return false
        resume(value)
        return true
    }

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
                        platformDisplayNameText = provider.metadata.displayNameText,
                        platformIconUrl = provider.metadata.iconUrl,
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
