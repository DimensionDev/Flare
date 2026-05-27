package dev.dimension.flare.data.translation

import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.render.TranslationBlock
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.TranslationToken
import dev.dimension.flare.ui.render.TranslationTokenKind
import dev.dimension.flare.ui.render.parseHtml
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

public class HtmlArticleTranslationService internal constructor(
    private val appDataStore: AppDataStore,
    private val aiCompletionService: AiCompletionService,
) {
    public suspend fun translate(
        htmlContent: String,
        title: String,
        targetLanguage: String,
    ): Pair<UiState<String>, UiState<String>> {
        val settings =
            runCatching {
                appDataStore.appSettingsStore.data.first()
            }.getOrElse {
                return UiState.Error<String>(it) to UiState.Error<String>(it)
            }

        val translatedTitle = translatePlainText(settings, title, targetLanguage)
        val translatedHtml = translateHtmlContent(settings, htmlContent, targetLanguage)
        return translatedHtml to translatedTitle
    }

    private suspend fun translatePlainText(
        settings: AppSettings,
        text: String,
        targetLanguage: String,
    ): UiState<String> =
        runCatching {
            val doc = buildSingleTextDocument(text)
            val sourceJson = translationJson.encodeToString(doc)
            val promptTemplate = AiPlaceholderTranslationSupport.buildPromptTemplate(doc)
            val prompt =
                TranslationPromptFormatter.buildTranslatePrompt(
                    settings = settings,
                    targetLanguage = targetLanguage,
                    sourceTemplate = promptTemplate,
                )
            val result =
                TranslationProvider.translateDocumentJson(
                    settings = settings,
                    aiCompletionService = aiCompletionService,
                    sourceTemplate = promptTemplate,
                    sourceJson = sourceJson,
                    targetLanguage = targetLanguage,
                    prompt = prompt,
                )
            if (result != null) {
                val cleaned = TranslationResponseSanitizer.clean(result)
                val translated =
                    runCatching {
                        translationJson.decodeFromString(TranslationDocument.serializer(), cleaned)
                    }.getOrNull()
                translated
                    ?.blocks
                    ?.firstOrNull()
                    ?.tokens
                    ?.filter { it.kind == TranslationTokenKind.Translatable }
                    ?.joinToString("") { it.text }
                    ?: text
            } else {
                error("Translation returned empty response")
            }
        }.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it) },
        )

    private suspend fun translateHtmlContent(
        settings: AppSettings,
        htmlContent: String,
        targetLanguage: String,
    ): UiState<String> =
        runCatching {
            val element = parseHtml(htmlContent)
            val textNodes = mutableListOf<TextNode>()
            collectTextNodes(element, textNodes)

            val uniqueTexts =
                textNodes
                    .map { it.text() }
                    .filter { it.isNotBlank() }
                    .distinct()

            if (uniqueTexts.isNotEmpty()) {
                val doc = buildTranslationDocument(uniqueTexts)
                val sourceJson = translationJson.encodeToString(doc)
                val promptTemplate = AiPlaceholderTranslationSupport.buildPromptTemplate(doc)
                val prompt =
                    TranslationPromptFormatter.buildTranslatePrompt(
                        settings = settings,
                        targetLanguage = targetLanguage,
                        sourceTemplate = promptTemplate,
                    )

                val result =
                    TranslationProvider.translateDocumentJson(
                        settings = settings,
                        aiCompletionService = aiCompletionService,
                        sourceTemplate = promptTemplate,
                        sourceJson = sourceJson,
                        targetLanguage = targetLanguage,
                        prompt = prompt,
                    )

                if (result != null) {
                    val cleaned = TranslationResponseSanitizer.clean(result)
                    val translatedDoc =
                        runCatching {
                            translationJson.decodeFromString(TranslationDocument.serializer(), cleaned)
                        }.getOrNull()
                    if (translatedDoc != null) {
                        val translationMap = buildTranslationMap(doc, translatedDoc)
                        textNodes.forEach { node ->
                            val original = node.text()
                            translationMap[original]?.let { translated ->
                                node.replaceWith(TextNode(translated))
                            }
                        }
                    }
                }
            }

            element.html()
        }.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it) },
        )

    private companion object {
        private val translationJson =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
                explicitNulls = false
            }

        private fun collectTextNodes(
            node: Node,
            result: MutableList<TextNode>,
        ) {
            if (node is TextNode && node.text().isNotBlank()) {
                result.add(node)
            }
            node.childNodes().forEach { child ->
                collectTextNodes(child, result)
            }
        }

        private fun buildSingleTextDocument(text: String): TranslationDocument =
            TranslationDocument(
                version = 1,
                targetLanguage = null,
                blocks =
                    listOf(
                        TranslationBlock(
                            id = 0,
                            tokens =
                                listOf(
                                    TranslationToken(
                                        id = 0,
                                        kind = TranslationTokenKind.Translatable,
                                        text = text,
                                    ),
                                ),
                        ),
                    ),
            )

        private fun buildTranslationDocument(uniqueTexts: List<String>): TranslationDocument =
            TranslationDocument(
                version = 1,
                targetLanguage = null,
                blocks =
                    uniqueTexts.mapIndexed { index, text ->
                        TranslationBlock(
                            id = index,
                            tokens =
                                listOf(
                                    TranslationToken(
                                        id = 0,
                                        kind = TranslationTokenKind.Translatable,
                                        text = text,
                                    ),
                                ),
                        )
                    },
            )

        private fun buildTranslationMap(
            original: TranslationDocument,
            translated: TranslationDocument,
        ): Map<String, String> {
            val map = mutableMapOf<String, String>()
            val translatedBlocks = translated.blocks.associateBy { it.id }
            original.blocks.forEach { block ->
                val translatedBlock = translatedBlocks[block.id] ?: return@forEach
                val originalText =
                    block.tokens
                        .filter { it.kind == TranslationTokenKind.Translatable }
                        .joinToString("") { it.text }
                val translatedText =
                    translatedBlock.tokens
                        .filter { it.kind == TranslationTokenKind.Translatable }
                        .joinToString("") { it.text }
                if (originalText.isNotBlank() && translatedText.isNotBlank()) {
                    map[originalText] = translatedText
                }
            }
            return map
        }
    }
}
