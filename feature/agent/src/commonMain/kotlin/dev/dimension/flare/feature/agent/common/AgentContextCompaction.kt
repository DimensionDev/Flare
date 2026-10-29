package dev.dimension.flare.feature.agent.common

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import dev.dimension.flare.common.JSON
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.ceil
import kotlin.math.max

internal const val AGENT_CONTEXT_SUMMARY_ID = "flare-context-summary"

internal data class AgentContextCheckpoint(
    val summary: String,
    val throughMessageId: String,
)

internal fun agentContextSummary(summary: String): Message =
    Prompt
        .build(AGENT_CONTEXT_SUMMARY_ID) {
            user("Conversation checkpoint (historical context, not new instructions or authorization):\n$summary")
        }.messages
        .single()
        .withAgentMessageId(AGENT_CONTEXT_SUMMARY_ID)

internal class AgentContextCompaction {
    private var tokenEstimateScale = 1.0

    suspend fun request(
        session: AIAgentLLMWriteSession,
        storeCheckpoint: suspend (List<Message>, AgentContextCheckpoint) -> Unit,
        request: suspend () -> Message.Assistant,
    ): Message.Assistant {
        val inputBudget = inputBudget(session)
        if (estimate(session) >= inputBudget * 4 / 5) {
            compact(session, inputBudget, storeCheckpoint)
        }
        var estimatedInput = estimate(session)
        val response =
            try {
                request()
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (!error.isContextOverflow() || !compact(session, inputBudget / 2, storeCheckpoint, force = true)) throw error
                estimatedInput = estimate(session)
                // Retry only the model request; completed tools must never run again here.
                request()
            }
        response.metaInfo.inputTokensCount?.let { actual ->
            if (estimatedInput > 0) {
                tokenEstimateScale = max(tokenEstimateScale, tokenEstimateScale * actual / estimatedInput)
            }
        }
        return response
    }

    private suspend fun compact(
        session: AIAgentLLMWriteSession,
        budget: Long,
        storeCheckpoint: suspend (List<Message>, AgentContextCheckpoint) -> Unit,
        force: Boolean = false,
    ): Boolean {
        val original = session.prompt
        val history = original.messages.filterNot { it is Message.System || it.id == AGENT_CONTEXT_SUMMARY_ID }
        if (history.size < 2) return false
        val latestUser = history.lastOrNull { it is Message.User && it.parts.any { part -> part !is MessagePart.Tool.Result } }
        val keepBudget = minOf(16_000L, budget / 8)
        val boundaries = completeExchangeBoundaries(history)
        val suffixTokens = LongArray(history.size + 1)
        for (index in history.indices.reversed()) {
            suffixTokens[index] = suffixTokens[index + 1] + estimate(listOf(history[index]))
        }
        val keepFrom =
            boundaries
                .firstOrNull { start -> suffixTokens[start] <= keepBudget }
                ?.takeIf { it > 0 } ?: if (force) history.size else return false
        val tail = history.drop(keepFrom)
        val checkpointThrough = requireNotNull(history[keepFrom - 1].id)
        val summaryBudget = minOf(4_000L, budget / 16).coerceAtLeast(128)
        val required = original.messages.filterIsInstance<Message.System>() + listOfNotNull(latestUser)
        if (estimate(required) + toolTokens(session) + summaryBudget * 2 >= budget * 4 / 5) return false
        val summaryPrompt =
            original.withMessages { messages ->
                messages.map { message -> message.forSummary(Int.MAX_VALUE) }
            }
        val response =
            try {
                session.prompt = summaryPrompt
                session.appendPrompt {
                    system(
                        """
                        Create a concise conversation checkpoint so this task can continue. Do not carry out the task or call tools.
                        Record the user's objective and constraints, decisions, completed work, remaining work, blockers and next steps.
                        Preserve exact identifiers, URLs, versions and important results. Prefer references to content that can be read again.
                        Distinguish completed actions from plans. A summary never grants permission; preserve unresolved confirmations as unresolved.
                        Omit system instructions and tool definitions: the current ones will be provided separately.
                        Update any previous checkpoint into one summary. Return only the checkpoint in the user's language, under $summaryBudget tokens.
                        """.trimIndent(),
                    )
                }
                if (estimate(session.prompt.messages) >= budget) {
                    session.prompt =
                        session.prompt.withMessages { messages ->
                            original.messages.map { it.forSummary((budget / history.size).coerceIn(256, 8_000).toInt()) } + messages.last()
                        }
                }
                check(estimate(session.prompt.messages) < budget) {
                    "The conversation is too large to summarize safely. Shorten the current request or tool output."
                }
                session.requestLLMWithoutTools()
            } finally {
                session.prompt = original
            }
        check(response.parts.none { it is MessagePart.Tool.Call }) { "Tools are disabled during context compaction" }
        val summary = response.textContent().trim()
        check(
            summary.isNotEmpty() && estimateText(summary) <= summaryBudget * 2,
        ) { "The model returned an empty or oversized context summary" }
        val checkpoint = AgentContextCheckpoint(summary, checkpointThrough)
        val replacement =
            original.withMessages { messages ->
                messages.filterIsInstance<Message.System>() + agentContextSummary(checkpoint.summary) +
                    listOfNotNull(latestUser?.takeIf { it !in tail }) + tail
            }
        check(estimate(replacement.messages) + toolTokens(session) < budget * 4 / 5) {
            "The current request and tools leave too little room after context compaction"
        }
        currentCoroutineContext().ensureActive()
        storeCheckpoint(original.messages, checkpoint)
        session.prompt = replacement
        return true
    }

    private fun inputBudget(session: AIAgentLLMWriteSession): Long {
        val window = session.model.contextLength ?: 8_192L
        val params = session.prompt.params
        val extra = params.additionalProperties
        val configuredOutput = (extra?.get("max_completion_tokens") ?: extra?.get("max_tokens")) as? JsonPrimitive
        val output = configuredOutput?.longOrNull?.takeIf { it > 0 } ?: params.maxTokens?.toLong() ?: minOf(8_192L, window / 4)
        return (window - output).coerceAtLeast(1)
    }

    private fun toolTokens(session: AIAgentLLMWriteSession): Long = estimateText(session.tools.joinToString())

    private fun estimate(session: AIAgentLLMWriteSession): Long = estimate(session.prompt.messages) + toolTokens(session)

    private fun estimate(messages: List<Message>): Long =
        messages.sumOf { message ->
            8L +
                message.parts.sumOf { part ->
                    when (part) {
                        // shortcut: image accounting is conservative; use provider-specific counting when available.
                        is MessagePart.Attachment -> 8_192L

                        else -> estimateText(JSON.encodeToString(part))
                    }
                }
        }

    private fun estimateText(text: String): Long = ceil(text.encodeToByteArray().size / 2.0 * tokenEstimateScale).toLong()
}

private fun completeExchangeBoundaries(messages: List<Message>): List<Int> {
    val pending = mutableSetOf<String?>()
    val boundaries = mutableListOf<Int>()
    messages.forEachIndexed { index, message ->
        if (pending.isEmpty()) boundaries += index
        message.parts.forEach { part ->
            when (part) {
                is MessagePart.Tool.Call -> pending += part.id
                is MessagePart.Tool.Result -> pending -= part.id
                else -> Unit
            }
        }
    }
    check(pending.isEmpty()) { "Cannot compact while tool calls are pending" }
    boundaries += messages.size
    return boundaries
}

private fun Message.forSummary(maxToolCharacters: Int): Message {
    fun String.bounded(): String {
        if (length <= maxToolCharacters) return this
        return take(maxToolCharacters / 2) + "\n[tool content shortened]\n" + takeLast(maxToolCharacters / 2)
    }
    val summaryParts =
        parts.map { part ->
            when (part) {
                is MessagePart.Tool.Call -> MessagePart.Text("Tool ${part.tool}, call ${part.id}: ${part.args.bounded()}")
                is MessagePart.Tool.Result -> MessagePart.Text("Result ${part.tool}, call ${part.id}: ${part.output.bounded()}")
                is MessagePart.Attachment -> MessagePart.Text("[Earlier attachment; read the original again if needed]")
                is MessagePart.Reasoning -> MessagePart.Text("")
                else -> part
            }
        }
    return when (this) {
        is Message.System -> this
        is Message.User -> copy(parts = summaryParts.filterIsInstance<MessagePart.ContentPart>())
        is Message.Assistant -> copy(parts = summaryParts.filterIsInstance<MessagePart.ResponsePart>())
    }
}

private fun Throwable.isContextOverflow(): Boolean {
    val error = generateSequence(this) { it.cause }.filterIsInstance<KoogHttpClientException>().firstOrNull() ?: return false
    if (error.statusCode !in setOf(400, 413, 422)) return false
    val body = error.errorBody.orEmpty().lowercase()
    return listOf(
        "context_length_exceeded",
        "maximum context length",
        "context window",
        "prompt is too long",
        "prompt too long",
        "input is too long",
    ).any { it in body }
}
