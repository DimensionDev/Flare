package dev.dimension.flare.data.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore

internal val Context.appSettings: DataStore<AppSettings> by dataStore(
    fileName = "app_settings.pb",
    serializer = AppSettingsSerializer,
)
