package dev.dimension.flare.data.translation

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.TranslationTokenKind
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal suspend fun AppSettings.translateDocumentJson(
    aiCompletionService: AiCompletionService,
    sourceText: String,
    sourceJson: String,
    targetLanguage: String,
    prompt: String,
): String? =
    when (translateConfig.provider) {
        AppSettings.TranslateConfig.Provider.AI ->
            aiCompletionService.translate(
                config = aiConfig,
                source = sourceText,
                targetLanguage = targetLanguage,
                prompt = prompt,
            )

        AppSettings.TranslateConfig.Provider.Google ->
            GoogleWebTranslationProvider.translateDocumentJson(
                sourceJson = sourceJson,
                targetLanguage = targetLanguage,
            )
    }

internal suspend fun AppSettings.translateBatchDocumentJson(
    aiCompletionService: AiCompletionService,
    sourceJson: String,
    sourceDocument: PreTranslationBatchDocument,
    targetLanguage: String,
    prompt: String,
): String? =
    when (translateConfig.provider) {
        AppSettings.TranslateConfig.Provider.AI ->
            aiCompletionService.translate(
                config = aiConfig,
                source = sourceJson,
                targetLanguage = targetLanguage,
                prompt = prompt,
            )

        AppSettings.TranslateConfig.Provider.Google ->
            GoogleWebTranslationProvider.translateBatchDocumentJson(
                sourceDocument = sourceDocument,
                targetLanguage = targetLanguage,
            )
    }

private object GoogleWebTranslationProvider {
    suspend fun translateDocumentJson(
        sourceJson: String,
        targetLanguage: String,
    ): String =
        sourceJson
            .decodeJson(TranslationDocument.serializer())
            .translate(targetLanguage)
            .encodeJson(TranslationDocument.serializer())

    suspend fun translateBatchDocumentJson(
        sourceDocument: PreTranslationBatchDocument,
        targetLanguage: String,
    ): String =
        sourceDocument
            .translate(targetLanguage)
            .encodeJson(PreTranslationBatchDocument.serializer())

    private suspend fun PreTranslationBatchDocument.translate(targetLanguage: String): PreTranslationBatchDocument =
        copy(
            targetLanguage = targetLanguage,
            items =
                items.map { item ->
                    item.copy(
                        payload = item.payload?.translate(targetLanguage),
                    )
                },
        )

    private suspend fun PreTranslationBatchPayload.translate(targetLanguage: String): PreTranslationBatchPayload =
        PreTranslationBatchPayload(
            content = content?.translate(targetLanguage),
            contentWarning = contentWarning?.translate(targetLanguage),
            title = title?.translate(targetLanguage),
            description = description?.translate(targetLanguage),
        )

    private suspend fun TranslationDocument.translate(targetLanguage: String): TranslationDocument =
        copy(
            targetLanguage = targetLanguage,
            blocks =
                blocks.map { block ->
                    block.copy(
                        tokens =
                            block.tokens.map { token ->
                                if (token.kind != TranslationTokenKind.Translatable || token.text.isBlank()) {
                                    token
                                } else {
                                    token.copy(
                                        text =
                                            translateText(
                                                sourceText = token.text,
                                                targetLanguage = targetLanguage,
                                            ),
                                    )
                                }
                            },
                    )
                },
        )

    private suspend fun translateText(
        sourceText: String,
        targetLanguage: String,
    ): String {
        val response =
            ktorClient()
                .get {
                    url("https://translate.google.com/translate_a/single")
                    parameter("client", "gtx")
                    parameter("sl", "auto")
                    parameter("tl", targetLanguage)
                    parameter("dt", "t")
                    parameter("q", sourceText)
                    parameter("ie", "UTF-8")
                    parameter("oe", "UTF-8")
                }.body<JsonArray>()
        return buildString {
            response.firstOrNull()?.jsonArray?.forEach { item ->
                item.jsonArray.firstOrNull()?.let { content ->
                    append(content.jsonPrimitive.content)
                }
            }
        }
    }
}
