package dev.dimension.flare.feature.agent.common

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import kotlinx.serialization.Serializable

internal class LoadUserRelationTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadUserRelationTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load the signed-in account's relation state with a target user through RelationDataSource. " +
                "Use this for follow/follower/block/mute/pending-follow relationship questions. " +
                "If account fields are blank, this loads relation state from every matching relation-capable signed-in account.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Exact user attachmentRef marker or ref from a previous tool result, such as [[user:Mastodon:host:id]].")
        val targetUserRef: String = "",
        @property:LLMDescription("Target user id. Prefer targetUserRef when available.")
        val targetUserId: String = "",
        @property:LLMDescription("Target user host. Leave blank only when the target key has no host.")
        val targetUserHost: String = "",
        @property:LLMDescription("Account id to inspect from. Leave blank to inspect every matching relation-capable account.")
        val accountId: String = "",
        @property:LLMDescription("Account host to inspect from. Leave blank only when the account key has no host.")
        val accountHost: String = "",
        @property:LLMDescription(
            "Platform names or aliases to narrow account selection. Leave empty to use the target user's platform when known, otherwise every relation-capable account.",
        )
        val platforms: List<String> = emptyList(),
        @property:LLMDescription("In a post insight session, set true to target the current post's author.")
        val useCurrentPostAuthor: Boolean = false,
    )

    override suspend fun execute(args: Args): String {
        val user =
            session.resolveRelationUser(args.toRelationUserArgs())
                ?: return session.relationUserSelectionMessage(
                    args = args.toRelationToolArgs(),
                    promptKey = AgentLocalizedTextKey.SelectRelationStateUser,
                    valuePrefix = AgentUiStrings.Relation.RelationStateUserPrefix,
                )
        val targets = session.resolveRelationTargets(args.toRelationAccountArgs(), user)
        if (targets.isEmpty()) {
            return session.noRelationTargetsMessage(args.platforms, user)
        }
        val results =
            targets.mapNotNull { target ->
                runCatching {
                    RelationStateResult(
                        target = target,
                        relation = target.dataSource.relationHandler.dataSource.relation(user.userKey),
                    )
                }.getOrNull()
            }
        if (results.isEmpty()) {
            return "No relation state was returned for ${user.userKey}."
        }
        return buildString {
            appendLine("User relation")
            appendLine("Target user key: ${user.userKey}")
            appendLine("Target platform: ${user.platformType?.name.orEmpty()}")
            user.profile?.let { profile ->
                appendLine("Target displayName: ${profile.name.raw}")
                appendLine("Target handle: ${profile.handle.raw}")
                appendLine("Target attachmentRef: ${profile.agentAttachmentMarker()}")
            }
            results.forEach { result ->
                appendLine()
                append(result.toToolText())
            }
        }.trim()
    }

    companion object {
        const val NAME = "load_user_relation"
    }
}

internal class ListRelationActionsTool(
    private val session: AgentToolSession,
) : SimpleTool<ListRelationActionsTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "List executable relation actions for a target user through RelationDataSource. " +
                "Use this when the user asks what account relationship actions are available, or when action/account selection is ambiguous.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Exact user attachmentRef marker or ref from a previous tool result, such as [[user:Mastodon:host:id]].")
        val targetUserRef: String = "",
        @property:LLMDescription("Target user id. Prefer targetUserRef when available.")
        val targetUserId: String = "",
        @property:LLMDescription("Target user host. Leave blank only when the target key has no host.")
        val targetUserHost: String = "",
        @property:LLMDescription("Account id to use. Leave blank when account selection is ambiguous.")
        val accountId: String = "",
        @property:LLMDescription("Account host to use. Leave blank only when the account key has no host.")
        val accountHost: String = "",
        @property:LLMDescription(
            "Platform names or aliases to narrow account selection. Leave empty to use the target user's platform when known, otherwise every relation-capable account.",
        )
        val platforms: List<String> = emptyList(),
        @property:LLMDescription("In a post insight session, set true to target the current post's author.")
        val useCurrentPostAuthor: Boolean = false,
    )

    override suspend fun execute(args: Args): String {
        val toolArgs = args.toRelationToolArgs()
        val user =
            session.resolveRelationUser(toolArgs.user)
                ?: return session.relationUserSelectionMessage(
                    args = toolArgs,
                    promptKey = AgentLocalizedTextKey.SelectRelationUser,
                    valuePrefix = AgentUiStrings.Relation.ExecuteRelationUserPrefix,
                )
        val candidates = session.relationActionCandidates(user, toolArgs.account)
        if (candidates.isEmpty()) {
            return session.noRelationActionsMessage(toolArgs.account.platforms, user)
        }
        session.setRelationActionSelectionRequest(user, candidates)
        return buildString {
            appendLine("Available relation actions for ${user.userKey}:")
            candidates.forEach { candidate ->
                appendLine(
                    "- accountKey=${candidate.target.accountKey}, platform=${candidate.target.platformType.name}, " +
                        "actionId=${candidate.action.id}, actionName=${candidate.action.label}, " +
                        "supportedType=${candidate.action.requiredType.name}",
                )
            }
        }.trim()
    }

    companion object {
        const val NAME = "list_relation_actions"
    }
}

internal class ExecuteRelationActionTool(
    private val session: AgentToolSession,
    private val actionHandler: suspend (RelationDataSource, RelationAction, MicroBlogKey, Boolean) -> Unit = { dataSource, action, userKey, requestFollow ->
        when (action) {
            RelationAction.Follow -> dataSource.relationHandler.follow(userKey = userKey, requestFollow = requestFollow).join()
            RelationAction.Unfollow -> dataSource.relationHandler.unfollow(userKey).join()
            RelationAction.Block -> dataSource.relationHandler.block(userKey).join()
            RelationAction.Unblock -> dataSource.relationHandler.unblock(userKey).join()
            RelationAction.Mute -> dataSource.relationHandler.mute(userKey).join()
            RelationAction.Unmute -> dataSource.relationHandler.unmute(userKey).join()
        }
    },
) : SimpleTool<ExecuteRelationActionTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Execute a relation action for a target user through RelationDataSource after explicit confirmation. " +
                "Supported actions are follow, unfollow, block, unblock, mute, and unmute. " +
                "Use list_relation_actions first when action, account, or target user is ambiguous.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Relation action: follow, unfollow, block, unblock, mute, or unmute.")
        val action: String = "",
        @property:LLMDescription("Exact user attachmentRef marker or ref from a previous tool result, such as [[user:Mastodon:host:id]].")
        val targetUserRef: String = "",
        @property:LLMDescription("Target user id. Prefer targetUserRef when available.")
        val targetUserId: String = "",
        @property:LLMDescription("Target user host. Leave blank only when the target key has no host.")
        val targetUserHost: String = "",
        @property:LLMDescription("Account id to execute from. Leave blank only when account selection resolves to exactly one account.")
        val accountId: String = "",
        @property:LLMDescription("Account host to execute from. Leave blank only when the account key has no host.")
        val accountHost: String = "",
        @property:LLMDescription(
            "Platform names or aliases to narrow account selection. Leave empty to use the target user's platform when known, otherwise every relation-capable account.",
        )
        val platforms: List<String> = emptyList(),
        @property:LLMDescription("For follow on platforms with protected accounts, set true only when the user asked to request follow.")
        val requestFollow: Boolean = false,
        @property:LLMDescription("In a post insight session, set true to target the current post's author.")
        val useCurrentPostAuthor: Boolean = false,
        @property:LLMDescription("Set true only after the latest user message explicitly confirms the exact target user, account, and action.")
        val confirmed: Boolean = false,
    )

    override suspend fun execute(args: Args): String {
        val toolArgs = args.toRelationToolArgs()
        val user =
            session.resolveRelationUser(toolArgs.user)
                ?: return session.relationUserSelectionMessage(
                    args = toolArgs,
                    promptKey = AgentLocalizedTextKey.SelectRelationUser,
                    valuePrefix = AgentUiStrings.Relation.ExecuteRelationUserPrefix,
                )
        val action =
            args.action.toRelationActionOrNull()
                ?: return session.relationActionSelectionMessage(user, toolArgs)
        val target =
            session.resolveSingleRelationTarget(toolArgs.account, user)
                ?: return session.relationAccountSelectionMessage(user, toolArgs, action)
        if (target.accountKey == user.userKey) {
            return "Relation action cannot target the same signed-in account: ${target.accountKey}."
        }
        if (!target.supports(action)) {
            return "${target.platformType.name} account ${target.accountKey} does not support ${action.label}."
        }
        if (!args.confirmed) {
            val localizedPrompt =
                AgentUiStrings.text(
                    AgentLocalizedTextKey.RelationConfirmationMessage,
                    action.label,
                    target.accountKey.toString(),
                    target.platformType.name,
                    user.userKey.toString(),
                    user.profile?.name?.raw.orEmpty(),
                    user.profile?.handle?.raw.orEmpty(),
                )
            session.inputRequestStore.set(
                AgentInputRequest(
                    requestId = "relation-confirm:${target.accountKey}:${user.userKey}:${action.id}",
                    localizedPrompt = localizedPrompt,
                    options =
                        listOf(
                            AgentInputRequest.Option(
                                id = "confirm",
                                value = AgentUiStrings.Common.ConfirmExecute,
                                localizedLabel = AgentUiStrings.text(AgentLocalizedTextKey.ConfirmExecute),
                            ),
                        ),
                    allowFreeText = true,
                    localizedFreeTextPlaceholder = AgentUiStrings.text(AgentLocalizedTextKey.RelationConfirmationPlaceholder),
                    userPreview = user.profile,
                ),
            )
            return localizedPrompt.toAgentProtocolText()
        }

        actionHandler(target.dataSource, action, user.userKey, args.requestFollow)
        return buildString {
            appendLine("Relation action submitted.")
            appendLine("Action: ${action.label}")
            appendLine("Account: ${target.accountKey}")
            appendLine("Target user: ${user.userKey}")
            appendLine("Platform: ${target.platformType.name}")
        }.trim()
    }

    companion object {
        const val NAME = "execute_relation_action"
    }
}

internal enum class RelationAction(
    val id: String,
    val label: String,
    val requiredType: RelationActionType,
) {
    Follow("follow", "Follow", RelationActionType.Follow),
    Unfollow("unfollow", "Unfollow", RelationActionType.Follow),
    Block("block", "Block", RelationActionType.Block),
    Unblock("unblock", "Unblock", RelationActionType.Block),
    Mute("mute", "Mute", RelationActionType.Mute),
    Unmute("unmute", "Unmute", RelationActionType.Mute),
}

private data class RelationUserArgs(
    val targetUserRef: String,
    val targetUserId: String,
    val targetUserHost: String,
    val useCurrentPostAuthor: Boolean,
)

private data class RelationAccountArgs(
    val accountId: String,
    val accountHost: String,
    val platforms: List<String>,
)

private data class RelationToolArgs(
    val action: String,
    val user: RelationUserArgs,
    val account: RelationAccountArgs,
    val requestFollow: Boolean,
)

private data class RelationUserTarget(
    val userKey: MicroBlogKey,
    val platformType: PlatformType?,
    val profile: UiProfile?,
)

private data class RelationStateResult(
    val target: AgentRelationTarget,
    val relation: UiRelation,
)

private data class RelationActionCandidate(
    val target: AgentRelationTarget,
    val action: RelationAction,
)

private fun LoadUserRelationTool.Args.toRelationUserArgs(): RelationUserArgs =
    RelationUserArgs(
        targetUserRef = targetUserRef,
        targetUserId = targetUserId,
        targetUserHost = targetUserHost,
        useCurrentPostAuthor = useCurrentPostAuthor,
    )

private fun ListRelationActionsTool.Args.toRelationUserArgs(): RelationUserArgs =
    RelationUserArgs(
        targetUserRef = targetUserRef,
        targetUserId = targetUserId,
        targetUserHost = targetUserHost,
        useCurrentPostAuthor = useCurrentPostAuthor,
    )

private fun ExecuteRelationActionTool.Args.toRelationUserArgs(): RelationUserArgs =
    RelationUserArgs(
        targetUserRef = targetUserRef,
        targetUserId = targetUserId,
        targetUserHost = targetUserHost,
        useCurrentPostAuthor = useCurrentPostAuthor,
    )

private fun LoadUserRelationTool.Args.toRelationAccountArgs(): RelationAccountArgs =
    RelationAccountArgs(
        accountId = accountId,
        accountHost = accountHost,
        platforms = platforms,
    )

private fun ListRelationActionsTool.Args.toRelationAccountArgs(): RelationAccountArgs =
    RelationAccountArgs(
        accountId = accountId,
        accountHost = accountHost,
        platforms = platforms,
    )

private fun ExecuteRelationActionTool.Args.toRelationAccountArgs(): RelationAccountArgs =
    RelationAccountArgs(
        accountId = accountId,
        accountHost = accountHost,
        platforms = platforms,
    )

private fun LoadUserRelationTool.Args.toRelationToolArgs(): RelationToolArgs =
    RelationToolArgs(
        action = "",
        user = toRelationUserArgs(),
        account = toRelationAccountArgs(),
        requestFollow = false,
    )

private fun ListRelationActionsTool.Args.toRelationToolArgs(): RelationToolArgs =
    RelationToolArgs(
        action = "",
        user = toRelationUserArgs(),
        account = toRelationAccountArgs(),
        requestFollow = false,
    )

private fun ExecuteRelationActionTool.Args.toRelationToolArgs(): RelationToolArgs =
    RelationToolArgs(
        action = action,
        user = toRelationUserArgs(),
        account = toRelationAccountArgs(),
        requestFollow = requestFollow,
    )

private suspend fun AgentToolSession.resolveRelationUser(args: RelationUserArgs): RelationUserTarget? {
    if (args.useCurrentPostAuthor) {
        status?.currentPost?.agentDisplayPost()?.user?.let { user ->
            return user.toRelationUserTarget()
        }
    }
    args.targetUserRef.trim().takeIf { it.isNotBlank() }?.let { ref ->
        findRelationUserByRef(ref)?.let {
            return it
        }
        parseRelationUserRef(ref)?.let {
            return it
        }
    }
    microBlogRelationKeyOrNull(args.targetUserId, args.targetUserHost)?.let { key ->
        findRelationUserByKey(key)?.let {
            return it
        }
        return RelationUserTarget(
            userKey = key,
            platformType = null,
            profile = null,
        )
    }
    return availableRelationUsers().singleOrNull()?.toRelationUserTarget()
}

private suspend fun AgentToolSession.availableRelationUsers(): List<UiProfile> =
    buildList {
        status?.currentPost?.agentDisplayPost()?.user?.let(::add)
        attachmentStore
            .snapshot()
            .forEach { attachment ->
                when (attachment) {
                    is AgentConversationAttachment.Post -> attachment.post.agentDisplayPost().user?.let(::add)
                    is AgentConversationAttachment.User -> add(attachment.user)
                    is AgentConversationAttachment.InputRequest -> Unit
                }
            }
    }.distinctBy { it.platformType to it.key }

private suspend fun AgentToolSession.findRelationUserByRef(ref: String): RelationUserTarget? {
    val normalizedRef = ref.normalizedUserRef()
    return availableRelationUsers()
        .firstOrNull { user ->
            user.agentUserRefAliases().any { alias -> alias.normalizedUserRef() == normalizedRef }
        }?.toRelationUserTarget()
}

private suspend fun AgentToolSession.findRelationUserByKey(key: MicroBlogKey): RelationUserTarget? =
    availableRelationUsers()
        .firstOrNull { user -> user.key == key }
        ?.toRelationUserTarget()

private fun parseRelationUserRef(ref: String): RelationUserTarget? {
    val value =
        ref
            .trim()
            .removePrefix("[[user:")
            .removeSuffix("]]")
    val parts = value.split(":")
    if (parts.size < 3) {
        return null
    }
    val platformType = PlatformType.entries.firstOrNull { it.name.equals(parts[0], ignoreCase = true) }
    val host = parts[1]
    val id = parts.drop(2).joinToString(":")
    if (id.isBlank()) {
        return null
    }
    return RelationUserTarget(
        userKey = MicroBlogKey(id = id, host = host),
        platformType = platformType,
        profile = null,
    )
}

private fun UiProfile.toRelationUserTarget(): RelationUserTarget =
    RelationUserTarget(
        userKey = key,
        platformType = platformType,
        profile = this,
    )

private fun AgentToolSession.resolveRelationTargets(
    args: RelationAccountArgs,
    user: RelationUserTarget,
): List<AgentRelationTarget> {
    val requestedAccount =
        args.accountId
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { MicroBlogKey(id = it, host = args.accountHost.trim()) }
    val platformTargets =
        relationTargets
            .filterRelationTargetsByPlatformNames(args.platforms)
            .let { targets ->
                user.platformType?.let { platformType ->
                    targets.filter { it.platformType == platformType }
                } ?: targets
            }
    return if (requestedAccount != null) {
        platformTargets.filter { it.accountKey == requestedAccount }
    } else {
        platformTargets
    }
}

private fun AgentToolSession.resolveSingleRelationTarget(
    args: RelationAccountArgs,
    user: RelationUserTarget,
): AgentRelationTarget? {
    val targets = resolveRelationTargets(args, user)
    return targets.singleOrNull()
}

private suspend fun AgentToolSession.relationActionCandidates(
    user: RelationUserTarget,
    args: RelationAccountArgs,
): List<RelationActionCandidate> =
    resolveRelationTargets(args, user)
        .flatMap { target ->
            val relation =
                runCatching {
                    target.dataSource.relationHandler.dataSource.relation(user.userKey)
                }.getOrNull()
            target.availableRelationActions(relation).map { action ->
                RelationActionCandidate(
                    target = target,
                    action = action,
                )
            }
        }

private fun AgentRelationTarget.availableRelationActions(relation: UiRelation?): List<RelationAction> =
    RelationActionType.entries
        .filter { it in dataSource.supportedRelationTypes }
        .flatMap { type ->
            when (type) {
                RelationActionType.Follow ->
                    listOf(
                        if (relation?.following == true || relation?.hasPendingFollowRequestFromYou == true) {
                            RelationAction.Unfollow
                        } else {
                            RelationAction.Follow
                        },
                    )

                RelationActionType.Block ->
                    listOf(
                        if (relation?.blocking == true) {
                            RelationAction.Unblock
                        } else {
                            RelationAction.Block
                        },
                    )

                RelationActionType.Mute ->
                    listOf(
                        if (relation?.muted == true) {
                            RelationAction.Unmute
                        } else {
                            RelationAction.Mute
                        },
                    )
            }
        }

private fun AgentRelationTarget.supports(action: RelationAction): Boolean = action.requiredType in dataSource.supportedRelationTypes

private suspend fun AgentToolSession.relationUserSelectionMessage(
    args: RelationToolArgs,
    promptKey: AgentLocalizedTextKey,
    valuePrefix: String,
): String {
    val candidates = availableRelationUsers().takeIf { it.isNotEmpty() }
    if (candidates == null) {
        return "Relation tool requires a target user. Use search_users/load_user_profile first, target the current post author, or provide targetUserId and targetUserHost."
    }
    inputRequestStore.set(
        AgentInputRequest(
            requestId = "relation-user:${args.action}:${candidates.joinToString { it.agentAttachmentRef() }}",
            localizedPrompt = AgentUiStrings.text(promptKey),
            options =
                candidates.take(RELATION_SELECTION_OPTION_LIMIT).map { user ->
                    AgentInputRequest.Option(
                        id = "user:${user.agentAttachmentRef()}",
                        localizedLabel = AgentUiStrings.text(AgentLocalizedTextKey.DynamicText, user.relationUserLabel()),
                        value =
                            buildString {
                                appendLine(valuePrefix)
                                appendRelationToolArgs(args.copy(user = user.toRelationUserTarget().toRelationUserArgs()))
                            },
                        userPreview = user,
                    )
                },
            allowFreeText = true,
            localizedFreeTextPlaceholder = AgentUiStrings.text(AgentLocalizedTextKey.RelationUserPlaceholder),
        ),
    )
    return AgentUiStrings.text(promptKey).toAgentProtocolText()
}

private suspend fun AgentToolSession.relationActionSelectionMessage(
    user: RelationUserTarget,
    args: RelationToolArgs,
): String {
    val candidates = relationActionCandidates(user, args.account)
    if (candidates.isEmpty()) {
        return noRelationActionsMessage(args.account.platforms, user)
    }
    setRelationActionSelectionRequest(user, candidates)
    return AgentUiStrings.text(AgentLocalizedTextKey.SelectRelationAction).toAgentProtocolText()
}

private suspend fun AgentToolSession.setRelationActionSelectionRequest(
    user: RelationUserTarget,
    candidates: List<RelationActionCandidate>,
) {
    inputRequestStore.set(
        AgentInputRequest(
            requestId = "relation-action:${user.userKey}:${candidates.joinToString { it.target.accountKey.toString() + ':' + it.action.id }}",
            localizedPrompt = AgentUiStrings.text(AgentLocalizedTextKey.SelectRelationAction),
            options =
                candidates.take(RELATION_SELECTION_OPTION_LIMIT).map { candidate ->
                    AgentInputRequest.Option(
                        id = "relation:${candidate.target.accountKey}:${candidate.action.id}",
                        localizedLabel =
                            AgentUiStrings.text(
                                AgentLocalizedTextKey.DynamicText,
                                "${candidate.action.label} / ${candidate.target.platformType.name}",
                            ),
                        value =
                            buildString {
                                appendLine(AgentUiStrings.Relation.ExecuteActionPrefix)
                                appendRelationActionArgs(
                                    action = candidate.action,
                                    user = user,
                                    target = candidate.target,
                                    requestFollow = false,
                                )
                            },
                        userPreview = user.profile,
                    )
                },
            allowFreeText = true,
            localizedFreeTextPlaceholder = AgentUiStrings.text(AgentLocalizedTextKey.RelationActionPlaceholder),
        ),
    )
}

private suspend fun AgentToolSession.relationAccountSelectionMessage(
    user: RelationUserTarget,
    args: RelationToolArgs,
    action: RelationAction,
): String {
    val targets = resolveRelationTargets(args.account, user)
    if (targets.isEmpty()) {
        return noRelationTargetsMessage(args.account.platforms, user)
    }
    inputRequestStore.set(
        AgentInputRequest(
            requestId = "relation-account:${action.id}:${user.userKey}:${args.account.platforms.joinToString()}",
            localizedPrompt = AgentUiStrings.text(AgentLocalizedTextKey.SelectRelationAccount),
            options =
                targets.take(RELATION_SELECTION_OPTION_LIMIT).map { target ->
                    AgentInputRequest.Option(
                        id = "account:${target.accountKey}",
                        localizedLabel = AgentUiStrings.text(AgentLocalizedTextKey.DynamicText, target.relationAccountLabel()),
                        value =
                            buildString {
                                appendLine(AgentUiStrings.Relation.AccountPrefix)
                                appendRelationActionArgs(
                                    action = action,
                                    user = user,
                                    target = target,
                                    requestFollow = args.requestFollow,
                                )
                            },
                        userPreview = user.profile,
                    )
                },
            allowFreeText = true,
            localizedFreeTextPlaceholder = AgentUiStrings.text(AgentLocalizedTextKey.RelationAccountPlaceholder),
        ),
    )
    return AgentUiStrings.text(AgentLocalizedTextKey.SelectRelationAccount).toAgentProtocolText()
}

private fun AgentToolSession.noRelationTargetsMessage(
    platforms: List<String>,
    user: RelationUserTarget,
): String =
    when {
        relationTargets.isEmpty() -> {
            "No signed-in accounts are available for relation actions."
        }

        platforms.isNotEmpty() -> {
            "No relation-capable account matches the requested platforms: ${platforms.joinToString()}."
        }

        user.platformType != null -> {
            "No relation-capable signed-in account is available for ${user.platformType.name}."
        }

        else -> {
            "No relation-capable signed-in account matches target user ${user.userKey}."
        }
    }

private fun AgentToolSession.noRelationActionsMessage(
    platforms: List<String>,
    user: RelationUserTarget,
): String =
    if (resolveRelationTargets(RelationAccountArgs("", "", platforms), user).isEmpty()) {
        noRelationTargetsMessage(platforms, user)
    } else {
        "No executable relation actions are available for ${user.userKey}."
    }

private fun RelationStateResult.toToolText(): String =
    buildString {
        appendLine("Account: ${target.accountKey}")
        appendLine("Platform: ${target.platformType.name}")
        appendLine("Supported relation types: ${target.dataSource.supportedRelationTypes.joinToString()}")
        appendLine("following: ${relation.following}")
        appendLine("isFans: ${relation.isFans}")
        appendLine("blocking: ${relation.blocking}")
        appendLine("blockedBy: ${relation.blockedBy}")
        appendLine("muted: ${relation.muted}")
        appendLine("hasPendingFollowRequestFromYou: ${relation.hasPendingFollowRequestFromYou}")
        appendLine("hasPendingFollowRequestToYou: ${relation.hasPendingFollowRequestToYou}")
    }

private fun String.toRelationActionOrNull(): RelationAction? =
    when (trim().lowercase()) {
        "follow", "request_follow", "request-follow", "关注", "请求关注" -> RelationAction.Follow
        "unfollow", "取消关注", "取关" -> RelationAction.Unfollow
        "block", "拉黑", "屏蔽用户" -> RelationAction.Block
        "unblock", "取消拉黑", "解除拉黑", "取消屏蔽用户" -> RelationAction.Unblock
        "mute", "静音", "屏蔽", "隐藏" -> RelationAction.Mute
        "unmute", "取消静音", "解除静音", "取消隐藏" -> RelationAction.Unmute
        else -> null
    }

private fun microBlogRelationKeyOrNull(
    id: String,
    host: String,
): MicroBlogKey? =
    id
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let { MicroBlogKey(id = it, host = host.trim()) }

private fun UiProfile.agentUserRefAliases(): Set<String> =
    buildSet {
        add(agentAttachmentRef())
        add(agentAttachmentMarker())
        add(key.toString())
        add("${key.host}:${key.id}")
        add("${key.id}:${key.host}")
        add("${platformType.name}:$key")
        add("${platformType.name}:${key.host}:${key.id}")
        add("${platformType.name}:${key.id}:${key.host}")
    }

private fun String.normalizedUserRef(): String =
    trim()
        .removePrefix("[[user:")
        .removeSuffix("]]")
        .lowercase()

private fun UiProfile.relationUserLabel(): String =
    listOf(
        name.raw.takeIf { it.isNotBlank() },
        handle.raw.takeIf { it.isNotBlank() },
        key.toString(),
    ).filterNotNull()
        .distinct()
        .joinToString(" ")

private fun AgentRelationTarget.relationAccountLabel(): String = "${platformType.name} / $accountKey"

private fun RelationUserTarget.toRelationUserArgs(): RelationUserArgs =
    RelationUserArgs(
        targetUserRef = profile?.agentAttachmentMarker().orEmpty(),
        targetUserId = userKey.id,
        targetUserHost = userKey.host,
        useCurrentPostAuthor = false,
    )

private fun StringBuilder.appendRelationToolArgs(args: RelationToolArgs) {
    args.action.trim().takeIf { it.isNotBlank() }?.let {
        appendLine("action=$it")
    }
    args.account.accountId.trim().takeIf { it.isNotBlank() }?.let {
        appendLine("accountId=$it")
        appendLine("accountHost=${args.account.accountHost.trim()}")
    }
    if (args.account.platforms.isNotEmpty()) {
        appendLine("platforms=${args.account.platforms.joinToString()}")
    }
    appendLine("targetUserRef=${args.user.targetUserRef}")
    appendLine("targetUserId=${args.user.targetUserId}")
    appendLine("targetUserHost=${args.user.targetUserHost}")
    if (args.user.useCurrentPostAuthor) {
        appendLine("useCurrentPostAuthor=true")
    }
    if (args.requestFollow) {
        appendLine("requestFollow=true")
    }
}

private fun StringBuilder.appendRelationActionArgs(
    action: RelationAction,
    user: RelationUserTarget,
    target: AgentRelationTarget,
    requestFollow: Boolean,
) {
    appendLine("action=${action.id}")
    appendLine("accountId=${target.accountKey.id}")
    appendLine("accountHost=${target.accountKey.host}")
    appendLine("platforms=${target.platformType.name}")
    user.profile?.let {
        appendLine("targetUserRef=${it.agentAttachmentMarker()}")
    }
    appendLine("targetUserId=${user.userKey.id}")
    appendLine("targetUserHost=${user.userKey.host}")
    if (requestFollow) {
        appendLine("requestFollow=true")
    }
}

private const val RELATION_SELECTION_OPTION_LIMIT = 12
