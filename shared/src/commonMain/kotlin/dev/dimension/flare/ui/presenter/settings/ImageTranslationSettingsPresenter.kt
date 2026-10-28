package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationField
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.data.datastore.model.imageTranslationFields
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch

public class ImageTranslationSettingsPresenter : PresenterBase<ImageTranslationSettingsPresenter.State>() {
    private val store by koinInject<AppDataStore>()
    private val persistenceScope by koinInject<kotlinx.coroutines.CoroutineScope>()

    public interface State {
        public val config: ImageTranslationConfig
        public val providers: List<ImageTranslationProvider>
        public val fields: List<ImageTranslationField>

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
        val config = settings.imageTranslationConfig
        return object : State {
            override val config = config
            override val providers = ImageTranslationProvider.entries.toList()
            override val fields = imageTranslationFields(config.provider)

            override fun selectProvider(provider: ImageTranslationProvider) {
                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    store.appSettingsStore.updateData {
                        it.copy(
                            imageTranslationConfig = it.imageTranslationConfig.copy(provider = provider),
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
