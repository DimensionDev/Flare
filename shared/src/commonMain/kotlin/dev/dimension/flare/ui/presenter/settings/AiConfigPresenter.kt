package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public enum class AiTypeOption {
    OnDevice,
    OpenAI,
}

public enum class TranslateProviderOption {
    AI,
    Google,
}

public class AiConfigPresenter :
    PresenterBase<AiConfigPresenter.State>(),
    KoinComponent {
    private val appDataStore by inject<AppDataStore>()
    private val openAIService by inject<OpenAIService>()
    private val onDeviceAI by inject<OnDeviceAI>()

    @Immutable
    public interface State {
        public val aiConfig: AppSettings.AiConfig
        public val translateConfig: AppSettings.TranslateConfig
        public val openAIModels: UiState<ImmutableList<String>>
        public val supportedTypes: ImmutableList<AiTypeOption>
        public val supportedTranslateProviders: ImmutableList<TranslateProviderOption>
        public val serverSuggestions: ImmutableList<String>

        public fun update(block: AppSettings.AiConfig.() -> AppSettings.AiConfig)

        public fun updateTranslateConfig(block: AppSettings.TranslateConfig.() -> AppSettings.TranslateConfig)

        public fun selectType(type: AiTypeOption)

        public fun selectTranslateProvider(type: TranslateProviderOption)
    }

    @OptIn(FlowPreview::class)
    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val appSettings by remember { appDataStore.appSettingsStore.data }
            .collectAsState(AppSettings(version = ""))
        val aiConfig = appSettings.aiConfig
        val translateConfig = appSettings.translateConfig
        var openAIModels by remember {
            mutableStateOf<UiState<ImmutableList<String>>>(UiState.Success(persistentListOf()))
        }
        var supportedTypes by remember {
            mutableStateOf<ImmutableList<AiTypeOption>>(persistentListOf(AiTypeOption.OpenAI))
        }
        val supportedTranslateProviders =
            remember {
                persistentListOf(
                    TranslateProviderOption.AI,
                    TranslateProviderOption.Google,
                )
            }

        LaunchedEffect(Unit) {
            supportedTypes =
                buildList {
                    if (onDeviceAI.isAvailable()) {
                        add(AiTypeOption.OnDevice)
                    }
                    add(AiTypeOption.OpenAI)
                }.toImmutableList()
        }

        LaunchedEffect(Unit) {
            snapshotFlow { aiConfig.type as? AppSettings.AiConfig.Type.OpenAI }
                .map { (it?.serverUrl ?: "") to (it?.apiKey ?: "") }
                .distinctUntilChanged()
                .debounce(666L)
                .collectLatest { (serverUrl, apiKey) ->
                    if (serverUrl.isBlank() || apiKey.isBlank()) {
                        openAIModels = UiState.Success(persistentListOf())
                    } else {
                        openAIModels = UiState.Loading()
                        openAIModels =
                            runCatching {
                                UiState.Success(
                                    openAIService
                                        .models(
                                            serverUrl = serverUrl,
                                            apiKey = apiKey,
                                        ).toImmutableList(),
                                )
                            }.getOrElse {
                                UiState.Error(it)
                            }
                    }
                }
        }
        return object : State {
            override val aiConfig: AppSettings.AiConfig = aiConfig
            override val translateConfig: AppSettings.TranslateConfig = translateConfig
            override val openAIModels: UiState<ImmutableList<String>> = openAIModels
            override val supportedTypes: ImmutableList<AiTypeOption> = supportedTypes
            override val supportedTranslateProviders: ImmutableList<TranslateProviderOption> = supportedTranslateProviders
            override val serverSuggestions: ImmutableList<String> = SERVER_SUGGESTIONS

            override fun update(block: AppSettings.AiConfig.() -> AppSettings.AiConfig) {
                scope.launch {
                    withContext(Dispatchers.Main) {
                        appDataStore.appSettingsStore.updateData { current ->
                            current.copy(
                                aiConfig =
                                    block
                                        .invoke(current.aiConfig)
                                        .normalized(),
                            )
                        }
                    }
                }
            }

            override fun updateTranslateConfig(block: AppSettings.TranslateConfig.() -> AppSettings.TranslateConfig) {
                scope.launch {
                    withContext(Dispatchers.Main) {
                        appDataStore.appSettingsStore.updateData { current ->
                            current.copy(
                                translateConfig =
                                    block
                                        .invoke(current.translateConfig)
                                        .normalized(),
                            )
                        }
                    }
                }
            }

            override fun selectType(type: AiTypeOption) {
                update {
                    when (type) {
                        AiTypeOption.OnDevice -> copy(type = AppSettings.AiConfig.Type.OnDevice)
                        AiTypeOption.OpenAI -> {
                            val currentType = this.type as? AppSettings.AiConfig.Type.OpenAI
                            copy(
                                type =
                                    currentType
                                        ?: AppSettings.AiConfig.Type.OpenAI(
                                            serverUrl = "",
                                            apiKey = "",
                                            model = "",
                                        ),
                            )
                        }
                    }
                }
            }

            override fun selectTranslateProvider(type: TranslateProviderOption) {
                updateTranslateConfig {
                    copy(
                        provider =
                            when (type) {
                                TranslateProviderOption.AI -> AppSettings.TranslateConfig.Provider.AI
                                TranslateProviderOption.Google -> AppSettings.TranslateConfig.Provider.Google
                            },
                    )
                }
            }
        }
    }
}

private fun AppSettings.AiConfig.normalized(): AppSettings.AiConfig = this

private fun AppSettings.TranslateConfig.normalized(): AppSettings.TranslateConfig = this

private val SERVER_SUGGESTIONS =
    persistentListOf(
        "https://api.openai.com/v1/",
        "https://generativelanguage.googleapis.com/v1beta/openai/",
        "https://openrouter.ai/api/v1/",
        "https://api.x.ai/v1/",
        "https://dashscope.aliyuncs.com/compatible-mode/v1/",
        "https://open.bigmodel.cn/api/paas/v4/",
        "https://api.moonshot.cn/v1/",
        "https://api.siliconflow.cn/v1/",
        "https://api.minimaxi.com/v1/",
        "https://api.groq.com/openai/v1/",
        "https://api.together.xyz/v1/",
        "https://api.deepseek.com/v1/",
        "https://api.fireworks.ai/inference/v1/",
    )
