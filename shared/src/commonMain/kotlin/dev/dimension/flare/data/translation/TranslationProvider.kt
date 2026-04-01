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
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
        engine(
            settings = settings,
            aiCompletionService = aiCompletionService,
        ).translateDocumentJson(
            sourceText = sourceText,
            sourceJson = sourceJson,
            targetLanguage = targetLanguage,
            prompt = prompt,
        )

    suspend fun translateBatchDocumentJson(
        settings: AppSettings,
        aiCompletionService: AiCompletionService,
        sourceJson: String,
        sourceDocument: PreTranslationBatchDocument,
        targetLanguage: String,
        prompt: String,
    ): String? =
        engine(
            settings = settings,
            aiCompletionService = aiCompletionService,
        ).translateBatchDocumentJson(
            sourceJson = sourceJson,
            sourceDocument = sourceDocument,
            targetLanguage = targetLanguage,
            prompt = prompt,
        )

    private fun engine(
        settings: AppSettings,
        aiCompletionService: AiCompletionService,
    ): TranslationEngine =
        when (val provider = settings.translateConfig.provider) {
            AppSettings.TranslateConfig.Provider.AI ->
                AiTranslationEngine(
                    config = settings.aiConfig,
                    aiCompletionService = aiCompletionService,
                )

            AppSettings.TranslateConfig.Provider.GoogleWeb -> GoogleWebTranslationEngine
            is AppSettings.TranslateConfig.Provider.DeepL -> DeepLTranslationEngine(provider)
            is AppSettings.TranslateConfig.Provider.GoogleCloud -> GoogleCloudTranslationEngine(provider)
            is AppSettings.TranslateConfig.Provider.LibreTranslate -> LibreTranslateEngine(provider)
        }
}

internal interface TranslationEngine {
    suspend fun translateDocumentJson(
        sourceText: String,
        sourceJson: String,
        targetLanguage: String,
        prompt: String,
    ): String?

    suspend fun translateBatchDocumentJson(
        sourceJson: String,
        sourceDocument: PreTranslationBatchDocument,
        targetLanguage: String,
        prompt: String,
    ): String?
}

private class AiTranslationEngine(
    private val config: AppSettings.AiConfig,
    private val aiCompletionService: AiCompletionService,
) : TranslationEngine {
    override suspend fun translateDocumentJson(
        sourceText: String,
        sourceJson: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        aiCompletionService.translate(
            config = config,
            source = sourceText,
            targetLanguage = targetLanguage,
            prompt = prompt,
        )

    override suspend fun translateBatchDocumentJson(
        sourceJson: String,
        sourceDocument: PreTranslationBatchDocument,
        targetLanguage: String,
        prompt: String,
    ): String? =
        aiCompletionService.translate(
            config = config,
            source = sourceJson,
            targetLanguage = targetLanguage,
            prompt = prompt,
        )
}

private object GoogleWebTranslationEngine : TranslationEngine {
    private const val MAX_CONCURRENT_REQUESTS = 4

    private val httpClient: HttpClient by lazy {
        ktorClient()
    }

    override suspend fun translateDocumentJson(
        sourceText: String,
        sourceJson: String,
        targetLanguage: String,
        prompt: String,
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

    override suspend fun translateBatchDocumentJson(
        sourceJson: String,
        sourceDocument: PreTranslationBatchDocument,
        targetLanguage: String,
        prompt: String,
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

private class DeepLTranslationEngine(
    private val config: AppSettings.TranslateConfig.Provider.DeepL,
) : TranslationEngine {
    private val httpClient: HttpClient by lazy {
        ktorClient()
    }

    override suspend fun translateDocumentJson(
        sourceText: String,
        sourceJson: String,
        targetLanguage: String,
        prompt: String,
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

    override suspend fun translateBatchDocumentJson(
        sourceJson: String,
        sourceDocument: PreTranslationBatchDocument,
        targetLanguage: String,
        prompt: String,
    ): String {
        val translatedTexts =
            translateTexts(
                sourceTexts = GoogleWebTranslationDocumentSupport.collectUniqueTranslatableTexts(sourceDocument),
                targetLanguage = targetLanguage,
            )
        return GoogleWebTranslationDocumentSupport
            .applyTranslations(
                document = sourceDocument,
                targetLanguage = targetLanguage,
                translatedTexts = translatedTexts,
            ).encodeJson(PreTranslationBatchDocument.serializer())
    }

    private suspend fun translateTexts(
        sourceTexts: List<String>,
        targetLanguage: String,
    ): Map<String, String> =
        translateTextsInChunks(
            sourceTexts = sourceTexts,
            chunkSize = 50,
            translateChunk = { requestTexts ->
                require(config.apiKey.isNotBlank()) { "DeepL API key is not configured" }
                val response =
                    httpClient
                        .post {
                            url(
                                if (config.usePro) {
                                    "https://api.deepl.com/v2/translate"
                                } else {
                                    "https://api-free.deepl.com/v2/translate"
                                },
                            )
                            header(HttpHeaders.Authorization, "DeepL-Auth-Key ${config.apiKey}")
                            contentType(ContentType.Application.FormUrlEncoded)
                            setBody(
                                FormDataContent(
                                    Parameters.build {
                                        requestTexts.forEach { text ->
                                            append("text", text)
                                        }
                                        append("target_lang", targetLanguage.toDeepLTargetLanguage())
                                    },
                                ),
                            )
                        }.body<DeepLTranslateResponse>()
                response.translations.map { it.text.trim() }
            },
        )
}

private class GoogleCloudTranslationEngine(
    private val config: AppSettings.TranslateConfig.Provider.GoogleCloud,
) : TranslationEngine {
    private val httpClient: HttpClient by lazy {
        ktorClient()
    }

    override suspend fun translateDocumentJson(
        sourceText: String,
        sourceJson: String,
        targetLanguage: String,
        prompt: String,
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

    override suspend fun translateBatchDocumentJson(
        sourceJson: String,
        sourceDocument: PreTranslationBatchDocument,
        targetLanguage: String,
        prompt: String,
    ): String {
        val translatedTexts =
            translateTexts(
                sourceTexts = GoogleWebTranslationDocumentSupport.collectUniqueTranslatableTexts(sourceDocument),
                targetLanguage = targetLanguage,
            )
        return GoogleWebTranslationDocumentSupport
            .applyTranslations(
                document = sourceDocument,
                targetLanguage = targetLanguage,
                translatedTexts = translatedTexts,
            ).encodeJson(PreTranslationBatchDocument.serializer())
    }

    private suspend fun translateTexts(
        sourceTexts: List<String>,
        targetLanguage: String,
    ): Map<String, String> =
        translateTextsInChunks(
            sourceTexts = sourceTexts,
            chunkSize = 100,
            translateChunk = { requestTexts ->
                require(config.apiKey.isNotBlank()) { "Google Cloud Translation API key is not configured" }
                val response =
                    httpClient
                        .post {
                            url("https://translation.googleapis.com/language/translate/v2")
                            parameter("key", config.apiKey)
                            contentType(ContentType.Application.Json)
                            setBody(
                                GoogleCloudTranslateV2Request(
                                    q = requestTexts,
                                    target = targetLanguage.toGoogleCloudTargetLanguage(),
                                    format = "text",
                                ),
                            )
                        }.body<GoogleCloudTranslateV2Response>()
                response.data.translations.map { it.translatedText.trim() }
            },
        )
}

private class LibreTranslateEngine(
    private val config: AppSettings.TranslateConfig.Provider.LibreTranslate,
) : TranslationEngine {
    private val httpClient: HttpClient by lazy {
        ktorClient()
    }

    override suspend fun translateDocumentJson(
        sourceText: String,
        sourceJson: String,
        targetLanguage: String,
        prompt: String,
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

    override suspend fun translateBatchDocumentJson(
        sourceJson: String,
        sourceDocument: PreTranslationBatchDocument,
        targetLanguage: String,
        prompt: String,
    ): String {
        val translatedTexts =
            translateTexts(
                sourceTexts = GoogleWebTranslationDocumentSupport.collectUniqueTranslatableTexts(sourceDocument),
                targetLanguage = targetLanguage,
            )
        return GoogleWebTranslationDocumentSupport
            .applyTranslations(
                document = sourceDocument,
                targetLanguage = targetLanguage,
                translatedTexts = translatedTexts,
            ).encodeJson(PreTranslationBatchDocument.serializer())
    }

    private suspend fun translateTexts(
        sourceTexts: List<String>,
        targetLanguage: String,
    ): Map<String, String> =
        translateTextsInChunks(
            sourceTexts = sourceTexts,
            chunkSize = 60,
            translateChunk = { requestTexts ->
                val baseUrl = config.baseUrl.trim().trimEnd('/')
                require(baseUrl.isNotBlank()) { "LibreTranslate base URL is not configured" }
                val response =
                    httpClient
                        .post {
                            url("$baseUrl/translate")
                            contentType(ContentType.Application.Json)
                            setBody(
                                LibreTranslateRequest(
                                    q = requestTexts,
                                    target = targetLanguage,
                                    format = "text",
                                    apiKey = config.apiKey.ifBlank { null },
                                ),
                            )
                        }.body<LibreTranslateResponse>()
                response.translatedText
            },
        )
}

private suspend fun translateTextsInChunks(
    sourceTexts: List<String>,
    chunkSize: Int,
    translateChunk: suspend (List<String>) -> List<String>,
): Map<String, String> {
    if (sourceTexts.isEmpty()) {
        return emptyMap()
    }

    val translated = LinkedHashMap<String, String>(sourceTexts.size)
    sourceTexts.chunked(chunkSize).forEach { chunk ->
        val prepared =
            chunk.map { sourceText ->
                PreparedSourceText(
                    source = sourceText,
                    trimmed = GoogleWebTranslationWhitespaceSupport.trimBoundaryWhitespace(sourceText),
                )
            }
        val nonBlank = prepared.filter { it.trimmed.isNotEmpty() }
        val translatedChunk =
            if (nonBlank.isEmpty()) {
                emptyList()
            } else {
                translateChunk(nonBlank.map { it.trimmed })
            }
        require(translatedChunk.size == nonBlank.size) {
            "Translation provider returned ${translatedChunk.size} results for ${nonBlank.size} inputs"
        }

        var translatedIndex = 0
        prepared.forEach { item ->
            translated[item.source] =
                if (item.trimmed.isEmpty()) {
                    item.source
                } else {
                    GoogleWebTranslationWhitespaceSupport.preserveSourceBoundaryWhitespace(
                        sourceText = item.source,
                        translatedText = translatedChunk[translatedIndex++],
                    )
                }
        }
    }
    return translated
}

private data class PreparedSourceText(
    val source: String,
    val trimmed: String,
)

internal fun String.toDeepLTargetLanguage(): String {
    val normalized =
        replace('_', '-')
            .trim()
            .uppercase()
    if (normalized.isBlank()) {
        return normalized
    }

    val parts = normalized.split('-').filter { it.isNotBlank() }
    val language = parts.firstOrNull() ?: return normalized
    val qualifiers = parts.drop(1).toSet()

    return when (language) {
        "EN" ->
            if ("GB" in qualifiers) {
                "EN-GB"
            } else {
                "EN-US"
            }

        "PT" ->
            if ("BR" in qualifiers) {
                "PT-BR"
            } else {
                "PT-PT"
            }

        "ZH" ->
            when {
                qualifiers.any { it in setOf("HANT", "TW", "HK", "MO", "CHT") } -> "ZH-HANT"
                qualifiers.any { it in setOf("HANS", "CN", "SG", "CHS", "CH") } -> "ZH-HANS"
                else -> "ZH"
            }

        else -> language
    }
}

private fun String.toGoogleCloudTargetLanguage(): String = replace('_', '-').trim()

@Serializable
private data class DeepLTranslateResponse(
    val translations: List<DeepLTranslation>,
)

@Serializable
private data class DeepLTranslation(
    val text: String,
)

@Serializable
private data class GoogleCloudTranslateV2Request(
    val q: List<String>,
    val target: String,
    val format: String,
)

@Serializable
private data class GoogleCloudTranslateV2Response(
    val data: GoogleCloudTranslateV2Data = GoogleCloudTranslateV2Data(),
)

@Serializable
private data class GoogleCloudTranslateV2Data(
    val translations: List<GoogleCloudTranslation> = emptyList(),
)

@Serializable
private data class GoogleCloudTranslation(
    @SerialName("translatedText")
    val translatedText: String,
)

@Serializable
private data class LibreTranslateRequest(
    val q: List<String>,
    val target: String,
    val format: String,
    @SerialName("api_key")
    val apiKey: String? = null,
)

@Serializable
private data class LibreTranslateResponse(
    @SerialName("translatedText")
    val translatedText: List<String> = emptyList(),
)

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
