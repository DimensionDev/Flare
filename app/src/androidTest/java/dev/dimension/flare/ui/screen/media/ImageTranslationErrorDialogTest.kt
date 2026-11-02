package dev.dimension.flare.ui.screen.media

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.data.translation.image.ImageTranslationError
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.presenter.media.ImageTranslationItem
import dev.dimension.flare.ui.presenter.media.ImageTranslationPresenter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ImageTranslationErrorDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun detailsCanBeViewedAndCopiedOnlyFromTheFailureDialog() {
        val details = "HTTP status: 401\nDetails: Invalid original-key at https://example.invalid/image?signature=value"
        val media = UiMedia.Image("https://example.invalid/source.png", "https://example.invalid/source.png", null, 1f, 1f, false)
        val state = State(ImageTranslationItem(error = ImageTranslationError.Authentication, errorDetails = details))
        compose.setContent { MaterialExpressiveTheme { ImageTranslationErrorDialog(state, media) } }
        compose.onNodeWithText(details).assertDoesNotExist()
        compose.onNodeWithText("View error details").performClick()
        compose.onNodeWithText(details).assertExists()
        compose.onNodeWithText("Copy error details").performClick()
        compose.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(
                details,
                clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.text
                    ?.toString(),
            )
        }
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText(details).assertDoesNotExist()
        compose.onNodeWithText("View error details").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(details, state.entry.errorDetails)
        }
    }

    private class State(
        initial: ImageTranslationItem,
    ) : ImageTranslationPresenter.State {
        var entry by mutableStateOf(initial)
        override val configured = true

        override fun item(media: UiMedia) = entry

        override fun displayed(media: UiMedia) = media

        override fun toggle(media: UiMedia): Unit = error("Viewing details must not start translation")

        override fun regenerate(media: UiMedia): Unit = error("Copying details must not start translation")

        override fun clearError(media: UiMedia) {
            entry = entry.copy(error = null)
        }

        override fun setErrorDetailsVisible(
            media: UiMedia,
            visible: Boolean,
        ) {
            entry =
                entry.copy(error = null, errorDetailsVisible = visible)
        }

        override fun imageFailed(
            media: UiMedia,
            url: String,
        ) = Unit
    }
}
