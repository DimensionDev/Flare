package dev.dimension.flare.server.service

import dev.dimension.flare.server.service.ai.AIService
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class TLDRService(
    private val aiService: AIService,
) {
    suspend fun summarize(text: String, targetLanguage: String): String {
        val prompt = """
            Summarize the following text in $targetLanguage
            Respond in raw text.
            Text: "$text"
        """.trimIndent()
        return aiService.generate(prompt).trim()
    }
}