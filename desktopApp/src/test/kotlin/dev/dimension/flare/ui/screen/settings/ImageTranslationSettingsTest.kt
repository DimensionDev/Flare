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
import dev.dimension.flare.data.datastore.model.imageTranslationFields
import dev.dimension.flare.data.translation.image.ImageTranslationError
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.presenter.media.ImageTranslationItem
import dev.dimension.flare.ui.presenter.media.ImageTranslationPresenter
import dev.dimension.flare.ui.presenter.settings.ImageTranslationSettingsPresenter
import dev.dimension.flare.ui.screen.media.ImageTranslationActions
import dev.dimension.flare.ui.theme.FlareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class ImageTranslationSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val media =
        UiMedia.Image(
            url = "https://example.invalid/image.png",
            previewUrl = "https://example.invalid/image.png",
            description = null,
            height = 1f,
            width = 1f,
            sensitive = false,
        )

    @Test
    fun viewerUpdatesTranslationControlsFromLocalReadinessOnly() {
        val state = TranslationState()
        compose.setContent { FlareTheme(isDarkTheme = false) { ImageTranslationActions(state, media) } }
        compose.onNodeWithText("Translate image").assertDoesNotExist()
        compose.onNodeWithText("Translate again").assertDoesNotExist()
        compose.runOnIdle { state.configured = true }
        compose.onNodeWithText("Translate image").assertExists()
        compose.runOnIdle { state.entry = ImageTranslationItem(error = ImageTranslationError.Authentication) }
        compose.onNodeWithText("Translate image").assertExists()
        compose.onNodeWithText("Check the provider credentials and service access.").assertExists()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { state.configured = false }
        compose.onNodeWithText("Translate image").assertDoesNotExist()
        compose.onNodeWithText("Image translation provider").assertDoesNotExist()
        compose.onNodeWithText("Translation settings").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, state.toggles) }
    }

    @Test
    fun unconfiguredViewerKeepsTranslationSwitchAndHidesRegeneration() {
        val state = TranslationState()
        state.entry = ImageTranslationItem(translated = media.copy(url = "file:///translated.png"), showingTranslated = true)
        compose.setContent { FlareTheme(isDarkTheme = false) { ImageTranslationActions(state, media) } }
        compose.onNodeWithText("Translate image").assertDoesNotExist()
        compose.onNodeWithText("Translate again").assertDoesNotExist()
        compose.onNodeWithText("Show original").performClick()
        compose.onNodeWithText("Show translation").performClick()
        compose.onNodeWithText("Show original").assertExists()
        compose.runOnIdle { state.configured = true }
        compose.onNodeWithText("Translate again").assertExists()
        compose.runOnIdle { state.configured = false }
        compose.onNodeWithText("Translate again").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(2, state.toggles)
            assertEquals(0, state.regenerations)
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
        compose.onNodeWithText("Enter manually…").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("ap-beijing").performClick()
        compose.runOnIdle {
            assertEquals(ImageTranslationProvider.Tencent, state.config.provider)
            assertEquals("1", state.config.value("mode"))
            assertEquals("ap-beijing", state.config.value("region"))
        }
    }

    @Test
    fun apiKeyIsVisibleAndEditorOnlyCommitsConfirmedEdits() {
        val state = State()
        show(state)
        compose.onNodeWithContentDescription("API key").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("saved-key")
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextReplacement("discarded")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals("saved-key", state.config.value("apiKey")) }
        compose.onNodeWithContentDescription("API key").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("updated-key")
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals("updated-key", state.config.value("apiKey")) }
        compose.onNodeWithText("updated-key").assertExists()
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
    fun leavingSharedConfigurationPrefillsEvenSavedEmptyFieldsAndRefreshesAfterSharingAgain() {
        val state =
            State(
                ImageTranslationConfig(ImageTranslationProvider.OpenAIImages, useSharedAIConfig = true)
                    .withValue("baseUrl", "")
                    .withValue("apiKey", "")
                    .withValue("model", ""),
            )
        state.modelSuggestions = listOf("basic-model", "custom-image-model")
        show(state)
        compose.onNodeWithContentDescription("API key").assertDoesNotExist()
        compose.onNodeWithText("Image model").assertDoesNotExist()
        compose.onNodeWithContentDescription("Service URL").assertDoesNotExist()
        compose.onNodeWithContentDescription("Translation prompt").assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("basic-key", state.config.value("apiKey")) }
        compose.onNodeWithText("basic-model").performScrollTo().performClick()
        compose.onNodeWithText("custom-image-model").performClick()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).performScrollTo().performClick()
        compose.onNodeWithText("Image model").assertDoesNotExist()
        compose.runOnIdle { assertEquals("basic-model", state.config.value("model")) }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).performClick()
        compose.runOnIdle { assertEquals("basic-model", state.config.value("model")) }
    }

    @Test
    fun sharedConnectionFieldsExpandAndCollapseWithAnIntermediateLayout() {
        val state = State(ImageTranslationConfig(ImageTranslationProvider.OpenAIImages, useSharedAIConfig = true))
        show(state)
        val prompt = compose.onNodeWithText("Translation prompt")
        val closed = prompt.fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { state.setUseSharedConfig(false) }
            compose.mainClock.advanceTimeBy(80)
            val entering = prompt.fetchSemanticsNode().boundsInRoot.top
            compose.mainClock.advanceTimeBy(1_000)
            val opened = prompt.fetchSemanticsNode().boundsInRoot.top
            assertTrue("Expected an intermediate expanded layout: $closed < $entering < $opened", closed < entering && entering < opened)
            compose.runOnIdle { state.setUseSharedConfig(true) }
            compose.mainClock.advanceTimeBy(80)
            val leaving = prompt.fetchSemanticsNode().boundsInRoot.top
            assertTrue("Expected an intermediate collapsed layout: $closed < $leaving < $opened", closed < leaving && leaving < opened)
            compose.mainClock.advanceTimeBy(1_000)
            assertEquals(closed, prompt.fetchSemanticsNode().boundsInRoot.top, 1f)
            compose.onNodeWithContentDescription("API key").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
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
    fun modelSelectorsNeverOfferTextInputWhileLoadingEmptyOrFailed() {
        val state =
            State(
                ImageTranslationConfig(
                    ImageTranslationProvider.OpenAIImages,
                ).withValue("apiKey", "saved-key").withValue("model", "existing-image"),
            )
        state.modelLoading = true
        show(state)
        compose.onNodeWithText("existing-image").performScrollTo().performClick()
        compose.onNodeWithText("Loading models…").assertExists()
        compose.onNodeWithText("Enter manually…").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.runOnIdle {
            state.modelLoading = false
            state.modelSuggestions = listOf("detected-image")
        }
        compose.onNodeWithText("detected-image").performClick()
        compose.runOnIdle {
            assertEquals("detected-image", state.config.value("model"))
            state.modelSuggestions = emptyList()
        }
        compose.onNodeWithText("detected-image").performScrollTo().performClick()
        compose.onNodeWithText("No models").assertExists()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.runOnIdle { state.modelFailed = true }
        compose.onNodeWithText("Failed to load models").assertExists()
        compose.onNodeWithText("Enter manually…").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.runOnIdle { assertEquals("detected-image", state.config.value("model")) }
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

    private class TranslationState : ImageTranslationPresenter.State {
        override var configured by mutableStateOf(false)
        var entry by mutableStateOf(ImageTranslationItem())
        var toggles = 0
        var regenerations = 0

        override fun item(media: UiMedia) = entry

        override fun displayed(media: UiMedia) = if (entry.showingTranslated) entry.translated ?: media else media

        override fun toggle(media: UiMedia) {
            toggles++
            entry = entry.copy(showingTranslated = !entry.showingTranslated)
        }

        override fun regenerate(media: UiMedia) {
            regenerations++
        }

        override fun clearError(media: UiMedia) {
            entry = entry.copy(error = null)
        }

        override fun imageFailed(
            media: UiMedia,
            url: String,
        ) = Unit
    }

    private class State(
        initial: ImageTranslationConfig = ImageTranslationConfig(ImageTranslationProvider.OpenAIImages).withValue("apiKey", "saved-key"),
    ) : ImageTranslationSettingsPresenter.State {
        private var saved by mutableStateOf(initial)
        var modelSuggestions by mutableStateOf(emptyList<String>())
        var modelLoading by mutableStateOf(false)
        var modelFailed by mutableStateOf(false)
        private val basic =
            AppSettings.AiConfig(
                type = AppSettings.AiConfig.Type.OpenAI("https://basic.example/v1", "basic-key", "basic-model"),
            )
        override val config get() = saved.resolved(basic)
        override val providers = ImageTranslationProvider.entries.toList()
        override val fields get() =
            imageTranslationFields(config.provider).map {
                when {
                    it.id == "model" -> {
                        it.copy(suggestions = modelSuggestions, loading = modelLoading, failed = modelFailed)
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
