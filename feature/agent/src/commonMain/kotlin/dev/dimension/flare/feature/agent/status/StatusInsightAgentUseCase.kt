package dev.dimension.flare.feature.agent.status

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import androidx.paging.LoadState
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.handler.PostTranslationDisplay
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.feature.agent.runtime.AgentAvailability
import dev.dimension.flare.feature.agent.runtime.FlareAgentRuntime
import dev.dimension.flare.feature.agent.runtime.FlareAgentRuntimeProvider
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class StatusInsightAgentUseCase(
    private val runtimeProvider: FlareAgentRuntimeProvider,
) {
    operator fun invoke(
        postDataSource: PostDataSource,
        statusKey: MicroBlogKey,
        searchDataSources: List<AccountMicroblogDataSource>,
    ): Flow<StatusInsightEvent> =
        channelFlow {
            run(postDataSource, statusKey, searchDataSources)
        }

    private suspend fun SendChannel<StatusInsightEvent>.run(
        postDataSource: PostDataSource,
        statusKey: MicroBlogKey,
        searchDataSources: List<AccountMicroblogDataSource>,
    ) {
        val runtime =
            runtimeProvider.createRuntime()
                ?: throw StatusInsightAgentUnavailableException(runtimeProvider.availability())
        send(StatusInsightEvent.Trace(StatusInsightPhase.LoadingPostContext))
        val post = postDataSource.loadPost(statusKey)
        send(StatusInsightEvent.PostLoaded(post))
        send(StatusInsightEvent.Trace(StatusInsightPhase.PostContextLoaded))
        val imageAttachments = post.aiImageAttachments()
        if (imageAttachments.isNotEmpty()) {
            send(StatusInsightEvent.Trace(StatusInsightPhase.PreparingImages))
        }
        val searchTargets = postDataSource.statusInsightSearchTargets(searchDataSources, post.platformType)
        val toolRegistry = postDataSource.statusInsightToolRegistry(statusKey, searchTargets)
        val systemPrompt = STATUS_INSIGHT_SYSTEM_PROMPT.withSearchPlatformGuidance(searchTargets)
        val result =
            try {
                runtime.runAgent(
                    prompt = post.toInsightPrompt(Locale.language, includeImages = true),
                    toolRegistry = toolRegistry,
                    systemPrompt = systemPrompt,
                ) { event ->
                    send(event)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                if (imageAttachments.isEmpty() || !throwable.isImageContentUnsupported()) {
                    throw throwable
                }
                send(StatusInsightEvent.Trace(StatusInsightPhase.ImagesUnsupportedFallback))
                runtime.runAgent(
                    prompt = post.toInsightPrompt(Locale.language, includeImages = false),
                    toolRegistry = toolRegistry,
                    systemPrompt = systemPrompt,
                ) { event ->
                    send(event)
                }
            }

        send(StatusInsightEvent.Result(result.cleanPlainText()))
    }

    private suspend fun FlareAgentRuntime.runAgent(
        prompt: Prompt,
        toolRegistry: ToolRegistry,
        systemPrompt: String,
        onEvent: suspend (StatusInsightEvent.Trace) -> Unit,
    ): String {
        val agent = createAgent(toolRegistry, systemPrompt, onEvent)
        return try {
            agent.run(prompt)
        } finally {
            agent.close()
        }
    }

    private fun FlareAgentRuntime.createAgent(
        toolRegistry: ToolRegistry,
        systemPrompt: String,
        onEvent: suspend (StatusInsightEvent.Trace) -> Unit,
    ): AIAgent<Prompt, String> =
        AIAgent
            .builder()
            .promptExecutor(promptExecutor)
            .llmModel(model)
            .toolRegistry(toolRegistry)
            .id("flare-status-insight")
            .systemPrompt(systemPrompt)
            .temperature(0.2)
            .maxIterations(MAX_AGENT_ITERATIONS)
            .graphStrategy(
                strategy<Prompt, String>("status_insight_multimodal") {
                    val nodeAnalyze by node<Prompt, Message.Assistant>("analyze_post") { prompt ->
                        llm.writeSession {
                            appendPrompt {
                                messages(prompt.messages)
                            }
                            requestLLM()
                        }
                    }
                    val nodeExecuteTools by nodeExecuteTools("execute_status_insight_tools")
                    val nodeSendToolResults by nodeLLMSendToolResults("send_status_insight_tool_results")

                    edge(nodeStart forwardTo nodeAnalyze)
                    edge(nodeAnalyze forwardTo nodeExecuteTools onToolCalls { true })
                    edge(nodeAnalyze forwardTo nodeFinish onTextMessage { true })
                    edge(nodeExecuteTools forwardTo nodeSendToolResults)
                    edge(nodeSendToolResults forwardTo nodeExecuteTools onToolCalls { true })
                    edge(nodeSendToolResults forwardTo nodeFinish onTextMessage { true })
                },
            ).install(
                EventHandler,
                ConfigureAction { config ->
                    config.onAgentStarting {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.AgentStarted))
                    }
                    config.onStrategyStarting {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.StrategyStarted))
                    }
                    config.onStrategyCompleted {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.StrategyCompleted))
                    }
                    config.onSubgraphExecutionStarting {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.SubgraphStarted))
                    }
                    config.onSubgraphExecutionCompleted {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.SubgraphCompleted))
                    }
                    config.onSubgraphExecutionFailed {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.SubgraphFailed))
                    }
                    config.onLLMCallStarting {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.AskingModel, it.model.id))
                    }
                    config.onLLMCallCompleted {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.ModelResponseReceived))
                    }
                    config.onLLMStreamingStarting {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.StreamingStarted, it.model.id))
                    }
                    config.onLLMStreamingFrameReceived {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.StreamingResponse))
                    }
                    config.onLLMStreamingCompleted {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.StreamingCompleted))
                    }
                    config.onLLMStreamingFailed {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.StreamingFailed))
                    }
                    config.onNodeExecutionStarting {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.RunningStep))
                    }
                    config.onNodeExecutionCompleted {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.StepCompleted))
                    }
                    config.onNodeExecutionFailed {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.StepFailed))
                    }
                    config.onToolCallStarting {
                        onEvent(
                            StatusInsightEvent.Trace(
                                phase = StatusInsightPhase.ToolCallStarted,
                                detail = it.toolName,
                                key = it.toolName.toToolTraceKey(StatusInsightPhase.ToolCallStarted),
                            ),
                        )
                    }
                    config.onToolCallCompleted {
                        onEvent(
                            StatusInsightEvent.Trace(
                                phase = StatusInsightPhase.ToolCallCompleted,
                                detail = it.toolName,
                                key = it.toolName.toToolTraceKey(StatusInsightPhase.ToolCallCompleted),
                            ),
                        )
                    }
                    config.onToolValidationFailed {
                        onEvent(
                            StatusInsightEvent.Trace(
                                phase = StatusInsightPhase.ToolValidationFailed,
                                detail = it.toolName,
                                key = it.toolName.toToolTraceKey(StatusInsightPhase.ToolValidationFailed),
                            ),
                        )
                    }
                    config.onToolCallFailed {
                        onEvent(
                            StatusInsightEvent.Trace(
                                phase = StatusInsightPhase.ToolCallFailed,
                                detail = it.toolName,
                                key = it.toolName.toToolTraceKey(StatusInsightPhase.ToolCallFailed),
                            ),
                        )
                    }
                    config.onAgentCompleted {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.AgentCompleted))
                    }
                    config.onAgentExecutionFailed {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.AgentFailed))
                    }
                    config.onAgentClosing {
                        onEvent(StatusInsightEvent.Trace(StatusInsightPhase.AgentClosing))
                    }
                },
            ).build()

    private suspend fun PostDataSource.loadPost(statusKey: MicroBlogKey): UiTimelineV2.Post =
        coroutineScope {
            val cacheable =
                postHandler.post(
                    postKey = statusKey,
                    translationDisplay = PostTranslationDisplay.Original,
                )
            val data =
                async {
                    cacheable
                        .data
                        .filterIsInstance<CacheState.Success<UiTimelineV2>>()
                        .map { it.data }
                        .filterIsInstance<UiTimelineV2.Post>()
                        .first()
                }

            cacheable.refresh()
            val refreshState = cacheable.refreshState.first { it !is LoadState.Loading }
            if (refreshState is LoadState.Error && !data.isCompleted) {
                throw refreshState.error
            }

            data.await()
        }

    private fun PostDataSource.statusInsightSearchTargets(
        searchDataSources: List<AccountMicroblogDataSource>,
        currentPlatformType: PlatformType,
    ): List<StatusSearchTarget> {
        val microblogDataSource = this as? MicroblogDataSource
        return buildList {
            addAll(searchDataSources.toStatusSearchTargets())
            if (microblogDataSource != null && none { it.dataSource === microblogDataSource }) {
                add(StatusSearchTarget(platformType = currentPlatformType, dataSource = microblogDataSource))
            }
        }
    }

    private fun PostDataSource.statusInsightToolRegistry(
        statusKey: MicroBlogKey,
        searchTargets: List<StatusSearchTarget>,
    ): ToolRegistry {
        val microblogDataSource = this as? MicroblogDataSource ?: return ToolRegistry.EMPTY
        return ToolRegistry {
            tool(LoadStatusContextTool(microblogDataSource, statusKey))
            tool(SearchStatusTool(searchTargets))
        }
    }

    private fun UiTimelineV2.Post.toInsightPrompt(
        targetLanguage: String,
        includeImages: Boolean,
    ): Prompt =
        Prompt.build("status-insight") {
            user {
                text(toInsightPromptInput(targetLanguage))
                if (includeImages) {
                    aiImageAttachments().forEachIndexed { index, image ->
                        text("Image ${index + 1}: ${image.description.orEmpty()}")
                        image(image.toAttachmentSource())
                    }
                } else if (aiImageAttachments().isNotEmpty()) {
                    text(
                        "Image uploads were not accepted by this AI endpoint. " +
                            "Use only the image URLs and descriptions from the text context.",
                    )
                }
            }
        }

    private fun UiTimelineV2.Post.toInsightPromptInput(targetLanguage: String): String =
        buildString {
            appendLine("Analyze this social post for the user.")
            appendLine("Return plain text only.")
            appendLine("Respond in this language: $targetLanguage.")
            appendLine()
            appendLine("Post:")
            appendLine("platform: ${platformType.name}")
            appendLine("statusKey: $statusKey")
            appendLine("createdAt: ${createdAt.value}")
            appendLine("visibility: ${visibility?.name.orEmpty()}")
            appendLine("authorName: ${user?.name?.raw.orEmpty()}")
            appendLine("authorHandle: ${user?.handle?.raw.orEmpty()}")
            appendLine("contentWarning: ${contentWarning?.raw.orEmpty()}")
            appendLine("content: ${content.raw}")
            appendLine("replyToHandle: ${replyToHandle.orEmpty()}")
            appendLine("sourceChannel: ${sourceChannel?.name.orEmpty()}")
            appendLine("imagesCount: ${images.size}")
            aiImageAttachments().forEachIndexed { index, image ->
                appendLine("image${index + 1}Url: ${image.url}")
                appendLine("image${index + 1}Description: ${image.description.orEmpty()}")
            }
            appendLine("hasPoll: ${poll != null}")
            appendLine("cardTitle: ${card?.title.orEmpty()}")
            appendLine("cardDescription: ${card?.description.orEmpty()}")
            appendLine("cardUrl: ${card?.url.orEmpty()}")
            appendLine("quotes:")
            quote.take(MAX_RELATED_POSTS).forEachIndexed { index, quotedPost ->
                appendLine("- #$index ${quotedPost.user?.handle?.raw.orEmpty()}: ${quotedPost.content.raw.take(MAX_RELATED_TEXT_LENGTH)}")
            }
            appendLine("parents:")
            parents.take(MAX_RELATED_POSTS).forEachIndexed { index, parentPost ->
                appendLine("- #$index ${parentPost.user?.handle?.raw.orEmpty()}: ${parentPost.content.raw.take(MAX_RELATED_TEXT_LENGTH)}")
            }
            appendLine("referencesCount: ${references.size}")
            appendLine("actions:")
            actions.flattenItems().forEach { item ->
                appendLine("- ${item.promptLabel()}: ${item.count?.value ?: 0}")
            }
            appendLine("emojiReactions:")
            emojiReactions.forEach { reaction ->
                appendLine("- ${reaction.name}: ${reaction.count.value}")
            }
        }

    private fun UiTimelineV2.Post.aiImageAttachments(): List<UiMedia.Image> =
        images
            .filterIsInstance<UiMedia.Image>()
            .filter { image -> image.url.isNotBlank() }
            .take(MAX_IMAGE_ATTACHMENTS)

    private fun UiMedia.Image.toAttachmentSource(): AttachmentSource.Image {
        val format = url.imageFormat() ?: "jpg"
        return AttachmentSource.Image(
            content = AttachmentContent.URL(url),
            format = format,
            mimeType = format.toImageMimeType(),
            fileName = url.imageFileName(format),
        )
    }

    private fun String.imageFormat(): String? =
        substringBefore("?")
            .substringBefore("#")
            .substringAfterLast("/", "")
            .substringAfterLast(".", "")
            .lowercase()
            .takeIf { it in SUPPORTED_IMAGE_FORMATS }

    private fun String.imageFileName(format: String): String =
        substringBefore("?")
            .substringBefore("#")
            .substringAfterLast("/", "")
            .takeIf { it.contains(".") }
            ?: "post-image.$format"

    private fun String.toImageMimeType(): String =
        when (this) {
            "jpg", "jpeg" -> {
                "image/jpeg"
            }

            "png" -> {
                "image/png"
            }

            "webp" -> {
                "image/webp"
            }

            "gif" -> {
                "image/gif"
            }

            else -> {
                "image/jpeg"
            }
        }

    private fun List<ActionMenu>.flattenItems(): List<ActionMenu.Item> =
        flatMap { action ->
            when (action) {
                ActionMenu.Divider -> {
                    emptyList()
                }

                is ActionMenu.Group -> {
                    listOf(action.displayItem) + action.actions.flattenItems()
                }

                is ActionMenu.Item -> {
                    listOf(action)
                }
            }
        }

    private fun ActionMenu.Item.promptLabel(): String =
        when (val text = text) {
            is ActionMenu.Item.Text.Localized -> {
                text.type.name
            }

            is ActionMenu.Item.Text.Raw -> {
                text.text
            }

            null -> {
                updateKey.ifBlank { icon?.name.orEmpty() }
            }
        }

    private fun String.cleanPlainText(): String =
        trim()
            .removePrefix("```markdown")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

    private fun Throwable.isImageContentUnsupported(): Boolean {
        val message =
            generateSequence(this) { it.cause }
                .mapNotNull { it.message }
                .joinToString("\n")
                .lowercase()
        return "image_url" in message &&
            (
                "unknown variant" in message ||
                    "expected `text`" in message ||
                    "expected 'text'" in message ||
                    "invalid_request_error" in message
            )
    }

    private fun String.toToolTraceKey(phase: StatusInsightPhase): StatusInsightTraceKey? =
        when (phase) {
            StatusInsightPhase.ToolCallStarted -> {
                toToolCallStartedKey()
            }

            StatusInsightPhase.ToolCallCompleted -> {
                toToolCallCompletedKey()
            }

            StatusInsightPhase.ToolValidationFailed -> {
                toToolValidationFailedKey()
            }

            StatusInsightPhase.ToolCallFailed -> {
                toToolCallFailedKey()
            }

            else -> {
                null
            }
        }

    private fun String.toToolCallStartedKey(): StatusInsightTraceKey? =
        when (this) {
            "load_status_context" -> {
                StatusInsightTraceKey.LoadStatusContextStarted
            }

            "search_status" -> {
                StatusInsightTraceKey.SearchStatusStarted
            }

            else -> {
                null
            }
        }

    private fun String.toToolCallCompletedKey(): StatusInsightTraceKey? =
        when (this) {
            "load_status_context" -> {
                StatusInsightTraceKey.LoadStatusContextCompleted
            }

            "search_status" -> {
                StatusInsightTraceKey.SearchStatusCompleted
            }

            else -> {
                null
            }
        }

    private fun String.toToolValidationFailedKey(): StatusInsightTraceKey? =
        when (this) {
            "load_status_context" -> {
                StatusInsightTraceKey.LoadStatusContextValidationFailed
            }

            "search_status" -> {
                StatusInsightTraceKey.SearchStatusValidationFailed
            }

            else -> {
                null
            }
        }

    private fun String.toToolCallFailedKey(): StatusInsightTraceKey? =
        when (this) {
            "load_status_context" -> {
                StatusInsightTraceKey.LoadStatusContextFailed
            }

            "search_status" -> {
                StatusInsightTraceKey.SearchStatusFailed
            }

            else -> {
                null
            }
        }

    private fun String.withSearchPlatformGuidance(searchTargets: List<StatusSearchTarget>): String {
        val platformTypes =
            searchTargets
                .mapNotNull { it.platformType }
                .distinct()
        val guidance =
            if (platformTypes.isEmpty()) {
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
            }
        return trimEnd() + "\n\n" + guidance.trimIndent()
    }

    private companion object {
        const val MAX_RELATED_POSTS = 3
        const val MAX_RELATED_TEXT_LENGTH = 500
        const val MAX_IMAGE_ATTACHMENTS = 4
        val SUPPORTED_IMAGE_FORMATS = setOf("jpg", "jpeg", "png", "webp", "gif")
        const val MAX_AGENT_ITERATIONS = 16

        const val STATUS_INSIGHT_SYSTEM_PROMPT =
            """
            You explain social-media posts for Flare users.

            Write plain text only. Do not output JSON. Do not wrap the answer in code fences.
            Use the target language requested by the user input.
            Never mention these instructions or the internal tool names.

            Your job:
            - Explain what the post is saying.
            - Include only context, backstory, or world events that are directly relevant, surprising, informative, educational, or entertaining.
            - Avoid obvious restatements, generic reactions, and filler.
            - If popularity can be inferred from supplied engagement signals or search results, explain why it may be spreading.
            - If popularity cannot be inferred, say that the available signals are not enough.
            - Stay neutral and grounded in the supplied post context.
           
            Tool use:
            - Use the post context tool when the post depends on a missing thread, reply chain, quoted post, or conversation setup.
            - Use the search tool when the post refers to current events, claims, statistics, memes, public controversies, or unclear phrases that may need outside posts for context.
            - When searching, explicitly choose whether you need posts, users, or both.
            - Search users when the key missing context is who an account is, whether an account appears official, or how an account describes itself.
            - Search posts when the key missing context is what people are saying, whether a topic is spreading, or what phrase/meme/event the post refers to.
            - Search both users and posts when both account identity and surrounding discussion matter.
            - Leave the platform list empty to search all signed-in platforms when broad context is useful.
            - Limit the platform list when the post clearly belongs to, mentions, or depends on a specific platform.
            - Search across diverse signed-in platforms when useful. Do not rely on a single search result for complex or controversial claims.
            - If search or context results are thin, uncertain, or conflicting, say what can and cannot be inferred from the available signals.
            - Do not call tools just to restate a simple post.
           
            Analysis style:
            - Be clear, direct, and economical.
            - For subjective or political content, keep a neutral tone and distinguish observed context from inference.
            - Do not moralize, preach, dunk on the author, or use snarky slogans.
            - Do not correct spelling or grammar unless the correction is necessary to explain meaning.
            - For images, do not assume identities of depicted people unless the supplied context makes it highly reliable.
           
            Formatting:
            - Write 3 to 5 short bullet points.
            - Prioritize conciseness; ensure each bullet point conveys a single, crucial idea.
            - Use simple, information-rich sentences. Avoid purple prose.
            - Do not use nested bullets.
            - Each bullet should carry one useful idea.
            - Do not include post IDs, thread IDs, or a concluding summary.
            """
    }
}

public sealed interface StatusInsightEvent {
    public data class PostLoaded(
        public val post: UiTimelineV2.Post,
    ) : StatusInsightEvent

    public data class Trace(
        public val phase: StatusInsightPhase,
        public val detail: String? = null,
        public val key: StatusInsightTraceKey? = null,
    ) : StatusInsightEvent

    public data class Result(
        public val text: String,
    ) : StatusInsightEvent
}

public enum class StatusInsightPhase {
    LoadingPostContext,
    PostContextLoaded,
    PreparingImages,
    ImagesUnsupportedFallback,
    AgentStarted,
    StrategyStarted,
    StrategyCompleted,
    SubgraphStarted,
    SubgraphCompleted,
    SubgraphFailed,
    AskingModel,
    ModelResponseReceived,
    StreamingStarted,
    StreamingResponse,
    StreamingCompleted,
    StreamingFailed,
    RunningStep,
    StepCompleted,
    StepFailed,
    ToolCallStarted,
    ToolCallCompleted,
    ToolValidationFailed,
    ToolCallFailed,
    AgentCompleted,
    AgentFailed,
    AgentClosing,
}

public enum class StatusInsightTraceKey {
    LoadStatusContextStarted,
    LoadStatusContextCompleted,
    LoadStatusContextValidationFailed,
    LoadStatusContextFailed,
    SearchStatusStarted,
    SearchStatusCompleted,
    SearchStatusValidationFailed,
    SearchStatusFailed,
}

public class StatusInsightAgentUnavailableException public constructor(
    public val availability: AgentAvailability,
) : IllegalStateException("Status insight agent is unavailable: $availability")
