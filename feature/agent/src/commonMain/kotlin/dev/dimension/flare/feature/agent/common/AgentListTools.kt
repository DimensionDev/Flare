package dev.dimension.flare.feature.agent.common

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.contentPostOrNull
import kotlinx.serialization.Serializable

internal class ListListsTool(
    private val session: AgentToolSession,
) : SimpleTool<ListListsTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "List cached social lists from the user's list-capable signed-in accounts. " +
                "Use this before list operations when the target list id is unknown.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Platform names to load. Leave empty to load every list-capable signed-in platform.")
        val platforms: List<String> = emptyList(),
        @property:LLMDescription("Optional title or description substring to filter lists.")
        val query: String = "",
        @property:LLMDescription("Maximum number of lists per account. Defaults to 20 and is capped at 50.")
        val maxItems: Int = DEFAULT_LIST_TOOL_ITEMS,
    )

    override suspend fun execute(args: Args): String {
        val targets = session.listTargets.filterListTargetsByPlatformNames(args.platforms)
        if (targets.isEmpty()) return noListTargetsMessage(args.platforms)
        val query = args.query.trim()
        val maxItems = args.maxItems.listToolLimit()
        return buildString {
            appendLine("Lists")
            appendLine("source: cached list data from list-capable accounts")
            if (query.isNotBlank()) appendLine("query: \"$query\"")
            appendLine()
            targets.forEach { target ->
                val lists =
                    runCatching { target.listCached() }
                        .getOrElse { emptyList() }
                        .filter { list ->
                            query.isBlank() ||
                                list.title.contains(query, ignoreCase = true) ||
                                list.description.orEmpty().contains(query, ignoreCase = true)
                        }
                appendLine("Account: ${target.accountKey}")
                appendLine("Platform: ${target.platformType.name}")
                append(lists.toListToolText(emptyMessage = "No cached lists matched.", maxItems = maxItems))
                appendLine()
            }
        }.take(MAX_LIST_TOOL_RESULT_LENGTH)
    }

    companion object {
        const val NAME = "list_lists"
    }
}

internal class LoadListInfoTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadListInfoTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description = "Load detailed metadata for a specific social list by list id.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("List id.")
        val listId: String,
        @property:LLMDescription("Account id to load from. Leave blank only when platform selection resolves to one account.")
        val accountId: String = "",
        @property:LLMDescription("Account host. Leave blank only when the account key has no host.")
        val accountHost: String = "",
        @property:LLMDescription("Platform names to narrow account selection.")
        val platforms: List<String> = emptyList(),
    )

    override suspend fun execute(args: Args): String {
        val listId = args.listId.trim()
        if (listId.isBlank()) return "List id is blank."
        val targets = session.listTargets.resolveListTargets(args.accountId, args.accountHost, args.platforms)
        if (targets.isEmpty()) return noListTargetsMessage(args.platforms, args.accountId, args.accountHost)
        return buildString {
            appendLine("List info")
            appendLine("listId: $listId")
            appendLine()
            targets.forEach { target ->
                appendLine("Account: ${target.accountKey}")
                appendLine("Platform: ${target.platformType.name}")
                val list = runCatching { target.loadListInfo(listId) }.getOrNull()
                if (list == null) {
                    appendLine("No list info was returned.")
                } else {
                    append(list.toListToolText())
                }
                appendLine()
            }
        }.take(MAX_LIST_TOOL_RESULT_LENGTH)
    }

    companion object {
        const val NAME = "load_list_info"
    }
}

internal class LoadListTimelineTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadListTimelineTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description = "Load posts from a specific social list timeline.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("List id.")
        val listId: String,
        @property:LLMDescription("Account id to load from. Leave blank only when platform selection resolves to one account.")
        val accountId: String = "",
        @property:LLMDescription("Account host. Leave blank only when the account key has no host.")
        val accountHost: String = "",
        @property:LLMDescription("Platform names to narrow account selection.")
        val platforms: List<String> = emptyList(),
        @property:LLMDescription("Maximum timeline items per account. Defaults to 20 and is capped at 50.")
        val maxItems: Int = DEFAULT_LIST_TOOL_ITEMS,
    )

    override suspend fun execute(args: Args): String {
        val listId = args.listId.trim()
        if (listId.isBlank()) return "List id is blank."
        val targets = session.listTargets.resolveListTargets(args.accountId, args.accountHost, args.platforms)
        if (targets.isEmpty()) return noListTargetsMessage(args.platforms, args.accountId, args.accountHost)
        val maxItems = args.maxItems.listToolLimit()
        val results =
            targets.map { target ->
                target to runCatching { target.loadListTimeline(listId, maxItems) }.getOrElse { emptyList() }
            }
        session.messagePartStore.addPosts(results.flatMap { it.second }.mapNotNull { it.contentPostOrNull() })
        return buildString {
            appendLine("List timeline")
            appendLine("listId: $listId")
            appendLine("source: live list timeline data source; local cache not used")
            appendLine()
            results.forEach { (target, items) ->
                appendLine("Account: ${target.accountKey}")
                appendLine("Platform: ${target.platformType.name}")
                append(items.toTimelineToolText(maxItems = maxItems))
                appendLine()
            }
        }.take(MAX_LIST_TOOL_RESULT_LENGTH)
    }

    companion object {
        const val NAME = "load_list_timeline"
    }
}

internal class LoadUserListsTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadUserListsTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description = "Load the lists that contain a specific user.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("User id.")
        val userId: String,
        @property:LLMDescription("User host. Leave blank only when the user key has no host.")
        val userHost: String = "",
        @property:LLMDescription("Account id to load from. Leave blank to try every matching list-capable account.")
        val accountId: String = "",
        @property:LLMDescription("Account host. Leave blank only when the account key has no host.")
        val accountHost: String = "",
        @property:LLMDescription("Platform names to narrow account selection.")
        val platforms: List<String> = emptyList(),
        @property:LLMDescription("Maximum lists per account. Defaults to 20 and is capped at 50.")
        val maxItems: Int = DEFAULT_LIST_TOOL_ITEMS,
    )

    override suspend fun execute(args: Args): String {
        val userKey = microBlogKeyOrNull(args.userId, args.userHost) ?: return "User id is blank."
        val targets = session.listTargets.resolveListTargets(args.accountId, args.accountHost, args.platforms)
        if (targets.isEmpty()) return noListTargetsMessage(args.platforms, args.accountId, args.accountHost)
        val maxItems = args.maxItems.listToolLimit()
        return buildString {
            appendLine("User lists")
            appendLine("userKey: $userKey")
            appendLine()
            targets.forEach { target ->
                val lists = runCatching { target.loadUserLists(userKey) }.getOrElse { emptyList() }
                appendLine("Account: ${target.accountKey}")
                appendLine("Platform: ${target.platformType.name}")
                append(lists.toGenericListToolText(emptyMessage = "No lists were returned for this user.", maxItems = maxItems))
                appendLine()
            }
        }.take(MAX_LIST_TOOL_RESULT_LENGTH)
    }

    companion object {
        const val NAME = "load_user_lists"
    }
}

internal class CreateListTool(
    private val session: AgentToolSession,
) : SimpleTool<CreateListTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description = "Create a social list after explicit confirmation.",
    ) {
    @Serializable
    internal data class Args(
        val title: String,
        val description: String = "",
        val accountId: String = "",
        val accountHost: String = "",
        val platforms: List<String> = emptyList(),
        val confirmed: Boolean = false,
    )

    override suspend fun execute(args: Args): String {
        val title = args.title.trim()
        if (title.isBlank()) return "List title is blank."
        val target =
            session.listTargets.resolveSingleListTarget(args.accountId, args.accountHost, args.platforms)
                ?: return ambiguousListAccountMessage(args.platforms)
        val metadata = ListMetaData(title = title, description = args.description.trim().takeIf { it.isNotBlank() })
        if (!args.confirmed) {
            return target.listConfirmation(
                store = session.inputRequestStore,
                requestId = "list-create:${target.accountKey}:$title",
                event = "create_list",
                actionLabel = "Create list",
                metadata = metadata,
            )
        }
        target.createList(metadata)
        return "List created successfully.\nAccount: ${target.accountKey}\nPlatform: ${target.platformType.name}\nTitle: $title"
    }

    companion object {
        const val NAME = "create_list"
    }
}

internal class UpdateListTool(
    private val session: AgentToolSession,
) : SimpleTool<UpdateListTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description = "Update social list metadata after explicit confirmation.",
    ) {
    @Serializable
    internal data class Args(
        val listId: String,
        val title: String,
        val description: String = "",
        val accountId: String = "",
        val accountHost: String = "",
        val platforms: List<String> = emptyList(),
        val confirmed: Boolean = false,
    )

    override suspend fun execute(args: Args): String {
        val listId = args.listId.trim()
        val title = args.title.trim()
        if (listId.isBlank()) return "List id is blank."
        if (title.isBlank()) return "List title is blank."
        val target =
            session.listTargets.resolveSingleListTarget(args.accountId, args.accountHost, args.platforms)
                ?: return ambiguousListAccountMessage(args.platforms)
        val metadata = ListMetaData(title = title, description = args.description.trim().takeIf { it.isNotBlank() })
        if (!args.confirmed) {
            return target.listConfirmation(
                store = session.inputRequestStore,
                requestId = "list-update:${target.accountKey}:$listId",
                event = "update_list",
                actionLabel = "Update list",
                metadata = metadata,
                listId = listId,
            )
        }
        target.updateList(listId, metadata)
        return "List updated successfully.\nAccount: ${target.accountKey}\nPlatform: ${target.platformType.name}\nList id: $listId"
    }

    companion object {
        const val NAME = "update_list"
    }
}

internal class DeleteListTool(
    private val session: AgentToolSession,
) : SimpleTool<DeleteListTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description = "Delete a social list after explicit confirmation.",
    ) {
    @Serializable
    internal data class Args(
        val listId: String,
        val accountId: String = "",
        val accountHost: String = "",
        val platforms: List<String> = emptyList(),
        val confirmed: Boolean = false,
    )

    override suspend fun execute(args: Args): String {
        val listId = args.listId.trim()
        if (listId.isBlank()) return "List id is blank."
        val target =
            session.listTargets.resolveSingleListTarget(args.accountId, args.accountHost, args.platforms)
                ?: return ambiguousListAccountMessage(args.platforms)
        if (!args.confirmed) {
            return target.simpleListConfirmation(
                store = session.inputRequestStore,
                requestId = "list-delete:${target.accountKey}:$listId",
                event = "delete_list",
                actionLabel = "Delete list",
                listId = listId,
            )
        }
        target.deleteList(listId)
        return "List deleted successfully.\nAccount: ${target.accountKey}\nPlatform: ${target.platformType.name}\nList id: $listId"
    }

    companion object {
        const val NAME = "delete_list"
    }
}

internal class AddListMemberTool(
    private val session: AgentToolSession,
) : SimpleTool<AddListMemberTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description = "Add a user to a social list after explicit confirmation.",
    ) {
    @Serializable
    internal data class Args(
        val listId: String,
        val userId: String,
        val userHost: String = "",
        val accountId: String = "",
        val accountHost: String = "",
        val platforms: List<String> = emptyList(),
        val confirmed: Boolean = false,
    )

    override suspend fun execute(args: Args): String {
        val listId = args.listId.trim()
        if (listId.isBlank()) return "List id is blank."
        val userKey = microBlogKeyOrNull(args.userId, args.userHost) ?: return "User id is blank."
        val target =
            session.listTargets.resolveSingleListTarget(args.accountId, args.accountHost, args.platforms)
                ?: return ambiguousListAccountMessage(args.platforms)
        if (!args.confirmed) {
            return target.memberConfirmation(
                store = session.inputRequestStore,
                requestId = "list-add-member:${target.accountKey}:$listId:$userKey",
                event = "add_list_member",
                actionLabel = "Add list member",
                listId = listId,
                userKey = userKey,
            )
        }
        target.addMember(listId, userKey)
        return "List member added successfully.\n" +
            "Account: ${target.accountKey}\n" +
            "Platform: ${target.platformType.name}\n" +
            "List id: $listId\n" +
            "User: $userKey"
    }

    companion object {
        const val NAME = "add_list_member"
    }
}

internal class RemoveListMemberTool(
    private val session: AgentToolSession,
) : SimpleTool<RemoveListMemberTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description = "Remove a user from a social list after explicit confirmation.",
    ) {
    @Serializable
    internal data class Args(
        val listId: String,
        val userId: String,
        val userHost: String = "",
        val accountId: String = "",
        val accountHost: String = "",
        val platforms: List<String> = emptyList(),
        val confirmed: Boolean = false,
    )

    override suspend fun execute(args: Args): String {
        val listId = args.listId.trim()
        if (listId.isBlank()) return "List id is blank."
        val userKey = microBlogKeyOrNull(args.userId, args.userHost) ?: return "User id is blank."
        val target =
            session.listTargets.resolveSingleListTarget(args.accountId, args.accountHost, args.platforms)
                ?: return ambiguousListAccountMessage(args.platforms)
        if (!args.confirmed) {
            return target.memberConfirmation(
                store = session.inputRequestStore,
                requestId = "list-remove-member:${target.accountKey}:$listId:$userKey",
                event = "remove_list_member",
                actionLabel = "Remove list member",
                listId = listId,
                userKey = userKey,
            )
        }
        target.removeMember(listId, userKey)
        return "List member removed successfully.\n" +
            "Account: ${target.accountKey}\n" +
            "Platform: ${target.platformType.name}\n" +
            "List id: $listId\n" +
            "User: $userKey"
    }

    companion object {
        const val NAME = "remove_list_member"
    }
}

private fun List<AgentListTarget>.resolveListTargets(
    accountId: String,
    accountHost: String,
    platforms: List<String>,
): List<AgentListTarget> {
    val requestedAccount = microBlogKeyOrNull(accountId, accountHost)
    val targets = filterListTargetsByPlatformNames(platforms)
    return requestedAccount?.let { account -> targets.filter { it.accountKey == account } } ?: targets
}

private fun List<AgentListTarget>.resolveSingleListTarget(
    accountId: String,
    accountHost: String,
    platforms: List<String>,
): AgentListTarget? = resolveListTargets(accountId, accountHost, platforms).singleOrNull()

private fun noListTargetsMessage(
    platforms: List<String>,
    accountId: String = "",
    accountHost: String = "",
): String =
    when {
        accountId.isNotBlank() -> {
            "No list-capable signed-in account matches ${MicroBlogKey(accountId.trim(), accountHost.trim())}."
        }

        platforms.isNotEmpty() -> {
            "No list-capable signed-in accounts are available for the requested platforms: ${platforms.joinToString()}."
        }

        else -> {
            "No signed-in accounts are available for list tools."
        }
    }

private fun ambiguousListAccountMessage(platforms: List<String>): String =
    if (platforms.isEmpty()) {
        "List mutation requires exactly one list-capable account. Provide accountId/accountHost or a platform that resolves to one account."
    } else {
        "List mutation requires exactly one list-capable account for platforms: ${platforms.joinToString()}."
    }

private fun microBlogKeyOrNull(
    id: String,
    host: String,
): MicroBlogKey? = id.trim().takeIf { it.isNotBlank() }?.let { MicroBlogKey(id = it, host = host.trim()) }

private suspend fun AgentListTarget.listConfirmation(
    store: AgentToolInputRequestStore,
    requestId: String,
    event: String,
    actionLabel: String,
    metadata: ListMetaData,
    listId: String? = null,
): String {
    store.setListInputRequest(requestId, event, accountKey) {
        appendLine("title=${metadata.title}")
        metadata.description?.let { appendLine("description=$it") }
        listId?.let { appendLine("listId=$it") }
    }
    return buildString {
        appendLine("event=${event}_confirmation_required")
        appendLine("inputRequestId=$requestId")
        appendLine("action=$actionLabel")
        appendLine("account=$accountKey")
        appendLine("platform=${platformType.name}")
        listId?.let { appendLine("listId=$it") }
        appendLine("title=${metadata.title}")
        metadata.description?.let { appendLine("description=$it") }
    }.trim()
}

private suspend fun AgentListTarget.simpleListConfirmation(
    store: AgentToolInputRequestStore,
    requestId: String,
    event: String,
    actionLabel: String,
    listId: String,
): String {
    store.setListInputRequest(requestId, event, accountKey) {
        appendLine("listId=$listId")
    }
    return buildString {
        appendLine("event=${event}_confirmation_required")
        appendLine("inputRequestId=$requestId")
        appendLine("action=$actionLabel")
        appendLine("account=$accountKey")
        appendLine("platform=${platformType.name}")
        appendLine("listId=$listId")
    }.trim()
}

private suspend fun AgentListTarget.memberConfirmation(
    store: AgentToolInputRequestStore,
    requestId: String,
    event: String,
    actionLabel: String,
    listId: String,
    userKey: MicroBlogKey,
): String {
    store.setListInputRequest(requestId, event, accountKey) {
        appendLine("listId=$listId")
        appendLine("userId=${userKey.id}")
        appendLine("userHost=${userKey.host}")
    }
    return buildString {
        appendLine("event=${event}_confirmation_required")
        appendLine("inputRequestId=$requestId")
        appendLine("action=$actionLabel")
        appendLine("account=$accountKey")
        appendLine("platform=${platformType.name}")
        appendLine("listId=$listId")
        appendLine("user=$userKey")
    }.trim()
}

private suspend fun AgentToolInputRequestStore.setListInputRequest(
    requestId: String,
    event: String,
    accountKey: MicroBlogKey,
    extra: StringBuilder.() -> Unit,
) {
    val value =
        buildString {
            appendLine("event=${event}_confirmed")
            appendLine("confirmed=true")
            appendLine("accountId=${accountKey.id}")
            appendLine("accountHost=${accountKey.host}")
            extra()
        }
    set(
        AgentPendingInputRequest(
            requestId = requestId,
            options = listOf(AgentPendingInputRequest.Option(id = "confirm", value = value)),
            allowFreeText = true,
        ),
    )
}

private fun Int.listToolLimit(): Int = coerceIn(1, MAX_LIST_TOOL_ITEMS)

private fun List<UiList.List>.toListToolText(
    emptyMessage: String,
    maxItems: Int,
): String {
    if (isEmpty()) return "$emptyMessage\n"
    val lists = this
    return buildString {
        lists.take(maxItems).forEachIndexed { index, list ->
            appendLine("List #${index + 1}")
            append(list.toListToolText())
        }
    }
}

private fun List<UiList>.toGenericListToolText(
    emptyMessage: String,
    maxItems: Int,
): String {
    if (isEmpty()) return "$emptyMessage\n"
    val lists = this
    return buildString {
        lists.take(maxItems).forEachIndexed { index, list ->
            appendLine("List #${index + 1}")
            append(list.toGenericListToolText())
        }
    }
}

private fun UiList.List.toListToolText(): String =
    buildString {
        appendLine("listId: $id")
        appendLine("title: $title")
        appendLine("description: ${description.orEmpty()}")
        appendLine("readonly: $readonly")
        appendLine("avatar: ${avatar.orEmpty()}")
        creator?.let {
            appendLine("creator: ${it.name.raw} (${it.handle.raw})")
        }
    }

private fun UiList.toGenericListToolText(): String =
    when (this) {
        is UiList.List -> {
            toListToolText()
        }

        is UiList.Feed -> {
            "listType: feed\nlistId: $id\ntitle: $title\ndescription: ${description.orEmpty()}\nreadonly: $readonly\n"
        }

        is UiList.Antenna -> {
            "listType: antenna\nlistId: $id\ntitle: $title\nreadonly: $readonly\n"
        }

        is UiList.Channel -> {
            "listType: channel\nlistId: $id\ntitle: $title\ndescription: ${description?.raw.orEmpty()}\nreadonly: $readonly\n"
        }
    }

private fun List<UiTimelineV2>.toTimelineToolText(maxItems: Int): String {
    if (isEmpty()) return "No list timeline items were returned.\n"
    val items = this
    return buildString {
        items.take(maxItems).forEachIndexed { index, item ->
            appendLine("Timeline item #${index + 1}")
            append(item.toTimelineItemToolText())
        }
    }
}

private fun UiTimelineV2.toTimelineItemToolText(): String =
    when (this) {
        is UiTimelineV2.TimelinePostItem -> toListPostToolText()
        is UiTimelineV2.Post -> toListPostToolText()
        is UiTimelineV2.User -> value.toListUserToolText(message)
        is UiTimelineV2.UserList -> "itemType: user_list\nstatusKey: $statusKey\nusers: ${users.joinToString { it.handle.raw }}\n"
        is UiTimelineV2.Message -> "itemType: message\nstatusKey: $statusKey\ncreatedAt: ${createdAt.value}\n"
        is UiTimelineV2.Feed -> "itemType: feed\ntitle: ${title.orEmpty()}\nurl: $url\n"
    }

private fun UiTimelineV2.TimelinePostItem.toListPostToolText(): String =
    buildString {
        presentation.message?.let { appendLine("messageStatusKey: ${it.statusKey}") }
        append(displayPost.toListPostToolText())
    }

private fun UiTimelineV2.Post.toListPostToolText(): String =
    buildString {
        appendLine("itemType: post")
        appendLine("attachmentRef: ${agentAttachmentMarker()}")
        appendLine("platform: ${platformType.name}")
        appendLine("statusKey: $statusKey")
        appendLine("createdAt: ${createdAt.value}")
        appendLine("authorName: ${user?.name?.raw.orEmpty()}")
        appendLine("authorHandle: ${user?.handle?.raw.orEmpty()}")
        appendLine("content: ${content.original.raw.take(MAX_LIST_TEXT_LENGTH)}")
        appendLine("imagesCount: ${images.size}")
        images.filterIsInstance<UiMedia.Image>().take(MAX_LIST_IMAGES).forEachIndexed { index, image ->
            appendLine("image${index + 1}Url: ${image.url}")
            appendLine("image${index + 1}Description: ${image.description.orEmpty()}")
        }
    }

private fun UiProfile.toListUserToolText(message: UiTimelineV2.Message?): String =
    buildString {
        appendLine("itemType: user")
        message?.let { appendLine("messageStatusKey: ${it.statusKey}") }
        appendLine("attachmentRef: ${agentAttachmentMarker()}")
        appendLine("platform: ${platformType.name}")
        appendLine("userKey: $key")
        appendLine("displayName: ${name.raw}")
        appendLine("handle: ${handle.raw}")
    }

private const val DEFAULT_LIST_TOOL_ITEMS = 20
private const val MAX_LIST_TOOL_ITEMS = 50
private const val MAX_LIST_TOOL_RESULT_LENGTH = 80_000
private const val MAX_LIST_TEXT_LENGTH = 1_000
private const val MAX_LIST_IMAGES = 4
