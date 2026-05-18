package dev.dimension.flare.data.model.appearance

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.AppearanceSettings
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
public suspend fun migrateAppearanceV1ToV2(
    pathProducer: PlatformPathProducer,
    bagStore: DataStore<AppearanceBag>,
) {
    if (!legacyAppearanceSettingsExists(pathProducer)) return
    if (bagStore.data
            .first()
            .entries
            .isNotEmpty()
    ) {
        deleteLegacyAppearanceSettings(pathProducer)
        return
    }

    val v1 = readLegacyAppearanceSettings(pathProducer)
    if (v1 != null) {
        bagStore.updateData { v1.toPatch().toBag() }
    }
    deleteLegacyAppearanceSettings(pathProducer)
}

internal expect suspend fun legacyAppearanceSettingsExists(pathProducer: PlatformPathProducer): Boolean

internal expect suspend fun readLegacyAppearanceSettings(pathProducer: PlatformPathProducer): AppearanceSettings?

internal expect suspend fun deleteLegacyAppearanceSettings(pathProducer: PlatformPathProducer)
