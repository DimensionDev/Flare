package dev.dimension.flare.server.service.ai

import io.ktor.server.config.ApplicationConfig

internal interface AIService {
    suspend fun generate(prompt: String): String

    companion object {
        fun create(config: ApplicationConfig): AIService {
            return when (config.property("ai.type").getString()) {
                "openai" -> OpenAIAIService(config)
                "ollama" -> LocalOllamaAIService(config)
                else -> throw IllegalArgumentException("Unknown AI service type")
            }
        }
    }
}
