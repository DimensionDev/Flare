package dev.dimension.flare.feature.agent.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal data class AgentVisibleResult(
    val text: String,
    val inputRequest: AgentInputRequest?,
)

internal fun resolveAgentVisibleResult(
    text: String,
    inputRequest: AgentPendingInputRequest?,
): AgentVisibleResult {
    val metadata = text.agentInputRequestUiMetadata()
    val visibleText =
        text
            .removeAgentInputRequestUiMetadata()
            .cleanAgentVisibleText()
    return AgentVisibleResult(
        text = visibleText,
        inputRequest = inputRequest?.toInputRequest(metadata),
    )
}

private fun AgentPendingInputRequest.toInputRequest(metadata: AgentInputRequestUiMetadata?): AgentInputRequest? {
    if (metadata?.requestId != null && metadata.requestId != requestId) {
        return null
    }
    val metadataById = metadata?.options.orEmpty().associateBy { it.id }
    return AgentInputRequest(
        requestId = requestId,
        options =
            options.map { option ->
                val optionMetadata = metadataById[option.id]
                val hasRenderablePreview = option.userPreview != null || option.postPreview != null
                AgentInputRequest.Option(
                    id = option.id,
                    label =
                        optionMetadata
                            ?.label
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?: if (hasRenderablePreview) "" else return null,
                    buttonType =
                        optionMetadata?.buttonType
                            ?: if (hasRenderablePreview) AgentInputRequestOptionButtonType.Secondary else return null,
                    value = option.value,
                    submit = option.submit,
                    userPreview = option.userPreview,
                    postPreview = option.postPreview,
                )
            },
        allowFreeText = allowFreeText,
        postPreview = postPreview,
        userPreview = userPreview,
    )
}

private fun String.agentInputRequestUiMetadata(): AgentInputRequestUiMetadata? =
    agentInputRequestUiMetadataRegex
        .findAll(this)
        .mapNotNull { match ->
            runCatching {
                agentInputRequestUiJson.decodeFromString<AgentInputRequestUiMetadata>(match.groupValues[1].trim())
            }.getOrNull()
        }.lastOrNull()

internal fun String.removeAgentInputRequestUiMetadata(): String = replace(agentInputRequestUiMetadataRegex, "")

@Serializable
private data class AgentInputRequestUiMetadata(
    val requestId: String? = null,
    val options: List<Option> = emptyList(),
) {
    @Serializable
    data class Option(
        val id: String,
        val label: String,
        val buttonType: AgentInputRequestOptionButtonType,
    )
}

private val agentInputRequestUiMetadataRegex =
    Regex("""<!--\s*flare-agent-actions\s*([\s\S]*?)\s*-->""", RegexOption.IGNORE_CASE)

private val agentInputRequestUiJson =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
