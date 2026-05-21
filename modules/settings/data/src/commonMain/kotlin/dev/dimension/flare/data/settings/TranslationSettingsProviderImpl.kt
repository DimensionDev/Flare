package dev.dimension.flare.data.settings

import dev.dimension.flare.data.database.cache.model.TranslationSettingsProvider
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.settings.TranslationSettingsSupport
import dev.dimension.flare.data.translation.TranslationDisplayOptions
import kotlinx.coroutines.flow.Flow

public class TranslationSettingsProviderImpl(
    private val appDataStore: AppDataStore,
) : TranslationSettingsProvider {
    override val displayOptionsFlow: Flow<TranslationDisplayOptions>
        get() = TranslationSettingsSupport.displayOptionsFlow(appDataStore)
}
