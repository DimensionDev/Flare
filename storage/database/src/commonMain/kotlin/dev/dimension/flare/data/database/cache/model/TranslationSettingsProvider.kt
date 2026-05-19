package dev.dimension.flare.data.database.cache.model

import kotlinx.coroutines.flow.Flow

public interface TranslationSettingsProvider {
    public val displayOptionsFlow: Flow<TranslationDisplayOptions>
}
