package dev.dimension.flare.data.ai

import dev.dimension.flare.data.datastore.model.AppSettings

public class AiCompletionService(
    private val openAIService: OpenAIService,
    private val onDeviceAI: OnDeviceAI,
) {
    public suspend fun translate(
        config: AppSettings.AiConfig,
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        complete(config.type, prompt) {
            onDeviceAI.translate(source, targetLanguage, prompt)
        }

    public suspend fun tldr(
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
            AppSettings.AiConfig.Type.OnDevice -> {
                if (onDeviceAI.isAvailable()) {
                    onDeviceCall()
                } else {
                    null
                }
            }

            is AppSettings.AiConfig.Type.OpenAI -> {
                openAIService.chatCompletionOrNull(
                    config = type,
                    prompt = prompt,
                )
            }
        }
}
