package dev.dimension.flare.server.service.ai

internal interface AIService {
    suspend fun generate(prompt: String): String
}
