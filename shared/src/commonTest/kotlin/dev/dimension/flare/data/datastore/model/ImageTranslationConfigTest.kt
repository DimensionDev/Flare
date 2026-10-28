package dev.dimension.flare.data.datastore.model

import dev.dimension.flare.data.translation.image.fingerprint
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class ImageTranslationConfigTest {
    private val basic =
        AppSettings.AiConfig(
            type = AppSettings.AiConfig.Type.OpenAI("https://basic.example/v1", "basic-key", "image-model"),
        )

    @Serializable
    private data class PreviousImageConfig(
        val provider: ImageTranslationProvider,
        val settings: List<ImageTranslationProviderSettings>,
    )

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
    fun leavingSharedModePrefillsPreviouslySavedEmptyConnectionFields() {
        val inherited =
            ImageTranslationConfig(ImageTranslationProvider.OpenAIImages, useSharedAIConfig = true)
                .withValue("baseUrl", "")
                .withValue("apiKey", "")
                .withValue("model", "")
        val detached = inherited.withSharedAIConfig(false, basic)
        assertEquals("https://basic.example/v1", detached.value("baseUrl"))
        assertEquals("basic-key", detached.value("apiKey"))
        assertEquals("image-model", detached.value("model"))
    }

    @Test
    fun leavingSharedModeUsesTheCurrentBasicConnectionInsteadOfAnOldCustomSnapshot() {
        val oldCustom =
            ImageTranslationConfig(ImageTranslationProvider.OpenAIImages)
                .withValue("baseUrl", "https://old-custom.example/v1")
                .withValue("apiKey", "old-custom-key")
                .withValue("model", "old-custom-model")
                .withValue("prompt", "Keep this image prompt")
                .withValue("inputFormat", "Multipart")
        val inherited = oldCustom.withSharedAIConfig(true, basic)
        val changedBasic = basic.copy(type = AppSettings.AiConfig.Type.OpenAI("https://current.example/v1", "current-key", "current-model"))
        val detached = inherited.withSharedAIConfig(false, changedBasic)
        assertEquals("https://current.example/v1", detached.value("baseUrl"))
        assertEquals("current-key", detached.value("apiKey"))
        assertEquals("current-model", detached.value("model"))
        assertEquals("Keep this image prompt", detached.value("prompt"))
        assertEquals("Multipart", detached.value("inputFormat"))
    }

    @Test
    fun modelAndRegionRemainSelectorsWithoutCandidatesAndApiKeysArePlainText() {
        ImageTranslationProvider.entries.forEach { provider ->
            imageTranslationFields(provider).forEach { field ->
                if (field.id == "model" || field.id == "region") {
                    assertTrue(field.copy(suggestions = emptyList(), choices = emptyList(), loading = false).isSelectionField)
                }
                if (field.id == "apiKey") assertFalse(field.secret)
            }
        }
        assertTrue(imageTranslationFields(ImageTranslationProvider.Tencent).first { it.id == "secretKey" }.secret)
    }

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

    @Test
    fun existingImageConfigurationStaysIndependentAfterProtobufAndJsonUpgrade() {
        val previous =
            PreviousImageConfig(
                ImageTranslationProvider.OpenAIImages,
                listOf(
                    ImageTranslationProviderSettings(
                        ImageTranslationProvider.OpenAIImages,
                        mapOf(
                            "apiKey" to "saved-key",
                            "model" to "saved-model",
                        ),
                    ),
                ),
            )
        val decoded =
            listOf(
                ProtoBuf.decodeFromByteArray(
                    ImageTranslationConfig.serializer(),
                    ProtoBuf.encodeToByteArray(PreviousImageConfig.serializer(), previous),
                ),
                Json.decodeFromString(ImageTranslationConfig.serializer(), Json.encodeToString(PreviousImageConfig.serializer(), previous)),
            )
        decoded.forEach {
            assertFalse(it.useSharedAIConfig)
            assertEquals("saved-key", it.resolved(basic).value("apiKey"))
            assertEquals("saved-model", it.resolved(basic).value("model"))
        }
        val shared = decoded.first().copy(useSharedAIConfig = true)
        assertEquals(
            shared,
            ProtoBuf.decodeFromByteArray(
                ImageTranslationConfig.serializer(),
                ProtoBuf.encodeToByteArray(ImageTranslationConfig.serializer(), shared),
            ),
        )
    }

    @Test
    fun sharedConnectionAndModelFollowBasicSettingsAndKeepImageSpecificOptions() {
        val config =
            ImageTranslationConfig(ImageTranslationProvider.OpenAIImages, useSharedAIConfig = true)
                .withValue("apiKey", "old-custom-key")
                .withValue("inputFormat", "Multipart")
                .withValue("prompt", "Image-only prompt")
        val resolved = config.resolved(basic.copy(agentType = AppSettings.AiConfig.Type.OpenAI("agent", "agent-key", "agent-model")))
        assertEquals("https://basic.example/v1", resolved.value("baseUrl"))
        assertEquals("basic-key", resolved.value("apiKey"))
        assertEquals("image-model", resolved.value("model"))
        assertEquals("Multipart", resolved.value("inputFormat"))
        assertEquals("Image-only prompt", resolved.value("prompt"))
        assertEquals(listOf("inputFormat", "prompt"), resolved.editableFields.map { it.id })
        assertTrue(resolved.configured)
        assertEquals("old-custom-key", config.value("apiKey"))
    }

    @Test
    fun customEditsStayIndependentUntilSharingIsEnabledAgain() {
        val config =
            ImageTranslationConfig(
                ImageTranslationProvider.OpenAIImages,
                useSharedAIConfig = true,
            ).withValue("prompt", "Keep this prompt")
        val custom = config.withSharedAIConfig(false, basic).withValue("model", "custom-image-model")
        assertFalse(custom.usesSharedAIConfig)
        assertEquals("basic-key", custom.value("apiKey"))
        assertEquals("https://basic.example/v1", custom.value("baseUrl"))
        assertEquals("Keep this prompt", custom.value("prompt"))
        val changed = basic.copy(type = AppSettings.AiConfig.Type.OpenAI("https://new.example/v1", "new-key", "new-model"))
        assertEquals(custom, custom.resolved(changed))
        val shared = custom.withSharedAIConfig(true, changed)
        assertEquals("new-model", shared.resolved(changed).value("model"))
        val copied = shared.withSharedAIConfig(false, changed)
        assertEquals("https://new.example/v1", copied.value("baseUrl"))
        assertEquals("new-key", copied.value("apiKey"))
        assertEquals("new-model", copied.value("model"))
        assertEquals("Keep this prompt", copied.value("prompt"))
    }

    @Test
    fun settingAlreadyCustomModeDoesNotOverwriteEdits() {
        val custom =
            ImageTranslationConfig(ImageTranslationProvider.OpenAIImages)
                .withValue("baseUrl", "https://custom.example/v1")
                .withValue("apiKey", "custom-key")
                .withValue("model", "custom-image")
        assertEquals(custom, custom.withSharedAIConfig(false, basic))
    }

    @Test
    fun unsupportedBasicConfigurationNeverFallsBackToSavedCustomCredentials() {
        val config =
            ImageTranslationConfig(ImageTranslationProvider.OpenAIImages, useSharedAIConfig = true)
                .withValue("apiKey", "old-custom-key")
                .withValue("model", "old-custom-model")
        val resolved = config.resolved(basic.copy(type = AppSettings.AiConfig.Type.OnDevice))
        assertFalse(resolved.configured)
        assertEquals("", resolved.value("baseUrl"))
        assertEquals("", resolved.value("apiKey"))
        assertEquals("", resolved.value("model"))
    }

    @Test
    fun readinessUsesLocalFieldsAndAbsoluteServiceUrlsOnly() {
        val config =
            ImageTranslationConfig(
                ImageTranslationProvider.OpenAIImages,
            ).withValue("apiKey", "key").withValue("model", "unknown-image")
        assertTrue(config.configured)
        listOf(
            "",
            "relative/v1",
            "service.example/v1",
            "//service.example/v1",
            "ftp://service.example",
            "https://",
            "https://:443/v1",
            "https://bad host/v1",
            "https://user:password@service.example/v1",
            "https://service.example/v1?key=secret",
            "https://service.example/v1?",
            "https://service.example/v1#fragment",
            "https://service.example/v1#",
            "https://service.example:invalid/v1",
        ).forEach { url ->
            assertFalse(config.withValue("baseUrl", url).configured, url)
        }
        listOf("https://service.example/custom/v1/", "http://127.0.0.1:18473/v1").forEach { url ->
            assertTrue(config.withValue("baseUrl", url).configured, url)
        }
        listOf("apiKey", "model", "prompt").forEach { field ->
            assertFalse(config.withValue(field, " ").configured, field)
        }
        assertFalse(config.withValue("inputFormat", "unsupported").configured)
        assertFalse(config.copy(provider = ImageTranslationProvider.Disabled).configured)
        assertFalse(
            config
                .copy(
                    useSharedAIConfig = true,
                ).resolved(basic.copy(type = AppSettings.AiConfig.Type.OpenAI("bad-url", "key", "model")))
                .configured,
        )
    }

    @Test
    fun dedicatedProviderCredentialsStayIndependentOfBasicSettings() {
        listOf(ImageTranslationProvider.Gemini, ImageTranslationProvider.Tencent, ImageTranslationProvider.Qwen).forEach { provider ->
            val config = ImageTranslationConfig(provider, useSharedAIConfig = true).withValue("apiKey", "provider-key")
            assertFalse(config.usesSharedAIConfig)
            assertEquals(config, config.resolved(basic))
            assertEquals(imageTranslationFields(provider), config.editableFields)
        }
    }

    @Test
    fun sharedCacheIdentityTracksConnectionAndModelButIgnoresChatAndAgentOptions() {
        val config = ImageTranslationConfig(ImageTranslationProvider.OpenAIImages, useSharedAIConfig = true)
        val type = basic.type as AppSettings.AiConfig.Type.OpenAI
        val fingerprint = config.resolved(basic).fingerprint()
        listOf(type.copy(serverUrl = "https://new.example/v1"), type.copy(apiKey = "new-key"), type.copy(model = "new-model")).forEach {
            assertNotEquals(fingerprint, config.resolved(basic.copy(type = it)).fingerprint())
        }
        assertEquals(
            fingerprint,
            config
                .resolved(
                    basic.copy(type = type.copy(reasoningEffort = "high", extraBody = "{}"), agentType = type.copy(model = "agent")),
                ).fingerprint(),
        )
        val custom = config.withSharedAIConfig(false, basic)
        assertEquals(custom.fingerprint(), custom.resolved(basic.copy(type = type.copy(model = "new-model"))).fingerprint())
    }
}
