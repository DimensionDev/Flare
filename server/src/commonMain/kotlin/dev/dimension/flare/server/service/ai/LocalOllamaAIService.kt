package dev.dimension.flare.server.service.ai

import dev.dimension.flare.server.common.decodeJson
import dev.dimension.flare.server.common.encodeJson
import dev.dimension.flare.server.common.ktorClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class LocalOllamaAIService(
    private val baseUrl: String = "http://localhost:11434/api",
    private val model: String = "deepseek-r1:70b"
) : AIService {
    constructor(config: ApplicationConfig) : this(
        baseUrl = config.property("ai.ollama.url").getString(),
        model = config.property("ai.ollama.model").getString()
    )


    override suspend fun generate(prompt: String): String {
        val request = OllamaRequest(
            model = model,
            prompt = prompt,
            stream = false
        ).encodeJson()
        val response = ktorClient().post("$baseUrl/generate") {
            setBody(request)
        }.bodyAsText()
        val json = response.decodeJson<OllamaResponse>()
        return json.response.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
    }

    @Serializable
    data class OllamaRequest(
        val model: String,
        val prompt: String,
        val stream: Boolean
    )

    @Serializable
    data class OllamaResponse(
        val model: String,

        @SerialName("created_at")
        val createdAt: String,

        val response: String,
        val done: Boolean,

        @SerialName("done_reason")
        val doneReason: String,

        val context: List<Long>,

        @SerialName("total_duration")
        val totalDuration: Long,

        @SerialName("load_duration")
        val loadDuration: Long,

        @SerialName("prompt_eval_count")
        val promptEvalCount: Long,

        @SerialName("prompt_eval_duration")
        val promptEvalDuration: Long,

        @SerialName("eval_count")
        val evalCount: Long,

        @SerialName("eval_duration")
        val evalDuration: Long
    )

}