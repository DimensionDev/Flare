package dev.dimension.flare.feature.agent.common

import ai.koog.agents.core.tools.ToolRegistry
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.feature.agent.status.LoadStatusContextTool
import dev.dimension.flare.feature.agent.status.SearchPostsTool
import dev.dimension.flare.feature.agent.status.SearchUsersTool
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import org.koin.core.annotation.Single

@Single
internal class AgentToolProvider {
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

        val session =
            AgentToolSession(
                status = statusContext,
                searchTargets = searchTargets,
            )
        return AgentToolSet(
            toolRegistry =
                ToolRegistry {
                    tool(LoadStatusContextTool(session))
                    tool(SearchPostsTool(session))
                    tool(SearchUsersTool(session))
                },
            systemPromptGuidance = searchTargets.searchPlatformGuidance(),
            traceRegistry = AGENT_TOOL_TRACE_REGISTRY,
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
    )

    companion object {
        val Empty = AgentToolContext()
    }
}

internal data class AgentToolSession(
    val status: AgentToolContext.StatusContext?,
    val searchTargets: List<AgentSearchTarget>,
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

internal data class AgentToolSet(
    val toolRegistry: ToolRegistry,
    val systemPromptGuidance: String,
    val traceRegistry: AgentToolTraceRegistry,
) {
    companion object {
        val Empty =
            AgentToolSet(
                toolRegistry = ToolRegistry.EMPTY,
                systemPromptGuidance = "",
                traceRegistry = AgentToolTraceRegistry(emptyMap()),
            )
    }
}

internal data class AgentSearchTarget(
    val platformType: PlatformType?,
    val dataSource: MicroblogDataSource,
)

internal fun List<AccountMicroblogDataSource>.toAgentSearchTargets(): List<AgentSearchTarget> =
    map { item ->
        AgentSearchTarget(
            platformType = item.platformType,
            dataSource = item.dataSource,
        )
    }

internal fun List<AgentSearchTarget>.filterByPlatformNames(platforms: List<String>): List<AgentSearchTarget> {
    val platformFilter = platforms.toPlatformFilter()
    if (platformFilter.isEmpty()) {
        return this
    }
    return filter { it.platformType in platformFilter }
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
        """
    }.trimIndent()
}

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

private fun List<String>.toPlatformFilter(): Set<PlatformType> = mapNotNull { it.toPlatformTypeOrNull() }.toSet()

private fun String.toPlatformTypeOrNull(): PlatformType? {
    val normalized = searchPlatformKey()
    if (normalized == "all" || normalized == "*") {
        return null
    }
    return PlatformType.entries.firstOrNull { platformType ->
        normalized == platformType.searchNameKey() ||
            platformType.searchAliases().any { alias -> normalized == alias.searchPlatformKey() }
    }
}

private fun PlatformType.searchAliases(): List<String> =
    when (name) {
        PlatformType.Bluesky.name -> listOf("bsky")
        PlatformType.xQt.name -> listOf("x", "twitter")
        PlatformType.VVo.name -> listOf("weibo")
        else -> emptyList()
    }

private fun PlatformType.searchNameKey(): String = name.searchPlatformKey()

private fun String.searchPlatformKey(): String =
    trim()
        .lowercase()
        .replace("-", "")
        .replace("_", "")
        .replace(" ", "")
