package dev.dimension.flare.data.translation

import dev.dimension.flare.data.datastore.model.AppSettings

internal fun aiPreTranslateConfig(preTranslate: Boolean = true): AppSettings.TranslateConfig =
    AppSettings.TranslateConfig(
        preTranslate = preTranslate,
        provider = AppSettings.TranslateConfig.Provider.AI,
    )
