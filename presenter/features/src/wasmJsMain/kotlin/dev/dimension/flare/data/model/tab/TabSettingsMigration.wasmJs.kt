package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.TabSettings

@Suppress("UNUSED_PARAMETER")
internal actual suspend fun legacyTabSettingsExists(pathProducer: PlatformPathProducer): Boolean = false

@Suppress("UNUSED_PARAMETER")
internal actual suspend fun readLegacyTabSettings(pathProducer: PlatformPathProducer): TabSettings? = null

@Suppress("UNUSED_PARAMETER")
internal actual suspend fun deleteLegacyTabSettings(pathProducer: PlatformPathProducer) {
}
