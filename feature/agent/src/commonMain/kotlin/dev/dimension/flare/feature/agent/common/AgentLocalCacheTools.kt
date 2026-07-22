package dev.dimension.flare.feature.agent.common

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.serialization.Serializable

internal class SearchCachedPostsTool(
    private val session: AgentToolSession,
) : SimpleTool<SearchCachedPostsTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Search posts already stored in Flare's local cache. This tool does not use the network and may return stale data.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Text to search for in cached local post content.")
        val query: String,
        @property:LLMDescription("Maximum number of local cached posts to return. Defaults to 20 and is capped at 50.")
        val maxItems: Int = DEFAULT_LOCAL_CACHE_TOOL_ITEMS,
    )

    override suspend fun execute(args: Args): String {
        val repository = session.localCacheRepository ?: return localCacheUnavailableMessage()
        val query = args.query.trim()
        if (query.isBlank()) {
            return "Local cached post search requires a query."
        }
        val maxItems = args.maxItems.localCacheToolLimit()
        val posts = repository.searchPosts(query = query, limit = maxItems)
        session.messagePartStore.addPosts(posts)
        return posts.toLocalCachePostToolText(
            title = "Local cached post search",
            emptyMessage = "No locally cached posts matched the query.",
            metadata =
                listOf(
                    "query: \"$query\"",
                    LOCAL_CACHE_TOOL_SOURCE_NOTE,
                ),
            maxItems = maxItems,
        )
    }

    companion object {
        const val NAME = "search_cached_posts"
    }
}

internal class ListViewedPostsTool(
    private val session: AgentToolSession,
) : SimpleTool<ListViewedPostsTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "List posts from Flare's local viewed-post history. This tool does not use the network and may return stale data.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Maximum number of viewed posts to return. Defaults to 20 and is capped at 50.")
        val maxItems: Int = DEFAULT_LOCAL_CACHE_TOOL_ITEMS,
    )

    override suspend fun execute(args: Args): String {
        val repository = session.localCacheRepository ?: return localCacheUnavailableMessage()
        val maxItems = args.maxItems.localCacheToolLimit()
        val posts = repository.listViewedPosts(limit = maxItems)
        session.messagePartStore.addPosts(posts)
        return posts.toLocalCachePostToolText(
            title = "Local viewed posts",
            emptyMessage = "No locally viewed posts were found.",
            metadata = listOf(LOCAL_CACHE_TOOL_SOURCE_NOTE),
            maxItems = maxItems,
        )
    }

    companion object {
        const val NAME = "list_viewed_posts"
    }
}

internal class SearchCachedUsersTool(
    private val session: AgentToolSession,
) : SimpleTool<SearchCachedUsersTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Search users already stored in Flare's local cache. This tool does not use the network and may return stale data.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Name or handle text to search for in cached local users.")
        val query: String,
        @property:LLMDescription("Maximum number of local cached users to return. Defaults to 20 and is capped at 50.")
        val maxItems: Int = DEFAULT_LOCAL_CACHE_TOOL_ITEMS,
    )

    override suspend fun execute(args: Args): String {
        val repository = session.localCacheRepository ?: return localCacheUnavailableMessage()
        val query = args.query.trim()
        if (query.isBlank()) {
            return "Local cached user search requires a query."
        }
        val maxItems = args.maxItems.localCacheToolLimit()
        val users = repository.searchUsers(query = query, limit = maxItems)
        session.messagePartStore.addUsers(users)
        val selectionRequest =
            users
                .takeIf { it.size > 1 }
                ?.let { candidates ->
                    session.setUserSelectionRequest(
                        users = candidates,
                        requestType = "local_cached_user_search_match",
                    )
                }
        return if (selectionRequest != null) {
            buildString {
                appendLine("Local cached user search")
                appendLine("query: \"$query\"")
                appendLine(LOCAL_CACHE_TOOL_SOURCE_NOTE)
                append(
                    userSelectionRequestToolText(
                        event = "local_cached_user_search_selection_required",
                        requestType = "local_cached_user_search_match",
                        request = selectionRequest,
                        candidates = users,
                    ),
                )
            }
        } else {
            users.toLocalCacheUserToolText(
                title = "Local cached user search",
                emptyMessage = "No locally cached users matched the query.",
                metadata =
                    listOf(
                        "query: \"$query\"",
                        LOCAL_CACHE_TOOL_SOURCE_NOTE,
                    ),
                maxItems = maxItems,
            )
        }
    }

    companion object {
        const val NAME = "search_cached_users"
    }
}

internal class ListViewedUsersTool(
    private val session: AgentToolSession,
) : SimpleTool<ListViewedUsersTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "List users from Flare's local viewed-user history. This tool does not use the network and may return stale data.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Maximum number of viewed users to return. Defaults to 20 and is capped at 50.")
        val maxItems: Int = DEFAULT_LOCAL_CACHE_TOOL_ITEMS,
    )

    override suspend fun execute(args: Args): String {
        val repository = session.localCacheRepository ?: return localCacheUnavailableMessage()
        val maxItems = args.maxItems.localCacheToolLimit()
        val users = repository.listViewedUsers(limit = maxItems)
        session.messagePartStore.addUsers(users)
        return users.toLocalCacheUserToolText(
            title = "Local viewed users",
            emptyMessage = "No locally viewed users were found.",
            metadata = listOf(LOCAL_CACHE_TOOL_SOURCE_NOTE),
            maxItems = maxItems,
        )
    }

    companion object {
        const val NAME = "list_viewed_users"
    }
}

internal const val AGENT_LOCAL_CACHE_TOOL_GUIDANCE: String =
    """
    Local cache/history tool guidance:
    - Local cache tools only inspect posts and users already stored on this device; they do not fetch from the network.
    - Use them when the user asks about local history, viewed items, cached data, or offline data.
    - If the user asks for latest, current, fresh, or real-time data, use live platform/subscription tools unless the user explicitly asks for local/offline cache.
    - Treat local cache results as possibly stale and say so when that distinction matters.
    """

private fun List<UiTimelineV2.Post>.toLocalCachePostToolText(
    title: String,
    emptyMessage: String,
    metadata: List<String>,
    maxItems: Int,
): String {
    val posts = this
    return buildString {
        appendLine(title)
        metadata.forEach { appendLine(it) }
        appendLine()
        appendLine("Posts")
        if (posts.isEmpty()) {
            appendLine(emptyMessage)
            return@buildString
        }
        posts.take(maxItems).forEachIndexed { index, post ->
            appendLine()
            appendLine("Post #${index + 1}")
            append(post.toLocalCachePostToolText())
        }
    }.take(MAX_LOCAL_CACHE_TOOL_RESULT_LENGTH)
}

private fun UiTimelineV2.Post.toLocalCachePostToolText(): String =
    buildString {
        appendLine("attachmentRef: ${agentAttachmentMarker()}")
        appendLine("platform: ${platformType.name}")
        appendLine("statusKey: $statusKey")
        appendLine("createdAt: ${createdAt.value}")
        appendLine("authorName: ${user?.name?.raw.orEmpty()}")
        appendLine("authorHandle: ${user?.handle?.raw.orEmpty()}")
        appendLine("contentWarning: ${contentWarning?.original?.raw.orEmpty()}")
        appendLine("content: ${content.original.raw.take(MAX_LOCAL_CACHE_POST_TEXT_LENGTH)}")
        appendLine("replyToHandle: ${replyToHandle.orEmpty()}")
        appendLine("sourceChannel: ${sourceChannel?.name.orEmpty()}")
        appendLine("imagesCount: ${images.size}")
        if (references.isNotEmpty()) {
            appendLine("references:")
            references.take(MAX_LOCAL_CACHE_RELATED_POSTS).forEachIndexed { index, reference ->
                appendLine("- #${index + 1} ${reference.type.name}: ${reference.statusKey}")
            }
        }
    }

private fun List<UiProfile>.toLocalCacheUserToolText(
    title: String,
    emptyMessage: String,
    metadata: List<String>,
    maxItems: Int,
): String {
    val users = this
    return buildString {
        appendLine(title)
        metadata.forEach { appendLine(it) }
        appendLine()
        appendLine("Users")
        if (users.isEmpty()) {
            appendLine(emptyMessage)
            return@buildString
        }
        users.take(maxItems).forEachIndexed { index, user ->
            appendLine()
            appendLine("User #${index + 1}")
            append(user.toLocalCacheUserToolText())
        }
    }.take(MAX_LOCAL_CACHE_TOOL_RESULT_LENGTH)
}

private fun UiProfile.toLocalCacheUserToolText(): String =
    buildString {
        appendLine("attachmentRef: ${agentAttachmentMarker()}")
        appendLine("platform: ${platformType.name}")
        appendLine("userKey: $key")
        appendLine("displayName: ${name.raw}")
        appendLine("handle: ${handle.raw}")
        appendLine("description: ${description?.raw.orEmpty().take(MAX_LOCAL_CACHE_USER_DESCRIPTION_LENGTH)}")
        appendLine("followers: ${matrices.fansCount}")
        appendLine("following: ${matrices.followsCount}")
        appendLine("posts: ${matrices.statusesCount}")
        appendLine("avatarUrl: ${avatar?.url.orEmpty()}")
        appendLine("bannerUrl: ${banner?.url.orEmpty()}")
    }

private fun Int.localCacheToolLimit(): Int = coerceIn(1, MAX_LOCAL_CACHE_TOOL_ITEMS)

private fun localCacheUnavailableMessage(): String = "Local cache tools are unavailable."

private const val LOCAL_CACHE_TOOL_SOURCE_NOTE = "source: Flare local cache only; network not used."
private const val DEFAULT_LOCAL_CACHE_TOOL_ITEMS = 20
private const val MAX_LOCAL_CACHE_TOOL_ITEMS = 50
private const val MAX_LOCAL_CACHE_TOOL_RESULT_LENGTH = 80_000
private const val MAX_LOCAL_CACHE_POST_TEXT_LENGTH = 800
private const val MAX_LOCAL_CACHE_USER_DESCRIPTION_LENGTH = 800
private const val MAX_LOCAL_CACHE_RELATED_POSTS = 3
private const val MAX_LOCAL_CACHE_RELATED_TEXT_LENGTH = 160
