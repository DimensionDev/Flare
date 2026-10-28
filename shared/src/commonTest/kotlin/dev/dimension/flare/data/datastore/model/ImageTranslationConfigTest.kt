package dev.dimension.flare.data.datastore.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalSerializationApi::class)
class ImageTranslationConfigTest {
    @Serializable
    private data class PreviousSettings(
        val version: String,
        val aiConfig: AppSettings.AiConfig = AppSettings.AiConfig(),
        val language: String = "",
        val translateConfig: AppSettings.TranslateConfig = AppSettings.TranslateConfig(),
        val linkOpenDefaults: AppSettings.LinkOpenDefaults = AppSettings.LinkOpenDefaults(),
        val mxgaEnabled: Boolean = false,
        val refreshHomeTimelineOnLaunch: Boolean = true,
        val homeTimelineAutoRefreshInterval: TimelineAutoRefreshInterval = TimelineAutoRefreshInterval.DISABLED,
        val rememberHomeTimelinePosition: Boolean = false,
    )

    @Test
    fun oldProtobufAndJsonKeepExistingSettingsAndDisableImageTranslation() {
        val old =
            PreviousSettings(
                "old",
                language = "ja",
                mxgaEnabled = true,
                refreshHomeTimelineOnLaunch = false,
                rememberHomeTimelinePosition = true,
            )
        val decoded = ProtoBuf.decodeFromByteArray(AppSettings.serializer(), ProtoBuf.encodeToByteArray(PreviousSettings.serializer(), old))
        assertEquals("ja", decoded.language)
        assertEquals(true, decoded.mxgaEnabled)
        assertEquals(false, decoded.refreshHomeTimelineOnLaunch)
        assertEquals(true, decoded.rememberHomeTimelinePosition)
        assertEquals(ImageTranslationProvider.Disabled, decoded.imageTranslationConfig.provider)
        assertFalse(Json.decodeFromString(AppSettings.serializer(), """{"version":"old"}""").imageTranslationConfig.configured)
    }

    @Test
    fun switchingProvidersAndRoundTripRetainIndependentCredentials() {
        val openAI = ImageTranslationConfig(ImageTranslationProvider.OpenAIImages).withValue("apiKey", "first").withValue("model", "image")
        val gemini = openAI.copy(provider = ImageTranslationProvider.Gemini).withValue("apiKey", "second")
        val config = gemini.copy(provider = ImageTranslationProvider.OpenAIImages)
        assertEquals("first", config.value("apiKey"))
        assertEquals("second", config.copy(provider = ImageTranslationProvider.Gemini).value("apiKey"))
        assertEquals(
            config,
            ProtoBuf.decodeFromByteArray(
                ImageTranslationConfig.serializer(),
                ProtoBuf.encodeToByteArray(ImageTranslationConfig.serializer(), config),
            ),
        )
    }
}
