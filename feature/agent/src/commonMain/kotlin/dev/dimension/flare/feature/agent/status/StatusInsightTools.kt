package dev.dimension.flare.feature.agent.status

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.feature.agent.common.AgentSearchTarget
import dev.dimension.flare.feature.agent.common.AgentToolSession
import dev.dimension.flare.feature.agent.common.AgentUserTarget
import dev.dimension.flare.feature.agent.common.agentAttachmentMarker
import dev.dimension.flare.feature.agent.common.agentAttachmentRef
import dev.dimension.flare.feature.agent.common.filterByPlatformNames
import dev.dimension.flare.feature.agent.common.filterPlatformTypesByPlatformNames
import dev.dimension.flare.feature.agent.common.filterUserTargetsByPlatformNames
import dev.dimension.flare.feature.agent.common.setUserSelectionRequest
import dev.dimension.flare.feature.agent.common.userSelectionRequestToolText
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.contentPostOrNull
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
                DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION +
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
        val posts =
            dataSource
                .context(statusKey)
                .load(
                    pageSize = STATUS_CONTEXT_PAGE_SIZE,
                    request = PagingRequest.Refresh,
                ).data
                .mapNotNull { it.contentPostOrNull() }
        session.messagePartStore.addPosts(posts)
        return buildPostToolResult(
            title = "Status context",
            emptyMessage = "No additional context posts were returned.",
            metadata =
                listOf(
                    "Status key: $statusKey",
                    DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE,
                ),
            posts = posts,
            maxItems = STATUS_CONTEXT_PAGE_SIZE,
        )
    }

    companion object {
        const val NAME = "load_status_context"
    }
}

internal class LoadPostContextTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadPostContextTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load conversation context for a specific post by status key, including surrounding replies or thread posts. " +
                DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION +
                "Use this after a tool result exposes a statusKey that needs thread context. " +
                "All returned post pages are loaded with page size 200.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Status id from a previous tool result.")
        val statusId: String,
        @property:LLMDescription("Status host from a previous tool result. Leave empty only when the platform uses empty-host keys.")
        val statusHost: String = "",
        @property:LLMDescription(
            "Platform names to load from. Leave empty to try every signed-in platform. " +
                "Use platform names or aliases supplied in the system instructions.",
        )
        val platforms: List<String> = emptyList(),
    )

    override suspend fun execute(args: Args): String {
        val statusKey = microBlogKeyOrNull(id = args.statusId, host = args.statusHost) ?: return "Status id is blank."
        val targets = session.searchTargets.resolveTargetsOrNull(args.platforms) ?: return noTargetsMessage(args.platforms)
        val posts = targets.loadPosts { context(statusKey) }
        session.messagePartStore.addPosts(posts)
        return buildPostToolResult(
            title = "Post context",
            emptyMessage = "No context posts were returned.",
            metadata =
                listOf(
                    "Status key: $statusKey",
                    "Platforms loaded: ${targets.platformNames()}",
                    DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE,
                ),
            posts = posts,
            maxItems = POST_TOOL_PAGE_SIZE,
        )
    }

    companion object {
        const val NAME = "load_post_context"
    }
}

internal class LoadHomeTimelineTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadHomeTimelineTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load home timeline posts from the user's signed-in social platforms. " +
                DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION +
                "Use this when the user asks what is currently in their home feed. " +
                "All returned post pages are loaded with page size 200.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription(
            "Platform names to load. Leave empty to load every signed-in platform. " +
                "Use platform names or aliases supplied in the system instructions.",
        )
        val platforms: List<String> = emptyList(),
    )

    override suspend fun execute(args: Args): String {
        val targets = session.searchTargets.resolveTargetsOrNull(args.platforms) ?: return noTargetsMessage(args.platforms)
        val posts = targets.loadPosts { homeTimeline() }
        session.messagePartStore.addPosts(posts)
        return buildPostToolResult(
            title = "Home timeline posts",
            emptyMessage = "No home timeline posts were returned.",
            metadata =
                listOf(
                    "Platforms loaded: ${targets.platformNames()}",
                    DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE,
                ),
            posts = posts,
            maxItems = POST_TOOL_PAGE_SIZE,
        )
    }

    companion object {
        const val NAME = "load_home_timeline"
    }
}

internal class LoadUserTimelineTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadUserTimelineTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load posts from a specific user's timeline. " +
                DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION +
                "Use this when the user asks for a user's recent posts or media posts. " +
                "All returned post pages are loaded with page size 200.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("User id from a profile or post result.")
        val userId: String = "",
        @property:LLMDescription("User host from a profile or post result. Leave empty only when the platform uses empty-host keys.")
        val userHost: String = "",
        @property:LLMDescription("Whether to load only media posts.")
        val mediaOnly: Boolean = false,
        @property:LLMDescription(
            "Platform names to load from. Leave empty to try every signed-in platform. " +
                "Use platform names or aliases supplied in the system instructions.",
        )
        val platforms: List<String> = emptyList(),
    )

    override suspend fun execute(args: Args): String {
        val userKey =
            microBlogKeyOrNull(id = args.userId, host = args.userHost)
                ?: return session.requestUserSelectionFromAttachments(
                    platforms = args.platforms,
                    requestType = "user_timeline",
                ) ?: "User id is blank."
        val targets = session.searchTargets.resolveTargetsOrNull(args.platforms) ?: return noTargetsMessage(args.platforms)
        val posts = targets.loadPosts { userTimeline(userKey = userKey, mediaOnly = args.mediaOnly) }
        session.messagePartStore.addPosts(posts)
        return buildPostToolResult(
            title = "User timeline posts",
            emptyMessage = "No user timeline posts were returned.",
            metadata =
                listOf(
                    "User key: $userKey",
                    "Media only: ${args.mediaOnly}",
                    "Platforms loaded: ${targets.platformNames()}",
                    DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE,
                ),
            posts = posts,
            maxItems = POST_TOOL_PAGE_SIZE,
        )
    }

    companion object {
        const val NAME = "load_user_timeline"
    }
}

internal class LoadDiscoverStatusesTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadDiscoverStatusesTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load discovered, trending, or recommended posts from signed-in platforms. " +
                DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION +
                "Use this when the user asks for discover posts, trends, or platform-visible recommendations. " +
                "All returned post pages are loaded with page size 200.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription(
            "Platform names to load. Leave empty to load every signed-in platform. " +
                "Use platform names or aliases supplied in the system instructions.",
        )
        val platforms: List<String> = emptyList(),
    )

    override suspend fun execute(args: Args): String {
        val targets = session.searchTargets.resolveTargetsOrNull(args.platforms) ?: return noTargetsMessage(args.platforms)
        val posts = targets.loadPosts { discoverStatuses() }
        session.messagePartStore.addPosts(posts)
        return buildPostToolResult(
            title = "Discover posts",
            emptyMessage = "No discover posts were returned.",
            metadata =
                listOf(
                    "Platforms loaded: ${targets.platformNames()}",
                    DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE,
                ),
            posts = posts,
            maxItems = POST_TOOL_PAGE_SIZE,
        )
    }

    companion object {
        const val NAME = "load_discover_posts"
    }
}

internal class SearchPostsTool(
    private val session: AgentToolSession,
) : SimpleTool<SearchPostsTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Search public or account-visible posts across the user's signed-in social platforms. " +
                DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION +
                "Use this when posts may explain a phrase, meme, event, claim, or why something is spreading. " +
                "If the user explicitly asks to search, find, check, compare, or inspect platform-visible posts, call this tool. " +
                "Leave platforms empty for cross-platform search; pass platform names when the user names specific platforms. " +
                "Use one concise query, then answer from the returned results. " +
                "All returned post pages are loaded with page size 200.",
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
        session.messagePartStore.addPosts(postResults)
        return buildString {
            appendLine("Search query: \"$query\"")
            appendLine("Search target: Posts")
            appendLine("Platforms searched: ${targets.joinToString { it.platformType?.name ?: "Unknown" }}")
            appendLine(DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE)
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
                DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION +
                "Use this when account identity, official status, bio, handle, or profile context may explain a post. " +
                "If the user asks to search, find, check, compare, or inspect platform-visible users/accounts, " +
                "call this tool. " +
                "Leave platforms empty for cross-platform search; pass platform names when the user names specific platforms. " +
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
        session.messagePartStore.addUsers(userResults)
        val selectionRequest =
            userResults
                .takeIf { it.size > 1 }
                ?.let { users ->
                    session.setUserSelectionRequest(
                        users = users,
                        requestType = "user_search_match",
                    )
                }
        return buildString {
            appendLine("Search query: \"$query\"")
            appendLine("Search target: Users")
            appendLine("Platforms searched: ${targets.joinToString { it.platformType?.name ?: "Unknown" }}")
            appendLine(DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE)
            appendLine()
            if (selectionRequest != null) {
                append(
                    userSelectionRequestToolText(
                        event = "user_search_selection_required",
                        requestType = "user_search_match",
                        request = selectionRequest,
                        candidates = userResults,
                    ),
                )
            } else {
                append(
                    userResults.toInsightUserToolListText(
                        title = "User search results",
                        emptyMessage = "No matching users were returned.",
                        maxItems = USER_SEARCH_PAGE_SIZE,
                    ),
                )
            }
        }.take(MAX_TOOL_RESULT_LENGTH)
    }

    companion object {
        const val NAME = "search_users"
    }
}

internal class LoadUserProfileTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadUserProfileTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load a user profile directly by user key from signed-in platforms that expose UserLoader. " +
                DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION +
                "Use this when a previous tool result, account key, post author, mention, or user request already provides a user id. " +
                "Do not use this for keyword search; use search_users for names, handles, or fuzzy identity lookup.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("User id from a userKey, accountKey, profile, post author, or mention.")
        val userId: String = "",
        @property:LLMDescription("User host from the userKey. This is used for disambiguation in result metadata.")
        val userHost: String = "",
        @property:LLMDescription(
            "Platform names to load from. Leave empty to try every signed-in platform with UserLoader. " +
                "Use platform names or aliases supplied in the system instructions.",
        )
        val platforms: List<String> = emptyList(),
    )

    override suspend fun execute(args: Args): String {
        val userKey =
            microBlogKeyOrNull(id = args.userId, host = args.userHost)
                ?: return session.requestUserSelectionFromAttachments(
                    platforms = args.platforms,
                    requestType = "user_profile",
                ) ?: "User id is blank."
        val targets = session.userTargets.resolveUserTargetsOrNull(args.platforms) ?: return noUserTargetsMessage(args.platforms)
        val users = targets.loadUserProfiles(userKey)
        session.messagePartStore.addUsers(users)
        return buildUserToolResult(
            title = "User profiles",
            emptyMessage = "No user profile was returned for $userKey.",
            metadata =
                listOf(
                    "User key: $userKey",
                    "Platforms loaded: ${targets.userPlatformNames()}",
                    DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE,
                ),
            users = users,
            maxItems = USER_TOOL_PAGE_SIZE,
        )
    }

    companion object {
        const val NAME = "load_user_profile"
    }
}

internal class LoadDiscoverUsersTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadDiscoverUsersTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load discovered, trending, or recommended user profiles from signed-in platforms. " +
                DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION +
                "Use this when the user asks for account recommendations or discover users.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription(
            "Platform names to load. Leave empty to load every signed-in platform. " +
                "Use platform names or aliases supplied in the system instructions.",
        )
        val platforms: List<String> = emptyList(),
    )

    override suspend fun execute(args: Args): String {
        val targets = session.searchTargets.resolveTargetsOrNull(args.platforms) ?: return noTargetsMessage(args.platforms)
        val users = targets.loadUsers { discoverUsers() }
        session.messagePartStore.addUsers(users)
        return buildUserToolResult(
            title = "Discover users",
            emptyMessage = "No discover users were returned.",
            metadata =
                listOf(
                    "Platforms loaded: ${targets.platformNames()}",
                    DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE,
                ),
            users = users,
            maxItems = USER_TOOL_PAGE_SIZE,
        )
    }

    companion object {
        const val NAME = "load_discover_users"
    }
}

internal class LoadDiscoverHashtagsTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadDiscoverHashtagsTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load discovered, trending, or recommended hashtags from signed-in platforms. " +
                DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION +
                "Use this when the user asks for trending topics or hashtags.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription(
            "Platform names to load. Leave empty to load every signed-in platform. " +
                "Use platform names or aliases supplied in the system instructions.",
        )
        val platforms: List<String> = emptyList(),
    )

    override suspend fun execute(args: Args): String {
        val targets = session.searchTargets.resolveTargetsOrNull(args.platforms) ?: return noTargetsMessage(args.platforms)
        val hashtags = targets.loadHashtags { discoverHashtags() }
        return buildString {
            appendLine("Discover hashtags")
            appendLine("Platforms loaded: ${targets.platformNames()}")
            appendLine(DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE)
            appendLine()
            append(
                hashtags.toInsightHashtagToolListText(
                    emptyMessage = "No discover hashtags were returned.",
                    maxItems = HASHTAG_TOOL_PAGE_SIZE,
                ),
            )
        }.take(MAX_TOOL_RESULT_LENGTH)
    }

    companion object {
        const val NAME = "load_discover_hashtags"
    }
}

internal class LoadFollowingTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadFollowingTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load the accounts followed by a specific user. " +
                DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION +
                "Use this when follow graph context may explain a user or post.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("User id from a profile or post result.")
        val userId: String = "",
        @property:LLMDescription("User host from a profile or post result. Leave empty only when the platform uses empty-host keys.")
        val userHost: String = "",
        @property:LLMDescription(
            "Platform names to load from. Leave empty to try every signed-in platform. " +
                "Use platform names or aliases supplied in the system instructions.",
        )
        val platforms: List<String> = emptyList(),
    )

    override suspend fun execute(args: Args): String {
        val userKey =
            microBlogKeyOrNull(id = args.userId, host = args.userHost)
                ?: return session.requestUserSelectionFromAttachments(
                    platforms = args.platforms,
                    requestType = "following",
                ) ?: "User id is blank."
        val targets = session.searchTargets.resolveTargetsOrNull(args.platforms) ?: return noTargetsMessage(args.platforms)
        val users = targets.loadUsers { following(userKey) }
        session.messagePartStore.addUsers(users)
        return buildUserToolResult(
            title = "Following users",
            emptyMessage = "No following users were returned.",
            metadata =
                listOf(
                    "User key: $userKey",
                    "Platforms loaded: ${targets.platformNames()}",
                    DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE,
                ),
            users = users,
            maxItems = USER_TOOL_PAGE_SIZE,
        )
    }

    companion object {
        const val NAME = "load_following"
    }
}

internal class LoadFollowersTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadFollowersTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load followers/fans for a specific user. " +
                DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION +
                "This wraps MicroblogDataSource.fans(). Use it when follower context may explain a user or post.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("User id from a profile or post result.")
        val userId: String = "",
        @property:LLMDescription("User host from a profile or post result. Leave empty only when the platform uses empty-host keys.")
        val userHost: String = "",
        @property:LLMDescription(
            "Platform names to load from. Leave empty to try every signed-in platform. " +
                "Use platform names or aliases supplied in the system instructions.",
        )
        val platforms: List<String> = emptyList(),
    )

    override suspend fun execute(args: Args): String {
        val userKey =
            microBlogKeyOrNull(id = args.userId, host = args.userHost)
                ?: return session.requestUserSelectionFromAttachments(
                    platforms = args.platforms,
                    requestType = "followers",
                ) ?: "User id is blank."
        val targets = session.searchTargets.resolveTargetsOrNull(args.platforms) ?: return noTargetsMessage(args.platforms)
        val users = targets.loadUsers { fans(userKey) }
        session.messagePartStore.addUsers(users)
        return buildUserToolResult(
            title = "Followers",
            emptyMessage = "No followers were returned.",
            metadata =
                listOf(
                    "User key: $userKey",
                    "Platforms loaded: ${targets.platformNames()}",
                    DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE,
                ),
            users = users,
            maxItems = USER_TOOL_PAGE_SIZE,
        )
    }

    companion object {
        const val NAME = "load_followers"
    }
}

internal class LoadProfileTabsTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadProfileTabsTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "List a user's profile tabs, or load posts from one selected profile tab. " +
                DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION +
                "If tabIndex or tabName is provided, the selected tab's post page is loaded with page size 200. " +
                "If neither is provided, only tab metadata is returned.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("User id from a profile or post result.")
        val userId: String = "",
        @property:LLMDescription("User host from a profile or post result. Leave empty only when the platform uses empty-host keys.")
        val userHost: String = "",
        @property:LLMDescription("One-based profile tab index to load. Leave null to list tabs or select by tabName.")
        val tabIndex: Int? = null,
        @property:LLMDescription("Profile tab name to load, such as Posts, Media, or PostsWithReplies.")
        val tabName: String? = null,
        @property:LLMDescription(
            "Platform names to load from. Leave empty to try every signed-in platform. " +
                "Use platform names or aliases supplied in the system instructions.",
        )
        val platforms: List<String> = emptyList(),
    )

    override suspend fun execute(args: Args): String {
        val userKey =
            microBlogKeyOrNull(id = args.userId, host = args.userHost)
                ?: return session.requestUserSelectionFromAttachments(
                    platforms = args.platforms,
                    requestType = "profile_tabs",
                ) ?: "User id is blank."
        val targets = session.searchTargets.resolveTargetsOrNull(args.platforms) ?: return noTargetsMessage(args.platforms)
        val tabResults = targets.loadProfileTabs(userKey)
        val selectedTabs =
            tabResults.mapNotNull { result ->
                val tab = result.tabs.selectProfileTab(tabIndex = args.tabIndex, tabName = args.tabName)
                tab?.let { result.target to it }
            }
        if (args.tabIndex == null && args.tabName.isNullOrBlank()) {
            return buildString {
                appendLine("Profile tabs")
                appendLine("User key: $userKey")
                appendLine("Platforms loaded: ${targets.platformNames()}")
                appendLine(DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE)
                tabResults.forEach { result ->
                    appendLine()
                    appendLine("Platform: ${result.target.platformType?.name ?: "Unknown"}")
                    if (result.tabs.isEmpty()) {
                        appendLine("No profile tabs were returned.")
                    } else {
                        result.tabs.forEachIndexed { index, tab ->
                            appendLine(
                                "Tab #${index + 1}: name=${tab.name.name}, displayType=${tab.displayType.name}, " +
                                    "showAllImagesInGallery=${tab.showAllImagesInGallery}",
                            )
                        }
                    }
                }
            }.take(MAX_TOOL_RESULT_LENGTH)
        }
        if (selectedTabs.isEmpty()) {
            return "No matching profile tab was found for user key $userKey."
        }
        val posts = selectedTabs.loadProfileTabPosts()
        session.messagePartStore.addPosts(posts)
        return buildPostToolResult(
            title = "Profile tab posts",
            emptyMessage = "No posts were returned from the selected profile tab.",
            metadata =
                listOf(
                    "User key: $userKey",
                    "Tab index: ${args.tabIndex?.toString().orEmpty()}",
                    "Tab name: ${args.tabName.orEmpty()}",
                    "Platforms loaded: ${selectedTabs.map { it.first }.platformNames()}",
                    DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE,
                ),
            posts = posts,
            maxItems = POST_TOOL_PAGE_SIZE,
        )
    }

    companion object {
        const val NAME = "load_profile_tabs"
    }
}

private fun microBlogKeyOrNull(
    id: String,
    host: String,
): MicroBlogKey? {
    val trimmedId = id.trim()
    if (trimmedId.isBlank()) {
        return null
    }
    return MicroBlogKey(id = trimmedId, host = host.trim())
}

private fun List<AgentSearchTarget>.resolveTargetsOrNull(platforms: List<String>): List<AgentSearchTarget>? =
    distinctBy { it.platformType }
        .filterByPlatformNames(platforms)
        .takeIf { it.isNotEmpty() }

private fun List<AgentUserTarget>.resolveUserTargetsOrNull(platforms: List<String>): List<AgentUserTarget>? =
    distinctBy { it.platformType }
        .filterUserTargetsByPlatformNames(platforms)
        .takeIf { it.isNotEmpty() }

private fun noTargetsMessage(platforms: List<String>): String =
    if (platforms.isEmpty()) {
        "No signed-in accounts are available for this MicroblogDataSource tool."
    } else {
        "No signed-in accounts are available for the requested platforms: ${platforms.joinToString()}."
    }

private fun noUserTargetsMessage(platforms: List<String>): String =
    if (platforms.isEmpty()) {
        "No signed-in accounts with UserLoader are available for this tool."
    } else {
        "No signed-in accounts with UserLoader are available for the requested platforms: ${platforms.joinToString()}."
    }

private fun List<AgentSearchTarget>.platformNames(): String = joinToString { it.platformType?.name ?: "Unknown" }

private fun List<AgentUserTarget>.userPlatformNames(): String = joinToString { it.platformType.name }

private suspend fun AgentToolSession.requestUserSelectionFromAttachments(
    platforms: List<String>,
    requestType: String,
): String? {
    val candidates =
        messagePartStore
            .snapshot()
            .filterIsInstance<AgentMessagePart.UserCard>()
            .map { it.user }
            .filterByPlatformNames(platforms)
            .distinctBy { it.platformType to it.key }
            .takeIf { it.isNotEmpty() }
            ?: return null
    val request =
        setUserSelectionRequest(
            users = candidates,
            requestType = requestType,
        )
    return userSelectionRequestToolText(
        event = "user_selection_required",
        requestType = requestType,
        request = request,
        candidates = candidates,
    )
}

private fun List<UiProfile>.filterByPlatformNames(platforms: List<String>): List<UiProfile> {
    val platformTypes = map { it.platformType }.filterPlatformTypesByPlatformNames(platforms)
    return when {
        platformTypes.isEmpty() && platforms.isNotEmpty() -> emptyList()
        platforms.isEmpty() -> this
        else -> filter { it.platformType in platformTypes }
    }
}

private suspend fun List<AgentSearchTarget>.loadPosts(
    loaderFactory: MicroblogDataSource.() -> RemoteLoader<UiTimelineV2>,
): List<UiTimelineV2.Post> =
    coroutineScope {
        map { target ->
            async {
                runCatching {
                    target.dataSource
                        .loaderFactory()
                        .load(
                            pageSize = POST_TOOL_PAGE_SIZE,
                            request = PagingRequest.Refresh,
                        ).data
                        .mapNotNull { it.contentPostOrNull() }
                }.getOrElse { emptyList() }
            }
        }.awaitAll()
    }.flatten()
        .distinctBy { it.platformType to it.statusKey }

private suspend fun List<AgentSearchTarget>.loadUsers(loaderFactory: MicroblogDataSource.() -> RemoteLoader<UiProfile>): List<UiProfile> =
    coroutineScope {
        map { target ->
            async {
                runCatching {
                    target.dataSource
                        .loaderFactory()
                        .load(
                            pageSize = USER_TOOL_PAGE_SIZE,
                            request = PagingRequest.Refresh,
                        ).data
                }.getOrElse { emptyList() }
            }
        }.awaitAll()
    }.flatten()
        .distinctBy { it.platformType to it.key }

private suspend fun List<AgentUserTarget>.loadUserProfiles(userKey: MicroBlogKey): List<UiProfile> =
    coroutineScope {
        map { target ->
            async {
                runCatching {
                    target.loadUserById(userKey.id)
                }.getOrNull()
            }
        }.awaitAll()
    }.filterNotNull()
        .distinctBy { it.platformType to it.key }

private suspend fun List<AgentSearchTarget>.loadHashtags(
    loaderFactory: MicroblogDataSource.() -> RemoteLoader<UiHashtag>,
): List<UiHashtag> =
    coroutineScope {
        map { target ->
            async {
                runCatching {
                    target.dataSource
                        .loaderFactory()
                        .load(
                            pageSize = HASHTAG_TOOL_PAGE_SIZE,
                            request = PagingRequest.Refresh,
                        ).data
                }.getOrElse { emptyList() }
            }
        }.awaitAll()
    }.flatten()
        .distinctBy { it.hashtag to it.searchContent }

private data class ProfileTabResult(
    val target: AgentSearchTarget,
    val tabs: List<ProfileTab>,
)

private suspend fun List<AgentSearchTarget>.loadProfileTabs(userKey: MicroBlogKey): List<ProfileTabResult> =
    coroutineScope {
        map { target ->
            async {
                runCatching {
                    ProfileTabResult(
                        target = target,
                        tabs = target.dataSource.profileTabs(userKey),
                    )
                }.getOrNull()
            }
        }.awaitAll()
    }.filterNotNull()

private fun List<ProfileTab>.selectProfileTab(
    tabIndex: Int?,
    tabName: String?,
): ProfileTab? {
    if (tabIndex != null) {
        return getOrNull(tabIndex - 1)
    }
    val normalizedTabName = tabName.orEmpty().trim()
    if (normalizedTabName.isBlank()) {
        return null
    }
    return firstOrNull { it.name.name.equals(normalizedTabName, ignoreCase = true) }
}

private suspend fun List<Pair<AgentSearchTarget, ProfileTab>>.loadProfileTabPosts(): List<UiTimelineV2.Post> =
    coroutineScope {
        map { (_, tab) ->
            async {
                runCatching {
                    tab.loader
                        .load(
                            pageSize = POST_TOOL_PAGE_SIZE,
                            request = PagingRequest.Refresh,
                        ).data
                        .mapNotNull { it.contentPostOrNull() }
                }.getOrElse { emptyList() }
            }
        }.awaitAll()
    }.flatten()
        .distinctBy { it.platformType to it.statusKey }

private fun buildPostToolResult(
    title: String,
    emptyMessage: String,
    metadata: List<String>,
    posts: List<UiTimelineV2.Post>,
    maxItems: Int,
): String =
    buildString {
        appendLine(title)
        metadata.forEach { appendLine(it) }
        appendLine()
        append(
            posts.toInsightPostToolListText(
                title = "Posts",
                emptyMessage = emptyMessage,
                maxItems = maxItems,
            ),
        )
    }.take(MAX_TOOL_RESULT_LENGTH)

private fun buildUserToolResult(
    title: String,
    emptyMessage: String,
    metadata: List<String>,
    users: List<UiProfile>,
    maxItems: Int,
): String =
    buildString {
        appendLine(title)
        metadata.forEach { appendLine(it) }
        appendLine()
        append(
            users.toInsightUserToolListText(
                title = "Users",
                emptyMessage = emptyMessage,
                maxItems = maxItems,
            ),
        )
    }.take(MAX_TOOL_RESULT_LENGTH)

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
                        .mapNotNull { it.contentPostOrNull() }
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

private fun List<UiHashtag>.toInsightHashtagToolListText(
    emptyMessage: String,
    maxItems: Int,
): String {
    val hashtags = this
    return buildString {
        if (hashtags.isEmpty()) {
            appendLine(emptyMessage)
            return@buildString
        }
        hashtags.take(maxItems).forEachIndexed { index, hashtag ->
            appendLine()
            appendLine("Hashtag #${index + 1}")
            appendLine("hashtag: ${hashtag.hashtag}")
            appendLine("searchContent: ${hashtag.searchContent}")
            appendLine("description: ${hashtag.description.orEmpty().take(MAX_TOOL_HASHTAG_DESCRIPTION_LENGTH)}")
        }
    }
}

private fun UiProfile.toInsightUserToolText(): String =
    buildString {
        appendLine("attachmentRef: ${agentAttachmentMarker()}")
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
        appendLine("attachmentRef: ${agentAttachmentMarker()}")
        appendLine("platform: ${platformType.name}")
        appendLine("statusKey: $statusKey")
        appendLine("createdAt: ${createdAt.value}")
        appendLine("authorName: ${user?.name?.raw.orEmpty()}")
        appendLine("authorHandle: ${user?.handle?.raw.orEmpty()}")
        appendLine("contentWarning: ${contentWarning?.original?.raw.orEmpty()}")
        appendLine("content: ${content.original.raw.take(MAX_TOOL_POST_TEXT_LENGTH)}")
        appendLine("replyToHandle: ${replyToHandle.orEmpty()}")
        appendLine("sourceChannel: ${sourceChannel?.name.orEmpty()}")
        appendLine("imagesCount: ${images.size}")
        images.filterIsInstance<UiMedia.Image>().take(MAX_TOOL_IMAGES).forEachIndexed { index, image ->
            appendLine("image${index + 1}Url: ${image.url}")
            appendLine("image${index + 1}Description: ${image.description.orEmpty()}")
        }
        if (references.isNotEmpty()) {
            appendLine("references:")
            references.take(MAX_TOOL_RELATED_POSTS).forEachIndexed { index, reference ->
                appendLine("- #${index + 1} ${reference.type.name}: ${reference.statusKey}")
            }
        }
    }

private const val STATUS_CONTEXT_PAGE_SIZE = 200
private const val STATUS_SEARCH_PAGE_SIZE = 200
private const val POST_TOOL_PAGE_SIZE = 200
private const val USER_SEARCH_PAGE_SIZE = 200
private const val USER_TOOL_PAGE_SIZE = 200
private const val HASHTAG_TOOL_PAGE_SIZE = 200
private const val MAX_TOOL_RESULT_LENGTH = 80_000
private const val MAX_TOOL_POST_TEXT_LENGTH = 800
private const val MAX_TOOL_USER_DESCRIPTION_LENGTH = 800
private const val MAX_TOOL_HASHTAG_DESCRIPTION_LENGTH = 800
private const val MAX_TOOL_RELATED_POSTS = 3
private const val MAX_TOOL_RELATED_TEXT_LENGTH = 300
private const val MAX_TOOL_IMAGES = 4
private const val DIRECT_MICROBLOG_DATA_SOURCE_DESCRIPTION =
    "This is not a cache lookup; it calls the active MicroblogDataSource loader directly. "
private const val DIRECT_MICROBLOG_DATA_SOURCE_RESULT_NOTE =
    "Note: this tool result is not from cache; it was loaded through MicroblogDataSource."
