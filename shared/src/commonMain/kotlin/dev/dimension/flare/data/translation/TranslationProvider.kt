package dev.dimension.flare.data.translation

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.TranslationTokenKind
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal object TranslationProvider {
    suspend fun translateDocumentJson(
        settings: AppSettings,
        aiCompletionService: AiCompletionService,
        sourceText: String,
        sourceJson: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        when (settings.translateConfig.provider) {
            AppSettings.TranslateConfig.Provider.AI ->
                aiCompletionService.translate(
                    config = settings.aiConfig,
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

    suspend fun translateBatchDocumentJson(
        settings: AppSettings,
        aiCompletionService: AiCompletionService,
        sourceJson: String,
        sourceDocument: PreTranslationBatchDocument,
        targetLanguage: String,
        prompt: String,
    ): String? =
        when (settings.translateConfig.provider) {
            AppSettings.TranslateConfig.Provider.AI ->
                aiCompletionService.translate(
                    config = settings.aiConfig,
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
}

private object GoogleWebTranslationProvider {
    private const val MAX_CONCURRENT_REQUESTS = 4

    private val httpClient: HttpClient by lazy {
        ktorClient()
    }

    suspend fun translateDocumentJson(
        sourceJson: String,
        targetLanguage: String,
    ): String =
        sourceJson
            .decodeJson(TranslationDocument.serializer())
            .let { document ->
                val translatedTexts =
                    translateTexts(
                        sourceTexts = GoogleWebTranslationDocumentSupport.collectUniqueTranslatableTexts(document),
                        targetLanguage = targetLanguage,
                    )
                GoogleWebTranslationDocumentSupport
                    .applyTranslations(
                        document = document,
                        targetLanguage = targetLanguage,
                        translatedTexts = translatedTexts,
                    ).encodeJson(TranslationDocument.serializer())
            }

    suspend fun translateBatchDocumentJson(
        sourceDocument: PreTranslationBatchDocument,
        targetLanguage: String,
    ): String =
        sourceDocument
            .let { document ->
                val translatedTexts =
                    translateTexts(
                        sourceTexts = GoogleWebTranslationDocumentSupport.collectUniqueTranslatableTexts(document),
                        targetLanguage = targetLanguage,
                    )
                GoogleWebTranslationDocumentSupport
                    .applyTranslations(
                        document = document,
                        targetLanguage = targetLanguage,
                        translatedTexts = translatedTexts,
                    ).encodeJson(PreTranslationBatchDocument.serializer())
            }

    private suspend fun translateTexts(
        sourceTexts: List<String>,
        targetLanguage: String,
    ): Map<String, String> {
        if (sourceTexts.isEmpty()) {
            return emptyMap()
        }
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        return coroutineScope {
            sourceTexts
                .map { sourceText ->
                    async {
                        semaphore.withPermit {
                            sourceText to translateText(sourceText = sourceText, targetLanguage = targetLanguage)
                        }
                    }
                }.awaitAll()
                .toMap()
        }
    }

    private suspend fun translateText(
        sourceText: String,
        targetLanguage: String,
    ): String {
        val requestText = GoogleWebTranslationWhitespaceSupport.trimBoundaryWhitespace(sourceText)
        if (requestText.isEmpty()) {
            return sourceText
        }
        val response =
            httpClient
                .get {
                    url("https://translate.google.com/translate_a/single")
                    parameter("client", "gtx")
                    parameter("sl", "auto")
                    parameter("tl", targetLanguage)
                    parameter("dt", "t")
                    parameter("q", requestText)
                    parameter("ie", "UTF-8")
                    parameter("oe", "UTF-8")
                }.body<JsonArray>()
        val translatedText =
            buildString {
                response.firstOrNull()?.jsonArray?.forEach { item ->
                    item.jsonArray.firstOrNull()?.let { content ->
                        append(content.jsonPrimitive.content)
                    }
                }
            }
        return GoogleWebTranslationWhitespaceSupport.preserveSourceBoundaryWhitespace(
            sourceText = sourceText,
            translatedText = translatedText,
        )
    }
}

internal object GoogleWebTranslationDocumentSupport {
    fun collectUniqueTranslatableTexts(document: TranslationDocument): List<String> =
        LinkedHashSet<String>()
            .apply {
                document.blocks.forEach { block ->
                    block.tokens.forEach { token ->
                        if (token.kind == TranslationTokenKind.Translatable && token.text.isNotBlank()) {
                            add(token.text)
                        }
                    }
                }
            }.toList()

    fun collectUniqueTranslatableTexts(document: PreTranslationBatchDocument): List<String> =
        LinkedHashSet<String>()
            .apply {
                document.items.forEach { item ->
                    collectTranslatableTexts(item.payload)
                }
            }.toList()

    fun applyTranslations(
        document: TranslationDocument,
        targetLanguage: String,
        translatedTexts: Map<String, String>,
    ): TranslationDocument =
        document.copy(
            targetLanguage = targetLanguage,
            blocks =
                document.blocks.map { block ->
                    block.copy(
                        tokens =
                            block.tokens.map { token ->
                                when {
                                    token.kind != TranslationTokenKind.Translatable || token.text.isBlank() -> token
                                    else ->
                                        token.copy(
                                            text =
                                                translatedTexts[token.text]
                                                    ?: error("Missing translated text for token '${token.text.take(50)}'"),
                                        )
                                }
                            },
                    )
                },
        )

    fun applyTranslations(
        document: PreTranslationBatchDocument,
        targetLanguage: String,
        translatedTexts: Map<String, String>,
    ): PreTranslationBatchDocument =
        document.copy(
            targetLanguage = targetLanguage,
            items =
                document.items.map { item ->
                    item.copy(
                        payload =
                            item.payload?.let { payload ->
                                applyTranslations(
                                    payload = payload,
                                    targetLanguage = targetLanguage,
                                    translatedTexts = translatedTexts,
                                )
                            },
                    )
                },
        )

    private fun MutableSet<String>.collectTranslatableTexts(payload: PreTranslationBatchPayload?) {
        if (payload == null) {
            return
        }
        collectTranslatableTexts(payload.content)
        collectTranslatableTexts(payload.contentWarning)
        collectTranslatableTexts(payload.title)
        collectTranslatableTexts(payload.description)
    }

    private fun MutableSet<String>.collectTranslatableTexts(document: TranslationDocument?) {
        if (document == null) {
            return
        }
        addAll(collectUniqueTranslatableTexts(document))
    }

    private fun applyTranslations(
        payload: PreTranslationBatchPayload,
        targetLanguage: String,
        translatedTexts: Map<String, String>,
    ): PreTranslationBatchPayload =
        PreTranslationBatchPayload(
            content = payload.content?.let { applyTranslations(it, targetLanguage, translatedTexts) },
            contentWarning = payload.contentWarning?.let { applyTranslations(it, targetLanguage, translatedTexts) },
            title = payload.title?.let { applyTranslations(it, targetLanguage, translatedTexts) },
            description = payload.description?.let { applyTranslations(it, targetLanguage, translatedTexts) },
        )
}

internal object GoogleWebTranslationWhitespaceSupport {
    fun trimBoundaryWhitespace(text: String): String = text.trim { it.isWhitespace() }

    fun preserveSourceBoundaryWhitespace(
        sourceText: String,
        translatedText: String,
    ): String {
        val leadingWhitespace = sourceText.takeWhile { it.isWhitespace() }
        val trailingWhitespace = sourceText.reversed().takeWhile { it.isWhitespace() }.reversed()
        val translatedCore = trimBoundaryWhitespace(translatedText)
        return buildString(sourceText.length + translatedCore.length) {
            append(leadingWhitespace)
            append(translatedCore)
            append(trailingWhitespace)
        }
    }
}
