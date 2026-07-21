package dev.dimension.flare.feature.agent.common

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

internal class LoadNotificationsTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadNotificationsTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load notification timeline items from the user's notification-capable signed-in social accounts. " +
                "Use this when the user asks about notifications, mentions, comments, replies, likes, " +
                "or recent account activity directed at them.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Notification filter: All, Mention, Comment, or Like. Defaults to All.")
        val filter: String = "All",
        @property:LLMDescription(
            "Account id to load notifications from. Leave blank to load all matching notification-capable accounts.",
        )
        val accountId: String = "",
        @property:LLMDescription("Account host. Leave blank only when the account key has no host.")
        val accountHost: String = "",
        @property:LLMDescription(
            "Platform names to load. Leave empty to load every notification-capable signed-in platform. " +
                "Use platform names or aliases supplied in the system instructions.",
        )
        val platforms: List<String> = emptyList(),
        @property:LLMDescription("Maximum number of notification timeline items per account. Defaults to 20 and is capped at 50.")
        val maxItems: Int = DEFAULT_NOTIFICATION_TOOL_ITEMS,
    )

    override suspend fun execute(args: Args): String {
        val filter = args.filter.toNotificationFilterOrNull() ?: return "Unsupported notification filter: ${args.filter}."
        val targets = session.notificationTargets.resolveNotificationTargets(args)
        if (targets.isEmpty()) {
            return noNotificationTargetsMessage(args)
        }
        val unsupportedTargets = targets.filterNot { filter in it.dataSource.supportedNotificationFilter }
        if (unsupportedTargets.isNotEmpty()) {
            return buildString {
                appendLine("Notification filter ${filter.name} is not supported by:")
                unsupportedTargets.forEach { target ->
                    appendLine(
                        "- accountKey=${target.accountKey}, " +
                            "platform=${target.platformType.name}, " +
                            "supported=${target.dataSource.supportedNotificationFilter.joinToString()}",
                    )
                }
            }.trim()
        }

        val pageSize = args.maxItems.coerceIn(1, MAX_NOTIFICATION_TOOL_ITEMS)
        val results = targets.loadNotifications(filter = filter, pageSize = pageSize)
        val items = results.flatMap { it.items }
        session.messagePartStore.addPosts(items.mapNotNull { it.notificationPostOrNull() })
        session.messagePartStore.addUsers(
            items
                .flatMap { item ->
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

                        is UiTimelineV2.User -> {
                            listOf(item.value)
                        }

                        is UiTimelineV2.UserList -> {
                            item.users
                        }

                        is UiTimelineV2.Message -> {
                            listOfNotNull(item.user)
                        }

                        is UiTimelineV2.Post -> {
                            listOfNotNull(item.user)
                        }

                        is UiTimelineV2.Feed -> {
                            emptyList()
                        }
                    }
                }.distinctBy { it.key },
        )

        return buildString {
            appendLine("Notifications")
            appendLine("filter: ${filter.name}")
            appendLine("accounts loaded: ${targets.joinToString { "${it.platformType.name}:${it.accountKey}" }}")
            appendLine("source: live notification timeline data source; local cache not used")
            appendLine()
            if (items.isEmpty()) {
                appendLine("No notification items were returned.")
                return@buildString
            }
            results.forEach { result ->
                appendLine("Account: ${result.target.accountKey}")
                appendLine("Platform: ${result.target.platformType.name}")
                if (result.items.isEmpty()) {
                    appendLine("No notification items were returned for this account.")
                } else {
                    result.items.take(pageSize).forEachIndexed { index, item ->
                        appendLine()
                        appendLine("Notification #${index + 1}")
                        append(item.toNotificationItemToolText())
                    }
                }
                appendLine()
            }
        }.take(MAX_NOTIFICATION_TOOL_RESULT_LENGTH)
    }

    companion object {
        const val NAME = "load_notifications"
    }
}

private data class NotificationToolResult(
    val target: AgentNotificationTarget,
    val items: List<UiTimelineV2>,
)

private fun List<AgentNotificationTarget>.resolveNotificationTargets(args: LoadNotificationsTool.Args): List<AgentNotificationTarget> {
    val requestedAccount =
        args.accountId
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { MicroBlogKey(id = it, host = args.accountHost.trim()) }
    val platformTargets =
        distinctBy { it.accountKey to it.platformType }
            .filterNotificationTargetsByPlatformNames(args.platforms)
    return when (requestedAccount) {
        null -> platformTargets
        else -> platformTargets.filter { it.accountKey == requestedAccount }
    }
}

private fun noNotificationTargetsMessage(args: LoadNotificationsTool.Args): String =
    when {
        args.accountId.isNotBlank() -> {
            "No notification-capable signed-in account matches ${MicroBlogKey(args.accountId.trim(), args.accountHost.trim())}."
        }

        args.platforms.isNotEmpty() -> {
            "No notification-capable signed-in accounts are available for the requested platforms: ${args.platforms.joinToString()}."
        }

        else -> {
            "No signed-in accounts are available for notification tools."
        }
    }

private suspend fun List<AgentNotificationTarget>.loadNotifications(
    filter: NotificationFilter,
    pageSize: Int,
): List<NotificationToolResult> =
    coroutineScope {
        map { target ->
            async {
                val items =
                    runCatching {
                        target.dataSource
                            .notification(filter)
                            .load(
                                pageSize = pageSize,
                                request = PagingRequest.Refresh,
                            ).data
                    }.getOrElse { emptyList() }
                NotificationToolResult(target = target, items = items)
            }
        }.awaitAll()
    }

private fun String.toNotificationFilterOrNull(): NotificationFilter? {
    val key =
        trim()
            .lowercase()
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "")
    return when (key) {
        "", "all", "全部", "所有" -> NotificationFilter.All
        "mention", "mentions", "at", "@", "提及", "提到" -> NotificationFilter.Mention
        "comment", "comments", "reply", "replies", "评论", "回复" -> NotificationFilter.Comment
        "like", "likes", "favorite", "favorites", "favourite", "favourites", "赞", "点赞", "喜欢" -> NotificationFilter.Like
        else -> NotificationFilter.entries.firstOrNull { it.name.equals(trim(), ignoreCase = true) }
    }
}

private fun UiTimelineV2.toNotificationItemToolText(): String =
    when (this) {
        is UiTimelineV2.TimelinePostItem -> toNotificationPostToolText()
        is UiTimelineV2.Post -> toNotificationPostToolText()
        is UiTimelineV2.Message -> toNotificationMessageToolText()
        is UiTimelineV2.User -> toNotificationUserToolText()
        is UiTimelineV2.UserList -> toNotificationUserListToolText()
        is UiTimelineV2.Feed -> toNotificationFeedToolText()
    }

private fun UiTimelineV2.TimelinePostItem.toNotificationPostToolText(): String =
    buildString {
        presentation.message?.let { append(it.toNotificationMessageSummary()) }
        append(displayPost.toNotificationPostToolText())
    }

private fun UiTimelineV2.Post.toNotificationPostToolText(): String =
    buildString {
        appendLine("itemType: post")
        appendLine("attachmentRef: ${agentAttachmentMarker()}")
        appendLine("platform: ${platformType.name}")
        appendLine("statusKey: $statusKey")
        appendLine("createdAt: ${createdAt.value}")
        appendLine("authorName: ${user?.name?.raw.orEmpty()}")
        appendLine("authorHandle: ${user?.handle?.raw.orEmpty()}")
        appendLine("content: ${content.original.raw.take(MAX_NOTIFICATION_TEXT_LENGTH)}")
    }

private fun UiTimelineV2.notificationPostOrNull(): UiTimelineV2.Post? =
    when (this) {
        is UiTimelineV2.TimelinePostItem -> displayPost
        is UiTimelineV2.Post -> this
        else -> null
    }

private fun UiTimelineV2.Message.toNotificationMessageToolText(): String =
    buildString {
        appendLine("itemType: message")
        append(toNotificationMessageSummary())
        appendLine("statusKey: $statusKey")
        appendLine("createdAt: ${createdAt.value}")
        user?.let {
            appendLine("userAttachmentRef: ${it.agentAttachmentMarker()}")
            appendLine("userName: ${it.name.raw}")
            appendLine("userHandle: ${it.handle.raw}")
        }
    }

private fun UiTimelineV2.Message.toNotificationMessageSummary(): String =
    buildString {
        appendLine(
            "message: " +
                when (val notificationType = type) {
                    is UiTimelineV2.Message.Type.Localized -> {
                        buildString {
                            append(notificationType.data.name)
                            if (notificationType.args.isNotEmpty()) {
                                append("(")
                                append(notificationType.args.joinToString())
                                append(")")
                            }
                        }
                    }

                    is UiTimelineV2.Message.Type.Raw -> {
                        notificationType.content.take(MAX_NOTIFICATION_TEXT_LENGTH)
                    }

                    is UiTimelineV2.Message.Type.Unknown -> {
                        notificationType.rawType
                    }
                },
        )
    }

private fun UiTimelineV2.User.toNotificationUserToolText(): String =
    buildString {
        message?.let { append(it.toNotificationMessageSummary()) }
        appendLine("itemType: user")
        appendLine("attachmentRef: ${value.agentAttachmentMarker()}")
        appendLine("platform: ${value.platformType.name}")
        appendLine("userKey: ${value.key}")
        appendLine("displayName: ${value.name.raw}")
        appendLine("handle: ${value.handle.raw}")
        appendLine("description: ${value.description?.raw.orEmpty().take(MAX_NOTIFICATION_TEXT_LENGTH)}")
    }

private fun UiTimelineV2.UserList.toNotificationUserListToolText(): String =
    buildString {
        message?.let { append(it.toNotificationMessageSummary()) }
        appendLine("itemType: user_list")
        appendLine("statusKey: $statusKey")
        appendLine("createdAt: ${createdAt.value}")
        users.take(MAX_NOTIFICATION_USERS).forEachIndexed { index, user ->
            appendLine("user${index + 1}AttachmentRef: ${user.agentAttachmentMarker()}")
            appendLine("user${index + 1}Name: ${user.name.raw}")
            appendLine("user${index + 1}Handle: ${user.handle.raw}")
        }
        post?.let {
            appendLine("postAttachmentRef: ${it.agentAttachmentMarker()}")
            appendLine("postStatusKey: ${it.statusKey}")
            appendLine("postContent: ${it.content.original.raw.take(MAX_NOTIFICATION_TEXT_LENGTH)}")
        }
    }

private fun UiTimelineV2.Feed.toNotificationFeedToolText(): String =
    buildString {
        appendLine("itemType: feed")
        appendLine("title: ${title.orEmpty()}")
        appendLine("url: $url")
        appendLine("createdAt: ${createdAt.value}")
        appendLine("source: ${source.name}")
        appendLine("description: ${description.orEmpty().take(MAX_NOTIFICATION_TEXT_LENGTH)}")
    }

private const val DEFAULT_NOTIFICATION_TOOL_ITEMS = 20
private const val MAX_NOTIFICATION_TOOL_ITEMS = 50
private const val MAX_NOTIFICATION_TOOL_RESULT_LENGTH = 80_000
private const val MAX_NOTIFICATION_TEXT_LENGTH = 1_000
private const val MAX_NOTIFICATION_USERS = 10
