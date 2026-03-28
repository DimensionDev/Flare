package dev.dimension.flare.data.network.ai

import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.data.datastore.model.AppSettings

internal class AiCompletionService(
    private val openAIService: OpenAIService,
    private val onDeviceAI: OnDeviceAI,
) {
    suspend fun translate(
        config: AppSettings.AiConfig,
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        complete(config.type, prompt) {
            onDeviceAI.translate(source, targetLanguage, prompt)
        }

    suspend fun tldr(
        config: AppSettings.AiConfig,
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        complete(config.type, prompt) {
            onDeviceAI.tldr(source, targetLanguage, prompt)
        }

    private suspend fun complete(
        type: AppSettings.AiConfig.Type,
        prompt: String,
        onDeviceCall: suspend () -> String?,
    ): String? =
        when (type) {
            AppSettings.AiConfig.Type.OnDevice ->
                if (onDeviceAI.isAvailable()) {
                    onDeviceCall()
                } else {
                    null
                }

            is AppSettings.AiConfig.Type.OpenAI ->
                openAIService.chatCompletionOrNull(
                    config = type,
                    prompt = prompt,
                )
        }
}
