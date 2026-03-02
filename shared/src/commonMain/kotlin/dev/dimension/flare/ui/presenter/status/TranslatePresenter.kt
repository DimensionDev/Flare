package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AiPromptDefaults
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class TranslatePresenter(
    private val source: String,
    private val targetLanguage: String = Locale.language,
) : PresenterBase<UiState<String>>(),
    KoinComponent {
    private val openAIService by inject<OpenAIService>()
    private val appDataStore: AppDataStore by inject()
    private val onDeviceAI: OnDeviceAI by inject()

    @Composable
    override fun body(): UiState<String> {
        return produceState(UiState.Loading()) {
            value =
                runCatching {
                    val aiConfig =
                        appDataStore.appSettingsStore.data
                            .first()
                            .aiConfig
                    if (!aiConfig.translation) {
                        return@runCatching legacyGoogleTranslate()
                    }
                    val promptTemplate =
                        aiConfig.translatePrompt.ifBlank {
                            AiPromptDefaults.TRANSLATE_PROMPT
                        }
                    val prompt = buildTranslatePrompt(promptTemplate, targetLanguage, source)
                    when (val type = aiConfig.type) {
                        AppSettings.AiConfig.Type.OnDevice ->
                            onDeviceAI.translate(source, targetLanguage, prompt) ?: legacyGoogleTranslate()
                        is AppSettings.AiConfig.Type.OpenAI -> {
                            if (type.serverUrl.isBlank() || type.apiKey.isBlank() || type.model.isBlank()) {
                                legacyGoogleTranslate()
                            } else {
                                openAIService.chatCompletion(
                                    config = type,
                                    prompt = prompt,
                                )
                            }
                        }
                    }
                }.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
        }.value
    }

    private suspend fun legacyGoogleTranslate(): String {
        val baseUrl = "https://translate.google.com/translate_a/single"
        val response =
            ktorClient()
                .get {
                    url(baseUrl)
                    parameter("client", "gtx")
                    parameter("sl", "auto")
                    parameter("tl", targetLanguage)
                    parameter("dt", "t")
                    parameter("q", source)
                    parameter("ie", "UTF-8")
                    parameter("oe", "UTF-8")
                }.body<JsonArray>()
        return buildString {
            response.firstOrNull()?.jsonArray?.forEach {
                it.jsonArray.firstOrNull()?.let {
                    val content = it.jsonPrimitive.content
                    if (content.isNotEmpty()) {
                        append(content)
                        append("\n")
                    }
                }
            }
        }
    }

    private fun buildTranslatePrompt(
        template: String,
        targetLanguage: String,
        sourceText: String,
    ): String =
        template
            .replace("{target_language}", targetLanguage)
            .replace("{source_text}", sourceText)
}
