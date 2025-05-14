package dev.dimension.flare.server.service

import dev.dimension.flare.server.service.ai.AIService

internal class TranslatorService(
    private val aiService: AIService,
) {
    suspend fun translate(text: String, targetLang: String): String {
        val prompt = """
            You are a translation assistant. Your task is to translate text from one language to another.
            Make sure to keep the meaning and context of the original text intact.
            Respond in raw text
            Translate the following text to $targetLang:
            "$text"
        """.trimIndent()
        return aiService.generate(prompt).trim()
    }
}