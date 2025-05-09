package dev.dimension.flare.server.service

import dev.dimension.flare.server.common.decodeJson
import dev.dimension.flare.server.service.ai.AIService
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class TranslatorService(
    private val aiService: AIService,
) {
    suspend fun translate(text: String, targetLang: String): Response {
        val prompt = """
            You are a translation assistant. Your task is to translate text from one language to another.
            Translate the following text to $targetLang:
            "$text"
            Make sure to keep the meaning and context of the original text intact.
            Respond raw text in this format:
            {
                "translation": "<translated_text>",
                "detected_language": "<detected_language>"
            }
        """.trimIndent()
        val result = aiService.generate(prompt).trim()
            .let {
                if (it.startsWith("```json")) {
                    // incase the response is in code block format
                    it.removePrefix("```json")
                        .removeSuffix("```")
                        .trim()
                } else {
                    it
                }
            }
        return result.decodeJson<Response>()
    }

    @Serializable
    data class Response(
        val translation: String,
        @SerialName("detected_language")
        val detectedLanguage: String,
    )
}