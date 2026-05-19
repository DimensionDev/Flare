package dev.dimension.flare.data.model.appearance

import dev.dimension.flare.data.io.PlatformPathProducer

internal actual suspend fun legacyAppearanceSettingsExists(pathProducer: PlatformPathProducer): Boolean = false

internal actual suspend fun readLegacyAppearanceSettings(pathProducer: PlatformPathProducer): LegacyAppearanceSettings? = null

internal actual suspend fun deleteLegacyAppearanceSettings(pathProducer: PlatformPathProducer) {
}
