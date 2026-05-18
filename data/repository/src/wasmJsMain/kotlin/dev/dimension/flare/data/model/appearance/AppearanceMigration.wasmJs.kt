package dev.dimension.flare.data.model.appearance

import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.AppearanceSettings

internal actual suspend fun legacyAppearanceSettingsExists(pathProducer: PlatformPathProducer): Boolean = false

internal actual suspend fun readLegacyAppearanceSettings(pathProducer: PlatformPathProducer): AppearanceSettings? = null

internal actual suspend fun deleteLegacyAppearanceSettings(pathProducer: PlatformPathProducer) {
}
