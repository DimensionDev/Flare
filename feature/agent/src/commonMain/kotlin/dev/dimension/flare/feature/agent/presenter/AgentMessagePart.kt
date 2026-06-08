package dev.dimension.flare.feature.agent.presenter

import androidx.compose.runtime.Immutable
import dev.dimension.flare.feature.agent.common.AgentConversationAttachment
import dev.dimension.flare.feature.agent.common.agentAttachmentRef
import dev.dimension.flare.feature.agent.common.agentDisplayPost
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
public sealed interface AgentMessagePart {
    @Immutable
    public data class Text(
        val markdown: String,
    ) : AgentMessagePart

    @Immutable
    public data class PostCard(
        val post: UiTimelineV2.Post,
    ) : AgentMessagePart

    @Immutable
    public data class UserCard(
        val user: UiProfile,
    ) : AgentMessagePart
}

internal fun String.toAgentTextParts(): ImmutableList<AgentMessagePart> =
    trim()
        .takeIf { it.isNotEmpty() }
        ?.let { persistentListOf(AgentMessagePart.Text(it)) }
        ?: persistentListOf()

internal fun parseAgentMessageParts(
    text: String,
    attachments: List<AgentConversationAttachment>,
): ImmutableList<AgentMessagePart> {
    if (attachments.isEmpty()) {
        return text.toAgentTextParts()
    }
    val posts =
        attachments
            .filterIsInstance<AgentConversationAttachment.Post>()
            .flatMap { attachment ->
                attachment.post.agentAttachmentRefAliases().map { ref -> ref to attachment }
            }.toMap()
    val users =
        attachments
            .filterIsInstance<AgentConversationAttachment.User>()
            .flatMap { attachment ->
                attachment.user.agentAttachmentRefAliases().map { ref -> ref to attachment }
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
