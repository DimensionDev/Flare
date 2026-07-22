package dev.dimension.flare.feature.agent.common

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlin.time.Clock

internal class ComposePostTool(
    private val session: AgentToolSession,
) : SimpleTool<ComposePostTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Prepare or publish a social post through ComposeDataSource. " +
                "Supports action=new, action=reply, and action=quote. " +
                "Call with confirmed=false first to get a user-facing confirmation message. " +
                "If the user did not choose a platform or account, still call this tool with blank account fields " +
                "so it can create a structured UI selection request. " +
                "Call with confirmed=true only when the latest user message explicitly confirms the exact account, action, target post, " +
                "and content previously shown for confirmation. This tool does not support media or polls.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Post text to send. Must be exactly the content the user wants published.")
        val content: String,
        @property:LLMDescription("Compose action: new, reply, or quote.")
        val action: String = "new",
        @property:LLMDescription(
            "Account id to send from. Prefer values from compose guidance. Leave blank only when platform selection resolves to exactly one account.",
        )
        val accountId: String = "",
        @property:LLMDescription(
            "Account host to send from. Prefer values from compose guidance. Leave blank only when the account key has no host.",
        )
        val accountHost: String = "",
        @property:LLMDescription(
            "Platform names or aliases to narrow account selection. Leave empty when the user did not choose a platform; the tool will request a structured platform/account selection if needed.",
        )
        val platforms: List<String> = emptyList(),
        @property:LLMDescription("Visibility for the post: Public, Home, Followers, Specified, or Channel.")
        val visibility: String = "Public",
        @property:LLMDescription("ISO 639-1 language codes for the post text. Leave empty to use en.")
        val languages: List<String> = emptyList(),
        @property:LLMDescription("Whether the post should be marked sensitive.")
        val sensitive: Boolean = false,
        @property:LLMDescription("Content warning/spoiler text. Leave blank for none.")
        val spoilerText: String = "",
        @property:LLMDescription(
            "For reply or quote, status id of the target post. Prefer targetPostRef from tool results when available.",
        )
        val targetStatusId: String = "",
        @property:LLMDescription("For reply or quote, status host of the target post. Leave blank only when the target key has no host.")
        val targetStatusHost: String = "",
        @property:LLMDescription(
            "For reply or quote, exact post attachmentRef marker or ref from a previous tool result, such as [[post:Mastodon:host:id]].",
        )
        val targetPostRef: String = "",
        @property:LLMDescription("For reply or quote in a post insight session, set true to target the current post.")
        val useCurrentStatus: Boolean = false,
        @property:LLMDescription(
            "Set true only after the latest user message explicitly confirms the exact previously restated account, action, target post, and content.",
        )
        val confirmed: Boolean = false,
    )

    override suspend fun execute(args: Args): String {
        val content = args.content.trim()
        if (content.isBlank()) {
            return "Post content is blank."
        }
        val action = args.action.toComposeActionOrNull() ?: return "Unsupported compose action: ${args.action}."
        val reference = resolveReference(args, action)
        if (action != ComposeAction.New && reference == null) {
            return targetSelectionMessage(args, content, action)
        }
        val target = resolveComposeTarget(args) ?: return accountSelectionMessage(args, content, action, reference)
        val visibility = args.visibility.toPostVisibilityOrNull() ?: return "Unsupported visibility: ${args.visibility}."
        val config = target.dataSource.composeConfig(action.composeType)
        val maxLength = config.text?.maxLength?.firstOrNull()
        val remainingLength = config.text?.remainingLength(content)?.firstOrNull()
        if (maxLength != null && remainingLength != null && remainingLength < 0) {
            return "Post content is ${maxLength - remainingLength} characters, but ${target.platformType.name} allows " +
                "at most $maxLength for ${action.label}."
        }
        if (visibility != UiTimelineV2.Post.Visibility.Public && config.visibility == null) {
            return "${target.platformType.name} does not expose visibility selection for ${action.label} posts."
        }
        if (args.spoilerText.isNotBlank() && config.contentWarning == null) {
            return "${target.platformType.name} does not expose content warning selection for ${action.label} posts."
        }

        val data =
            ComposeData(
                content = content,
                visibility = visibility,
                language = args.languages.normalizedLanguages(config.language?.maxCount),
                sensitive = args.sensitive,
                spoilerText = args.spoilerText.trim().takeIf { it.isNotBlank() },
                referenceStatus = reference?.toComposeReference(action),
            )
        if (!args.confirmed) {
            val userPreview = target.loadUserPreview()
            val inputRequest =
                target.confirmationInputRequest(
                    data = data,
                    action = action,
                    reference = reference,
                    userPreview = userPreview,
                )
            session.inputRequestStore.set(inputRequest)
            return target.confirmationMessage(
                data = data,
                action = action,
                reference = reference,
                userPreview = userPreview,
                inputRequest = inputRequest,
            )
        }

        target.dataSource.compose(data = data) {
            // Progress callbacks are meaningful for media uploads; this tool only sends text posts.
        }
        return buildString {
            appendLine("Post sent successfully.")
            appendLine("Action: ${action.label}")
            appendLine("Account: ${target.accountKey}")
            appendLine("Platform: ${target.platformType.name}")
            reference?.let {
                appendLine("Target status: ${it.statusKey}")
            }
            appendLine("Visibility: ${data.visibility.name}")
        }.trim()
    }

    private fun resolveComposeTarget(args: Args): AgentComposeTarget? {
        val requestedAccount =
            args.accountId
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { MicroBlogKey(id = it, host = args.accountHost.trim()) }
        val targets =
            if (args.platforms.isEmpty()) {
                session.composeTargets
            } else {
                session.composeTargets.filterComposeTargetsByPlatformNames(args.platforms)
            }
        return when {
            requestedAccount != null -> targets.firstOrNull { it.accountKey == requestedAccount }
            targets.size == 1 -> targets.first()
            else -> null
        }
    }

    private suspend fun resolveReference(
        args: Args,
        action: ComposeAction,
    ): ComposeReference? {
        if (action == ComposeAction.New) {
            return null
        }
        if (args.useCurrentStatus) {
            session.status?.let { status ->
                return ComposeReference(
                    statusKey = status.currentPost?.agentDisplayPost()?.statusKey ?: status.statusKey,
                    post = status.currentPost?.agentDisplayPost(),
                )
            }
        }
        args.targetPostRef.trim().takeIf { it.isNotBlank() }?.let { ref ->
            findPostByRef(ref)?.let { post ->
                val displayPost = post.agentDisplayPost()
                return ComposeReference(statusKey = displayPost.statusKey, post = displayPost)
            }
        }
        microBlogKeyOrNull(id = args.targetStatusId, host = args.targetStatusHost)?.let { key ->
            val post = findPostByKey(key)?.agentDisplayPost()
            return ComposeReference(statusKey = post?.statusKey ?: key, post = post)
        }
        val postTargets = availableReferencePosts()
        return when (postTargets.size) {
            1 -> {
                val post = postTargets.first()
                ComposeReference(statusKey = post.statusKey, post = post)
            }

            else -> {
                null
            }
        }
    }

    private suspend fun targetSelectionMessage(
        args: Args,
        content: String,
        action: ComposeAction,
    ): String {
        val postTargets = availableReferencePosts()
        if (postTargets.isEmpty()) {
            return "${action.label} requires a target post. Open a post insight session, " +
                "use search_posts/load_post_context first, or provide targetStatusId and targetStatusHost."
        }
        val request =
            AgentPendingInputRequest(
                requestId = "compose-target:${action.label}:${content.hashCode()}:${args.platforms.joinToString()}",
                options =
                    postTargets.map { post ->
                        AgentPendingInputRequest.Option(
                            id = "post:${post.agentAttachmentRef()}",
                            value =
                                buildString {
                                    appendLine("event=compose_target_selected")
                                    appendLine("action=${action.label}")
                                    appendLine("targetStatusId=${post.statusKey.id}")
                                    appendLine("targetStatusHost=${post.statusKey.host}")
                                    appendLine("targetPostRef=${post.agentAttachmentMarker()}")
                                    appendLine()
                                    appendAccountArgs(args)
                                    appendLine("content:")
                                    append(content)
                                },
                            postPreview = post,
                        )
                    },
                allowFreeText = true,
            )
        session.inputRequestStore.set(request)
        return buildString {
            appendLine("event=compose_target_selection_required")
            appendLine("action=${action.label}")
            appendLine("inputRequestId=${request.requestId}")
            appendLine("inputRequestOptions:")
            postTargets.forEach { post ->
                appendLine("- optionId=post:${post.agentAttachmentRef()}")
                appendLine("  optionKind=post")
                appendLine("  platform=${post.platformType.name}")
                appendLine("  targetStatus=${post.statusKey}")
                appendLine("  author=${post.user?.composeDisplayLabel().orEmpty()}")
                appendLine("  summary=${post.content.original.raw.take(120)}")
            }
        }.trim()
    }

    private suspend fun accountSelectionMessage(
        args: Args,
        content: String,
        action: ComposeAction,
        reference: ComposeReference?,
    ): String {
        if (session.composeTargets.isEmpty()) {
            return "No signed-in accounts are available for composing posts."
        }
        val targets =
            if (args.platforms.isEmpty()) {
                session.composeTargets
            } else {
                session.composeTargets.filterComposeTargetsByPlatformNames(args.platforms)
            }
        if (targets.isEmpty()) {
            return "No compose-capable account matches the requested platforms: ${args.platforms.joinToString()}."
        }
        val platformGroups = targets.groupBy { it.platformType }
        if (args.platforms.isEmpty() && platformGroups.size > 1) {
            return platformSelectionMessage(
                args = args,
                content = content,
                action = action,
                reference = reference,
                platformGroups = platformGroups,
            )
        }
        val targetOptions =
            targets.map { target ->
                val userPreview = target.loadUserPreview()
                target to userPreview
            }
        val request =
            AgentPendingInputRequest(
                requestId =
                    "compose-account:${action.label}:${content.hashCode()}:" +
                        "${args.platforms.joinToString()}:${reference?.statusKey}",
                options =
                    targetOptions.map { (target, userPreview) ->
                        AgentPendingInputRequest.Option(
                            id = "account:${target.accountKey}",
                            value =
                                buildString {
                                    appendLine("event=compose_account_selected")
                                    appendLine("action=${action.label}")
                                    appendLine("accountId=${target.accountKey.id}")
                                    appendLine("accountHost=${target.accountKey.host}")
                                    appendLine("platform=${target.platformType.name}")
                                    reference?.let {
                                        appendLine("targetStatusId=${it.statusKey.id}")
                                        appendLine("targetStatusHost=${it.statusKey.host}")
                                    }
                                    appendLine()
                                    appendLine("content:")
                                    append(content)
                                },
                            userPreview = userPreview,
                        )
                    },
                allowFreeText = true,
            )
        session.inputRequestStore.set(request)
        return buildString {
            appendLine("event=compose_account_selection_required")
            appendLine("action=${action.label}")
            appendLine("inputRequestId=${request.requestId}")
            appendLine("inputRequestOptions:")
            targetOptions.forEach { (target, userPreview) ->
                appendLine("- optionId=account:${target.accountKey}")
                appendLine("  optionKind=account")
                appendLine("  accountKey=${target.accountKey}")
                appendLine("  platform=${target.platformType.name}")
                appendLine("  displayName=${userPreview?.name?.raw.orEmpty()}")
                appendLine("  handle=${userPreview?.handle?.canonical.orEmpty()}")
            }
        }.trim()
    }

    private suspend fun platformSelectionMessage(
        args: Args,
        content: String,
        action: ComposeAction,
        reference: ComposeReference?,
        platformGroups: Map<PlatformType, List<AgentComposeTarget>>,
    ): String {
        val request =
            AgentPendingInputRequest(
                requestId = "compose-platform:${action.label}:${content.hashCode()}:${reference?.statusKey}",
                options =
                    platformGroups.entries
                        .sortedBy { it.key.name }
                        .map { (platformType, targets) ->
                            AgentPendingInputRequest.Option(
                                id = "platform:${platformType.name}",
                                value =
                                    buildString {
                                        appendLine("event=compose_platform_selected")
                                        appendLine("action=${action.label}")
                                        appendLine("platforms=${platformType.name}")
                                        reference?.let {
                                            appendLine("targetStatusId=${it.statusKey.id}")
                                            appendLine("targetStatusHost=${it.statusKey.host}")
                                        }
                                        appendLine()
                                        appendAccountArgs(args)
                                        appendLine("content:")
                                        append(content)
                                    },
                            )
                        },
                allowFreeText = true,
            )
        session.inputRequestStore.set(request)
        return buildString {
            appendLine("event=compose_platform_selection_required")
            appendLine("action=${action.label}")
            appendLine("inputRequestId=${request.requestId}")
            appendLine("inputRequestOptions:")
            platformGroups.entries
                .sortedBy { it.key.name }
                .forEach { (platformType, targets) ->
                    appendLine("- optionId=platform:${platformType.name}")
                    appendLine("  optionKind=platform")
                    appendLine("  platform=${platformType.name}")
                    appendLine("  accountCount=${targets.size}")
                }
        }.trim()
    }

    private fun AgentComposeTarget.confirmationInputRequest(
        data: ComposeData,
        action: ComposeAction,
        reference: ComposeReference?,
        userPreview: UiProfile?,
    ): AgentPendingInputRequest =
        AgentPendingInputRequest(
            requestId = "compose:${action.label}:$accountKey:${reference?.statusKey}:${data.content.hashCode()}",
            options =
                listOf(
                    AgentPendingInputRequest.Option(
                        id = "confirm",
                        value =
                            buildString {
                                appendLine("event=compose_confirmed")
                                appendLine("confirmed=true")
                                appendLine("action=${action.label}")
                                appendLine("accountId=${accountKey.id}")
                                appendLine("accountHost=${accountKey.host}")
                                appendLine("platform=${platformType.name}")
                                reference?.let {
                                    appendLine("targetStatusId=${it.statusKey.id}")
                                    appendLine("targetStatusHost=${it.statusKey.host}")
                                }
                                appendLine("visibility=${data.visibility.name}")
                                if (data.language.isNotEmpty()) {
                                    appendLine("languages=${data.language.joinToString()}")
                                }
                                appendLine("sensitive=${data.sensitive}")
                                data.spoilerText?.takeIf { it.isNotBlank() }?.let {
                                    appendLine("spoilerText=$it")
                                }
                                appendLine("content:")
                                append(data.content)
                            },
                    ),
                ),
            allowFreeText = true,
            postPreview = composePreviewPost(data = data, userPreview = userPreview),
        )

    private fun AgentComposeTarget.confirmationMessage(
        data: ComposeData,
        action: ComposeAction,
        reference: ComposeReference?,
        userPreview: UiProfile?,
        inputRequest: AgentPendingInputRequest,
    ): String =
        buildString {
            appendLine("event=compose_confirmation_required")
            appendLine("inputRequestId=${inputRequest.requestId}")
            appendLine("inputRequestOptions:")
            appendLine("- optionId=confirm")
            appendLine("  optionKind=confirmation")
            appendLine("action=${action.label}")
            appendLine("accountLabel=${userPreview?.composeDisplayLabel() ?: composeAccountLabel()}")
            appendLine("handle=${userPreview?.handle?.canonical.orEmpty()}")
            appendLine("accountKey=$accountKey")
            appendLine("accountId=${accountKey.id}")
            appendLine("accountHost=${accountKey.host}")
            appendLine("platform=${platformType.name}")
            appendLine("visibility=${data.visibility.name}")
            reference?.let {
                appendLine("targetStatus=${it.statusKey}")
                appendLine("targetStatusId=${it.statusKey.id}")
                appendLine("targetStatusHost=${it.statusKey.host}")
                it.post?.user?.composeDisplayLabel()?.takeIf { label -> label.isNotBlank() }?.let { label ->
                    appendLine("targetAuthor=$label")
                }
                it.post?.content?.original?.raw?.take(160)?.takeIf { summary -> summary.isNotBlank() }?.let { summary ->
                    appendLine("targetSummary=$summary")
                }
            }
            if (data.language.isNotEmpty()) {
                appendLine("languages=${data.language.joinToString()}")
            }
            appendLine("sensitive=${data.sensitive}")
            data.spoilerText?.takeIf { it.isNotBlank() }?.let {
                appendLine("spoilerText=$it")
            }
            appendLine("content:")
            append(data.content)
        }

    private fun AgentComposeTarget.composePreviewPost(
        data: ComposeData,
        userPreview: UiProfile?,
    ): UiTimelineV2.Post =
        UiTimelineV2.Post(
            platformType = platformType,
            images = persistentListOf(),
            sensitive = data.sensitive,
            contentWarning = data.spoilerText?.toUiPlainText()?.let { UiTranslatableText(original = it) },
            user = userPreview,
            content = UiTranslatableText(original = data.content.toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey =
                MicroBlogKey(
                    id = "agent-compose-preview-${data.content.hashCode()}",
                    host = accountKey.host,
                ),
            card = null,
            createdAt = Clock.System.now().toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = data.visibility,
            replyToHandle = null,
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(accountKey),
        )

    private suspend fun AgentComposeTarget.loadUserPreview(): UiProfile? =
        session.userTargets
            .firstOrNull { target ->
                target.accountKey == accountKey && target.platformType == platformType
            }?.let { target ->
                runCatching {
                    target.loadUserById(accountKey.id)
                }.getOrNull()
            }

    private suspend fun availableReferencePosts(): List<UiTimelineV2.Post> =
        buildList {
            session.status
                ?.currentPost
                ?.agentDisplayPost()
                ?.let(::add)
            session.messagePartStore
                .snapshot()
                .filterIsInstance<AgentMessagePart.PostCard>()
                .map { it.post.agentDisplayPost() }
                .forEach(::add)
        }.distinctBy { it.platformType to it.statusKey }

    private suspend fun findPostByRef(ref: String): UiTimelineV2.Post? {
        val normalizedRef = ref.normalizedPostRef()
        return availableReferencePosts().firstOrNull { post ->
            post.agentPostRefAliases().any { alias -> alias.normalizedPostRef() == normalizedRef }
        }
    }

    private suspend fun findPostByKey(key: MicroBlogKey): UiTimelineV2.Post? =
        availableReferencePosts().firstOrNull { post ->
            post.statusKey == key
        }

    private fun AgentComposeTarget.composeAccountLabel(): String = "${platformType.name} / $accountKey"

    private fun UiProfile.composeDisplayLabel(): String =
        listOf(
            name.raw.takeIf { it.isNotBlank() },
            handle.canonical,
        ).filterNotNull()
            .distinct()
            .joinToString(" ")

    private fun ComposeReference.toComposeReference(action: ComposeAction): ComposeData.ReferenceStatus =
        ComposeData.ReferenceStatus(
            composeStatus =
                when (action) {
                    ComposeAction.New -> error("New posts do not have a compose reference.")
                    ComposeAction.Reply -> ComposeStatus.Reply(statusKey)
                    ComposeAction.Quote -> ComposeStatus.Quote(statusKey)
                },
        )

    private fun StringBuilder.appendAccountArgs(args: Args) {
        args.accountId.trim().takeIf { it.isNotBlank() }?.let {
            appendLine("accountId=$it")
            appendLine("accountHost=${args.accountHost.trim()}")
        }
        if (args.platforms.isNotEmpty()) {
            appendLine("platforms=${args.platforms.joinToString()}")
        }
    }

    companion object {
        const val NAME = "compose_post"
    }
}

private data class ComposeReference(
    val statusKey: MicroBlogKey,
    val post: UiTimelineV2.Post?,
)

private enum class ComposeAction(
    val composeType: ComposeType,
    val label: String,
) {
    New(
        composeType = ComposeType.New,
        label = "new",
    ),
    Reply(
        composeType = ComposeType.Reply,
        label = "reply",
    ),
    Quote(
        composeType = ComposeType.Quote,
        label = "quote",
    ),
}

private fun String.toComposeActionOrNull(): ComposeAction? =
    when (trim().lowercase()) {
        "", "new", "post", "compose", "send", "publish", "tweet", "toot", "发帖", "发送" -> ComposeAction.New
        "reply", "respond", "comment", "回复", "评论" -> ComposeAction.Reply
        "quote", "quote_post", "quote-post", "引用", "引用转发" -> ComposeAction.Quote
        else -> null
    }

private fun microBlogKeyOrNull(
    id: String,
    host: String,
): MicroBlogKey? =
    id
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let { MicroBlogKey(id = it, host = host.trim()) }

private fun UiTimelineV2.Post.agentPostRefAliases(): Set<String> =
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

private fun String.normalizedPostRef(): String =
    trim()
        .removePrefix("[[post:")
        .removeSuffix("]]")
        .lowercase()

private fun String.toPostVisibilityOrNull(): UiTimelineV2.Post.Visibility? =
    UiTimelineV2.Post.Visibility.entries.firstOrNull { visibility ->
        trim().equals(visibility.name, ignoreCase = true)
    }

private fun List<String>.normalizedLanguages(maxCount: Int?): List<String> {
    val values =
        map { it.trim().lowercase() }
            .filter { it.length == 2 && it.all { char -> char in 'a'..'z' } }
            .distinct()
            .ifEmpty { listOf("en") }
    return maxCount?.let { values.take(it) } ?: values
}
