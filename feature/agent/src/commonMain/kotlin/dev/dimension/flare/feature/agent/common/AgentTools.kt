package dev.dimension.flare.feature.agent.common

import ai.koog.agents.core.tools.ToolRegistry
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.subscription.KoinSubscriptionDataSource
import dev.dimension.flare.data.datasource.subscription.SubscriptionDataSource
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.data.repository.LocalCacheRepository
import dev.dimension.flare.feature.agent.status.LoadDiscoverHashtagsTool
import dev.dimension.flare.feature.agent.status.LoadDiscoverStatusesTool
import dev.dimension.flare.feature.agent.status.LoadDiscoverUsersTool
import dev.dimension.flare.feature.agent.status.LoadFollowersTool
import dev.dimension.flare.feature.agent.status.LoadFollowingTool
import dev.dimension.flare.feature.agent.status.LoadHomeTimelineTool
import dev.dimension.flare.feature.agent.status.LoadPostContextTool
import dev.dimension.flare.feature.agent.status.LoadProfileTabsTool
import dev.dimension.flare.feature.agent.status.LoadStatusContextTool
import dev.dimension.flare.feature.agent.status.LoadUserProfileTool
import dev.dimension.flare.feature.agent.status.LoadUserTimelineTool
import dev.dimension.flare.feature.agent.status.SearchPostsTool
import dev.dimension.flare.feature.agent.status.SearchUsersTool
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.toUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

@Single
internal class AgentToolProvider(
    private val localCacheRepository: LocalCacheRepository,
) {
    fun resolve(context: AgentToolContext): AgentToolSet {
        val statusContext = context.status
        val microblogDataSource = statusContext?.postDataSource as? MicroblogDataSource
        val searchTargets =
            buildList {
                addAll(context.searchDataSources.toAgentSearchTargets())
                if (statusContext != null && microblogDataSource != null && none { it.dataSource === microblogDataSource }) {
                    add(AgentSearchTarget(platformType = statusContext.currentPlatformType, dataSource = microblogDataSource))
                }
            }
        val composeTargets = context.searchDataSources.toAgentComposeTargets()
        val postActionTargets =
            buildList {
                addAll(context.searchDataSources.toAgentPostActionTargets())
                if (statusContext != null && none { it.dataSource === statusContext.postDataSource }) {
                    val authenticated = statusContext.postDataSource as? AuthenticatedMicroblogDataSource
                    if (authenticated != null) {
                        add(
                            AgentPostActionTarget(
                                accountKey = authenticated.accountKey,
                                platformType = statusContext.currentPlatformType,
                                dataSource = statusContext.postDataSource,
                            ),
                        )
                    }
                }
            }
        val relationTargets =
            buildList {
                addAll(context.searchDataSources.toAgentRelationTargets())
                if (statusContext != null) {
                    val authenticated = statusContext.postDataSource as? AuthenticatedMicroblogDataSource
                    val relationDataSource = statusContext.postDataSource as? RelationDataSource
                    if (authenticated != null && relationDataSource != null && none { it.dataSource === relationDataSource }) {
                        add(
                            AgentRelationTarget(
                                accountKey = authenticated.accountKey,
                                platformType = statusContext.currentPlatformType,
                                dataSource = relationDataSource,
                            ),
                        )
                    }
                }
            }
        val userTargets =
            buildList {
                addAll(context.searchDataSources.toAgentUserTargets())
                if (statusContext != null && microblogDataSource != null && none { it.dataSource === microblogDataSource }) {
                    microblogDataSource
                        .toAgentUserTarget(
                            accountKey = null,
                            platformType = statusContext.currentPlatformType,
                        )?.let(::add)
                }
            }

        val session =
            AgentToolSession(
                status = statusContext,
                searchTargets = searchTargets,
                composeTargets = composeTargets,
                postActionTargets = postActionTargets,
                relationTargets = relationTargets,
                subscriptionDataSource = KoinSubscriptionDataSource,
                localCacheRepository = localCacheRepository,
                userTargets = userTargets,
                attachmentStore = AgentToolAttachmentStore(),
                inputRequestStore = AgentToolInputRequestStore(),
            )
        return AgentToolSet(
            toolRegistry =
                ToolRegistry {
                    tool(LoadStatusContextTool(session))
                    tool(LoadPostContextTool(session))
                    tool(SearchPostsTool(session))
                    tool(SearchUsersTool(session))
                    tool(LoadUserProfileTool(session))
                    tool(LoadHomeTimelineTool(session))
                    tool(LoadUserTimelineTool(session))
                    tool(LoadDiscoverStatusesTool(session))
                    tool(LoadDiscoverUsersTool(session))
                    tool(LoadDiscoverHashtagsTool(session))
                    tool(LoadFollowingTool(session))
                    tool(LoadFollowersTool(session))
                    tool(LoadProfileTabsTool(session))
                    tool(ComposePostTool(session))
                    tool(ListPostActionsTool(session))
                    tool(ExecutePostActionTool(session))
                    tool(LoadUserRelationTool(session))
                    tool(ListRelationActionsTool(session))
                    tool(ExecuteRelationActionTool(session))
                    tool(ListSubscriptionSourcesTool(session))
                    tool(DetectSubscriptionSourceTool(session))
                    tool(LoadSubscriptionTimelineTool(session))
                    tool(SaveSubscriptionSourceTool(session))
                    tool(DeleteSubscriptionSourceTool(session))
                    tool(LoadRssArticleTool(session))
                    tool(SearchCachedPostsTool(session))
                    tool(ListViewedPostsTool(session))
                    tool(SearchCachedUsersTool(session))
                    tool(ListViewedUsersTool(session))
                },
            systemPromptGuidance =
                listOf(
                    searchTargets.searchPlatformGuidance(),
                    composeTargets.composePlatformGuidance(),
                    postActionTargets.postActionGuidance(),
                    relationTargets.relationActionGuidance(),
                    AGENT_SUBSCRIPTION_TOOL_GUIDANCE,
                    AGENT_LOCAL_CACHE_TOOL_GUIDANCE,
                    AGENT_MICROBLOG_TOOL_SOURCE_GUIDANCE,
                    AGENT_SEARCH_BEHAVIOR_GUIDANCE,
                    AGENT_COMPOSE_BEHAVIOR_GUIDANCE,
                    AGENT_CONFIRMATION_BEHAVIOR_GUIDANCE,
                    AGENT_ATTACHMENT_GUIDANCE,
                ).joinToString(separator = "\n\n"),
            traceRegistry = AGENT_TOOL_TRACE_REGISTRY,
            attachmentStore = session.attachmentStore,
            inputRequestStore = session.inputRequestStore,
        )
    }

    private companion object {
        val AGENT_TOOL_TRACE_REGISTRY =
            agentToolTraceRegistry(
                LoadStatusContextTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.LoadStatusContextStarted,
                        completed = AgentToolKey.LoadStatusContextCompleted,
                        validationFailed = AgentToolKey.LoadStatusContextValidationFailed,
                        failed = AgentToolKey.LoadStatusContextFailed,
                    ),
                LoadPostContextTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.LoadStatusContextStarted,
                        completed = AgentToolKey.LoadStatusContextCompleted,
                        validationFailed = AgentToolKey.LoadStatusContextValidationFailed,
                        failed = AgentToolKey.LoadStatusContextFailed,
                    ),
                SearchPostsTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                SearchUsersTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchUsersStarted,
                        completed = AgentToolKey.SearchUsersCompleted,
                        validationFailed = AgentToolKey.SearchUsersValidationFailed,
                        failed = AgentToolKey.SearchUsersFailed,
                    ),
                LoadUserProfileTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchUsersStarted,
                        completed = AgentToolKey.SearchUsersCompleted,
                        validationFailed = AgentToolKey.SearchUsersValidationFailed,
                        failed = AgentToolKey.SearchUsersFailed,
                    ),
                LoadHomeTimelineTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                LoadUserTimelineTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                LoadDiscoverStatusesTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                LoadDiscoverUsersTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchUsersStarted,
                        completed = AgentToolKey.SearchUsersCompleted,
                        validationFailed = AgentToolKey.SearchUsersValidationFailed,
                        failed = AgentToolKey.SearchUsersFailed,
                    ),
                LoadDiscoverHashtagsTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                LoadFollowingTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchUsersStarted,
                        completed = AgentToolKey.SearchUsersCompleted,
                        validationFailed = AgentToolKey.SearchUsersValidationFailed,
                        failed = AgentToolKey.SearchUsersFailed,
                    ),
                LoadFollowersTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchUsersStarted,
                        completed = AgentToolKey.SearchUsersCompleted,
                        validationFailed = AgentToolKey.SearchUsersValidationFailed,
                        failed = AgentToolKey.SearchUsersFailed,
                    ),
                LoadProfileTabsTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                ListPostActionsTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                ExecutePostActionTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                LoadUserRelationTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchUsersStarted,
                        completed = AgentToolKey.SearchUsersCompleted,
                        validationFailed = AgentToolKey.SearchUsersValidationFailed,
                        failed = AgentToolKey.SearchUsersFailed,
                    ),
                ListRelationActionsTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchUsersStarted,
                        completed = AgentToolKey.SearchUsersCompleted,
                        validationFailed = AgentToolKey.SearchUsersValidationFailed,
                        failed = AgentToolKey.SearchUsersFailed,
                    ),
                ExecuteRelationActionTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchUsersStarted,
                        completed = AgentToolKey.SearchUsersCompleted,
                        validationFailed = AgentToolKey.SearchUsersValidationFailed,
                        failed = AgentToolKey.SearchUsersFailed,
                    ),
                ListSubscriptionSourcesTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                DetectSubscriptionSourceTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                LoadSubscriptionTimelineTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                SaveSubscriptionSourceTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                DeleteSubscriptionSourceTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                LoadRssArticleTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                SearchCachedPostsTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                ListViewedPostsTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchPostsStarted,
                        completed = AgentToolKey.SearchPostsCompleted,
                        validationFailed = AgentToolKey.SearchPostsValidationFailed,
                        failed = AgentToolKey.SearchPostsFailed,
                    ),
                SearchCachedUsersTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchUsersStarted,
                        completed = AgentToolKey.SearchUsersCompleted,
                        validationFailed = AgentToolKey.SearchUsersValidationFailed,
                        failed = AgentToolKey.SearchUsersFailed,
                    ),
                ListViewedUsersTool.NAME to
                    AgentToolTraceKeys(
                        started = AgentToolKey.SearchUsersStarted,
                        completed = AgentToolKey.SearchUsersCompleted,
                        validationFailed = AgentToolKey.SearchUsersValidationFailed,
                        failed = AgentToolKey.SearchUsersFailed,
                    ),
            )
    }
}

internal data class AgentToolContext(
    val status: StatusContext? = null,
    val searchDataSources: List<AccountMicroblogDataSource> = emptyList(),
) {
    data class StatusContext(
        val postDataSource: PostDataSource,
        val statusKey: MicroBlogKey,
        val currentPlatformType: PlatformType,
        val currentPost: UiTimelineV2.Post? = null,
    )

    companion object {
        val Empty = AgentToolContext()
    }
}

internal data class AgentToolSession(
    val status: AgentToolContext.StatusContext?,
    val searchTargets: List<AgentSearchTarget>,
    val composeTargets: List<AgentComposeTarget>,
    val postActionTargets: List<AgentPostActionTarget>,
    val relationTargets: List<AgentRelationTarget> = emptyList(),
    val subscriptionDataSource: SubscriptionDataSource? = null,
    val userTargets: List<AgentUserTarget>,
    val attachmentStore: AgentToolAttachmentStore,
    val inputRequestStore: AgentToolInputRequestStore,
    val subscriptionItemStore: AgentSubscriptionItemStore = AgentSubscriptionItemStore(),
    val localCacheRepository: LocalCacheRepository? = null,
) {
    fun statusMicroblogDataSource(): MicroblogDataSource? = status?.postDataSource as? MicroblogDataSource

    fun statusKey(): MicroBlogKey? = status?.statusKey

    fun statusContextUnavailableMessage(): String =
        when {
            status == null -> {
                "The current agent session does not include a post context."
            }

            statusMicroblogDataSource() == null -> {
                "The current post context does not support microblog tools."
            }

            else -> {
                "The current post context is unavailable."
            }
        }
}

internal class AgentToolAttachmentStore {
    private val mutex = Mutex()
    private val attachments = mutableListOf<AgentConversationAttachment>()

    suspend fun addPosts(posts: List<UiTimelineV2.Post>) {
        mutex.withLock {
            attachments += posts.map { AgentConversationAttachment.Post(it) }
        }
    }

    suspend fun addUsers(users: List<UiProfile>) {
        mutex.withLock {
            attachments += users.map { AgentConversationAttachment.User(it) }
        }
    }

    suspend fun snapshot(): List<AgentConversationAttachment> =
        mutex.withLock {
            attachments
                .distinctBy {
                    when (it) {
                        is AgentConversationAttachment.Post -> "post:${it.post.platformType}:${it.post.statusKey}"
                        is AgentConversationAttachment.User -> "user:${it.user.platformType}:${it.user.key}"
                        is AgentConversationAttachment.InputRequest -> "input-request:${it.state.request.requestId}"
                    }
                }
        }
}

internal class AgentToolInputRequestStore {
    private val mutex = Mutex()
    private var inputRequest: AgentInputRequest? = null

    suspend fun set(request: AgentInputRequest) {
        mutex.withLock {
            inputRequest = request
        }
    }

    suspend fun snapshot(): AgentInputRequest? =
        mutex.withLock {
            inputRequest
        }
}

internal data class AgentToolSet(
    val toolRegistry: ToolRegistry,
    val systemPromptGuidance: String,
    val traceRegistry: AgentToolTraceRegistry,
    val attachmentStore: AgentToolAttachmentStore,
    val inputRequestStore: AgentToolInputRequestStore,
) {
    companion object {
        val Empty =
            AgentToolSet(
                toolRegistry = ToolRegistry.EMPTY,
                systemPromptGuidance = "",
                traceRegistry = AgentToolTraceRegistry(emptyMap()),
                attachmentStore = AgentToolAttachmentStore(),
                inputRequestStore = AgentToolInputRequestStore(),
            )
    }
}

private const val AGENT_CONFIRMATION_BEHAVIOR_GUIDANCE =
    """
    Confirmation UI guidance:
    - For any action that changes user data or performs a side effect, call the relevant tool with confirmed=false first.
    - After a confirmed=false call, always surface the confirmation content returned by the tool. Do not return an empty assistant message.
    - If the action is about a concrete post or user, include the relevant attachmentRef in the visible response when available so Flare can render the post/user card.
    - Do not ask the user to manually type confirmation when a confirmation input request is available; let Flare show the confirmation button.
    - Only call the same tool with confirmed=true after the latest user input explicitly confirms the pending request.
    """

internal data class AgentSearchTarget(
    val platformType: PlatformType?,
    val dataSource: MicroblogDataSource,
)

internal data class AgentComposeTarget(
    val accountKey: MicroBlogKey,
    val platformType: PlatformType,
    val dataSource: ComposeDataSource,
)

internal data class AgentPostActionTarget(
    val accountKey: MicroBlogKey,
    val platformType: PlatformType,
    val dataSource: PostDataSource,
)

internal data class AgentRelationTarget(
    val accountKey: MicroBlogKey,
    val platformType: PlatformType,
    val dataSource: RelationDataSource,
)

internal data class AgentUserTarget(
    val accountKey: MicroBlogKey?,
    val platformType: PlatformType,
    val dataSource: MicroblogDataSource,
    val loadUserById: suspend (String) -> UiProfile,
)

internal fun List<AccountMicroblogDataSource>.toAgentSearchTargets(): List<AgentSearchTarget> =
    map { item ->
        AgentSearchTarget(
            platformType = item.platformType,
            dataSource = item.dataSource,
        )
    }

internal fun List<AccountMicroblogDataSource>.toAgentComposeTargets(): List<AgentComposeTarget> =
    mapNotNull { item ->
        val dataSource = item.dataSource as? ComposeDataSource ?: return@mapNotNull null
        AgentComposeTarget(
            accountKey = item.accountKey,
            platformType = item.platformType,
            dataSource = dataSource,
        )
    }

internal fun List<AccountMicroblogDataSource>.toAgentPostActionTargets(): List<AgentPostActionTarget> =
    mapNotNull { item ->
        val dataSource = item.dataSource as? PostDataSource ?: return@mapNotNull null
        AgentPostActionTarget(
            accountKey = item.accountKey,
            platformType = item.platformType,
            dataSource = dataSource,
        )
    }

internal fun List<AccountMicroblogDataSource>.toAgentRelationTargets(): List<AgentRelationTarget> =
    mapNotNull { item ->
        val dataSource = item.dataSource as? RelationDataSource ?: return@mapNotNull null
        AgentRelationTarget(
            accountKey = item.accountKey,
            platformType = item.platformType,
            dataSource = dataSource,
        )
    }

internal fun List<AccountMicroblogDataSource>.toAgentUserTargets(): List<AgentUserTarget> =
    mapNotNull { item ->
        item.dataSource.toAgentUserTarget(
            accountKey = item.accountKey,
            platformType = item.platformType,
        )
    }

private fun MicroblogDataSource.toAgentUserTarget(
    accountKey: MicroBlogKey?,
    platformType: PlatformType,
): AgentUserTarget? {
    val userDataSource = this as? UserDataSource ?: return null
    return AgentUserTarget(
        accountKey = accountKey,
        platformType = platformType,
        dataSource = this,
        loadUserById = { userId ->
            when (
                val state =
                    userDataSource
                        .userHandler
                        .userById(userId)
                        .toUi()
                        .first { it.isTerminalUserLoadState() }
            ) {
                is UiState.Success -> state.data
                is UiState.Error -> throw state.throwable
                is UiState.Loading -> error("User profile is still loading.")
            }
        },
    )
}

private fun UiState<UiProfile>.isTerminalUserLoadState(): Boolean =
    when (this) {
        is UiState.Success -> true
        is UiState.Error -> throwable.message != "Data is null"
        is UiState.Loading -> false
    }

internal fun List<AgentSearchTarget>.filterByPlatformNames(platforms: List<String>): List<AgentSearchTarget> {
    val platformFilter = platforms.toPlatformFilter()
    return when {
        platformFilter.searchAll -> this
        platformFilter.platformTypes.isEmpty() -> emptyList()
        else -> filter { it.platformType in platformFilter.platformTypes }
    }
}

internal fun List<AgentUserTarget>.filterUserTargetsByPlatformNames(platforms: List<String>): List<AgentUserTarget> {
    val platformFilter = platforms.toPlatformFilter()
    return when {
        platformFilter.searchAll -> this
        platformFilter.platformTypes.isEmpty() -> emptyList()
        else -> filter { it.platformType in platformFilter.platformTypes }
    }
}

internal fun List<AgentComposeTarget>.filterComposeTargetsByPlatformNames(platforms: List<String>): List<AgentComposeTarget> {
    val platformFilter = platforms.toPlatformFilter()
    return when {
        platformFilter.searchAll -> this
        platformFilter.platformTypes.isEmpty() -> emptyList()
        else -> filter { it.platformType in platformFilter.platformTypes }
    }
}

internal fun List<AgentRelationTarget>.filterRelationTargetsByPlatformNames(platforms: List<String>): List<AgentRelationTarget> {
    val platformFilter = platforms.toPlatformFilter()
    return when {
        platformFilter.searchAll -> this
        platformFilter.platformTypes.isEmpty() -> emptyList()
        else -> filter { it.platformType in platformFilter.platformTypes }
    }
}

internal fun Iterable<PlatformType>.filterPlatformTypesByPlatformNames(platforms: List<String>): Set<PlatformType> {
    val platformFilter = platforms.toPlatformFilter()
    return when {
        platformFilter.searchAll -> toSet()
        platformFilter.platformTypes.isEmpty() -> emptySet()
        else -> filter { it in platformFilter.platformTypes }.toSet()
    }
}

internal fun List<AgentSearchTarget>.searchPlatformGuidance(): String {
    val platformTypes =
        mapNotNull { it.platformType }
            .distinct()
    return if (platformTypes.isEmpty()) {
        """

        Search platform guidance:
        - No searchable signed-in platforms are currently available.
        - If you use search, leave the platform list empty.
        """
    } else {
        val platformLines =
            platformTypes.joinToString(separator = "\n") { platformType ->
                buildString {
                    append("- ")
                    append(platformType.name)
                    val aliases = platformType.searchAliases()
                    if (aliases.isNotEmpty()) {
                        append(" (aliases: ")
                        append(aliases.joinToString())
                        append(")")
                    }
                }
            }
        """

        Search platform guidance:
        - Available searchable platforms are:
        $platformLines
        - Use these exact platform names in the search tool's platform list when limiting search.
        - You may use the listed aliases; they resolve to the corresponding platform.
        - Leave the platform list empty to search every available platform.
        - If the user asks for cross-platform, all-platform, broad, or general social context, leave the platform list empty.
        - If the user explicitly names one or more platforms in a search request, pass those platform names or aliases to the search tool.
        """
    }.trimIndent()
}

internal fun List<AgentComposeTarget>.composePlatformGuidance(): String =
    if (isEmpty()) {
        """

        Compose guidance:
        - No signed-in accounts currently support composing posts.
        - If the user asks to publish or send a post, explain that no compose-capable account is available.
        """
    } else {
        val accountLines =
            joinToString(separator = "\n") { target ->
                buildString {
                    append("- accountKey=")
                    append(target.accountKey)
                    append(", platform=")
                    append(target.platformType.name)
                    val aliases = target.platformType.searchAliases()
                    if (aliases.isNotEmpty()) {
                        append(" (aliases: ")
                        append(aliases.joinToString())
                        append(")")
                    }
                }
            }
        """

        Compose guidance:
        - Available compose-capable signed-in accounts are:
        $accountLines
        - Use compose_post when the user asks to publish, post, toot, tweet, send a status, reply to a post, quote a post, or otherwise compose social content.
        - For replies and quotes, call compose_post with action=reply or action=quote and provide the current status, a returned post attachmentRef, or an explicit target status key.
        - If the user did not specify a platform or account and several accounts could match, still call compose_post with confirmed=false, blank account fields, and empty platforms so the tool can show a structured platform/account picker. Do not ask with a numbered prose list.
        - For the first compose request, call compose_post with confirmed=false and then show the returned confirmation wording to the user.
        - Only call compose_post with confirmed=true when the latest user message explicitly confirms the exact account and content already shown for confirmation.
        - Never publish if the latest user message changes the account, content, visibility, or other post settings; request confirmation again.
        """
    }.trimIndent()

internal fun List<AgentPostActionTarget>.postActionGuidance(): String =
    if (isEmpty()) {
        """

        Post action guidance:
        - No signed-in accounts currently expose executable post actions.
        """
    } else {
        val accountLines =
            joinToString(separator = "\n") { target ->
                "- accountKey=${target.accountKey}, platform=${target.platformType.name}"
            }
        """

        Post action guidance:
        - Available post-action signed-in accounts are:
        $accountLines
        - Use list_post_actions when the user asks what can be done to a post, or when the target action is ambiguous.
        - Use execute_post_action when the user asks to like, unlike, repost, unrepost, bookmark, unbookmark, favorite, unfavorite, react, unreact, vote in a poll, accept a follow request, reject a follow request, or otherwise execute an available post action.
        - Do not use post action tools for translate, show original, share, fx share, or more-menu UI operations.
        - Use compose_post for reply, comment, and quote because those actions require text content.
        - Always require confirmation before executing execute_post_action. Do not call execute_post_action with confirmed=true unless the latest user message clearly confirms the exact action and target post already shown.
        """
    }.trimIndent()

internal fun List<AgentRelationTarget>.relationActionGuidance(): String =
    if (isEmpty()) {
        """

        Relation action guidance:
        - No signed-in accounts currently expose relation actions.
        """
    } else {
        val accountLines =
            joinToString(separator = "\n") { target ->
                "- accountKey=${target.accountKey}, platform=${target.platformType.name}, supported=${target.dataSource.supportedRelationTypes.joinToString()}"
            }
        """

        Relation action guidance:
        - Available relation-capable signed-in accounts are:
        $accountLines
        - Use load_user_relation when the user asks whether they follow, are followed by, blocked, muted, or have pending follow requests with a user.
        - Use list_relation_actions when the user asks what account relationship actions are available for a user, or when the target action is ambiguous.
        - Use execute_relation_action when the user asks to follow, unfollow, block, unblock, mute, or unmute a user.
        - For relation actions, prefer a user attachmentRef, the current post author, or an explicit user key. If the target user or account is ambiguous, call the relation tool with blank target/account fields so it can show a structured picker.
        - Always require confirmation before executing execute_relation_action. Do not call execute_relation_action with confirmed=true unless the latest user message clearly confirms the exact account, target user, and action already shown.
        """
    }.trimIndent()

internal data class AgentToolTraceKeys(
    val started: AgentToolKey,
    val completed: AgentToolKey,
    val validationFailed: AgentToolKey,
    val failed: AgentToolKey,
)

internal class AgentToolTraceRegistry(
    private val keysByToolName: Map<String, AgentToolTraceKeys>,
) {
    fun keyFor(
        toolName: String?,
        phase: AgentPhase,
    ): AgentToolKey? {
        val keys = keysByToolName[toolName] ?: return null
        return when (phase) {
            AgentPhase.ToolCallStarted -> keys.started
            AgentPhase.ToolCallCompleted -> keys.completed
            AgentPhase.ToolValidationFailed -> keys.validationFailed
            AgentPhase.ToolCallFailed -> keys.failed
            else -> null
        }
    }
}

internal fun agentToolTraceRegistry(vararg entries: Pair<String, AgentToolTraceKeys>): AgentToolTraceRegistry =
    AgentToolTraceRegistry(entries.toMap())

private data class AgentPlatformFilter(
    val searchAll: Boolean,
    val platformTypes: Set<PlatformType>,
)

private fun List<String>.toPlatformFilter(): AgentPlatformFilter {
    val normalizedPlatforms =
        map { it.searchPlatformKey() }
            .filter { it.isNotBlank() }
    if (normalizedPlatforms.isEmpty() || normalizedPlatforms.any { it in SEARCH_ALL_PLATFORM_KEYS }) {
        return AgentPlatformFilter(
            searchAll = true,
            platformTypes = emptySet(),
        )
    }
    return AgentPlatformFilter(
        searchAll = false,
        platformTypes = normalizedPlatforms.mapNotNull { it.toPlatformTypeOrNull() }.toSet(),
    )
}

private fun String.toPlatformTypeOrNull(): PlatformType? =
    PlatformType.entries.firstOrNull { platformType ->
        this == platformType.searchNameKey() ||
            platformType.searchAliases().any { alias -> this == alias.searchPlatformKey() }
    }

private fun PlatformType.searchAliases(): List<String> =
    when (name) {
        PlatformType.Mastodon.name -> listOf("masto", "fediverse", "长毛象", "联邦宇宙")
        PlatformType.Misskey.name -> listOf("mk")
        PlatformType.Bluesky.name -> listOf("bsky", "blue sky", "蓝天")
        PlatformType.Pixiv.name -> listOf("pxv", "p站")
        PlatformType.xQt.name -> listOf("x", "x.com", "twitter", "twitter.com", "推特")
        PlatformType.VVo.name -> listOf("weibo", "sina weibo", "微博", "新浪微博")
        PlatformType.Nostr.name -> listOf("nostr")
        else -> emptyList()
    }

private fun PlatformType.searchNameKey(): String = name.searchPlatformKey()

private fun String.searchPlatformKey(): String =
    trim()
        .lowercase()
        .replace("-", "")
        .replace("_", "")
        .replace(" ", "")
        .replace(".", "")

private val SEARCH_ALL_PLATFORM_KEYS =
    setOf(
        "*",
        "all",
        "any",
        "global",
        "crossplatform",
        "everywhere",
        "allplatforms",
        "全部",
        "所有",
        "全平台",
        "跨平台",
        "各平台",
    ).map { it.searchPlatformKey() }.toSet()

private const val AGENT_SUBSCRIPTION_TOOL_GUIDANCE =
    """
    Subscription tool guidance:
    - Use subscription tools when the user asks about Flare subscriptions, subscribed sources, RSS feeds, RSS articles, or subscription-backed timelines such as Mastodon public/local/trends.
    - Subscriptions are typed by SubscriptionType values: RSS, MASTODON_TRENDS, MASTODON_PUBLIC, and MASTODON_LOCAL. Future subscription types should be treated the same way when exposed by the app.
    - Use detect_subscription_source before saving a URL or host when the exact type is unclear.
    - Use load_subscription_timeline for a saved source or an explicit type + URL/host. RSS results are feed articles; Mastodon subscription timelines return post items.
    - Use load_rss_article only for actual RSS article URLs or rssArticleRef values returned by load_subscription_timeline. Do not use it for Mastodon posts or other non-RSS subscription items.
    - Saving or deleting a subscription is a user-visible side effect. Always require confirmation before calling save_subscription_source or delete_subscription_source with confirmed=true.
    - When the user asks to add, save, update, remove, or delete a subscription source, do not write a manual confirmation message as the final answer. Call save_subscription_source or delete_subscription_source with confirmed=false first. The tool will create a structured confirmation request with one confirm button.
    - After save_subscription_source or delete_subscription_source returns a confirmation message for confirmed=false, return that message to the user. Do not ask for confirmation in your own words and do not claim the source has been saved/deleted yet.
    - Only call save_subscription_source or delete_subscription_source with confirmed=true when the latest user message explicitly confirms the exact source and action previously shown by the tool confirmation request.
    """

private const val AGENT_MICROBLOG_TOOL_SOURCE_GUIDANCE =
    """
    Microblog tool source guidance:
    - The MicroblogDataSource tools are not cache tools and do not query Flare's local cache.
    - They call the active signed-in platform data source loaders directly, so results reflect what those accounts and platforms return at tool-call time.
    - All post-returning MicroblogDataSource tools request page size 200.
    """

private const val AGENT_SEARCH_BEHAVIOR_GUIDANCE =
    """
    Search behavior guidance:
    - Treat search as an action the user can request. If the user asks to search, find, look up, check, compare, or inspect posts/users/social discussion, call the relevant search tool before answering.
    - If the user names a platform in a search or social-context request, call the relevant search tool and pass that platform name or alias in the platform list.
    - If the user asks for broad social context, cross-platform context, trends, public discussion, examples, recommendations, or recent platform-visible information without narrowing to one platform, leave the platform list empty so the search runs across every signed-in platform.
    - Prefer one concise all-platform search over answering from memory when the request depends on signed-in social content.
    - If search is unavailable or returns no useful results, say that plainly instead of pretending you searched.
    """

private const val AGENT_COMPOSE_BEHAVIOR_GUIDANCE =
    """
    Compose behavior guidance:
    - Publishing is a user-visible side effect. Always require a confirmation step before sending.
    - The confirmation message must restate the account, action, content, and target post when replying or quoting. Examples: "确认使用以下账号发送以下内容吗？", "确认使用以下账号回复以下帖子，并发送以下内容吗？"
    - Do not call compose_post with confirmed=true unless the latest user message clearly confirms the previously restated account, action, target post, and content.
    - If the user says to edit or revise the post after a confirmation request, call compose_post again with confirmed=false for the revised content.
    - When platform, account, or target-post choice is ambiguous, prefer the structured input request returned by compose_post instead of writing a numbered list in prose.
    - Do not invent media, polls, target posts, or account identities. For replies and quotes, use the current post context, a returned post attachmentRef, or ask the user to choose a target post.
    """

private const val AGENT_ATTACHMENT_GUIDANCE =
    """
    UI attachment guidance:
    - Tool results may include attachmentRef markers like [[post:...]] or [[user:...]].
    - These markers render as rich UI cards in Flare. Prefer showing a card instead of only describing a returned post or user in prose.
    - If your final answer discusses, recommends, compares, cites, summarizes, or identifies a specific returned post or user, include that item's exact attachmentRef marker.
    - Place each marker on its own line exactly where the card should appear, usually immediately after the sentence or bullet that refers to it.
    - Use up to 3 highly relevant cards. Do not include low-value cards that would distract from the answer.
    - Only use attachmentRef markers that appeared in tool results. Never invent attachmentRef markers.
    - Do not explain the marker syntax to the user.
    - Example placement: write a short sentence, then put the exact marker copied from the tool result on the next line.
    """
