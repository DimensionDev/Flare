package dev.dimension.flare.feature.agent.runtime

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorAPI
import ai.koog.prompt.executor.model.ResolvedModel
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import dev.dimension.flare.data.network.ai.AiImageFailure
import dev.dimension.flare.data.network.ai.AiImageFallback
import dev.dimension.flare.data.network.ai.AiImagePreparer
import dev.dimension.flare.data.network.ai.classifyAiImageFailure
import dev.dimension.flare.feature.agent.common.AgentPhase
import dev.dimension.flare.feature.agent.common.AgentTrace
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.coroutines.withTimeoutOrNull

internal expect val supportsAgentImageFallback: Boolean

internal class AgentImageFallback(
    private val delegate: PromptExecutor,
    private val images: List<UiMedia.Image>,
    private val prepareImage: (suspend (UiMedia.Image, Int) -> AttachmentSource.Image)?,
    private val onTrace: suspend (AgentTrace) -> Unit,
) : PromptExecutor(),
    PromptExecutorAPI by delegate {
    private val fallback = AiImageFallback(allowTextFallback = true)
    private var textFallbackReported = false
    private val prepared = mutableMapOf<String, AttachmentSource.Image>()

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant = executeWithFallback(prompt) { delegate.execute(it, model, tools) }

    override suspend fun execute(
        prompt: Prompt,
        model: ResolvedModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant = executeWithFallback(prompt) { delegate.execute(it, model, tools) }

    // shortcut: the graph uses execute; add recovery before enabling structured or streaming calls.
    private suspend fun executeWithFallback(
        prompt: Prompt,
        execute: suspend (Prompt) -> Message.Assistant,
    ): Message.Assistant {
        if (prompt.imageAttachments().isEmpty()) return execute(prompt)
        return fallback.execute(Exception::imageFailure) { mode ->
            when (mode) {
                AiImageFallback.Mode.Url -> {
                    execute(prompt)
                }

                AiImageFallback.Mode.Inline -> {
                    onTrace(AgentTrace(AgentPhase.PreparingImages))
                    val inline =
                        withTimeoutOrNull(60_000) { prompt.withInlineImages() }
                            ?: error("Image preparation timed out")
                    execute(inline)
                }

                AiImageFallback.Mode.Text -> {
                    prepared.clear()
                    if (!textFallbackReported) {
                        textFallbackReported = true
                        onTrace(AgentTrace(AgentPhase.ImagesUnsupportedFallback))
                    }
                    execute(prompt.withoutImages())
                }
            }
        }
    }

    private suspend fun Prompt.withInlineImages(): Prompt {
        val prepareImage = checkNotNull(prepareImage) { "Image preparation is unavailable on this platform" }
        val attachments = imageAttachments()
        val maxBytes = minOf(MAX_IMAGE_BYTES, MAX_TOTAL_IMAGE_BYTES / attachments.size)
        val mediaByAttachment =
            attachments.associateWith { attachment ->
                val url = (attachment.content as? AttachmentContent.URL)?.url ?: error("Missing original image URL")
                // Historical URLs without their media context must not borrow another account's credentials or route.
                images.filter { it.url == url }.distinctBy { it.cacheKey to it.proxy }.singleOrNull()
                    ?: error("Missing image account context")
            }
        prepared.keys.retainAll(mediaByAttachment.values.map { it.preparedKey() }.toSet())
        val replacements = mutableMapOf<AttachmentSource.Image, AttachmentSource.Image>()
        for ((attachment, media) in mediaByAttachment) {
            val key = media.preparedKey()
            val cached = prepared[key]
            val image =
                if (cached != null && cached.byteSize() <= maxBytes) {
                    cached
                } else {
                    prepareImage(media, maxBytes).also {
                        check(it.byteSize() in 1..maxBytes) { "Prepared image exceeds request budget" }
                        prepared[key] = it
                    }
                }
            replacements[attachment] = image
        }
        return mapImages { it.copy(source = replacements.getValue(it.source as AttachmentSource.Image)) }
    }

    private fun Prompt.withoutImages(): Prompt = mapImages { MessagePart.Text(TEXT_ONLY_NOTICE) }

    private fun Prompt.imageAttachments(): List<AttachmentSource.Image> {
        val images = mutableListOf<AttachmentSource.Image>()
        mapImages {
            images += it.source as AttachmentSource.Image
            it
        }
        return images
    }

    private fun Prompt.mapImages(transform: (MessagePart.Attachment) -> MessagePart.ContentPart): Prompt {
        fun content(part: MessagePart.ContentPart): MessagePart.ContentPart =
            if (part is MessagePart.Attachment && part.source is AttachmentSource.Image) transform(part) else part
        return withMessages { messages ->
            messages.map { message ->
                when (message) {
                    is Message.System -> {
                        message
                    }

                    is Message.User -> {
                        message.copy(
                            parts =
                                message.parts.map { part ->
                                    when (part) {
                                        is MessagePart.ContentPart -> content(part)
                                        is MessagePart.Tool.Result -> part.copy(parts = part.parts.map(::content))
                                    }
                                },
                        )
                    }

                    is Message.Assistant -> {
                        message.copy(
                            parts =
                                message.parts.map { part ->
                                    if (part is MessagePart.ContentPart) content(part) else part
                                },
                        )
                    }
                }
            }
        }
    }

    override fun close() {
        prepared.clear()
        delegate.close()
    }

    private companion object {
        const val MAX_IMAGE_BYTES = AiImagePreparer.MAX_IMAGE_BYTES
        const val MAX_TOTAL_IMAGE_BYTES = 16 * 1024 * 1024
        const val TEXT_ONLY_NOTICE =
            "[Images could not be read. Analyze only the supplied text and image descriptions. " +
                "Do not infer visual details or claim to have seen these images. Briefly tell the user that images are unavailable, in their language.]"
    }
}

private fun UiMedia.Image.preparedKey(): String = "$proxy|$cacheKey"

private fun AttachmentSource.Image.byteSize(): Int =
    (content as? AttachmentContent.Binary)?.asBytes()?.size ?: error("Expected inline image bytes")

private fun Exception.imageFailure(): AiImageFailure? {
    val error = generateSequence<Throwable>(this) { it.cause }.filterIsInstance<KoogHttpClientException>().firstOrNull() ?: return null
    return classifyAiImageFailure(error.statusCode, error.errorBody.orEmpty())
}
