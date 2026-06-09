package dev.dimension.flare.feature.agent.common

import dev.dimension.flare.ui.model.UiProfile

internal suspend fun AgentToolSession.setUserSelectionRequest(
    users: List<UiProfile>,
    requestType: String,
): AgentPendingInputRequest {
    val candidates =
        users
            .distinctBy { it.platformType to it.key }
            .take(AGENT_USER_SELECTION_OPTION_LIMIT)
    val request =
        AgentPendingInputRequest(
            requestId = "user-selection:${candidates.joinToString { it.agentAttachmentRef() }}",
            options =
                candidates.map { user ->
                    AgentPendingInputRequest.Option(
                        id = "user:${user.agentAttachmentRef()}",
                        value = user.userSelectionValue(requestType),
                        userPreview = user,
                    )
                },
            allowFreeText = true,
        )
    inputRequestStore.set(request)
    return request
}

internal fun userSelectionRequestToolText(
    event: String,
    requestType: String,
    request: AgentPendingInputRequest,
    candidates: List<UiProfile>,
): String =
    buildString {
        appendLine("event=$event")
        appendLine("requestType=$requestType")
        appendLine("inputRequestId=${request.requestId}")
        appendLine("inputRequestOptions:")
        candidates
            .distinctBy { it.platformType to it.key }
            .take(AGENT_USER_SELECTION_OPTION_LIMIT)
            .forEach { user ->
                appendLine("- optionId=user:${user.agentAttachmentRef()}")
                appendLine("  optionKind=user")
                appendLine("  platform=${user.platformType.name}")
                appendLine("  userKey=${user.key}")
                appendLine("  displayName=${user.name.raw}")
                appendLine("  handle=${user.handle.raw}")
            }
    }.trim()

private fun UiProfile.userSelectionValue(requestType: String): String =
    buildString {
        appendLine("event=user_selected")
        appendLine("requestType=$requestType")
        appendLine("userRef=${agentAttachmentMarker()}")
        appendLine("userId=${key.id}")
        appendLine("userHost=${key.host}")
        appendLine("platform=${platformType.name}")
        appendLine("displayName=${name.raw}")
        appendLine("handle=${handle.raw}")
    }

private const val AGENT_USER_SELECTION_OPTION_LIMIT = 8
