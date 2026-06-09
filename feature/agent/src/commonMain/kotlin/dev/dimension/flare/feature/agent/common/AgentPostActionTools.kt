package dev.dimension.flare.feature.agent.common

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickPostEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.postEventOrNull
import kotlinx.serialization.Serializable

internal class ListPostActionsTool(
    private val session: AgentToolSession,
) : SimpleTool<ListPostActionsTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "List executable post actions from a target post's ActionMenu. " +
                "This excludes translate/show-original, share/more UI actions, and reply/quote/comment compose actions.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Exact post attachmentRef marker or ref from a previous tool result, such as [[post:Mastodon:host:id]].")
        val targetPostRef: String = "",
        @property:LLMDescription("Target status id. Prefer targetPostRef when available.")
        val targetStatusId: String = "",
        @property:LLMDescription("Target status host. Leave blank only when the target key has no host.")
        val targetStatusHost: String = "",
        @property:LLMDescription("In a post insight session, set true to target the current post.")
        val useCurrentStatus: Boolean = false,
    )

    override suspend fun execute(args: Args): String {
        val post = session.resolveActionPost(args.toTargetArgs()) ?: return session.postActionTargetSelectionMessage(args.toTargetArgs())
        val actions = post.executablePostActions()
        if (actions.isEmpty()) {
            return "No executable post actions are available for ${post.statusKey}. Translate and pure UI actions are not included."
        }
        session.inputRequestStore.set(
            AgentInputRequest(
                requestId = "post-actions:${post.statusKey}",
                localizedPrompt = AgentUiStrings.text(AgentLocalizedTextKey.SelectPostAction),
                options =
                    actions.map { action ->
                        AgentInputRequest.Option(
                            id = action.id,
                            localizedLabel = AgentUiStrings.text(AgentLocalizedTextKey.DynamicText, action.label),
                            value =
                                buildString {
                                    appendLine(AgentUiStrings.PostAction.ExecutePostActionPrefix)
                                    appendLine("targetPostRef=${post.agentAttachmentMarker()}")
                                    appendLine("targetStatusId=${post.statusKey.id}")
                                    appendLine("targetStatusHost=${post.statusKey.host}")
                                    appendLine("actionId=${action.id}")
                                    appendLine("actionName=${action.label}")
                                },
                        )
                    },
                allowFreeText = true,
                localizedFreeTextPlaceholder = AgentUiStrings.text(AgentLocalizedTextKey.PostActionPlaceholder),
            ),
        )
        return buildString {
            appendLine("Available post actions for ${post.statusKey}:")
            actions.forEach { action ->
                appendLine("- actionId=${action.id}, actionName=${action.label}, safety=${action.safety.label}")
            }
        }.trim()
    }

    companion object {
        const val NAME = "list_post_actions"
    }
}

internal class ExecutePostActionTool(
    private val session: AgentToolSession,
    private val actionHandler: (PostDataSource, PostEvent) -> Unit = { dataSource, event ->
        dataSource.postEventHandler.handleEvent(event)
    },
) : SimpleTool<ExecutePostActionTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Execute an available post action from a target post's ActionMenu after explicit confirmation. " +
                "Use list_post_actions first when actionId or target post is ambiguous. " +
                "This excludes translate/show-original, share/more UI actions, and reply/quote/comment compose actions.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Action id returned by list_post_actions. Prefer this over actionName.")
        val actionId: String = "",
        @property:LLMDescription("Action label/name, such as Like, Unlike, Retweet, Bookmark, React, Favorite, or Vote.")
        val actionName: String = "",
        @property:LLMDescription("Exact post attachmentRef marker or ref from a previous tool result, such as [[post:Mastodon:host:id]].")
        val targetPostRef: String = "",
        @property:LLMDescription("Target status id. Prefer targetPostRef when available.")
        val targetStatusId: String = "",
        @property:LLMDescription("Target status host. Leave blank only when the target key has no host.")
        val targetStatusHost: String = "",
        @property:LLMDescription("In a post insight session, set true to target the current post.")
        val useCurrentStatus: Boolean = false,
        @property:LLMDescription("Set true only after the latest user message explicitly confirms the exact target post and action.")
        val confirmed: Boolean = false,
    )

    override suspend fun execute(args: Args): String {
        val post = session.resolveActionPost(args.toTargetArgs()) ?: return session.postActionTargetSelectionMessage(args.toTargetArgs())
        val actions = post.executablePostActions()
        if (actions.isEmpty()) {
            return "No executable post actions are available for ${post.statusKey}. Translate and pure UI actions are not included."
        }
        val action =
            actions.resolveAction(args.actionId, args.actionName)
                ?: return session.postActionSelectionMessage(post, actions)
        val target =
            session.postActionTargets.firstOrNull { it.accountKey == action.payload.accountKey }
                ?: return "No signed-in post action account matches ${action.payload.accountKey} for ${action.label}."

        if (!args.confirmed) {
            val localizedPrompt =
                AgentUiStrings.text(
                    AgentLocalizedTextKey.PostActionConfirmationMessage,
                    action.label,
                    action.payload.accountKey.toString(),
                    post.statusKey.toString(),
                    post.user?.let { it.name.raw.takeIf { name -> name.isNotBlank() } ?: it.handle.canonical }.orEmpty(),
                    post.content.raw.take(160),
                )
            session.inputRequestStore.set(
                AgentInputRequest(
                    requestId = "post-action-confirm:${post.statusKey}:${action.id}",
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
                    localizedFreeTextPlaceholder = AgentUiStrings.text(AgentLocalizedTextKey.PostActionConfirmationPlaceholder),
                    postPreview = post,
                ),
            )
            return localizedPrompt.toAgentProtocolText()
        }

        actionHandler(target.dataSource, action.payload.postEvent)
        return buildString {
            appendLine("Post action executed successfully.")
            appendLine("Action: ${action.label}")
            appendLine("Account: ${action.payload.accountKey}")
            appendLine("Target status: ${post.statusKey}")
            appendLine("Platform: ${target.platformType.name}")
        }.trim()
    }

    companion object {
        const val NAME = "execute_post_action"
    }
}

private data class PostActionTargetArgs(
    val targetPostRef: String,
    val targetStatusId: String,
    val targetStatusHost: String,
    val useCurrentStatus: Boolean,
)

private data class ExecutablePostAction(
    val id: String,
    val label: String,
    val item: ActionMenu.Item,
    val payload: ClickPostEvent,
    val safety: PostActionSafety,
)

private enum class PostActionSafety(
    val label: String,
) {
    Low("low"),
    Medium("medium"),
    High("high"),
}

private fun ListPostActionsTool.Args.toTargetArgs(): PostActionTargetArgs =
    PostActionTargetArgs(
        targetPostRef = targetPostRef,
        targetStatusId = targetStatusId,
        targetStatusHost = targetStatusHost,
        useCurrentStatus = useCurrentStatus,
    )

private fun ExecutePostActionTool.Args.toTargetArgs(): PostActionTargetArgs =
    PostActionTargetArgs(
        targetPostRef = targetPostRef,
        targetStatusId = targetStatusId,
        targetStatusHost = targetStatusHost,
        useCurrentStatus = useCurrentStatus,
    )

private suspend fun AgentToolSession.resolveActionPost(args: PostActionTargetArgs): UiTimelineV2.Post? {
    if (args.useCurrentStatus) {
        status?.currentPost?.agentDisplayPost()?.let {
            return it
        }
    }
    args.targetPostRef.trim().takeIf { it.isNotBlank() }?.let { ref ->
        findActionPostByRef(ref)?.let {
            return it
        }
    }
    microBlogActionKeyOrNull(args.targetStatusId, args.targetStatusHost)?.let { key ->
        findActionPostByKey(key)?.let {
            return it
        }
    }
    return availableActionPosts().singleOrNull()
}

private suspend fun AgentToolSession.postActionTargetSelectionMessage(args: PostActionTargetArgs): String {
    val posts = availableActionPosts()
    if (posts.isEmpty()) {
        return "Post action requires a target post with available action menus. " +
            "Open a post insight session, use search_posts/load_post_context first, or provide a target post attachmentRef."
    }
    val localizedPrompt = AgentUiStrings.text(AgentLocalizedTextKey.SelectPostActionPost)
    inputRequestStore.set(
        AgentInputRequest(
            requestId = "post-action-target:${args.targetPostRef}:${args.targetStatusId}:${args.targetStatusHost}",
            localizedPrompt = localizedPrompt,
            options =
                posts.map { post ->
                    AgentInputRequest.Option(
                        id = "post:${post.agentAttachmentRef()}",
                        localizedLabel = AgentUiStrings.text(AgentLocalizedTextKey.DynamicText, post.postActionLabel()),
                        value =
                            buildString {
                                appendLine(AgentUiStrings.PostAction.OperatePostPrefix)
                                appendLine("targetPostRef=${post.agentAttachmentMarker()}")
                                appendLine("targetStatusId=${post.statusKey.id}")
                                appendLine("targetStatusHost=${post.statusKey.host}")
                            },
                        postPreview = post,
                    )
                },
            allowFreeText = true,
            localizedFreeTextPlaceholder = AgentUiStrings.text(AgentLocalizedTextKey.PostActionTargetPostPlaceholder),
        ),
    )
    return localizedPrompt.toAgentProtocolText()
}

private suspend fun AgentToolSession.postActionSelectionMessage(
    post: UiTimelineV2.Post,
    actions: List<ExecutablePostAction>,
): String {
    val localizedPrompt = AgentUiStrings.text(AgentLocalizedTextKey.SelectPostAction)
    inputRequestStore.set(
        AgentInputRequest(
            requestId = "post-action:${post.statusKey}",
            localizedPrompt = localizedPrompt,
            options =
                actions.map { action ->
                    AgentInputRequest.Option(
                        id = action.id,
                        localizedLabel = AgentUiStrings.text(AgentLocalizedTextKey.DynamicText, action.label),
                        value =
                            buildString {
                                appendLine(AgentUiStrings.PostAction.ExecutePostActionPrefix)
                                appendLine("targetPostRef=${post.agentAttachmentMarker()}")
                                appendLine("targetStatusId=${post.statusKey.id}")
                                appendLine("targetStatusHost=${post.statusKey.host}")
                                appendLine("actionId=${action.id}")
                                appendLine("actionName=${action.label}")
                            },
                    )
                },
            allowFreeText = true,
            localizedFreeTextPlaceholder = AgentUiStrings.text(AgentLocalizedTextKey.PostActionPlaceholder),
        ),
    )
    return localizedPrompt.toAgentProtocolText()
}

private suspend fun AgentToolSession.availableActionPosts(): List<UiTimelineV2.Post> =
    buildList {
        status?.currentPost?.agentDisplayPost()?.let(::add)
        attachmentStore
            .snapshot()
            .filterIsInstance<AgentConversationAttachment.Post>()
            .map { it.post.agentDisplayPost() }
            .forEach(::add)
    }.distinctBy { it.platformType to it.statusKey }

private suspend fun AgentToolSession.findActionPostByRef(ref: String): UiTimelineV2.Post? {
    val normalizedRef = ref.normalizedActionPostRef()
    return availableActionPosts().firstOrNull { post ->
        post.actionPostRefAliases().any { alias -> alias.normalizedActionPostRef() == normalizedRef }
    }
}

private suspend fun AgentToolSession.findActionPostByKey(key: MicroBlogKey): UiTimelineV2.Post? =
    availableActionPosts().firstOrNull { post ->
        post.statusKey == key
    }

private fun UiTimelineV2.Post.executablePostActions(): List<ExecutablePostAction> =
    actions
        .flattenActionItems()
        .mapIndexedNotNull { index, item ->
            val payload = item.clickEvent.postEventOrNull() ?: return@mapIndexedNotNull null
            val type = item.localizedType()
            if (type in SKIPPED_ACTION_TYPES) {
                return@mapIndexedNotNull null
            }
            val label = item.actionLabel()
            ExecutablePostAction(
                id = "action-$index-${label.actionKey()}",
                label = label,
                item = item,
                payload = payload,
                safety = type.actionSafety(),
            )
        }

private fun List<ActionMenu>.flattenActionItems(): List<ActionMenu.Item> =
    flatMap { action ->
        when (action) {
            ActionMenu.Divider -> emptyList()
            is ActionMenu.Group -> listOf(action.displayItem) + action.actions.flattenActionItems()
            is ActionMenu.Item -> listOf(action)
        }
    }

private fun ActionMenu.Item.actionLabel(): String =
    when (val text = text) {
        is ActionMenu.Item.Text.Localized -> text.type.name
        is ActionMenu.Item.Text.Raw -> text.text
        null -> updateKey.ifBlank { icon?.name.orEmpty() }.ifBlank { "Action" }
    }

private fun ActionMenu.Item.localizedType(): ActionMenu.Item.Text.Localized.Type? = (text as? ActionMenu.Item.Text.Localized)?.type

private fun List<ExecutablePostAction>.resolveAction(
    actionId: String,
    actionName: String,
): ExecutablePostAction? {
    val id = actionId.trim()
    if (id.isNotBlank()) {
        firstOrNull { it.id == id }?.let {
            return it
        }
    }
    val nameKey = actionName.actionKey()
    if (nameKey.isBlank()) {
        return singleOrNull()
    }
    return filter { action ->
        action.label.actionKey() == nameKey ||
            action.localizedAliases().any { it.actionKey() == nameKey }
    }.singleOrNull()
}

private fun ExecutablePostAction.localizedAliases(): List<String> =
    when (item.localizedType()) {
        ActionMenu.Item.Text.Localized.Type.Like -> listOf("like", "赞", "点赞")
        ActionMenu.Item.Text.Localized.Type.Unlike -> listOf("unlike", "取消赞", "取消点赞")
        ActionMenu.Item.Text.Localized.Type.Retweet -> listOf("retweet", "repost", "boost", "renote", "转发", "转推")
        ActionMenu.Item.Text.Localized.Type.Unretweet -> listOf("unretweet", "unrepost", "unboost", "取消转发", "取消转推")
        ActionMenu.Item.Text.Localized.Type.Bookmark -> listOf("bookmark", "收藏")
        ActionMenu.Item.Text.Localized.Type.Unbookmark -> listOf("unbookmark", "取消收藏")
        ActionMenu.Item.Text.Localized.Type.Favorite -> listOf("favorite", "favourite", "收藏")
        ActionMenu.Item.Text.Localized.Type.UnFavorite -> listOf("unfavorite", "unfavourite", "取消收藏")
        ActionMenu.Item.Text.Localized.Type.React -> listOf("react", "reaction", "表情回应")
        ActionMenu.Item.Text.Localized.Type.UnReact -> listOf("unreact", "remove reaction", "取消表情回应")
        ActionMenu.Item.Text.Localized.Type.AcceptFollowRequest -> listOf("accept follow request", "接受关注请求")
        ActionMenu.Item.Text.Localized.Type.RejectFollowRequest -> listOf("reject follow request", "拒绝关注请求")
        ActionMenu.Item.Text.Localized.Type.Report -> listOf("report", "举报")
        else -> emptyList()
    }

private fun ActionMenu.Item.Text.Localized.Type?.actionSafety(): PostActionSafety =
    when (this) {
        ActionMenu.Item.Text.Localized.Type.Like,
        ActionMenu.Item.Text.Localized.Type.Unlike,
        ActionMenu.Item.Text.Localized.Type.Bookmark,
        ActionMenu.Item.Text.Localized.Type.Unbookmark,
        ActionMenu.Item.Text.Localized.Type.Favorite,
        ActionMenu.Item.Text.Localized.Type.UnFavorite,
        -> PostActionSafety.Low

        ActionMenu.Item.Text.Localized.Type.Retweet,
        ActionMenu.Item.Text.Localized.Type.Unretweet,
        ActionMenu.Item.Text.Localized.Type.React,
        ActionMenu.Item.Text.Localized.Type.UnReact,
        -> PostActionSafety.Medium

        ActionMenu.Item.Text.Localized.Type.Report,
        ActionMenu.Item.Text.Localized.Type.AcceptFollowRequest,
        ActionMenu.Item.Text.Localized.Type.RejectFollowRequest,
        -> PostActionSafety.High

        else -> PostActionSafety.Medium
    }

private fun UiTimelineV2.Post.postActionLabel(): String =
    listOfNotNull(
        user?.let { user -> user.name.raw.takeIf { it.isNotBlank() } ?: user.handle.canonical },
        content.raw.take(48).takeIf { it.isNotBlank() },
    ).joinToString(" - ")
        .ifBlank { statusKey.toString() }

private fun microBlogActionKeyOrNull(
    id: String,
    host: String,
): MicroBlogKey? =
    id
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let { MicroBlogKey(id = it, host = host.trim()) }

private fun UiTimelineV2.Post.actionPostRefAliases(): Set<String> =
    buildSet {
        add(agentAttachmentRef())
        add(agentAttachmentMarker())
        add(statusKey.toString())
        add("${statusKey.host}:${statusKey.id}")
        add("${statusKey.id}:${statusKey.host}")
        add("${platformType.name}:$statusKey")
        add("${platformType.name}:${statusKey.host}:${statusKey.id}")
        add("${platformType.name}:${statusKey.id}:${statusKey.host}")
    }

private fun String.normalizedActionPostRef(): String =
    trim()
        .removePrefix("[[post:")
        .removeSuffix("]]")
        .lowercase()

private fun String.actionKey(): String =
    trim()
        .lowercase()
        .replace("-", "")
        .replace("_", "")
        .replace(" ", "")

private val SKIPPED_ACTION_TYPES =
    setOf(
        ActionMenu.Item.Text.Localized.Type.Reply,
        ActionMenu.Item.Text.Localized.Type.Comment,
        ActionMenu.Item.Text.Localized.Type.Quote,
        ActionMenu.Item.Text.Localized.Type.More,
        ActionMenu.Item.Text.Localized.Type.Share,
        ActionMenu.Item.Text.Localized.Type.FxShare,
        ActionMenu.Item.Text.Localized.Type.Translate,
        ActionMenu.Item.Text.Localized.Type.RetryTranslation,
        ActionMenu.Item.Text.Localized.Type.ShowOriginal,
    )
