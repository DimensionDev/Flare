package dev.dimension.flare.feature.agent.status

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.feature.agent.common.AgentSearchTarget
import dev.dimension.flare.feature.agent.common.AgentToolSession
import dev.dimension.flare.feature.agent.common.filterByPlatformNames
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

internal class LoadStatusContextTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadStatusContextTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load the current post's conversation context, including surrounding replies or thread posts. " +
                "Use this only when the post depends on missing conversation context. " +
                "After this tool returns, answer from the available context instead of calling more tools.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Why the current post needs conversation context.")
        val reason: String? = null,
    )

    override suspend fun execute(args: Args): String {
        val dataSource = session.statusMicroblogDataSource() ?: return session.statusContextUnavailableMessage()
        val statusKey = session.statusKey() ?: return session.statusContextUnavailableMessage()
        return dataSource
            .context(statusKey)
            .load(
                pageSize = STATUS_CONTEXT_PAGE_SIZE,
                request = PagingRequest.Refresh,
            ).data
            .filterIsInstance<UiTimelineV2.Post>()
            .toInsightPostToolListText(
                title = "Status context",
                emptyMessage = "No additional context posts were returned.",
                maxItems = STATUS_CONTEXT_PAGE_SIZE,
            )
    }

    companion object {
        const val NAME = "load_status_context"
    }
}

internal class SearchPostsTool(
    private val session: AgentToolSession,
) : SimpleTool<SearchPostsTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Search public or account-visible posts across the user's signed-in social platforms. " +
                "Use this when posts may explain a phrase, meme, event, claim, or why something is spreading. " +
                "Use one concise query, then answer from the returned results.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Search query. Keep it concise and use terms from the post.")
        val query: String,
        @property:LLMDescription(
            "Platform names to search. Leave empty to search all signed-in platforms. " +
                "Use the platform names or aliases supplied in the system instructions.",
        )
        val platforms: List<String> = emptyList(),
    )

    override suspend fun execute(args: Args): String {
        val query = args.query.trim()
        if (query.isBlank()) {
            return "Search query is blank."
        }
        val targets =
            session.searchTargets
                .distinctBy { it.platformType }
                .filterByPlatformNames(args.platforms)
        if (targets.isEmpty()) {
            return if (args.platforms.isEmpty()) {
                "No signed-in accounts are available for search."
            } else {
                "No signed-in accounts are available for the requested platforms: ${args.platforms.joinToString()}."
            }
        }
        val postResults = targets.searchPosts(query)
        return buildString {
            appendLine("Search query: \"$query\"")
            appendLine("Search target: Posts")
            appendLine("Platforms searched: ${targets.joinToString { it.platformType?.name ?: "Unknown" }}")
            appendLine()
            append(
                postResults.toInsightPostToolListText(
                    title = "Post search results",
                    emptyMessage = "No matching posts were returned.",
                    maxItems = STATUS_SEARCH_PAGE_SIZE,
                ),
            )
        }.take(MAX_TOOL_RESULT_LENGTH)
    }

    companion object {
        const val NAME = "search_posts"
    }
}

internal class SearchUsersTool(
    private val session: AgentToolSession,
) : SimpleTool<SearchUsersTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Search public or account-visible user profiles across the user's signed-in social platforms. " +
                "Use this when account identity, official status, bio, handle, or profile context may explain a post. " +
                "Use one concise query, then answer from the returned results.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Search query. Keep it concise and use terms from the post or account.")
        val query: String,
        @property:LLMDescription(
            "Platform names to search. Leave empty to search all signed-in platforms. " +
                "Use the platform names or aliases supplied in the system instructions.",
        )
        val platforms: List<String> = emptyList(),
    )

    override suspend fun execute(args: Args): String {
        val query = args.query.trim()
        if (query.isBlank()) {
            return "Search query is blank."
        }
        val targets =
            session.searchTargets
                .distinctBy { it.platformType }
                .filterByPlatformNames(args.platforms)
        if (targets.isEmpty()) {
            return if (args.platforms.isEmpty()) {
                "No signed-in accounts are available for search."
            } else {
                "No signed-in accounts are available for the requested platforms: ${args.platforms.joinToString()}."
            }
        }
        val userResults = targets.searchUsers(query)
        return buildString {
            appendLine("Search query: \"$query\"")
            appendLine("Search target: Users")
            appendLine("Platforms searched: ${targets.joinToString { it.platformType?.name ?: "Unknown" }}")
            appendLine()
            append(
                userResults.toInsightUserToolListText(
                    title = "User search results",
                    emptyMessage = "No matching users were returned.",
                    maxItems = USER_SEARCH_PAGE_SIZE,
                ),
            )
        }.take(MAX_TOOL_RESULT_LENGTH)
    }

    companion object {
        const val NAME = "search_users"
    }
}

private suspend fun List<AgentSearchTarget>.searchPosts(query: String): List<UiTimelineV2.Post> =
    coroutineScope {
        map { target ->
            async {
                runCatching {
                    target.dataSource
                        .searchStatus(query)
                        .load(
                            pageSize = STATUS_SEARCH_PAGE_SIZE,
                            request = PagingRequest.Refresh,
                        ).data
                        .filterIsInstance<UiTimelineV2.Post>()
                }.getOrElse { emptyList() }
            }
        }.awaitAll()
    }.flatten()
        .distinctBy { it.platformType to it.statusKey }

private suspend fun List<AgentSearchTarget>.searchUsers(query: String): List<UiProfile> =
    coroutineScope {
        map { target ->
            async {
                runCatching {
                    target.dataSource
                        .searchUser(query)
                        .load(
                            pageSize = USER_SEARCH_PAGE_SIZE,
                            request = PagingRequest.Refresh,
                        ).data
                }.getOrElse { emptyList() }
            }
        }.awaitAll()
    }.flatten()
        .distinctBy { it.platformType to it.key }

private fun List<UiTimelineV2.Post>.toInsightPostToolListText(
    title: String,
    emptyMessage: String,
    maxItems: Int,
): String {
    val posts = this
    return buildString {
        appendLine(title)
        if (posts.isEmpty()) {
            appendLine(emptyMessage)
            return@buildString
        }
        posts.take(maxItems).forEachIndexed { index, post ->
            appendLine()
            appendLine("Post #${index + 1}")
            append(post.toInsightPostToolText())
        }
    }
}

private fun List<UiProfile>.toInsightUserToolListText(
    title: String,
    emptyMessage: String,
    maxItems: Int,
): String {
    val users = this
    return buildString {
        appendLine(title)
        if (users.isEmpty()) {
            appendLine(emptyMessage)
            return@buildString
        }
        users.take(maxItems).forEachIndexed { index, user ->
            appendLine()
            appendLine("User #${index + 1}")
            append(user.toInsightUserToolText())
        }
    }
}

private fun UiProfile.toInsightUserToolText(): String =
    buildString {
        appendLine("platform: ${platformType.name}")
        appendLine("userKey: $key")
        appendLine("displayName: ${name.raw}")
        appendLine("handle: ${handle.raw}")
        appendLine("description: ${description?.raw.orEmpty().take(MAX_TOOL_USER_DESCRIPTION_LENGTH)}")
        appendLine("followers: ${matrices.fansCount}")
        appendLine("following: ${matrices.followsCount}")
        appendLine("posts: ${matrices.statusesCount}")
        appendLine("avatarUrl: ${avatar?.url.orEmpty()}")
        appendLine("bannerUrl: ${banner?.url.orEmpty()}")
    }

private fun UiTimelineV2.Post.toInsightPostToolText(): String =
    buildString {
        appendLine("platform: ${platformType.name}")
        appendLine("statusKey: $statusKey")
        appendLine("createdAt: ${createdAt.value}")
        appendLine("authorName: ${user?.name?.raw.orEmpty()}")
        appendLine("authorHandle: ${user?.handle?.raw.orEmpty()}")
        appendLine("contentWarning: ${contentWarning?.raw.orEmpty()}")
        appendLine("content: ${content.raw.take(MAX_TOOL_POST_TEXT_LENGTH)}")
        appendLine("replyToHandle: ${replyToHandle.orEmpty()}")
        appendLine("sourceChannel: ${sourceChannel?.name.orEmpty()}")
        appendLine("imagesCount: ${images.size}")
        images.filterIsInstance<UiMedia.Image>().take(MAX_TOOL_IMAGES).forEachIndexed { index, image ->
            appendLine("image${index + 1}Url: ${image.url}")
            appendLine("image${index + 1}Description: ${image.description.orEmpty()}")
        }
        if (quote.isNotEmpty()) {
            appendLine("quotes:")
            quote.take(MAX_TOOL_RELATED_POSTS).forEachIndexed { index, post ->
                appendLine("- #${index + 1} ${post.user?.handle?.raw.orEmpty()}: ${post.content.raw.take(MAX_TOOL_RELATED_TEXT_LENGTH)}")
            }
        }
        if (parents.isNotEmpty()) {
            appendLine("parents:")
            parents.take(MAX_TOOL_RELATED_POSTS).forEachIndexed { index, post ->
                appendLine("- #${index + 1} ${post.user?.handle?.raw.orEmpty()}: ${post.content.raw.take(MAX_TOOL_RELATED_TEXT_LENGTH)}")
            }
        }
    }

private const val STATUS_CONTEXT_PAGE_SIZE = 100
private const val STATUS_SEARCH_PAGE_SIZE = 20
private const val USER_SEARCH_PAGE_SIZE = 20
private const val MAX_TOOL_RESULT_LENGTH = 24_000
private const val MAX_TOOL_POST_TEXT_LENGTH = 800
private const val MAX_TOOL_USER_DESCRIPTION_LENGTH = 800
private const val MAX_TOOL_RELATED_POSTS = 3
private const val MAX_TOOL_RELATED_TEXT_LENGTH = 300
private const val MAX_TOOL_IMAGES = 4
