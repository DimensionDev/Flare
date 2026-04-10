package dev.dimension.flare.data.datastore.model

import androidx.datastore.core.okio.OkioSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
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
        val provider: Provider = Provider.GoogleWeb,
        val autoTranslateExcludedLanguages: List<String> = emptyList(),
    ) {
        @Serializable
        public sealed interface Provider {
            @Serializable
            @SerialName("AI")
            public data object AI : Provider

            @Serializable
            @SerialName("Google")
            public data object GoogleWeb : Provider

            @Serializable
            public data class DeepL(
                val apiKey: String = "",
                val usePro: Boolean = false,
            ) : Provider

            @Serializable
            public data class GoogleCloud(
                val apiKey: String = "",
            ) : Provider

            @Serializable
            public data class LibreTranslate(
                val baseUrl: String = "",
                val apiKey: String = "",
            ) : Provider
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
        ProtoBuf.decodeFromByteArray(source.readByteArray())

    override suspend fun writeTo(
        t: AppSettings,
        sink: BufferedSink,
    ) {
        sink.write(ProtoBuf.encodeToByteArray(t))
    }
}
