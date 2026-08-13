package dev.dimension.flare.feature.plugin.host

import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.wire.HttpRequestV1
import dev.dimension.flare.feature.plugin.wire.WireLimitsV1
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.ByteString.Companion.toByteString

internal class PluginHostGateway(
    transport: PluginHttpTransport,
) {
    private val http = PluginHttpHost(transport)

    suspend fun call(
        operation: String,
        argumentsJson: String,
        context: PluginInvocationContextV1,
        callTimeoutMillis: Long,
    ): String {
        require(argumentsJson.encodeToByteArray().size <= MAX_HOST_ARGUMENT_BYTES) { "Host arguments are too large" }
        return try {
            val value =
                when (operation) {
                    "http.request" -> {
                        val request = PluginJsonV1.decodeFromString<HttpRequestV1>(argumentsJson)
                        PluginJsonV1.encodeToJsonElement(
                            dev.dimension.flare.feature.plugin.wire.HttpResponseV1
                                .serializer(),
                            http.execute(request, context, callTimeoutMillis),
                        )
                    }

                    "credential.read" -> {
                        requireEmptyArguments(argumentsJson)
                        val credential = context.credential ?: throw PluginHostException("credential.denied", "Credential is unavailable")
                        credential.read().also(::requireCredentialSize)
                    }

                    "credential.replace" -> {
                        val request = PluginJsonV1.decodeFromString<CredentialReplaceRequestV1>(argumentsJson)
                        requireCredentialSize(request.value)
                        val credential = context.credential ?: throw PluginHostException("credential.denied", "Credential is unavailable")
                        credential.replace(request.value)
                        JsonPrimitive(true)
                    }

                    "crypto.randomHex" -> {
                        val request = PluginJsonV1.decodeFromString<RandomRequestV1>(argumentsJson)
                        require(request.size in 1..MAX_RANDOM_BYTES) { "Invalid random byte count" }
                        JsonPrimitive(platformSecureRandom(request.size).toByteString().hex())
                    }

                    "crypto.uuid" -> {
                        requireEmptyArguments(argumentsJson)
                        JsonPrimitive(platformUuid())
                    }

                    "crypto.sha256" -> {
                        val request = PluginJsonV1.decodeFromString<HashRequestV1>(argumentsJson)
                        require(request.value.encodeToByteArray().size <= MAX_HASH_INPUT_BYTES) { "Hash input is too large" }
                        JsonPrimitive(
                            request.value
                                .encodeToByteArray()
                                .toByteString()
                                .sha256()
                                .hex(),
                        )
                    }

                    "locale.current" -> {
                        requireEmptyArguments(argumentsJson)
                        JsonPrimitive(context.metadata.locale)
                    }

                    else -> {
                        throw PluginHostException("host.unsupported", "Unknown Host operation")
                    }
                }
            success(value)
        } catch (error: CancellationException) {
            throw error
        } catch (error: PluginHostException) {
            failure(error.code, error.message ?: "Host operation failed")
        } catch (error: IllegalArgumentException) {
            failure("host.invalid-request", error.message ?: "Invalid Host request")
        } catch (_: Throwable) {
            failure("host.unavailable", "Host operation failed")
        }
    }
}

@Serializable
private data class CredentialReplaceRequestV1(
    val value: JsonElement,
)

@Serializable
private data class RandomRequestV1(
    val size: Int,
)

@Serializable
private data class HashRequestV1(
    val value: String,
)

private fun requireEmptyArguments(value: String) {
    val element = PluginJsonV1.parseToJsonElement(value)
    require(element is JsonObject && element.isEmpty()) { "Host operation does not accept arguments" }
}

private fun requireCredentialSize(value: JsonElement) {
    require(
        PluginJsonV1.encodeToString(JsonElement.serializer(), value).encodeToByteArray().size <=
            WireLimitsV1.MAX_CREDENTIAL_BYTES,
    ) { "Credential is too large" }
}

private fun success(value: JsonElement): String =
    buildJsonObject {
        put("ok", true)
        put("value", value)
    }.toString()

private fun failure(
    code: String,
    message: String,
): String =
    buildJsonObject {
        put("ok", false)
        put(
            "error",
            buildJsonObject {
                put("code", code)
                put("message", message.take(MAX_HOST_ERROR_LENGTH))
            },
        )
    }.toString()

private const val MAX_HOST_ARGUMENT_BYTES = 5 * 1_024 * 1_024
private const val MAX_HASH_INPUT_BYTES = 1024 * 1_024
private const val MAX_RANDOM_BYTES = 1_024
private const val MAX_HOST_ERROR_LENGTH = 512
