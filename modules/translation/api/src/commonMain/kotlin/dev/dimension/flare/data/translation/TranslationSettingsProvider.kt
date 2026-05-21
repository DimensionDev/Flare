package dev.dimension.flare.data.translation

import kotlinx.coroutines.flow.Flow

public interface TranslationSettingsProvider {
    public val displayOptionsFlow: Flow<TranslationDisplayOptions>
}
