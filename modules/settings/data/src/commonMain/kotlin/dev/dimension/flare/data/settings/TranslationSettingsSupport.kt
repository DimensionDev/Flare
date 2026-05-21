package dev.dimension.flare.data.settings

import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.translationProviderCacheKey
import dev.dimension.flare.data.translation.TranslationDisplayOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

public object TranslationSettingsSupport {
    public fun displayOptionsFlow(appDataStore: AppDataStore): Flow<TranslationDisplayOptions> =
        appDataStore.appSettings
            .map(::displayOptions)
            .distinctUntilChanged()

    public fun displayOptions(settings: AppSettings): TranslationDisplayOptions =
        TranslationDisplayOptions(
            translationEnabled = true,
            autoDisplayEnabled = settings.translateConfig.preTranslate,
            providerCacheKey = settings.translationProviderCacheKey(),
        )
}
