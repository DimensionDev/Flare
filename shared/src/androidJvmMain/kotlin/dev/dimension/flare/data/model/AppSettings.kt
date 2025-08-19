package dev.dimension.flare.data.model

import androidx.datastore.core.Serializer
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
public object AppSettingsSerializer : Serializer<AppSettings> {
    override suspend fun readFrom(input: InputStream): AppSettings = ProtoBuf.decodeFromByteArray(input.readBytes())

    override suspend fun writeTo(
        t: AppSettings,
        output: OutputStream,
    ): Unit =
        withContext(Dispatchers.IO) {
            output.write(ProtoBuf.encodeToByteArray(t))
        }

    override val defaultValue: AppSettings
        get() =
            AppSettings(
                version = "",
            )
}
