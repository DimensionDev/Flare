package dev.dimension.flare.data.database.cache.model

import dev.dimension.flare.data.translation.TranslationDisplayOptions
import kotlinx.coroutines.flow.Flow

public interface TranslationSettingsProvider {
    public val displayOptionsFlow: Flow<TranslationDisplayOptions>
}
