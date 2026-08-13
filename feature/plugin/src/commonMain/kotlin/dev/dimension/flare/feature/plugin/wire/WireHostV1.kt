package dev.dimension.flare.feature.plugin.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
public data class HttpRequestV1(
    val method: String = "GET",
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: HttpBodyV1? = null,
    val timeoutMillis: Long? = null,
)

@Serializable
public sealed interface HttpBodyV1 {
    @Serializable
    @SerialName("json")
    public data class Json(
        val value: JsonElement,
    ) : HttpBodyV1

    @Serializable
    @SerialName("text")
    public data class Text(
        val value: String,
        val contentType: String = "text/plain; charset=utf-8",
    ) : HttpBodyV1

    @Serializable
    @SerialName("form")
    public data class Form(
        val values: Map<String, String>,
    ) : HttpBodyV1

    @Serializable
    @SerialName("multipart")
    public data class Multipart(
        val parts: List<HttpMultipartPartV1>,
    ) : HttpBodyV1
}

@Serializable
public sealed interface HttpMultipartPartV1 {
    public val name: String

    @Serializable
    @SerialName("text")
    public data class Text(
        override val name: String,
        val value: String,
        val contentType: String? = null,
    ) : HttpMultipartPartV1

    @Serializable
    @SerialName("asset")
    public data class Asset(
        override val name: String,
        val handle: String,
        val fileName: String? = null,
        val contentType: String? = null,
    ) : HttpMultipartPartV1
}

@Serializable
public data class HttpResponseV1(
    val status: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String,
)

@Serializable
public enum class PluginErrorCodeV1 {
    AuthenticationRequired,
    NotFound,
    Validation,
    RateLimited,
    Network,
    Remote,
    Unsupported,
    InvalidResponse,
    Cancelled,
}

@Serializable
public data class PluginErrorV1(
    val code: PluginErrorCodeV1,
    val message: WireTextV1,
    val retryAfterSeconds: Long? = null,
    val remoteCode: String? = null,
)
