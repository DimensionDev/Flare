package dev.dimension.flare.ui.presenter.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AiPromptDefaults
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AiTLDRPresenter(
    private val source: String,
    private val targetLanguage: String = Locale.language,
) : PresenterBase<UiState<String>>(),
    KoinComponent {
    private val appDataStore: AppDataStore by inject()
    private val aiCompletionService: AiCompletionService by inject()

    @Composable
    override fun body(): UiState<String> {
        return produceState(UiState.Loading()) {
            value =
                runCatching {
                    val aiConfig =
                        appDataStore.appSettingsStore.data
                            .first()
                            .aiConfig
                    if (!aiConfig.tldr) {
                        return@runCatching legacyFlareTldr()
                    }
                    val promptTemplate =
                        aiConfig.tldrPrompt.ifBlank {
                            AiPromptDefaults.TLDR_PROMPT
                        }
                    val prompt = buildTldrPrompt(promptTemplate, targetLanguage, source)
                    aiCompletionService.tldr(
                        config = aiConfig,
                        source = source,
                        targetLanguage = targetLanguage,
                        prompt = prompt,
                    ) ?: legacyFlareTldr()
                }.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
        }.value
    }

    private suspend fun legacyFlareTldr(): String {
        return "Sorry, but the TLDR feature is not configured. Please set up your AI configuration in the settings to use this feature."
//        val flareServerUrl = appDataStore.flareDataStore.data.first().serverUrl
//        return FlareDataSource(flareServerUrl).tldr(source, targetLanguage)
    }

    private fun buildTldrPrompt(
        template: String,
        targetLanguage: String,
        sourceText: String,
    ): String =
        template
            .replace("{target_language}", targetLanguage)
            .replace("{source_text}", sourceText)
}
