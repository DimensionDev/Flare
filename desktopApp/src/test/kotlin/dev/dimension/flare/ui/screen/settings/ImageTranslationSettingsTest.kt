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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.data.datastore.model.imageTranslationFields
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
        compose.runOnIdle {
            assertEquals(ImageTranslationProvider.Tencent, state.config.provider)
            assertEquals("1", state.config.value("mode"))
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

    private fun show(state: State) {
        compose.setContent {
            FlareTheme(isDarkTheme = false) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ImageTranslationSettingsContent(state)
                }
            }
        }
    }

    private class State : ImageTranslationSettingsPresenter.State {
        override var config by mutableStateOf(
            ImageTranslationConfig(ImageTranslationProvider.OpenAIImages).withValue("apiKey", "saved-key"),
        )
        override val providers = ImageTranslationProvider.entries.toList()
        override val fields get() = imageTranslationFields(config.provider)

        override fun selectProvider(provider: ImageTranslationProvider) {
            config = config.copy(provider = provider)
        }

        override fun setField(
            id: String,
            value: String,
        ) {
            config = config.withValue(id, value)
        }
    }
}
