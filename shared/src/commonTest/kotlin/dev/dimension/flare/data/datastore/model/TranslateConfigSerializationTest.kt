package dev.dimension.flare.data.datastore.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalSerializationApi::class)
class TranslateConfigSerializationTest {
    @Test
    fun decodesLegacyGoogleProviderAsGoogleWeb() {
        val bytes =
            ProtoBuf.encodeToByteArray(
                serializer = LegacyAppSettings.serializer(),
                LegacyAppSettings(
                    version = "legacy",
                    translateConfig =
                        LegacyTranslateConfig(
                            preTranslate = true,
                            provider = LegacyTranslateConfig.Provider.Google,
                        ),
                ),
            )

        val decoded = ProtoBuf.decodeFromByteArray(AppSettings.serializer(), bytes)

        assertEquals(true, decoded.translateConfig.preTranslate)
        assertEquals(emptyList(), decoded.translateConfig.autoTranslateExcludedLanguages)
        assertIs<AppSettings.TranslateConfig.Provider.GoogleWeb>(decoded.translateConfig.provider)
    }

    @Test
    fun encodesAndDecodesDedicatedProviders() {
        val deepL =
            AppSettings(
                version = "1",
                translateConfig =
                    AppSettings.TranslateConfig(
                        preTranslate = true,
                        provider =
                            AppSettings.TranslateConfig.Provider.DeepL(
                                apiKey = "deepl-key",
                                usePro = true,
                            ),
                    ),
            )
        val googleCloud =
            AppSettings(
                version = "1",
                translateConfig =
                    AppSettings.TranslateConfig(
                        provider =
                            AppSettings.TranslateConfig.Provider.GoogleCloud(
                                apiKey = "gcp-key",
                            ),
                    ),
            )
        val libreTranslate =
            AppSettings(
                version = "1",
                translateConfig =
                    AppSettings.TranslateConfig(
                        autoTranslateExcludedLanguages = listOf("en", "ja-JP"),
                        provider =
                            AppSettings.TranslateConfig.Provider.LibreTranslate(
                                baseUrl = "https://translate.example.com",
                                apiKey = "libre-key",
                            ),
                    ),
            )

        val deepLRoundTrip =
            ProtoBuf.decodeFromByteArray(
                AppSettings.serializer(),
                ProtoBuf.encodeToByteArray(AppSettings.serializer(), deepL),
            )
        val googleCloudRoundTrip =
            ProtoBuf.decodeFromByteArray(
                AppSettings.serializer(),
                ProtoBuf.encodeToByteArray(AppSettings.serializer(), googleCloud),
            )
        val libreTranslateRoundTrip =
            ProtoBuf.decodeFromByteArray(
                AppSettings.serializer(),
                ProtoBuf.encodeToByteArray(AppSettings.serializer(), libreTranslate),
            )

        assertEquals(
            AppSettings.TranslateConfig.Provider.DeepL(
                apiKey = "deepl-key",
                usePro = true,
            ),
            deepLRoundTrip.translateConfig.provider,
        )
        assertEquals(
            AppSettings.TranslateConfig.Provider.GoogleCloud(
                apiKey = "gcp-key",
            ),
            googleCloudRoundTrip.translateConfig.provider,
        )
        assertEquals(
            listOf("en", "ja-JP"),
            libreTranslateRoundTrip.translateConfig.autoTranslateExcludedLanguages,
        )
        assertEquals(
            AppSettings.TranslateConfig.Provider.LibreTranslate(
                baseUrl = "https://translate.example.com",
                apiKey = "libre-key",
            ),
            libreTranslateRoundTrip.translateConfig.provider,
        )
    }

    @Test
    fun decodesLegacyOpenAIConfigWithoutReasoningEffort() {
        val bytes =
            ProtoBuf.encodeToByteArray(
                serializer = LegacyOpenAISettings.serializer(),
                LegacyOpenAISettings(
                    version = "legacy",
                    aiConfig =
                        LegacyAiConfig(
                            type =
                                LegacyType.LegacyOpenAI(
                                    serverUrl = "https://api.openai.com/v1/",
                                    apiKey = "test-key",
                                    model = "gpt-5-mini",
                                ),
                        ),
                ),
            )

        val decoded = ProtoBuf.decodeFromByteArray(AppSettings.serializer(), bytes)
        val openAI = decoded.aiConfig.type as? AppSettings.AiConfig.Type.OpenAI

        assertEquals("https://api.openai.com/v1/", openAI?.serverUrl)
        assertEquals("test-key", openAI?.apiKey)
        assertEquals("gpt-5-mini", openAI?.model)
        assertEquals("", openAI?.reasoningEffort)
    }
}

@Serializable
private data class LegacyAppSettings(
    val version: String = "",
    val aiConfig: AppSettings.AiConfig = AppSettings.AiConfig(),
    val language: String = "",
    val translateConfig: LegacyTranslateConfig = LegacyTranslateConfig(),
)

@Serializable
private data class LegacyOpenAISettings(
    val version: String = "",
    val aiConfig: LegacyAiConfig = LegacyAiConfig(),
    val language: String = "",
    val translateConfig: AppSettings.TranslateConfig = AppSettings.TranslateConfig(),
)

@Serializable
private data class LegacyAiConfig(
    val translation: Boolean = false,
    val tldr: Boolean = false,
    val type: LegacyType = LegacyType.LegacyOpenAI(),
    val translatePrompt: String = AiPromptDefaults.TRANSLATE_PROMPT,
    val tldrPrompt: String = AiPromptDefaults.TLDR_PROMPT,
    val preTranslation: Boolean = false,
)

@Serializable
private sealed interface LegacyType {
    @Serializable
    @SerialName("dev.dimension.flare.data.datastore.model.AppSettings.AiConfig.Type.OnDevice")
    data object OnDevice : LegacyType

    @Serializable
    @SerialName("dev.dimension.flare.data.datastore.model.AppSettings.AiConfig.Type.OpenAI")
    data class LegacyOpenAI(
        val serverUrl: String = "",
        val apiKey: String = "",
        val model: String = "",
    ) : LegacyType
}

@Serializable
private data class LegacyTranslateConfig(
    val preTranslate: Boolean = false,
    val provider: Provider = Provider.Google,
) {
    @Serializable
    sealed interface Provider {
        @Serializable
        @SerialName("AI")
        data object AI : Provider

        @Serializable
        @SerialName("Google")
        data object Google : Provider
    }
}
