package dev.dimension.flare.data.network.authorization

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.URLBuilder
import io.ktor.http.encodeOAuth
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.use
import kotlin.random.Random
import kotlin.time.Clock

private const val OAUTH_CONSUMER_KEY = "oauth_consumer_key"
private const val OAUTH_NONCE = "oauth_nonce"
private const val OAUTH_SIGNATURE = "oauth_signature"
private const val OAUTH_SIGNATURE_METHOD = "oauth_signature_method"
private const val OAUTH_SIGNATURE_METHOD_VALUE = "HMAC-SHA1"
private const val OAUTH_TIMESTAMP = "oauth_timestamp"
private const val OAUTH_ACCESS_TOKEN = "oauth_token"
private const val OAUTH_VERSION = "oauth_version"
private const val OAUTH_VERSION_VALUE = "1.0"

internal class OAuthAuthorization(
    private val consumerKey: String,
    private val consumerSecret: String,
    private val accessToken: String? = null,
    private val accessSecret: String? = null,
    private val random: Random = Random.Default,
) : Authorization {
    override val hasAuthorization: Boolean = true

    private fun generateOAuthNonce(): String {
        val nonce = ByteArray(32).apply { random.nextBytes(this) }
        return ByteString.of(*nonce).base64().replace("\\W".toRegex(), "")
    }

    override fun getAuthorizationHeader(context: HttpRequestBuilder): String {
        val nonce = generateOAuthNonce()
        val timestamp =
            Clock.System
                .now()
                .epochSeconds
                .toString()

        val builder = URLBuilder()
        builder.parameters.appendAll(context.url.parameters.build())
        mapOf(
            OAUTH_CONSUMER_KEY to consumerKey,
            OAUTH_NONCE to nonce,
            OAUTH_SIGNATURE_METHOD to OAUTH_SIGNATURE_METHOD_VALUE,
            OAUTH_TIMESTAMP to timestamp,
            OAUTH_ACCESS_TOKEN to accessToken.orEmpty(),
            OAUTH_VERSION to OAUTH_VERSION_VALUE,
        ).forEach {
            if (it.value.isEmpty()) return@forEach
            builder.parameters.append(it.key, it.value)
        }
        val body = context.body
        if (body is FormDataContent) {
            builder.parameters.appendAll(body.formData)
        }

        val sortSigningBody =
            builder.parameters
                .build()
                .entries()
                .sortedBy { it.key }
                .joinToString(separator = "&") {
                    "${it.key}=${
                        when {
                            it.value.isEmpty() -> ""
                            it.value.size == 1 -> it.value.first()
                            else -> it.value.toString()
                        }.encodeOAuth()
                    }"
                }

        val signature =
            Buffer().use { base ->
                base.writeUtf8(context.method.value)
                base.writeByte('&'.code)
                base.writeUtf8(
                    context.url
                        .buildString()
                        .substringBefore('?')
                        .encodeOAuth(),
                )
                base.writeByte('&'.code)
                base.writeUtf8(sortSigningBody.encodeOAuth())

                val signingKey = "$consumerSecret&${accessSecret.orEmpty()}".encodeUtf8()
                base.hmacSha1(signingKey).base64()
            }

        return mapOf(
            OAUTH_CONSUMER_KEY to consumerKey,
            OAUTH_NONCE to nonce,
            OAUTH_SIGNATURE to signature,
            OAUTH_SIGNATURE_METHOD to OAUTH_SIGNATURE_METHOD_VALUE,
            OAUTH_TIMESTAMP to timestamp,
            OAUTH_ACCESS_TOKEN to accessToken.orEmpty(),
            OAUTH_VERSION to OAUTH_VERSION_VALUE,
        ).mapNotNull {
            if (it.value.isEmpty()) {
                null
            } else {
                "${it.key}=\"${it.value.encodeOAuth()}\""
            }
        }.joinToString(prefix = "OAuth ", separator = ", ")
    }
}
