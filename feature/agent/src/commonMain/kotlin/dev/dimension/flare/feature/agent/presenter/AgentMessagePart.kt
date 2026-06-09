package dev.dimension.flare.feature.agent.presenter

import androidx.compose.runtime.Immutable
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.common.agentAttachmentRef
import dev.dimension.flare.feature.agent.common.agentDisplayPost
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public sealed interface AgentMessagePart {
    @Serializable
    @Immutable
    public data class Text(
        val markdown: String,
    ) : AgentMessagePart

    @Serializable
    @Immutable
    public data class PostCard(
        val post: UiTimelineV2.Post,
    ) : AgentMessagePart

    @Serializable
    @Immutable
    public data class UserCard(
        val user: UiProfile,
    ) : AgentMessagePart

    @Serializable
    @Immutable
    public data class Actions(
        val request: AgentInputRequest,
        val selected: Boolean = false,
        val selectedOptionId: String? = null,
    ) : AgentMessagePart
}

internal fun String.toAgentTextParts(): ImmutableList<AgentMessagePart> =
    trim()
        .takeIf { it.isNotEmpty() }
        ?.let { persistentListOf(AgentMessagePart.Text(it)) }
        ?: persistentListOf()

internal fun parseAgentMessageParts(
    text: String,
    supportingParts: List<AgentMessagePart>,
): ImmutableList<AgentMessagePart> {
    if (supportingParts.isEmpty()) {
        return text.toAgentTextParts()
    }
    val posts =
        supportingParts
            .filterIsInstance<AgentMessagePart.PostCard>()
            .flatMap { part ->
                part.post.agentAttachmentRefAliases().map { ref -> ref to part }
            }.toMap()
    val users =
        supportingParts
            .filterIsInstance<AgentMessagePart.UserCard>()
            .flatMap { part ->
                part.user.agentAttachmentRefAliases().map { ref -> ref to part }
            }.toMap()
    val parts = mutableListOf<AgentMessagePart>()
    var cursor = 0

    attachmentRefRegex.findAll(text).forEach { match ->
        appendTextPart(text.substring(cursor, match.range.first), parts)
        val type = match.groupValues[1].lowercase()
        val ref = match.groupValues[2].trim().lowercase()
        when (type) {
            "post" -> posts[ref]?.post?.agentDisplayPost()?.let { parts += AgentMessagePart.PostCard(it) }
            "user" -> users[ref]?.user?.let { parts += AgentMessagePart.UserCard(it) }
        }
        cursor = match.range.last + 1
    }
    appendTextPart(text.substring(cursor), parts)

    return parts.takeIf { it.isNotEmpty() }?.toImmutableList() ?: text.toAgentTextParts()
}

internal fun buildAgentMessageParts(
    text: String,
    supportingParts: List<AgentMessagePart>,
    inputRequest: AgentInputRequest?,
    selected: Boolean = false,
    selectedOptionId: String? = null,
): ImmutableList<AgentMessagePart> {
    val parts = parseAgentMessageParts(text, supportingParts).toMutableList()
    if (inputRequest != null) {
        parts +=
            AgentMessagePart.Actions(
                request = inputRequest,
                selected = selected,
                selectedOptionId = selectedOptionId,
            )
    }
    return parts.toImmutableList()
}

internal fun List<AgentMessagePart>.markAgentInputRequestSelected(
    requestId: String,
    optionId: String,
): ImmutableList<AgentMessagePart> =
    map { part ->
        if (part is AgentMessagePart.Actions && part.request.requestId == requestId) {
            part.copy(
                selected = true,
                selectedOptionId = optionId,
            )
        } else {
            part
        }
    }.toImmutableList()

internal fun List<AgentMessagePart>.agentMessageText(): String =
    filterIsInstance<AgentMessagePart.Text>()
        .joinToString(separator = "\n\n") { it.markdown }
        .trim()

internal fun List<AgentMessagePart>.latestOpenAgentInputRequest(): AgentInputRequest? =
    filterIsInstance<AgentMessagePart.Actions>()
        .lastOrNull { !it.selected }
        ?.request

internal fun List<AgentMessagePart>.latestOpenAgentInputRequestForOption(option: AgentInputRequest.Option): AgentInputRequest? =
    filterIsInstance<AgentMessagePart.Actions>()
        .lastOrNull { actions ->
            !actions.selected &&
                actions.request.options.any { requestOption ->
                    requestOption.id == option.id && requestOption.value == option.value
                }
        }?.request

internal fun AgentMessagePart.agentMessagePartIdentity(): String =
    when (this) {
        is AgentMessagePart.PostCard -> {
            val post = post.agentDisplayPost()
            "post:${post.platformType}:${post.statusKey}"
        }

        is AgentMessagePart.UserCard -> {
            "user:${user.platformType}:${user.key}"
        }

        is AgentMessagePart.Actions -> {
            "actions:${request.requestId}"
        }

        is AgentMessagePart.Text -> {
            "text:$markdown"
        }
    }

internal fun Iterable<AgentMessagePart>.distinctAgentMessageParts(): List<AgentMessagePart> = distinctBy { it.agentMessagePartIdentity() }

private fun UiTimelineV2.Post.agentAttachmentRefAliases(): Set<String> =
    buildSet {
        addAll(
            attachmentRefAliases(
                canonicalRef = agentAttachmentRef(),
                platformName = platformType.name,
                key = statusKey,
            ),
        )
        val displayPost = agentDisplayPost()
        if (displayPost != this@agentAttachmentRefAliases) {
            addAll(
                attachmentRefAliases(
                    canonicalRef = displayPost.agentAttachmentRef(),
                    platformName = displayPost.platformType.name,
                    key = displayPost.statusKey,
                ),
            )
        }
    }

private fun UiProfile.agentAttachmentRefAliases(): Set<String> =
    attachmentRefAliases(
        canonicalRef = agentAttachmentRef(),
        platformName = platformType.name,
        key = key,
    )

private fun attachmentRefAliases(
    canonicalRef: String,
    platformName: String,
    key: MicroBlogKey,
): Set<String> =
    buildSet {
        add(canonicalRef)
        add(key.toString())
        add("${key.host}:${key.id}")
        add("${key.id}:${key.host}")
        add("$platformName:$key")
        add("$platformName:${key.host}:${key.id}")
        add("$platformName:${key.id}:${key.host}")
    }.flatMap { ref ->
        listOf(ref, ref.lowercase())
    }.toSet()

private fun appendTextPart(
    text: String,
    parts: MutableList<AgentMessagePart>,
) {
    val value = text.trim()
    if (value.isNotEmpty()) {
        parts += AgentMessagePart.Text(value)
    }
}

private val attachmentRefRegex = Regex("""\[\[\s*(post|user)\s*[:：]\s*([^\]]+?)\s*]]""", RegexOption.IGNORE_CASE)
