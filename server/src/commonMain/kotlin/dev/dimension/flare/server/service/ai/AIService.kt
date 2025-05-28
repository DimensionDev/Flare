package dev.dimension.flare.server.service.ai

import dev.dimension.flare.server.common.Log
import io.ktor.server.config.ApplicationConfig

internal interface AIService {
    suspend fun generate(prompt: String): String

    companion object {
        fun create(config: ApplicationConfig): AIService {
            val type = config.propertyOrNull("ai.type")?.getString()
                ?: throw IllegalArgumentException("AI service type not configured")
            Log.trace("AIService", "Creating AI service instance with type: $type")
            return when (type) {
                "openai" -> OpenAIAIService(config)
                "ollama" -> LocalOllamaAIService(config)
                else -> throw IllegalArgumentException("Unknown AI service type")
            }
        }
    }
}
