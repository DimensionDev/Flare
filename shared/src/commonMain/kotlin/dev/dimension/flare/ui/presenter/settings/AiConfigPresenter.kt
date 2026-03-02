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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public enum class AiTypeOption {
    OnDevice,
    OpenAI,
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
        public val openAIModels: UiState<ImmutableList<String>>
        public val supportedTypes: ImmutableList<AiTypeOption>

        public fun update(block: AppSettings.AiConfig.() -> AppSettings.AiConfig)

        public fun selectType(type: AiTypeOption)
    }

    @OptIn(FlowPreview::class)
    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val aiConfig by remember { appDataStore.appSettingsStore.data.map { it.aiConfig } }
            .collectAsState(AppSettings.AiConfig())
        var openAIModels by remember {
            mutableStateOf<UiState<ImmutableList<String>>>(UiState.Success(persistentListOf()))
        }
        val supportedTypes =
            remember {
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
                .drop(1)
                .collectLatest {
                    withContext(Dispatchers.Main) {
                        appDataStore.appSettingsStore.updateData {
                            it.copy(
                                aiConfig =
                                    it.aiConfig.copy(
                                        type =
                                            if (it.aiConfig.type is AppSettings.AiConfig.Type.OpenAI) {
                                                it.aiConfig.type.copy(
                                                    model = "",
                                                )
                                            } else {
                                                it.aiConfig.type
                                            },
                                    ),
                            )
                        }
                    }
                }
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
            override val openAIModels: UiState<ImmutableList<String>> = openAIModels
            override val supportedTypes: ImmutableList<AiTypeOption> = supportedTypes

            override fun update(block: AppSettings.AiConfig.() -> AppSettings.AiConfig) {
                scope.launch {
                    withContext(Dispatchers.Main) {
                        appDataStore.appSettingsStore.updateData { current ->
                            current.copy(aiConfig = block.invoke(current.aiConfig))
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
        }
    }
}
