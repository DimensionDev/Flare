package dev.dimension.flare.ui.screen.settings

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.R
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.ui.presenter.settings.ImageTranslationSettingsPresenter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3ExpressiveApi::class)
class ImageTranslationSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val mediaQueryIntegration = ComposeUiFlags.isMediaQueryIntegrationEnabled

    @Before fun setUp() {
        ComposeUiFlags.isMediaQueryIntegrationEnabled = true
    }

    @After fun tearDown() {
        ComposeUiFlags.isMediaQueryIntegrationEnabled = mediaQueryIntegration
    }

    @Test
    fun providerAndChoiceFieldsUseInlineMenus() {
        val state = State()
        show(state)
        compose.onNodeWithText(label(R.string.image_translation_automatic)).performScrollTo().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.image_translation_original_file)).performClick()
        compose.runOnIdle { assertEquals("Multipart", state.config.value("inputFormat")) }
        compose.onNodeWithText(label(R.string.image_translation_openai)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.image_translation_tencent)).performClick()
        compose.onNodeWithText(label(R.string.image_translation_pro)).performScrollTo().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.image_translation_lite)).performClick()
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
        compose.onNodeWithText(label(R.string.image_translation_api_key)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNode(hasSetTextAction()).performTextReplacement("discarded")
        compose.onNodeWithText(label(android.R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals("saved-key", state.config.value("apiKey")) }
        compose.onNodeWithText(label(R.string.image_translation_api_key)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("updated-key")
        compose.onNodeWithText(label(android.R.string.ok)).performClick()
        compose.runOnIdle { assertEquals("updated-key", state.config.value("apiKey")) }
        compose.onNodeWithText("updated-key").assertDoesNotExist()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.externalCacheDir, "image-translation-settings.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

    @Test
    fun sharedConfigurationHidesConnectionFieldsAndCustomModeKeepsItsImageModel() {
        val state = State(ImageTranslationConfig(ImageTranslationProvider.OpenAIImages, useSharedAIConfig = true))
        show(state)
        compose.onNodeWithText(label(R.string.image_translation_api_key)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.image_translation_model)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.image_translation_service_url)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.image_translation_prompt)).assertExists()
        compose.onNodeWithText(label(R.string.settings_ai_config_use_shared)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("basic-key", state.config.value("apiKey")) }
        compose.onNodeWithText(label(R.string.image_translation_model)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("custom-image-model")
        compose.onNodeWithText(label(android.R.string.ok)).performClick()
        compose.onNodeWithText(label(R.string.settings_ai_config_use_shared)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.image_translation_model)).assertDoesNotExist()
        compose.runOnIdle { assertEquals("basic-model", state.config.value("model")) }
        compose.onNodeWithText(label(R.string.settings_ai_config_use_shared)).performClick()
        compose.runOnIdle { assertEquals("custom-image-model", state.config.value("model")) }
    }

    @Test
    fun discoveredModelsCanBeSelectedAndManualInputSurvivesLoadingAndEmptyResults() {
        val state = State()
        state.modelLoading = true
        show(state)
        compose.onNodeWithText(label(R.string.image_translation_model)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.settings_ai_config_model_loading)).assertExists()
        compose.onNodeWithText(label(R.string.image_translation_manual_input)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("manual-image")
        compose.onNodeWithText(label(android.R.string.ok)).performClick()
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
        compose.onNodeWithText(label(R.string.image_translation_model)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("fallback-image")
        compose.onNodeWithText(label(android.R.string.ok)).performClick()
        compose.runOnIdle { assertEquals("fallback-image", state.config.value("model")) }
    }

    private fun show(state: State) {
        compose.setContent {
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) { ImageTranslationSettingsSection(state) }
            }
        }
    }

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

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
                if (it.id == "model") it.copy(suggestions = modelSuggestions, loading = modelLoading) else it
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
