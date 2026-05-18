package dev.dimension.flare.data.repository

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.migrateTabSettingsV1ToV2

internal object LegacyTabSettingsV2Migrator : TabSettingsV2Migrator {
    override suspend fun migrate(
        pathProducer: PlatformPathProducer,
        tabSettingsV2Store: DataStore<TabSettingsV2>,
    ) {
        migrateTabSettingsV1ToV2(
            pathProducer = pathProducer,
            tabSettingsV2Store = tabSettingsV2Store,
        )
    }
}
