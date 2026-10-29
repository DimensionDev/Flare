package dev.dimension.flare.feature.agent.common

import ai.koog.http.client.KoogHttpClientException
import androidx.compose.runtime.Immutable
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Immutable
@Serializable
public data class AgentFailure internal constructor(
    public val kind: AgentFailureKind,
    public val message: String,
    internal val retryMode: AgentRetryMode? = null,
    internal val userInput: String? = null,
    internal val supportingParts: List<AgentMessagePart> = emptyList(),
    internal val inputRequest: AgentPendingInputRequest? = null,
) {
    public val canRetry: Boolean get() = retryMode != null

    internal fun encode(): String = FAILURE_PREFIX + Json.encodeToString(this)

    public companion object {
        public fun fromThrowable(throwable: Throwable): AgentFailure = throwable.toAgentFailure()

        internal fun decode(value: String): AgentFailure =
            if (value.startsWith(FAILURE_PREFIX)) {
                runCatching { Json.decodeFromString<AgentFailure>(value.removePrefix(FAILURE_PREFIX)) }
                    .getOrElse { AgentFailure(AgentFailureKind.Unknown, "") }
            } else {
                AgentFailure(AgentFailureKind.Unknown, value.sanitizedAgentError())
            }
    }
}

@Serializable
public enum class AgentFailureKind {
    Network,
    RateLimited,
    Service,
    Configuration,
    Authentication,
    AccessDenied,
    Quota,
    Unknown,
    Interrupted,
}

@Serializable
internal enum class AgentRetryMode {
    Restart,
    Resume,
    Finish,
}

internal class AgentRetryableException(
    cause: Throwable,
    val retryMode: AgentRetryMode,
) : Exception(cause.message, cause) {
    var supportingParts: List<AgentMessagePart> = emptyList()
    var inputRequest: AgentPendingInputRequest? = null
}

internal fun Throwable.toAgentFailure(userInput: String? = null): AgentFailure {
    val causes = generateSequence(this) { it.cause }.take(16).toList()
    val modelFailure = causes.filterIsInstance<AgentRetryableException>().firstOrNull()
    causes
        .filterIsInstance<CancellationException>()
        .firstOrNull { modelFailure == null || it !is TimeoutCancellationException }
        ?.let { throw it }
    val http = causes.filterIsInstance<KoogHttpClientException>().firstOrNull()
    val unavailable = causes.any { it is FlareAgentUnavailableException }
    val body = http?.errorBody.orEmpty().take(16_384)
    val kind =
        when {
            unavailable -> AgentFailureKind.Configuration

            http?.statusCode == 401 -> AgentFailureKind.Authentication

            http?.statusCode == 403 -> AgentFailureKind.AccessDenied

            http?.statusCode == 402 || (
                http?.statusCode == 429 &&
                    body.contains(
                        "insufficient_quota",
                        ignoreCase = true,
                    )
            ) -> AgentFailureKind.Quota

            http?.statusCode == 429 -> AgentFailureKind.RateLimited

            http?.statusCode == 408 ||
                causes.any {
                    val name = it::class.simpleName.orEmpty()
                    name.contains("Timeout") ||
                        name in
                        listOf(
                            "UnknownHostException",
                            "UnresolvedAddressException",
                            "ConnectException",
                            "SocketException",
                            "DarwinHttpRequestException",
                            "SSLException",
                            "SSLHandshakeException",
                        )
                }
            -> AgentFailureKind.Network

            http != null && http.statusCode in 500..599 -> AgentFailureKind.Service

            else -> AgentFailureKind.Unknown
        }
    val raw = if (body.isNotBlank()) body.upstreamErrorMessage() else causes.lastOrNull { !it.message.isNullOrBlank() }?.message.orEmpty()
    return AgentFailure(
        kind = kind,
        message = raw.sanitizedAgentError(),
        retryMode =
            modelFailure?.retryMode
                ?: AgentRetryMode.Restart.takeIf { unavailable },
        userInput = userInput,
        supportingParts = modelFailure?.supportingParts.orEmpty(),
        inputRequest = modelFailure?.inputRequest,
    )
}

private fun String.upstreamErrorMessage(): String {
    val value = runCatching { Json.parseToJsonElement(this) }.getOrNull()
    if (value is JsonPrimitive) return value.contentOrNull ?: this
    if (value !is JsonObject) return this
    val error = value["error"]
    val source = error as? JsonObject ?: value
    return listOf(source["message"], source["detail"], source["error_description"], error)
        .filterIsInstance<JsonPrimitive>()
        .firstNotNullOfOrNull { it.contentOrNull?.takeIf(String::isNotBlank) } ?: this
}

internal fun String.sanitizedAgentError(): String =
    replace(Regex("(?i)((?:Bearer|Basic)\\s+)[^\\s\"',;<>]+"), "$1[redacted]")
        .replace(
            Regex("(?i)((?:api[_-]?key|access[_-]?token|authorization|password|secret)[\"']?\\s*[:=]\\s*[\"']?)[^\\s\"'&,;<>]+"),
            "$1[redacted]",
        ).replace(Regex("(?i)(https?://)[^/\\s@]+@"), "$1[redacted]@")
        .replace(Regex("\\bsk-[A-Za-z0-9_-]+"), "[redacted]")
        .filter { !it.isISOControl() || it == '\n' || it == '\t' }
        .trim()
        .take(2000)

private const val FAILURE_PREFIX = "flare-agent-failure:"
