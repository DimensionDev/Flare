package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.data.translation.AiPlaceholderTranslationSupport
import dev.dimension.flare.data.translation.TranslationPromptFormatter
import dev.dimension.flare.data.translation.TranslationProvider
import dev.dimension.flare.data.translation.TranslationResponseSanitizer
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.applyTranslationJson
import dev.dimension.flare.ui.render.toTranslationJson
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.render.uiRichTextOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AiTranslationTestPresenter :
    PresenterBase<AiTranslationTestPresenter.State>(),
    KoinComponent {
    private val appDataStore by inject<AppDataStore>()
    private val aiCompletionService by inject<AiCompletionService>()

    @Immutable
    public interface State {
        public val sampleText: UiRichText
        public val translatedText: UiRichText?
        public val isLoading: Boolean
        public val errorMessage: String?

        public fun runTest()
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val sampleText = remember { aiTranslationTestSampleText() }
        var translatedText by remember { mutableStateOf<UiRichText?>(null) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        return object : State {
            override val sampleText: UiRichText = sampleText
            override val translatedText: UiRichText? = translatedText
            override val isLoading: Boolean = isLoading
            override val errorMessage: String? = errorMessage

            override fun runTest() {
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    translatedText = null
                    val result =
                        tryRun {
                            val settings = appDataStore.appSettingsStore.data.first()
                            val targetLanguage = Locale.language
                            val sourceJson = sampleText.toTranslationJson(targetLanguage)
                            val promptTemplate =
                                AiPlaceholderTranslationSupport.buildPromptTemplate(
                                    sourceJson.decodeJson(TranslationDocument.serializer()),
                                )
                            val prompt =
                                TranslationPromptFormatter.buildTranslatePrompt(
                                    settings = settings,
                                    targetLanguage = targetLanguage,
                                    sourceTemplate = promptTemplate,
                                )
                            val translatedContent =
                                TranslationProvider.translateDocumentJson(
                                    settings = settings,
                                    aiCompletionService = aiCompletionService,
                                    sourceTemplate = promptTemplate,
                                    sourceJson = sourceJson,
                                    targetLanguage = targetLanguage,
                                    prompt = prompt,
                                )
                            require(!translatedContent.isNullOrBlank()) { "Translation returned empty response" }
                            toUiRichText(
                                source = sampleText,
                                translatedContent = translatedContent,
                            )
                        }
                    result
                        .onSuccess {
                            translatedText = it
                        }.onFailure {
                            errorMessage = it.message ?: "Translation failed"
                        }
                    isLoading = false
                }
            }
        }
    }
}

private fun aiTranslationTestSampleText(): UiRichText =
    uiRichTextOf(
        renderRuns =
            listOf(
                RenderContent.Text(
                    runs =
                        persistentListOf(
                            RenderRun.Text("Hello "),
                            RenderRun.Text("@alice", style = RenderTextStyle(link = "https://flareapp.moe")),
                            RenderRun.Text(" and welcome to "),
                            RenderRun.Text("#Flare", style = RenderTextStyle(link = "https://flareapp.moe")),
                            RenderRun.Text(". Check "),
                            RenderRun.Text("https://flareapp.moe", style = RenderTextStyle(link = "https://flareapp.moe")),
                        ),
                ),
            ),
    )

private fun toUiRichText(
    source: UiRichText,
    translatedContent: String,
): UiRichText =
    TranslationResponseSanitizer
        .clean(translatedContent)
        .let { cleaned ->
            tryRun {
                source.applyTranslationJson(cleaned)
            }.getOrElse {
                cleaned.toUiPlainText(listOf(Locale.language))
            }
        }
