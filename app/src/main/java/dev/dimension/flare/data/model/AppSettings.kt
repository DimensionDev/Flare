package dev.dimension.flare.data.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.InputStream
import java.io.OutputStream

@Serializable
internal data class AppSettings(
    val version: String,
    val aiConfig: AiConfig = AiConfig(),
) {
    @Serializable
    data class AiConfig(
        val translation: Boolean = false,
        val tldr: Boolean = true,
    )
}

@OptIn(ExperimentalSerializationApi::class)
private object PreferencesSerializer : Serializer<AppSettings> {
    override suspend fun readFrom(input: InputStream): AppSettings = ProtoBuf.decodeFromByteArray(input.readBytes())

    override suspend fun writeTo(
        t: AppSettings,
        output: OutputStream,
    ) = withContext(Dispatchers.IO) {
        output.write(ProtoBuf.encodeToByteArray(t))
    }

    override val defaultValue: AppSettings
        get() =
            AppSettings(
                version = "",
            )
}

internal val Context.appSettings: DataStore<AppSettings> by dataStore(
    fileName = "app_settings.pb",
    serializer = PreferencesSerializer,
)
