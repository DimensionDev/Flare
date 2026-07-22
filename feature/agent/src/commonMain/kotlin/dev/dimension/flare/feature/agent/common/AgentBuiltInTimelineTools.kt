package dev.dimension.flare.feature.agent.common

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.serialization.Serializable

internal class ListBuiltInTimelinesTool(
    private val session: AgentToolSession,
) : SimpleTool<ListBuiltInTimelinesTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description = "List built-in timeline tabs exposed by the user's signed-in social accounts.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Platform names to filter by. Leave empty to list every built-in timeline.")
        val platforms: List<String> = emptyList(),
        @property:LLMDescription("Optional title, timeline id, or spec id substring to filter timelines.")
        val query: String = "",
    )

    override suspend fun execute(args: Args): String {
        val query = args.query.trim()
        val targets =
            session.builtInTimelineTargets
                .filterBuiltInTimelineTargetsByPlatformNames(args.platforms)
                .filter { target ->
                    query.isBlank() ||
                        target.id.contains(query, ignoreCase = true) ||
                        target.specId.contains(query, ignoreCase = true) ||
                        target.title.contains(query, ignoreCase = true)
                }
        if (targets.isEmpty()) {
            return noBuiltInTimelineTargetsMessage(args.platforms)
        }
        return buildString {
            appendLine("Built-in timelines")
            appendLine("source: builtInTimelineTabs from signed-in account data sources")
            appendLine()
            targets.forEachIndexed { index, target ->
                appendLine("Timeline #${index + 1}")
                append(target.toBuiltInTimelineToolText())
                appendLine()
            }
        }.take(MAX_BUILT_IN_TIMELINE_TOOL_RESULT_LENGTH)
    }

    companion object {
        const val NAME = "list_builtin_timelines"
    }
}

internal class LoadBuiltInTimelineTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadBuiltInTimelineTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description = "Load timeline items from a built-in timeline exposed by a signed-in account.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Exact timelineId returned by list_builtin_timelines. Preferred when available.")
        val timelineId: String = "",
        @property:LLMDescription("Timeline spec id, such as common.home or mastodon.local. Use only when timelineId is unavailable.")
        val specId: String = "",
        @property:LLMDescription("Timeline title, such as Home, Discover, Bookmark, Favourite, Local, Public, Featured, or Liked.")
        val title: String = "",
        @property:LLMDescription("Account id to load from. Leave blank only when other fields resolve to exactly one timeline.")
        val accountId: String = "",
        @property:LLMDescription("Account host. Leave blank only when the account key has no host.")
        val accountHost: String = "",
        @property:LLMDescription("Platform names to narrow timeline selection.")
        val platforms: List<String> = emptyList(),
        @property:LLMDescription("Maximum timeline items to return. Defaults to 20 and is capped at 50.")
        val maxItems: Int = DEFAULT_BUILT_IN_TIMELINE_TOOL_ITEMS,
    )

    override suspend fun execute(args: Args): String {
        val target =
            session.builtInTimelineTargets.resolveBuiltInTimelineTarget(args)
                ?: return builtInTimelineSelectionMessage(session.builtInTimelineTargets, args)
        val maxItems = args.maxItems.builtInTimelineToolLimit()
        val items = runCatching { target.loadTimeline(maxItems) }.getOrElse { emptyList() }
        session.messagePartStore.addPosts(items.mapNotNull { it.agentContentPostOrNull() })
        session.messagePartStore.addUsers(items.extractBuiltInTimelineUsers())
        return buildString {
            appendLine("Built-in timeline")
            append(target.toBuiltInTimelineToolText())
            appendLine("source: live built-in timeline data source; local cache not used")
            appendLine()
            append(items.toBuiltInTimelineItemsToolText(maxItems))
        }.take(MAX_BUILT_IN_TIMELINE_TOOL_RESULT_LENGTH)
    }

    companion object {
        const val NAME = "load_builtin_timeline"
    }
}

private fun AgentBuiltInTimelineTarget.toBuiltInTimelineToolText(): String =
    buildString {
        appendLine("timelineId: $id")
        appendLine("specId: $specId")
        appendLine("title: $title")
        appendLine("accountKey: $accountKey")
        appendLine("platform: ${platformType.name}")
    }

private fun List<AgentBuiltInTimelineTarget>.resolveBuiltInTimelineTarget(
    args: LoadBuiltInTimelineTool.Args,
): AgentBuiltInTimelineTarget? {
    val requestedAccount = microBlogKeyOrNull(args.accountId, args.accountHost)
    val timelineId = args.timelineId.trim()
    val specId = args.specId.trim()
    val title = args.title.trim()
    val targets =
        filterBuiltInTimelineTargetsByPlatformNames(args.platforms)
            .filter { target -> requestedAccount == null || target.accountKey == requestedAccount }
            .filter { target -> timelineId.isBlank() || target.id == timelineId }
            .filter { target -> specId.isBlank() || target.specId.equals(specId, ignoreCase = true) }
            .filter { target -> title.isBlank() || target.title.equals(title, ignoreCase = true) }
    return targets.singleOrNull()
}

private fun builtInTimelineSelectionMessage(
    targets: List<AgentBuiltInTimelineTarget>,
    args: LoadBuiltInTimelineTool.Args,
): String {
    val candidates =
        targets
            .filterBuiltInTimelineTargetsByPlatformNames(args.platforms)
            .take(MAX_BUILT_IN_TIMELINE_SELECTION_ITEMS)
    if (candidates.isEmpty()) {
        return noBuiltInTimelineTargetsMessage(args.platforms)
    }
    return buildString {
        appendLine("Built-in timeline selection is ambiguous or unavailable.")
        appendLine("Use list_builtin_timelines first, then call load_builtin_timeline with an exact timelineId.")
        appendLine()
        appendLine("Available candidates")
        candidates.forEachIndexed { index, target ->
            appendLine("Candidate #${index + 1}")
            append(target.toBuiltInTimelineToolText())
        }
    }.take(MAX_BUILT_IN_TIMELINE_TOOL_RESULT_LENGTH)
}

private fun noBuiltInTimelineTargetsMessage(platforms: List<String>): String =
    if (platforms.isEmpty()) {
        "No built-in timelines are available from signed-in accounts."
    } else {
        "No built-in timelines are available for the requested platforms: ${platforms.joinToString()}."
    }

private fun microBlogKeyOrNull(
    id: String,
    host: String,
): MicroBlogKey? = id.trim().takeIf { it.isNotBlank() }?.let { MicroBlogKey(id = it, host = host.trim()) }

private fun List<UiTimelineV2>.toBuiltInTimelineItemsToolText(maxItems: Int): String {
    if (isEmpty()) return "No built-in timeline items were returned.\n"
    val items = this
    return buildString {
        items.take(maxItems).forEachIndexed { index, item ->
            appendLine("Timeline item #${index + 1}")
            append(item.toBuiltInTimelineItemToolText())
            appendLine()
        }
    }
}

private fun UiTimelineV2.toBuiltInTimelineItemToolText(): String =
    when (this) {
        is UiTimelineV2.TimelinePostItem -> toBuiltInTimelinePostToolText()
        is UiTimelineV2.Post -> toBuiltInTimelinePostToolText()
        is UiTimelineV2.User -> value.toBuiltInTimelineUserToolText(message)
        is UiTimelineV2.UserList -> "itemType: user_list\nstatusKey: $statusKey\nusers: ${users.joinToString { it.handle.raw }}\n"
        is UiTimelineV2.Message -> "itemType: message\nstatusKey: $statusKey\ncreatedAt: ${createdAt.value}\n"
        is UiTimelineV2.Feed -> "itemType: feed\ntitle: ${title.orEmpty()}\nurl: $url\nsource: ${source.name}\n"
    }

private fun UiTimelineV2.TimelinePostItem.toBuiltInTimelinePostToolText(): String =
    buildString {
        presentation.message?.let { appendLine("messageStatusKey: ${it.statusKey}") }
        append(displayPost.toBuiltInTimelinePostToolText())
    }

private fun UiTimelineV2.Post.toBuiltInTimelinePostToolText(): String =
    buildString {
        appendLine("itemType: post")
        appendLine("attachmentRef: ${agentAttachmentMarker()}")
        appendLine("platform: ${platformType.name}")
        appendLine("statusKey: $statusKey")
        appendLine("createdAt: ${createdAt.value}")
        appendLine("authorName: ${user?.name?.raw.orEmpty()}")
        appendLine("authorHandle: ${user?.handle?.raw.orEmpty()}")
        appendLine("contentWarning: ${contentWarning?.original?.raw.orEmpty()}")
        appendLine("content: ${content.original.raw.take(MAX_BUILT_IN_TIMELINE_TEXT_LENGTH)}")
        appendLine("imagesCount: ${images.size}")
        images.filterIsInstance<UiMedia.Image>().take(MAX_BUILT_IN_TIMELINE_IMAGES).forEachIndexed { index, image ->
            appendLine("image${index + 1}Url: ${image.url}")
            appendLine("image${index + 1}Description: ${image.description.orEmpty()}")
        }
    }

private fun UiProfile.toBuiltInTimelineUserToolText(message: UiTimelineV2.Message?): String =
    buildString {
        appendLine("itemType: user")
        message?.let { appendLine("messageStatusKey: ${it.statusKey}") }
        appendLine("attachmentRef: ${agentAttachmentMarker()}")
        appendLine("platform: ${platformType.name}")
        appendLine("userKey: $key")
        appendLine("displayName: ${name.raw}")
        appendLine("handle: ${handle.raw}")
        appendLine("description: ${description?.raw.orEmpty().take(MAX_BUILT_IN_TIMELINE_TEXT_LENGTH)}")
    }

private fun List<UiTimelineV2>.extractBuiltInTimelineUsers(): List<UiProfile> =
    flatMap { item ->
        when (item) {
            is UiTimelineV2.TimelinePostItem -> {
                listOfNotNull(
                    item.post.user,
                    item.displayPost.user,
                    item.presentation.message?.user,
                ) +
                    item.presentation.inlineParents.mapNotNull { it.user } +
                    item.presentation.quotes.mapNotNull { it.user }
            }

            is UiTimelineV2.Post -> {
                listOfNotNull(item.user)
            }

            is UiTimelineV2.User -> {
                listOf(item.value)
            }

            is UiTimelineV2.UserList -> {
                item.users
            }

            is UiTimelineV2.Message -> {
                listOfNotNull(item.user)
            }

            is UiTimelineV2.Feed -> {
                emptyList()
            }
        }
    }.distinctBy { it.key }

private fun UiTimelineV2.agentContentPostOrNull(): UiTimelineV2.Post? =
    when (this) {
        is UiTimelineV2.TimelinePostItem -> displayPost
        is UiTimelineV2.Post -> this
        else -> null
    }

private fun Int.builtInTimelineToolLimit(): Int = coerceIn(1, MAX_BUILT_IN_TIMELINE_TOOL_ITEMS)

private const val DEFAULT_BUILT_IN_TIMELINE_TOOL_ITEMS = 20
private const val MAX_BUILT_IN_TIMELINE_TOOL_ITEMS = 50
private const val MAX_BUILT_IN_TIMELINE_TOOL_RESULT_LENGTH = 80_000
private const val MAX_BUILT_IN_TIMELINE_SELECTION_ITEMS = 20
private const val MAX_BUILT_IN_TIMELINE_TEXT_LENGTH = 1_000
private const val MAX_BUILT_IN_TIMELINE_IMAGES = 4
