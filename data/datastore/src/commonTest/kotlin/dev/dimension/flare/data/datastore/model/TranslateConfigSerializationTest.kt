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
}

@Serializable
private data class LegacyAppSettings(
    val version: String = "",
    val aiConfig: AppSettings.AiConfig = AppSettings.AiConfig(),
    val language: String = "",
    val translateConfig: LegacyTranslateConfig = LegacyTranslateConfig(),
)

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
