package dev.dimension.flare.feature.plugin.login

import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.host.PluginUrlPolicy
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.manifest.CookieRequirementManifestV1
import dev.dimension.flare.feature.plugin.manifest.LoginInteractionV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimeKeyV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.feature.plugin.wire.CookieCheckRequestV1
import dev.dimension.flare.feature.plugin.wire.CookieSnapshotV1
import dev.dimension.flare.feature.plugin.wire.CookieValueV1
import dev.dimension.flare.feature.plugin.wire.LoginBeginRequestV1
import dev.dimension.flare.feature.plugin.wire.LoginSuccessV1
import dev.dimension.flare.feature.plugin.wire.LoginTransitionV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import io.ktor.http.URLProtocol
import io.ktor.http.parseUrl
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public data class PluginWebCookieRequestV1(
    val startUrl: String,
    val probes: List<PluginCookieProbeV1>,
)

public data class PluginCookieProbeV1(
    val url: String,
    val cookies: List<CookieRequirementManifestV1>,
)

public sealed interface PluginCookieCheckResultV1 {
    public data object Busy : PluginCookieCheckResultV1

    public data class AwaitingCookies(
        val missing: List<PluginCookieNameV1>,
    ) : PluginCookieCheckResultV1

    public data object Pending : PluginCookieCheckResultV1

    public data class Success(
        val value: LoginSuccessV1,
    ) : PluginCookieCheckResultV1
}

public data class PluginCookieNameV1(
    val sourceUrl: String,
    val name: String,
)

public object PluginWebCookieNavigationPolicyV1 {
    public fun isAllowed(url: String): Boolean = visibleOrigin(url) != null

    public fun visibleOrigin(url: String): String? =
        runCatching {
            require(url.length <= MAX_NAVIGATION_URL_LENGTH)
            val parsed = requireNotNull(parseUrl(url))
            require(parsed.protocol == URLProtocol.HTTPS && parsed.host.isNotBlank())
            require(parsed.user == null && parsed.password == null)
            "https://${parsed.host}${if (parsed.port == URLProtocol.HTTPS.defaultPort) "" else ":${parsed.port}"}"
        }.getOrNull()
}

public class PluginWebCookieLoginCoordinatorV1(
    private val runtimePool: PluginRuntimePool,
    private val entropy: PluginLoginEntropyV1 = PlatformPluginLoginEntropyV1,
) {
    public suspend fun begin(
        plugin: RunningPluginV1,
        methodId: String,
        origin: String,
        locale: String,
        expectedAccountId: String? = null,
    ): PluginWebCookieSessionV1 {
        val method = plugin.requireLoginMethod(methodId, LoginInteractionV1.WebCookie)
        val cookie = requireNotNull(method.cookie)
        val canonicalOrigin = PluginUrlPolicy.requireOrigin(origin)
        val context = plugin.loginContext(canonicalOrigin, locale)
        val request =
            PluginWebCookieRequestV1(
                startUrl = resolveManifestUrl(cookie.startUrl, canonicalOrigin, context.metadata.approvedOrigins),
                probes =
                    cookie.probes.map { probe ->
                        PluginCookieProbeV1(
                            url = resolveManifestUrl(probe.url, canonicalOrigin, context.metadata.approvedOrigins),
                            cookies = probe.cookies,
                        )
                    },
            )
        val flowId = entropy.newFlowId()
        val key = plugin.loginKey(flowId)
        try {
            val transition =
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
                        ),
                    requestSerializer = LoginBeginRequestV1.serializer(),
                    responseSerializer = LoginTransitionV1.serializer(),
                    validate = LoginTransitionV1::requireValid,
                )
            val webCookie =
                transition as? LoginTransitionV1.WebCookie
                    ?: throw PluginLoginException("login.transition", "Cookie login did not open a WebView")
            val returnedStart = PluginUrlPolicy.requireRequestUrl(webCookie.startUrl, context.metadata.approvedOrigins).toString()
            require(returnedStart == request.startUrl) { "Cookie login start URL differs from the manifest" }
            return PluginWebCookieSessionV1(
                runtimePool = runtimePool,
                plugin = plugin,
                methodId = methodId,
                flowId = flowId,
                context = context,
                key = key,
                request = request,
                expectedAccountId = expectedAccountId,
            )
        } catch (error: Throwable) {
            withContext(NonCancellable) { runtimePool.close(key) }
            throw error
        }
    }
}

public class PluginWebCookieSessionV1 internal constructor(
    private val runtimePool: PluginRuntimePool,
    private val plugin: RunningPluginV1,
    private val methodId: String,
    private val flowId: String,
    private val context: PluginInvocationContextV1,
    private val key: PluginRuntimeKeyV1,
    public val request: PluginWebCookieRequestV1,
    private val expectedAccountId: String?,
) {
    private val checkMutex = Mutex()
    private var closed = false

    /** Returns [PluginCookieCheckResultV1.Busy] instead of queuing overlapping poll ticks. */
    public suspend fun check(snapshot: CookieSnapshotV1): PluginCookieCheckResultV1 {
        if (!checkMutex.tryLock()) return PluginCookieCheckResultV1.Busy
        try {
            check(!closed) { "Cookie login session is closed" }
            snapshot.requireValid()
            val filtered = filter(snapshot)
            val present = filtered.cookies.associateBy { it.sourceUrl to it.name }
            val missing =
                request.probes.flatMap { probe ->
                    probe.cookies
                        .filter { it.required && (probe.url to it.name) !in present }
                        .map { PluginCookieNameV1(probe.url, it.name) }
                }
            if (missing.isNotEmpty()) return PluginCookieCheckResultV1.AwaitingCookies(missing)

            val transition =
                runtimePool.invoke(
                    plugin = plugin,
                    key = key,
                    context = context,
                    method = "login.$methodId.check",
                    request =
                        CookieCheckRequestV1(
                            methodId = methodId,
                            origin = context.metadata.origin,
                            flowId = flowId,
                            cookies = filtered,
                        ),
                    requestSerializer = CookieCheckRequestV1.serializer(),
                    responseSerializer = LoginTransitionV1.serializer(),
                    validate = LoginTransitionV1::requireValid,
                )
            return when (transition) {
                LoginTransitionV1.Pending -> {
                    PluginCookieCheckResultV1.Pending
                }

                is LoginTransitionV1.Success -> {
                    val success = plugin.requireLoginSuccess(transition.value, context.metadata.origin, expectedAccountId)
                    closed = true
                    withContext(NonCancellable) { runtimePool.close(key) }
                    PluginCookieCheckResultV1.Success(success)
                }

                is LoginTransitionV1.ExternalBrowser,
                is LoginTransitionV1.WebCookie,
                -> {
                    throw PluginLoginException("login.transition", "Cookie check returned an unsupported transition")
                }
            }
        } finally {
            checkMutex.unlock()
        }
    }

    public suspend fun close() {
        checkMutex.withLock {
            if (closed) return
            closed = true
            withContext(NonCancellable) { runtimePool.close(key) }
        }
    }

    private fun filter(snapshot: CookieSnapshotV1): CookieSnapshotV1 {
        val allowed = request.probes.associate { probe -> probe.url to probe.cookies.mapTo(linkedSetOf()) { it.name } }
        val cookies =
            snapshot.cookies.mapNotNull { cookie ->
                val canonicalUrl =
                    PluginUrlPolicy
                        .requireRequestUrl(cookie.sourceUrl, context.metadata.approvedOrigins)
                        .toString()
                if (cookie.name !in allowed[canonicalUrl].orEmpty()) {
                    null
                } else {
                    cookie.copy(sourceUrl = canonicalUrl)
                }
            }
        require(cookies.distinctBy { it.sourceUrl to it.name }.size == cookies.size) { "Duplicate Cookie value" }
        return CookieSnapshotV1(cookies.sortedWith(compareBy(CookieValueV1::sourceUrl, CookieValueV1::name))).also {
            it.requireValid()
        }
    }
}

private const val MAX_NAVIGATION_URL_LENGTH = 8_192
