package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.data.translation.image.ImageTranslationError
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.presenter.media.ImageTranslationItem
import dev.dimension.flare.ui.presenter.media.ImageTranslationPresenter
import dev.dimension.flare.ui.presenter.settings.ImageTranslationSettingsPresenter
import dev.dimension.flare.ui.screen.media.ImageTranslationActions
import dev.dimension.flare.ui.theme.FlareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class ImageTranslationSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun unconfiguredViewerOnlyShowsHintAndNeverOpensConfiguration() {
        var requested = 0
        var error by mutableStateOf<ImageTranslationError?>(null)
        val media =
            UiMedia.Image(
                url = "https://example.invalid/image.png",
                previewUrl = "https://example.invalid/image.png",
                description = null,
                height = 1f,
                width = 1f,
                sensitive = false,
            )
        val translation =
            object : ImageTranslationPresenter.State {
                override val configured = false

                override fun item(media: UiMedia) = ImageTranslationItem(error = error)

                override fun displayed(media: UiMedia) = media

                override fun toggle(media: UiMedia) {
                    requested++
                    error = ImageTranslationError.NotConfigured
                }

                override fun regenerate(media: UiMedia) = Unit

                override fun clearError(media: UiMedia) {
                    error = null
                }

                override fun imageFailed(
                    media: UiMedia,
                    url: String,
                ) = Unit
            }
        compose.setContent { FlareTheme(isDarkTheme = false) { ImageTranslationActions(translation, media) } }
        compose.onNodeWithText("Translate image").performClick()
        compose.onNodeWithText("Configure image translation in Translation settings first.").assertExists()
        compose.onNodeWithText("Image translation provider").assertDoesNotExist()
        compose.onNodeWithText("Translation settings").assertDoesNotExist()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle {
            assertEquals(1, requested)
            assertEquals(null, error)
        }
    }

    @Test
    fun providerAndChoiceFieldsUseInlineMenus() {
        val state = State()
        show(state)
        compose.onNodeWithText("Automatic").performScrollTo().performClick()
        compose.onNodeWithText("OK").assertDoesNotExist()
        compose.onNodeWithText("Original file").performClick()
        compose.runOnIdle { assertEquals("Multipart", state.config.value("inputFormat")) }
        compose.onNodeWithText("OpenAI Images compatible").performScrollTo().performClick()
        compose.onNodeWithText("Tencent Cloud").performClick()
        compose.onNodeWithText("Pro").performScrollTo().performClick()
        compose.onNodeWithText("Lite").performClick()
        compose.onNodeWithText("ap-guangzhou").performScrollTo().performClick()
        compose.onNodeWithText("ap-beijing").performClick()
        compose.runOnIdle {
            assertEquals(ImageTranslationProvider.Tencent, state.config.provider)
            assertEquals("1", state.config.value("mode"))
            assertEquals("ap-beijing", state.config.value("region"))
        }
    }

    @Test
    fun commonEditorMasksCredentialsAndOnlyCommitsConfirmedEdits() {
        val state = State()
        show(state)
        compose.onNodeWithContentDescription("API key").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNode(hasSetTextAction()).performTextReplacement("discarded")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals("saved-key", state.config.value("apiKey")) }
        compose.onNodeWithContentDescription("API key").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("updated-key")
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals("updated-key", state.config.value("apiKey")) }
        compose.onNodeWithText("updated-key").assertDoesNotExist()
        compose.onNodeWithContentDescription("Service URL").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("https://images.example/v1")
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals("https://images.example/v1", state.config.value("baseUrl")) }
        ImageIO.write(
            compose.onRoot().captureToImage().toAwtImage(),
            "png",
            File(System.getProperty("java.io.tmpdir"), "flare-image-translation-settings-desktop.png"),
        )
    }

    @Test
    fun sharedConfigurationHidesConnectionFieldsAndCustomModeKeepsItsImageModel() {
        val state = State(ImageTranslationConfig(ImageTranslationProvider.OpenAIImages, useSharedAIConfig = true))
        show(state)
        compose.onNodeWithContentDescription("API key").assertDoesNotExist()
        compose.onNodeWithContentDescription("Image model").assertDoesNotExist()
        compose.onNodeWithContentDescription("Service URL").assertDoesNotExist()
        compose.onNodeWithContentDescription("Translation prompt").assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("basic-key", state.config.value("apiKey")) }
        compose.onNodeWithContentDescription("Image model").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("custom-image-model")
        compose.onNodeWithText("OK").performClick()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Image model").assertDoesNotExist()
        compose.runOnIdle { assertEquals("basic-model", state.config.value("model")) }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).performClick()
        compose.runOnIdle { assertEquals("custom-image-model", state.config.value("model")) }
    }

    @Test
    fun customConnectionEditorPrefillsCurrentSettingsAndCommitsServerSuggestionsOnlyOnSave() {
        val state = State(ImageTranslationConfig(ImageTranslationProvider.OpenAIImages, useSharedAIConfig = true))
        show(state)
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Service URL").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("https://basic.example/v1")
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("api.openai")
        compose.onNodeWithText("https://api.openai.com/v1/").performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("https://api.openai.com/v1/")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals("https://basic.example/v1", state.config.value("baseUrl")) }
        compose.onNodeWithContentDescription("Service URL").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("https://basic.example/v1")
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("api.openai")
        compose.onNodeWithText("https://api.openai.com/v1/").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle {
            assertEquals("https://api.openai.com/v1/", state.config.value("baseUrl"))
            assertEquals("basic-key", state.config.value("apiKey"))
            assertEquals("basic-model", state.config.value("model"))
        }
    }

    @Test
    fun discoveredModelsCanBeSelectedAndManualInputSurvivesLoadingAndEmptyResults() {
        val state = State()
        state.modelLoading = true
        show(state)
        compose.onNodeWithText("Not set").performScrollTo().performClick()
        compose.onNodeWithText("Loading models…").assertExists()
        compose.onNodeWithText("Enter manually…").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("manual-image")
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle {
            state.modelLoading = false
            state.modelSuggestions = listOf("detected-image")
            assertEquals("manual-image", state.config.value("model"))
        }
        compose.onNodeWithText("manual-image").performScrollTo().performClick()
        compose.onNodeWithText("detected-image").performClick()
        compose.runOnIdle {
            assertEquals("detected-image", state.config.value("model"))
            state.modelSuggestions = emptyList()
        }
        compose.onNodeWithContentDescription("Image model").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("fallback-image")
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals("fallback-image", state.config.value("model")) }
    }

    private fun show(state: State) {
        compose.setContent {
            FlareTheme(isDarkTheme = false) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ImageTranslationSettingsContent(state)
                }
            }
        }
    }

    private class State(
        initial: ImageTranslationConfig = ImageTranslationConfig(ImageTranslationProvider.OpenAIImages).withValue("apiKey", "saved-key"),
    ) : ImageTranslationSettingsPresenter.State {
        private var saved by mutableStateOf(initial)
        var modelSuggestions by mutableStateOf(emptyList<String>())
        var modelLoading by mutableStateOf(false)
        private val basic =
            AppSettings.AiConfig(
                type = AppSettings.AiConfig.Type.OpenAI("https://basic.example/v1", "basic-key", "basic-model"),
            )
        override val config get() = saved.resolved(basic)
        override val providers = ImageTranslationProvider.entries.toList()
        override val fields get() =
            config.editableFields.map {
                when {
                    it.id == "model" -> {
                        it.copy(suggestions = modelSuggestions, loading = modelLoading)
                    }

                    it.id == "baseUrl" && config.provider == ImageTranslationProvider.OpenAIImages -> {
                        it.copy(editSuggestions = listOf("https://api.openai.com/v1/"))
                    }

                    else -> {
                        it
                    }
                }
            }
        override val useSharedConfig get() = config.usesSharedAIConfig

        override fun setUseSharedConfig(value: Boolean) {
            saved = saved.withSharedAIConfig(value, basic)
        }

        override fun selectProvider(provider: ImageTranslationProvider) {
            saved = saved.copy(provider = provider)
        }

        override fun setField(
            id: String,
            value: String,
        ) {
            saved = saved.withValue(id, value)
        }
    }
}
