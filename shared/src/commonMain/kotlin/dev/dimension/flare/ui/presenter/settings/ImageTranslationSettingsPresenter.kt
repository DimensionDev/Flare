package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationField
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.data.translation.image.ImageTranslationModelDiscovery
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

public class ImageTranslationSettingsPresenter : PresenterBase<ImageTranslationSettingsPresenter.State>() {
    private val store by koinInject<AppDataStore>()
    private val persistenceScope by koinInject<kotlinx.coroutines.CoroutineScope>()
    private val modelDiscovery by koinInject<ImageTranslationModelDiscovery>()

    public interface State {
        public val config: ImageTranslationConfig
        public val providers: List<ImageTranslationProvider>
        public val fields: List<ImageTranslationField>
        public val useSharedConfig: Boolean

        public fun setUseSharedConfig(value: Boolean)

        public fun selectProvider(provider: ImageTranslationProvider)

        public fun setField(
            id: String,
            value: String,
        )
    }

    @Composable
    override fun body(): State {
        val settings by store.appSettingsStore.data.collectAsState(AppSettings(version = ""))
        val scope = persistenceScope
        val config = settings.imageTranslationConfig.resolved(settings.aiConfig)
        val canDiscover =
            !config.usesSharedAIConfig &&
                config.provider in listOf(ImageTranslationProvider.OpenAIImages, ImageTranslationProvider.Gemini) &&
                config.value("baseUrl").isNotBlank() && config.value("apiKey").isNotBlank()
        var models by remember(config.provider, config.value("baseUrl"), config.value("apiKey"), canDiscover) {
            mutableStateOf<UiState<List<String>>>(if (canDiscover) UiState.Loading() else UiState.Success(emptyList()))
        }
        LaunchedEffect(config.provider, config.value("baseUrl"), config.value("apiKey"), canDiscover) {
            if (canDiscover) {
                delay(666)
                models =
                    try {
                        UiState.Success(modelDiscovery.models(config))
                    } catch (error: Exception) {
                        if (error is CancellationException && error !is TimeoutCancellationException) throw error
                        UiState.Error(error)
                    }
            }
        }
        return object : State {
            override val config = config
            override val providers = ImageTranslationProvider.entries.toList()
            override val fields =
                config.editableFields.map { field ->
                    when {
                        field.id == "model" && canDiscover -> {
                            field.copy(suggestions = (models as? UiState.Success)?.data.orEmpty(), loading = models is UiState.Loading)
                        }

                        field.id == "baseUrl" && config.provider == ImageTranslationProvider.OpenAIImages -> {
                            field.copy(editSuggestions = AI_SERVER_SUGGESTIONS)
                        }

                        else -> {
                            field
                        }
                    }
                }
            override val useSharedConfig = config.usesSharedAIConfig

            override fun setUseSharedConfig(value: Boolean) {
                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    store.appSettingsStore.updateData { current ->
                        current.copy(imageTranslationConfig = current.imageTranslationConfig.withSharedAIConfig(value, current.aiConfig))
                    }
                }
            }

            override fun selectProvider(provider: ImageTranslationProvider) {
                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    store.appSettingsStore.updateData {
                        val config = it.imageTranslationConfig
                        val firstOpenAIConfig =
                            provider == ImageTranslationProvider.OpenAIImages &&
                                config.settings.none { saved -> saved.provider == ImageTranslationProvider.OpenAIImages }
                        it.copy(
                            imageTranslationConfig =
                                config.copy(
                                    provider = provider,
                                    useSharedAIConfig = if (firstOpenAIConfig) true else config.useSharedAIConfig,
                                ),
                        )
                    }
                }
            }

            override fun setField(
                id: String,
                value: String,
            ) {
                val provider = config.provider
                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    store.appSettingsStore.updateData {
                        val current = it.imageTranslationConfig
                        val edited = current.copy(provider = provider).withValue(id, value)
                        it.copy(imageTranslationConfig = edited.copy(provider = current.provider))
                    }
                }
            }
        }
    }
}
