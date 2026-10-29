package dev.dimension.flare.feature.agent.common

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import dev.dimension.flare.common.JSON
import kotlinx.coroutines.CancellationException
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
        var estimatedInput = estimate(session)
        val response =
            try {
                if (estimatedInput >= inputBudget * 4 / 5) {
                    compact(session, inputBudget, storeCheckpoint) { estimatedInput = it }
                }
                estimatedInput = estimate(session)
                request()
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (error is CancellationException || !error.isContextOverflow()) throw error
                val original = session.prompt
                val recovery = recoverContext(session, inputBudget, rejectedInput = estimatedInput) ?: throw error
                // The projection is request-only: never persist shortened messages under their original IDs.
                val recovered =
                    try {
                        session.prompt = recovery
                        estimatedInput = estimate(session)
                        request()
                    } finally {
                        session.prompt = original
                    }
                session.appendPrompt { message(recovered) }
                recovered
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
        onSummaryRequest: (Long) -> Unit,
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
                ?.takeIf { it > 0 } ?: return false
        val tail = history.drop(keepFrom)
        val checkpointThrough = requireNotNull(history[keepFrom - 1].id)
        val summaryBudget = minOf(4_000L, budget / 16).coerceAtLeast(128)
        val required = original.messages.filterIsInstance<Message.System>() + listOfNotNull(latestUser)
        if (estimate(required) + toolTokens(session) + summaryBudget * 2 >= budget * 4 / 5) return false
        val summaryOutputBudget = (summaryBudget * 2).toInt()
        val summaryInputBudget = (session.model.contextLength ?: 8_192L) - summaryOutputBudget
        val summaryPrompt =
            original.withMessages { messages ->
                messages.map { message -> message.forSummary(Int.MAX_VALUE) }
            }
        val response =
            try {
                session.prompt =
                    summaryPrompt.withParams(
                        original.params.copy(
                            maxTokens = summaryOutputBudget,
                            schema = null,
                            additionalProperties =
                                original.params.additionalProperties?.filterKeys {
                                    it !in setOf("max_tokens", "max_completion_tokens", "response_format")
                                },
                        ),
                    )
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
                if (estimate(session.prompt.messages) >= summaryInputBudget) {
                    session.prompt =
                        session.prompt.withMessages { messages ->
                            original.messages.map { it.forSummary((budget / history.size).coerceIn(256, 8_000).toInt()) } + messages.last()
                        }
                }
                // A fallback window is a trigger estimate, not evidence that the provider will reject the original request.
                if (estimate(session.prompt.messages) >= summaryInputBudget) return false
                onSummaryRequest(estimate(session.prompt.messages))
                session.requestLLMWithoutTools()
            } finally {
                session.prompt = original
            }
        check(response.finishReason == null || response.finishReason?.lowercase() in setOf("stop", "end_turn")) {
            "The model did not finish the context summary"
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
        if (estimate(replacement.messages) + toolTokens(session) >= budget * 4 / 5) return false
        currentCoroutineContext().ensureActive()
        storeCheckpoint(original.messages, checkpoint)
        session.prompt = replacement
        return true
    }

    private fun recoverContext(
        session: AIAgentLLMWriteSession,
        inputBudget: Long,
        rejectedInput: Long,
    ): Prompt? {
        val original = session.prompt
        val messages = original.messages
        val before = estimate(session)
        val target = minOf(before / 2, rejectedInput / 2, if (session.model.contextLength == null) Long.MAX_VALUE else inputBudget * 4 / 5)
        val boundaries = completeExchangeBoundaries(messages)
        val latestUser = messages.indexOfLast { it is Message.User && it.parts.any { part -> part !is MessagePart.Tool.Result } }
        val latestCall = messages.indexOfLast { it.parts.any { part -> part is MessagePart.Tool.Call } }
        val latestBatchEnd = boundaries.firstOrNull { it > latestCall } ?: messages.size
        val required =
            messages.mapIndexed { index, message ->
                message is Message.System || message.id == AGENT_CONTEXT_SUMMARY_ID || index == latestUser ||
                    (latestCall >= 0 && index in latestCall until latestBatchEnd)
            }
        val projected =
            messages
                .mapIndexed { index, message ->
                    if (required[index]) message else message.trimToolResults(2_000)
                }.toMutableList()
        val retained = BooleanArray(messages.size) { true }
        val costs = projected.map { estimate(listOf(it)) }.toMutableList()
        val notice =
            Prompt
                .build("context-recovery") {
                    system(
                        "Some earlier context was omitted or shortened to fit this request. The original history is retained; no new authorization is implied.",
                    )
                }.messages
        var size = costs.sum() + toolTokens(session) + estimate(notice)
        for ((start, end) in boundaries.zipWithNext()) {
            if (size <= target) break
            if ((start until end).any { required[it] }) continue
            for (index in start until end) {
                retained[index] = false
                size -= costs[index]
            }
        }
        // Keep the latest call/result batch, shortening only result text if that batch itself exceeds the budget.
        for (limit in listOf(2_000, 512, 128)) {
            if (size <= target) break
            for (index in messages.indices.filter { retained[it] }) {
                val shortened = messages[index].trimToolResults(limit)
                projected[index] = shortened
                val cost = estimate(listOf(shortened))
                size += cost - costs[index]
                costs[index] = cost
            }
        }
        if (size > target || size >= before) return null
        return original.withMessages { projected.filterIndexed { index, _ -> retained[index] } + notice }
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

    // shortcut: estimate bytes and calibrate with reported usage until providers expose portable token counting.
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

private fun Message.forSummary(limit: Int): Message {
    val summaryParts =
        parts.map { part ->
            when (part) {
                is MessagePart.Tool.Call -> {
                    MessagePart.Text("Tool ${part.tool}, call ${part.id}: ${part.args.boundedToolContent(limit)}")
                }

                is MessagePart.Tool.Result -> {
                    MessagePart.Text(
                        "Result ${part.tool}, call ${part.id}: ${part.output.boundedToolContent(limit)}",
                    )
                }

                is MessagePart.Attachment -> {
                    MessagePart.Text("[Earlier attachment; read the original again if needed]")
                }

                is MessagePart.Reasoning -> {
                    MessagePart.Text("")
                }

                else -> {
                    part
                }
            }
        }
    return when (this) {
        is Message.System -> this
        is Message.User -> copy(parts = summaryParts.filterIsInstance<MessagePart.ContentPart>())
        is Message.Assistant -> copy(parts = summaryParts.filterIsInstance<MessagePart.ResponsePart>())
    }
}

private fun Message.trimToolResults(limit: Int): Message =
    if (this is Message.User) {
        copy(
            parts =
                parts.map { part ->
                    if (part is MessagePart.Tool.Result) {
                        part.copy(
                            parts =
                                part.parts.map {
                                    if (it is MessagePart.Text) {
                                        it.copy(text = it.text.boundedToolContent(limit))
                                    } else {
                                        it
                                    }
                                },
                        )
                    } else {
                        part
                    }
                },
        )
    } else {
        this
    }

private fun String.boundedToolContent(limit: Int): String =
    if (length <= limit) this else take(limit / 2) + "\n[tool content shortened]\n" + takeLast(limit / 2)

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
