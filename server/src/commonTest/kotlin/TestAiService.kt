package dev.dimension.flare

import dev.dimension.flare.server.service.ai.AIService

class TestAiService(
    private val response: String,
) : AIService {
    override suspend fun generate(prompt: String): String {
        return response
    }
}