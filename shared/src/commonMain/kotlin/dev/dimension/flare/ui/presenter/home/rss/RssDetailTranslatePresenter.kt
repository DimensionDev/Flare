package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.translation.TranslationPromptFormatter
import dev.dimension.flare.data.translation.TranslationProvider
import dev.dimension.flare.data.translation.TranslationResponseSanitizer
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.render.TranslationBlock
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.TranslationToken
import dev.dimension.flare.ui.render.TranslationTokenKind
import dev.dimension.flare.ui.render.parseHtml
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Presenter that translates the HTML content of an RSS article
 * by translating individual text nodes in the HTML tree.
 * This preserves the original HTML structure (headings, lists, images, etc.)
 * while replacing the text content with translations.
 */
public class RssDetailTranslatePresenter(
    private val htmlContent: String,
    private val title: String,
    private val targetLanguage: String = Locale.language,
) : PresenterBase<RssDetailTranslatePresenter.State>(),
    KoinComponent {
    private val appDataStore: AppDataStore by inject()
    private val aiCompletionService: AiCompletionService by inject()

    @Immutable
    public interface State {
        public val translatedHtml: UiState<String>
        public val translatedTitle: UiState<String>
    }

    @Composable
    override fun body(): State {
        val result =
            produceState<Pair<UiState<String>, UiState<String>>>(
                initialValue = UiState.Loading<String>() to UiState.Loading<String>(),
            ) {
                value = translate()
            }
        return object : State {
            override val translatedHtml = result.value.first
            override val translatedTitle = result.value.second
        }
    }

    private suspend fun translate(): Pair<UiState<String>, UiState<String>> {
        val settings =
            runCatching {
                appDataStore.appSettingsStore.data.first()
            }.getOrElse {
                return UiState.Error<String>(it) to UiState.Error<String>(it)
            }

        // Translate title
        val translatedTitle = translatePlainText(settings, title)

        // Translate HTML content
        val translatedHtml = translateHtmlContent(settings)

        return translatedHtml to translatedTitle
    }

    private suspend fun translatePlainText(
        settings: AppSettings,
        text: String,
    ): UiState<String> =
        runCatching {
            val doc = buildSingleTextDocument(text)
            val sourceJson = translationJson.encodeToString(doc)
            val sourceText = text
            val prompt =
                TranslationPromptFormatter.buildTranslatePrompt(
                    settings = settings,
                    targetLanguage = targetLanguage,
                    sourceText = sourceText,
                    sourceJson = sourceJson,
                )
            val result =
                TranslationProvider.translateDocumentJson(
                    settings = settings,
                    aiCompletionService = aiCompletionService,
                    sourceText = sourceText,
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

    private suspend fun translateHtmlContent(settings: AppSettings): UiState<String> =
        runCatching {
            // Parse HTML into a fresh Element tree
            val element = parseHtml(htmlContent)

            // Collect all text nodes with non-blank text
            val textNodes = mutableListOf<TextNode>()
            collectTextNodes(element, textNodes)

            if (textNodes.isEmpty()) {
                return@runCatching element.html()
            }

            // Get unique texts to minimize translation calls
            val uniqueTexts =
                textNodes
                    .map { it.text() }
                    .filter { it.isNotBlank() }
                    .distinct()

            if (uniqueTexts.isEmpty()) {
                return@runCatching element.html()
            }

            // Build a TranslationDocument from the unique texts
            val doc = buildTranslationDocument(uniqueTexts)
            val sourceJson = translationJson.encodeToString(doc)
            val sourceText = uniqueTexts.joinToString("\n")
            val prompt =
                TranslationPromptFormatter.buildTranslatePrompt(
                    settings = settings,
                    targetLanguage = targetLanguage,
                    sourceText = sourceText,
                    sourceJson = sourceJson,
                )

            val result =
                TranslationProvider.translateDocumentJson(
                    settings = settings,
                    aiCompletionService = aiCompletionService,
                    sourceText = sourceText,
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
                    // Build mapping from original text to translated text
                    val translationMap = buildTranslationMap(doc, translatedDoc)

                    // Apply translations back to text nodes
                    textNodes.forEach { node ->
                        val original = node.text()
                        translationMap[original]?.let { translated ->
                            node.replaceWith(TextNode(translated))
                        }
                    }
                }
            }

            // Serialize the modified Element tree back to HTML string
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
