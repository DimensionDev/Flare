package dev.dimension.flare.data.datastore.model

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
    val language: String = "",
    val translateConfig: TranslateConfig = TranslateConfig(),
) {
    @Serializable
    public data class TranslateConfig(
        val preTranslate: Boolean = false,
        val provider: Provider = Provider.Google,
    ) {
        @Serializable
        public sealed interface Provider {
            @Serializable
            public data object AI : Provider

            @Serializable
            public data object Google : Provider
        }
    }

    @Serializable
    public data class AiConfig(
        @Deprecated(
            message = "Translation is always enabled.",
            level = DeprecationLevel.ERROR,
        )
        val translation: Boolean = false,
        val tldr: Boolean = false,
        val type: Type = Type.OpenAI("", "", ""),
        val translatePrompt: String = AiPromptDefaults.TRANSLATE_PROMPT,
        val tldrPrompt: String = AiPromptDefaults.TLDR_PROMPT,
        @Deprecated(
            message = "Use AppSettings.translateConfig.preTranslate instead.",
            level = DeprecationLevel.ERROR,
        )
        val preTranslation: Boolean = false,
    ) {
        public companion object {
            // for iOS
            public val default: AiConfig = AiConfig()
        }

        @Serializable
        public sealed interface Type {
            @Serializable
            public data object OnDevice : Type

            @Serializable
            public data class OpenAI(
                val serverUrl: String,
                val apiKey: String,
                val model: String = "",
            ) : Type
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal object AppSettingsSerializer : OkioSerializer<AppSettings> {
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
