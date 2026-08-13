package dev.dimension.flare.feature.plugin.host

import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.wire.HttpAuthorizationV1
import dev.dimension.flare.feature.plugin.wire.HttpBodyV1
import dev.dimension.flare.feature.plugin.wire.HttpMultipartPartV1
import dev.dimension.flare.feature.plugin.wire.HttpRequestV1
import dev.dimension.flare.feature.plugin.wire.HttpResponseV1
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

public interface PluginHttpTransport {
    /** Executes exactly one request without redirects, cookies, or logging. */
    public suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1
}

public data class PluginTransportRequestV1(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: PluginTransportBodyV1?,
    val timeoutMillis: Long,
)

public sealed interface PluginTransportBodyV1 {
    public data class Text(
        val value: String,
        val contentType: String,
    ) : PluginTransportBodyV1

    public data class Form(
        val values: Map<String, String>,
    ) : PluginTransportBodyV1

    public data class Multipart(
        val parts: List<PluginTransportMultipartPartV1>,
    ) : PluginTransportBodyV1
}

public sealed interface PluginTransportMultipartPartV1 {
    public val name: String

    public data class Text(
        override val name: String,
        val value: String,
        val contentType: String?,
    ) : PluginTransportMultipartPartV1

    public data class Asset(
        override val name: String,
        val value: PluginAsset,
        val fileName: String,
        val contentType: String,
    ) : PluginTransportMultipartPartV1
}

public data class PluginTransportResponseV1(
    val status: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
)

internal class PluginHttpHost(
    private val transport: PluginHttpTransport,
) {
    suspend fun execute(
        request: HttpRequestV1,
        context: PluginInvocationContextV1,
        callTimeoutMillis: Long,
    ): HttpResponseV1 {
        validateRequest(request, callTimeoutMillis)
        var currentUrl = PluginUrlPolicy.requireRequestUrl(request.url, context.metadata.approvedOrigins)
        val body = request.body?.resolve(context.assets)
        val headers = request.buildHeaders()
        val timeout = request.timeoutMillis ?: callTimeoutMillis
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val response =
                transport.execute(
                    PluginTransportRequestV1(
                        method = request.method.uppercase(),
                        url = currentUrl.toString(),
                        headers = headers,
                        body = body,
                        timeoutMillis = timeout,
                    ),
                )
            validateResponse(response)
            val location = response.headers.firstValue(HttpHeaders.Location)
            if (response.status !in REDIRECT_STATUSES || location == null) {
                return response.toWire()
            }
            require(redirectCount < MAX_REDIRECTS) { "Too many HTTP redirects" }
            require(request.method.equals("GET", true) || request.method.equals("HEAD", true)) {
                "Redirects for requests with a body are not supported"
            }
            val redirected = PluginUrlPolicy.resolveRedirect(currentUrl, location, context.metadata.approvedOrigins)
            require(redirected.origin() == currentUrl.origin()) { "Cross-origin redirects are not allowed" }
            currentUrl = redirected
        }
        error("Too many HTTP redirects")
    }
}

private fun validateRequest(
    request: HttpRequestV1,
    callTimeoutMillis: Long,
) {
    require(request.method.uppercase() in ALLOWED_METHODS) { "Unsupported HTTP method" }
    require(request.headers.size <= MAX_REQUEST_HEADERS) { "Too many request headers" }
    require(
        request.headers.keys
            .map(String::lowercase)
            .distinct()
            .size == request.headers.size,
    ) { "Duplicate request header" }
    var headerBytes = 0
    request.headers.forEach { (name, value) ->
        val lower = name.lowercase()
        require(HEADER_NAME.matches(name) && lower !in FORBIDDEN_HEADERS && !lower.startsWith("proxy-") && !lower.startsWith("sec-")) {
            "Request header is not allowed: $name"
        }
        require(value.length <= MAX_HEADER_VALUE && value.none { it == '\r' || it == '\n' || it == '\u0000' }) {
            "Invalid request header value"
        }
        headerBytes += name.length + value.length
    }
    require(headerBytes <= MAX_HEADER_BYTES) { "Request headers are too large" }
    require(request.cookies.size <= MAX_COOKIES) { "Too many HTTP cookies" }
    request.cookies.forEach { cookie ->
        require(COOKIE_NAME.matches(cookie.name) && cookie.value.length <= MAX_COOKIE_VALUE && cookie.value.all(::isCookieValueChar)) {
            "Invalid HTTP cookie"
        }
    }
    request.authorization?.let { authorization ->
        when (authorization) {
            is HttpAuthorizationV1.Bearer -> {
                require(authorization.token.isNotBlank() && authorization.token.length <= MAX_AUTH_VALUE) { "Invalid bearer token" }
                require(authorization.token.none(Char::isISOControl)) { "Invalid bearer token" }
            }

            is HttpAuthorizationV1.Basic -> {
                require(authorization.username.length <= MAX_AUTH_VALUE && authorization.password.length <= MAX_AUTH_VALUE) {
                    "Invalid basic authorization"
                }
                require(':' !in authorization.username && authorization.username.none(Char::isISOControl)) {
                    "Invalid basic authorization"
                }
                require(authorization.password.none(Char::isISOControl)) { "Invalid basic authorization" }
            }
        }
    }
    require(request.timeoutMillis == null || request.timeoutMillis in 1..callTimeoutMillis) { "Invalid HTTP timeout" }
    require(callTimeoutMillis in 1..PluginCallTimeoutV1.Extended.millis) { "Invalid call timeout" }
    if (request.method.equals("GET", true) || request.method.equals("HEAD", true)) {
        require(request.body == null) { "GET and HEAD requests cannot contain a body" }
    }
    request.body?.validate()
}

private fun HttpBodyV1.validate() {
    when (this) {
        is HttpBodyV1.Json -> {
            val encoded =
                PluginJsonV1.encodeToString(
                    kotlinx.serialization.json.JsonElement
                        .serializer(),
                    value,
                )
            require(
                encoded.encodeToByteArray().size <= MAX_TEXT_BODY,
            ) {
                "JSON request body is too large"
            }
        }

        is HttpBodyV1.Text -> {
            require(value.encodeToByteArray().size <= MAX_TEXT_BODY && CONTENT_TYPE.matches(contentType)) { "Invalid text request body" }
        }

        is HttpBodyV1.Form -> {
            require(values.size <= MAX_FORM_VALUES) { "Too many form values" }
            require(values.all { (key, value) -> key.length in 1..MAX_FORM_KEY && value.length <= MAX_FORM_VALUE }) {
                "Invalid form value"
            }
            require(values.entries.sumOf { it.key.encodeToByteArray().size + it.value.encodeToByteArray().size } <= MAX_TEXT_BODY) {
                "Form request body is too large"
            }
        }

        is HttpBodyV1.Multipart -> {
            require(parts.isNotEmpty() && parts.size <= MAX_MULTIPART_PARTS) { "Invalid multipart part count" }
            parts.forEach { part ->
                require(MULTIPART_NAME.matches(part.name)) { "Invalid multipart name" }
                when (part) {
                    is HttpMultipartPartV1.Text -> {
                        require(part.value.encodeToByteArray().size <= MAX_MULTIPART_TEXT) { "Multipart text is too large" }
                        require(part.contentType == null || CONTENT_TYPE.matches(part.contentType)) { "Invalid multipart content type" }
                    }

                    is HttpMultipartPartV1.Asset -> {
                        require(ASSET_HANDLE.matches(part.handle)) { "Invalid asset handle" }
                        require(part.fileName == null || part.fileName.length <= MAX_FILE_NAME) { "Invalid asset file name" }
                        require(part.contentType == null || CONTENT_TYPE.matches(part.contentType)) { "Invalid asset content type" }
                    }
                }
            }
        }
    }
}

private fun HttpBodyV1.resolve(assets: Map<String, PluginAsset>): PluginTransportBodyV1 =
    when (this) {
        is HttpBodyV1.Json -> {
            PluginTransportBodyV1.Text(
                value =
                    PluginJsonV1.encodeToString(
                        kotlinx.serialization.json.JsonElement
                            .serializer(),
                        value,
                    ),
                contentType = "application/json; charset=utf-8",
            )
        }

        is HttpBodyV1.Text -> {
            PluginTransportBodyV1.Text(value, contentType)
        }

        is HttpBodyV1.Form -> {
            PluginTransportBodyV1.Form(values)
        }

        is HttpBodyV1.Multipart -> {
            PluginTransportBodyV1.Multipart(
                parts.map { part ->
                    when (part) {
                        is HttpMultipartPartV1.Text -> {
                            PluginTransportMultipartPartV1.Text(part.name, part.value, part.contentType)
                        }

                        is HttpMultipartPartV1.Asset -> {
                            val asset = assets[part.handle] ?: throw PluginHostException("asset.denied", "Asset handle is not available")
                            PluginTransportMultipartPartV1.Asset(
                                name = part.name,
                                value = asset,
                                fileName = sanitizeFileName(part.fileName ?: asset.fileName),
                                contentType = part.contentType ?: asset.mimeType ?: "application/octet-stream",
                            )
                        }
                    }
                },
            )
        }
    }

@OptIn(ExperimentalEncodingApi::class)
private fun HttpRequestV1.buildHeaders(): Map<String, String> =
    buildMap {
        putAll(headers)
        authorization?.let { value ->
            when (value) {
                is HttpAuthorizationV1.Bearer -> {
                    put(HttpHeaders.Authorization, "Bearer ${value.token}")
                }

                is HttpAuthorizationV1.Basic -> {
                    val encoded = Base64.encode("${value.username}:${value.password}".encodeToByteArray())
                    put(HttpHeaders.Authorization, "Basic $encoded")
                }
            }
        }
        if (cookies.isNotEmpty()) put(HttpHeaders.Cookie, cookies.joinToString("; ") { "${it.name}=${it.value}" })
    }

private fun validateResponse(response: PluginTransportResponseV1) {
    require(response.status in 100..599) { "Invalid HTTP response status" }
    require(response.body.size <= MAX_RESPONSE_BYTES) { "HTTP response is too large" }
    require(response.headers.size <= MAX_RESPONSE_HEADERS) { "Too many HTTP response headers" }
    require(
        response.headers.keys
            .map(String::lowercase)
            .distinct()
            .size == response.headers.size,
    ) { "Duplicate HTTP response header" }
    var total = 0
    response.headers.forEach { (name, values) ->
        require(HEADER_NAME.matches(name) && values.size <= MAX_RESPONSE_HEADER_VALUES) { "Invalid HTTP response header" }
        values.forEach { value ->
            require(value.length <= MAX_HEADER_VALUE && value.none { it == '\r' || it == '\n' || it == '\u0000' }) {
                "Invalid HTTP response header value"
            }
            total += name.length + value.length
        }
    }
    require(total <= MAX_HEADER_BYTES) { "HTTP response headers are too large" }
}

private fun PluginTransportResponseV1.toWire(): HttpResponseV1 =
    HttpResponseV1(
        status = status,
        headers =
            headers
                .filterKeys { it.lowercase() !in HIDDEN_RESPONSE_HEADERS }
                .mapKeys { it.key.lowercase() },
        body = body.decodeToString(throwOnInvalidSequence = true),
    )

private fun Map<String, List<String>>.firstValue(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

private fun Url.origin(): String = PluginUrlPolicy.requireOrigin("https://$host${if (port == 443) "" else ":$port"}")

private fun sanitizeFileName(value: String?): String =
    value
        ?.map { character -> if (character.isLetterOrDigit() || character in "._-") character else '_' }
        ?.joinToString("")
        ?.take(MAX_FILE_NAME)
        ?.ifBlank { "upload" }
        ?: "upload"

private fun isCookieValueChar(character: Char): Boolean =
    character.code == 0x21 || character.code in 0x23..0x2b || character.code in 0x2d..0x3a || character.code in 0x3c..0x7e

private val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
private val REDIRECT_STATUSES =
    setOf(
        HttpStatusCode.MovedPermanently.value,
        HttpStatusCode.Found.value,
        HttpStatusCode.SeeOther.value,
        HttpStatusCode.TemporaryRedirect.value,
        HttpStatusCode.PermanentRedirect.value,
    )
private val FORBIDDEN_HEADERS =
    setOf(
        "authorization",
        "connection",
        "content-length",
        "cookie",
        "host",
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade",
    )
private val HIDDEN_RESPONSE_HEADERS = FORBIDDEN_HEADERS + setOf("set-cookie", "set-cookie2")
private val HEADER_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}")
private val COOKIE_NAME = HEADER_NAME
private val MULTIPART_NAME = Regex("[A-Za-z0-9_.-]{1,128}")
private val ASSET_HANDLE = Regex("[A-Za-z0-9_-]{1,256}")
private val CONTENT_TYPE = Regex("[A-Za-z0-9!#$&^_.+*-]+/[A-Za-z0-9!#$&^_.+*-]+(?:\\s*;\\s*[A-Za-z0-9_-]+=[^\\r\\n;]+)*")
private const val MAX_REDIRECTS = 5
private const val MAX_REQUEST_HEADERS = 32
private const val MAX_RESPONSE_HEADERS = 64
private const val MAX_RESPONSE_HEADER_VALUES = 16
private const val MAX_HEADER_VALUE = 8_192
private const val MAX_HEADER_BYTES = 64 * 1_024
private const val MAX_COOKIES = 64
private const val MAX_COOKIE_VALUE = 16 * 1_024
private const val MAX_AUTH_VALUE = 64 * 1_024
private const val MAX_TEXT_BODY = 2 * 1_024 * 1_024
private const val MAX_FORM_VALUES = 256
private const val MAX_FORM_KEY = 256
private const val MAX_FORM_VALUE = 64 * 1_024
private const val MAX_MULTIPART_PARTS = 64
private const val MAX_MULTIPART_TEXT = 256 * 1_024
private const val MAX_FILE_NAME = 512
internal const val MAX_RESPONSE_BYTES: Int = 4 * 1_024 * 1_024
