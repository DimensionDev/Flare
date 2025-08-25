package dev.dimension.flare.data.model

import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.BufferedSink
import okio.BufferedSource

@Serializable
public data class AppSettings(
    val version: String,
    val aiConfig: AiConfig = AiConfig(),
) {
    @Serializable
    public data class AiConfig(
        val translation: Boolean = false,
        val tldr: Boolean = true,
    )
}

@OptIn(ExperimentalSerializationApi::class)
public object AppSettingsSerializer : OkioSerializer<AppSettings> {
    override val defaultValue: AppSettings
        get() =
            AppSettings(
                version = "",
            )

    override suspend fun readFrom(source: BufferedSource): AppSettings =
        withContext(Dispatchers.IO) {
            ProtoBuf.decodeFromByteArray(source.readByteArray())
        }

    override suspend fun writeTo(
        t: AppSettings,
        sink: BufferedSink,
    ) {
        withContext(Dispatchers.IO) {
            sink.write(ProtoBuf.encodeToByteArray(t))
        }
    }
}
