package dev.dimension.flare.feature.plugin.routing

import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.host.PluginUrlPolicy
import dev.dimension.flare.feature.plugin.login.accountHost
import dev.dimension.flare.feature.plugin.manifest.DeepLinkManifestV1
import dev.dimension.flare.feature.plugin.manifest.DeepLinkPathSegmentV1
import dev.dimension.flare.feature.plugin.manifest.DeepLinkTargetTypeV1
import dev.dimension.flare.feature.plugin.manifest.PluginManifestV1
import io.ktor.http.Url
import io.ktor.http.parseUrl

public sealed interface PluginManifestRouteV1 {
    public data class Profile(
        val value: String,
        val host: String,
    ) : PluginManifestRouteV1

    public data class Post(
        val id: String,
        val host: String,
    ) : PluginManifestRouteV1

    public data class Timeline(
        val timelineId: String,
    ) : PluginManifestRouteV1

    public data class Browser(
        val url: String,
    ) : PluginManifestRouteV1
}

/** Declarative matcher: this class has no Runtime or network dependency by design. */
public class PluginManifestDeepLinkRouterV1(
    private val manifest: PluginManifestV1,
) {
    public fun route(
        url: String,
        accountOrigin: String,
    ): PluginManifestRouteV1? {
        if (url.length > MAX_URL_LENGTH) return null
        val parsed = runCatching { parseUrl(url) }.getOrNull() ?: return null
        if (parsed.user != null || parsed.password != null) return null
        val canonicalAccountOrigin = runCatching { PluginUrlPolicy.requireOrigin(accountOrigin) }.getOrNull() ?: return null
        val accountHost = Url(canonicalAccountOrigin).accountHost()
        return manifest.platform.deepLinks.firstNotNullOfOrNull { rule ->
            match(rule, parsed, url, canonicalAccountOrigin, accountHost)
        }
    }

    private fun match(
        rule: DeepLinkManifestV1,
        url: Url,
        originalUrl: String,
        accountOrigin: String,
        accountHost: String,
    ): PluginManifestRouteV1? {
        val origin = if (rule.origin == PluginAbiV1.ACCOUNT_ORIGIN) accountOrigin else rule.origin
        if (runCatching { PluginUrlPolicy.requireRequestUrl(originalUrl, setOf(origin)) }.isFailure) return null
        if (url.segments.size != rule.path.size) return null
        val captures = mutableMapOf<String, String>()
        rule.path.zip(url.segments).forEach { (expected, actual) ->
            when (expected) {
                is DeepLinkPathSegmentV1.Literal -> {
                    if (expected.value != actual) return null
                }

                is DeepLinkPathSegmentV1.Capture -> {
                    if (actual.isEmpty() || actual.length > MAX_CAPTURE_LENGTH) return null
                    captures[expected.name] = actual
                }
            }
        }
        return when (rule.target.type) {
            DeepLinkTargetTypeV1.Profile -> {
                PluginManifestRouteV1.Profile(
                    value = render(requireNotNull(rule.target.value), captures),
                    host = accountHost,
                )
            }

            DeepLinkTargetTypeV1.Post -> {
                PluginManifestRouteV1.Post(
                    id = render(requireNotNull(rule.target.value), captures),
                    host = accountHost,
                )
            }

            DeepLinkTargetTypeV1.Timeline -> {
                PluginManifestRouteV1.Timeline(requireNotNull(rule.target.value))
            }

            DeepLinkTargetTypeV1.Browser -> {
                PluginManifestRouteV1.Browser(originalUrl)
            }
        }
    }

    private fun render(
        template: String,
        captures: Map<String, String>,
    ): String {
        val value = CAPTURE.replace(template) { match -> captures.getValue(match.groupValues[1]) }
        require(value.isNotEmpty() && value.length <= MAX_CAPTURE_LENGTH) { "Invalid Deep Link target" }
        return value
    }
}

private const val MAX_URL_LENGTH = 8_192
private const val MAX_CAPTURE_LENGTH = 4_096
private val CAPTURE = Regex("\\{([A-Za-z][A-Za-z0-9_.-]{0,127})}")
