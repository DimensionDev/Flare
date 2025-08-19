package dev.dimension.flare.data.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore

internal val Context.appearanceSettings: DataStore<AppearanceSettings> by dataStore(
    fileName = "appearance_settings.pb",
    serializer = AccountPreferencesSerializer,
)
