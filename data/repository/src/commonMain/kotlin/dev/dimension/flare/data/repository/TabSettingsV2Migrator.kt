package dev.dimension.flare.data.repository

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.tab.TabSettingsV2

public interface TabSettingsV2Migrator {
    public suspend fun migrate(
        pathProducer: PlatformPathProducer,
        tabSettingsV2Store: DataStore<TabSettingsV2>,
    )
}

public object NoOpTabSettingsV2Migrator : TabSettingsV2Migrator {
    override suspend fun migrate(
        pathProducer: PlatformPathProducer,
        tabSettingsV2Store: DataStore<TabSettingsV2>,
    ) {
    }
}
