package dev.dimension.flare.feature.plugin.login

import dev.dimension.flare.feature.plugin.host.PluginCallTimeoutV1
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.host.PluginUrlPolicy
import dev.dimension.flare.feature.plugin.host.platformSecureRandom
import dev.dimension.flare.feature.plugin.host.platformUuid
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.manifest.LoginInteractionV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimeKeyV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.feature.plugin.wire.LoginBeginRequestV1
import dev.dimension.flare.feature.plugin.wire.LoginResumeRequestV1
import dev.dimension.flare.feature.plugin.wire.LoginSuccessV1
import dev.dimension.flare.feature.plugin.wire.LoginTransitionV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import io.ktor.http.parseUrl
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import kotlin.time.Clock

public sealed interface PluginOAuthStartV1 {
    public data class ExternalBrowser(
        val flowId: String,
        val url: String,
    ) : PluginOAuthStartV1

    public data class Success(
        val value: LoginSuccessV1,
    ) : PluginOAuthStartV1
}

public data class PluginOAuthCallbackV1(
    val flowId: String,
    val parameters: Map<String, String>,
)

public interface PluginLoginEntropyV1 {
    public fun newFlowId(): String

    public fun newState(): String
}

public object PlatformPluginLoginEntropyV1 : PluginLoginEntropyV1 {
    override fun newFlowId(): String = platformUuid()

    override fun newState(): String = platformSecureRandom(OAUTH_STATE_BYTES).toByteString().hex()
}

public class PluginOAuthLoginCoordinatorV1(
    private val runtimePool: PluginRuntimePool,
    private val pendingStore: PluginOAuthPendingStoreV1,
    private val runningPlugin: (pluginId: String) -> RunningPluginV1?,
    private val entropy: PluginLoginEntropyV1 = PlatformPluginLoginEntropyV1,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    public suspend fun begin(
        plugin: RunningPluginV1,
        methodId: String,
        origin: String,
        locale: String,
        values: Map<String, String> = emptyMap(),
        expectedAccountId: String? = null,
    ): PluginOAuthStartV1 {
        plugin.requireLoginMethod(methodId, LoginInteractionV1.OAuth)
        require(values.size <= 32 && values.all { (key, value) -> FIELD_ID.matches(key) && value.length <= 16_384 }) {
            "Invalid OAuth login values"
        }
        val canonicalOrigin = PluginUrlPolicy.requireOrigin(origin)
        val flowId = entropy.newFlowId()
        val state = entropy.newState()
        val redirectUri = pluginOAuthRedirectUri(flowId)
        pendingStore.pruneCreatedBefore((nowEpochMillis() - OAUTH_TTL_MILLIS).coerceAtLeast(0))
        val context = plugin.loginContext(canonicalOrigin, locale)
        val key = plugin.loginKey(flowId)
        val transition =
            try {
                runtimePool.invoke(
                    plugin = plugin,
                    key = key,
                    context = context,
                    method = "login.$methodId.begin",
                    request =
                        LoginBeginRequestV1(
                            methodId = methodId,
                            origin = canonicalOrigin,
                            flowId = flowId,
                            state = state,
                            redirectUri = redirectUri,
                            values = values,
                        ),
                    requestSerializer = LoginBeginRequestV1.serializer(),
                    responseSerializer = LoginTransitionV1.serializer(),
                    timeout = PluginCallTimeoutV1.Extended,
                    validate = LoginTransitionV1::requireValid,
                )
            } finally {
                withContext(NonCancellable) { runtimePool.close(key) }
            }
        return when (transition) {
            is LoginTransitionV1.ExternalBrowser -> {
                PluginUrlPolicy.requireRequestUrl(transition.url, context.metadata.approvedOrigins)
                val pending =
                    PluginOAuthPendingV1(
                        pluginId = plugin.installed.pluginId,
                        platformId = plugin.installed.manifest.platform.id,
                        packageHash = plugin.installed.packageHash,
                        methodId = methodId,
                        flowId = flowId,
                        origin = canonicalOrigin,
                        state = state,
                        redirectUri = redirectUri,
                        createdAtEpochMillis = nowEpochMillis(),
                        pendingPayload = transition.pendingPayload,
                        expectedAccountId = expectedAccountId,
                    )
                pendingStore.save(pending)
                PluginOAuthStartV1.ExternalBrowser(flowId = flowId, url = transition.url)
            }

            is LoginTransitionV1.Success -> {
                PluginOAuthStartV1.Success(
                    plugin.requireLoginSuccess(transition.value, canonicalOrigin, expectedAccountId),
                )
            }

            LoginTransitionV1.Pending,
            is LoginTransitionV1.WebCookie,
            -> {
                throw PluginLoginException("login.transition", "OAuth begin returned an unsupported transition")
            }
        }
    }

    public suspend fun resume(
        callbackUrl: String,
        locale: String,
    ): LoginSuccessV1 {
        val callback = parsePluginOAuthCallback(callbackUrl)
        val pending = pendingStore.load(callback.flowId) ?: throw PluginLoginException("oauth.missing", "OAuth flow was not found")
        val callbackState = callback.parameters["state"]
        if (callbackState == null || !constantTimeEquals(callbackState, pending.state)) {
            throw PluginLoginException("oauth.state", "OAuth state does not match")
        }
        val age = nowEpochMillis() - pending.createdAtEpochMillis
        if (age !in 0..OAUTH_TTL_MILLIS) {
            pendingStore.consume(pending)
            throw PluginLoginException("oauth.expired", "OAuth flow has expired")
        }
        val plugin = runningPlugin(pending.pluginId)
        if (
            plugin == null ||
            plugin.installed.packageHash != pending.packageHash ||
            plugin.installed.manifest.platform.id != pending.platformId
        ) {
            pendingStore.consume(pending)
            throw PluginLoginException("oauth.package", "The plugin changed while OAuth was in progress")
        }
        plugin.requireLoginMethod(pending.methodId, LoginInteractionV1.OAuth)
        require(PluginUrlPolicy.requireOrigin(pending.origin) == pending.origin) { "Invalid OAuth pending origin" }
        require(pending.redirectUri == pluginOAuthRedirectUri(pending.flowId)) { "Invalid OAuth pending redirect URI" }
        if (!pendingStore.consume(pending)) throw PluginLoginException("oauth.replay", "OAuth callback was already consumed")

        val context = plugin.loginContext(pending.origin, locale)
        val key = plugin.loginKey(pending.flowId)
        val transition =
            try {
                runtimePool.invoke(
                    plugin = plugin,
                    key = key,
                    context = context,
                    method = "login.${pending.methodId}.resume",
                    request =
                        LoginResumeRequestV1(
                            methodId = pending.methodId,
                            origin = pending.origin,
                            flowId = pending.flowId,
                            redirectUri = pending.redirectUri,
                            callbackParameters = callback.parameters,
                            pendingPayload = pending.pendingPayload,
                        ),
                    requestSerializer = LoginResumeRequestV1.serializer(),
                    responseSerializer = LoginTransitionV1.serializer(),
                    timeout = PluginCallTimeoutV1.Extended,
                    validate = LoginTransitionV1::requireValid,
                )
            } finally {
                withContext(NonCancellable) { runtimePool.close(key) }
            }
        val success =
            transition as? LoginTransitionV1.Success
                ?: throw PluginLoginException("login.transition", "OAuth resume did not complete login")
        return plugin.requireLoginSuccess(success.value, pending.origin, pending.expectedAccountId)
    }
}

public fun parsePluginOAuthCallback(value: String): PluginOAuthCallbackV1 {
    require(value.length <= MAX_CALLBACK_URL_LENGTH) { "OAuth callback URL is too long" }
    val url = parseUrl(value) ?: throw PluginLoginException("oauth.callback", "Invalid OAuth callback URL")
    require(url.protocol.name.equals("flare", ignoreCase = true)) { "Invalid OAuth callback scheme" }
    require(url.host.equals("Callback", ignoreCase = true)) { "Invalid OAuth callback host" }
    require(url.user == null && url.password == null && url.fragment.isEmpty()) { "Invalid OAuth callback URL" }
    require(url.segments.size == 3 && url.segments[0] == "SignIn" && url.segments[1] == "Plugin") {
        "Invalid OAuth callback path"
    }
    val flowId = url.segments[2]
    pluginOAuthRedirectUri(flowId)
    val entries = url.parameters.entries()
    require(entries.size <= MAX_CALLBACK_PARAMETERS) { "Too many OAuth callback parameters" }
    val parameters =
        entries.associate { (name, values) ->
            require(FIELD_ID.matches(name) && values.size == 1 && values.single().length <= MAX_CALLBACK_PARAMETER_LENGTH) {
                "Invalid OAuth callback parameter"
            }
            name to values.single()
        }
    return PluginOAuthCallbackV1(flowId = flowId, parameters = parameters)
}

internal fun RunningPluginV1.loginContext(
    origin: String,
    locale: String,
): PluginInvocationContextV1 =
    PluginInvocationContextV1.login(
        pluginId = installed.pluginId,
        platformId = installed.manifest.platform.id,
        packageHash = installed.packageHash,
        candidateOrigin = origin,
        authOrigins = installed.manifest.permissions.authOrigins,
        locale = locale,
    )

internal fun RunningPluginV1.loginKey(flowId: String): PluginRuntimeKeyV1 =
    PluginRuntimeKeyV1.login(installed.pluginId, installed.packageHash, flowId)

private fun constantTimeEquals(
    first: String,
    second: String,
): Boolean {
    val left = first.encodeToByteArray()
    val right = second.encodeToByteArray()
    var difference = left.size xor right.size
    val length = maxOf(left.size, right.size)
    repeat(length) { index ->
        difference = difference or ((left.getOrElse(index) { 0 }.toInt()) xor (right.getOrElse(index) { 0 }.toInt()))
    }
    return difference == 0
}

private const val OAUTH_STATE_BYTES = 32
private const val OAUTH_TTL_MILLIS = 15 * 60 * 1_000L
private const val MAX_CALLBACK_URL_LENGTH = 16 * 1_024
private const val MAX_CALLBACK_PARAMETERS = 32
private const val MAX_CALLBACK_PARAMETER_LENGTH = 8 * 1_024
private val FIELD_ID = Regex("[A-Za-z][A-Za-z0-9_.-]{0,127}")
