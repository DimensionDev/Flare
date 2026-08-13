package dev.dimension.flare.feature.plugin.host

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.parseUrl

internal object PluginUrlPolicy {
    fun requireOrigin(value: String): String {
        require(value.length <= MAX_URL_LENGTH) { "Origin is too long" }
        val url = parseUrl(value) ?: error("Invalid HTTPS origin")
        validateHttps(url)
        require(url.segments.isEmpty() && url.parameters.isEmpty() && !url.trailingQuery && url.fragment.isEmpty()) {
            "Origin must not contain a path, query, or fragment"
        }
        return url.origin()
    }

    fun requireRequestUrl(
        value: String,
        approvedOrigins: Set<String>,
    ): Url {
        require(value.length <= MAX_URL_LENGTH) { "URL is too long" }
        val url = parseUrl(value) ?: error("Invalid HTTPS URL")
        validateHttps(url)
        require(url.fragment.isEmpty()) { "HTTP URLs must not contain fragments" }
        require(url.origin() in approvedOrigins) { "HTTP origin is not approved" }
        return url
    }

    fun resolveRedirect(
        current: Url,
        location: String,
        approvedOrigins: Set<String>,
    ): Url {
        require(location.isNotBlank() && location.length <= MAX_URL_LENGTH) { "Invalid redirect URL" }
        val absolute =
            when {
                location.startsWith("https://", ignoreCase = true) -> {
                    location
                }

                location.startsWith("//") -> {
                    "https:$location"
                }

                location.startsWith("/") -> {
                    current.origin() + location
                }

                else -> {
                    val directory = current.encodedPath.substringBeforeLast('/', "")
                    current.origin() + directory + "/" + location
                }
            }
        return requireRequestUrl(absolute, approvedOrigins)
    }

    private fun validateHttps(url: Url) {
        require(url.protocol == URLProtocol.HTTPS && url.host.isNotBlank()) { "Only HTTPS URLs are allowed" }
        require(url.user == null && url.password == null) { "URL credentials are not allowed" }
    }

    private fun Url.origin(): String = "https://$host${if (port == URLProtocol.HTTPS.defaultPort) "" else ":$port"}"
}

private const val MAX_URL_LENGTH = 8_192
