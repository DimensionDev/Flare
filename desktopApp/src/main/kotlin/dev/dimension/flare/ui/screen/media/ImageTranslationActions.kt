package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.presenter.media.ImageTranslationPresenter
import dev.dimension.flare.ui.screen.settings.ImageTranslationSettingsContent
import dev.dimension.flare.ui.screen.settings.imageTranslationText
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text

@Composable
internal fun ImageTranslationActions(
    translation: ImageTranslationPresenter.State,
    media: UiMedia,
) {
    if (media !is UiMedia.Image) return
    var showSettings by remember { mutableStateOf(false) }
    val item = translation.item(media)
    Row {
        SubtleButton(
            onClick = { if (translation.configured) translation.toggle(media) else showSettings = true },
            disabled = item.loading,
        ) {
            Text(
                imageTranslationText(
                    if (item.showingTranslated) {
                        "Show original"
                    } else if (item.translated !=
                        null
                    ) {
                        "Show translation"
                    } else {
                        "Translate image"
                    },
                ),
            )
        }
        if (item.translated != null) {
            SubtleButton(
                onClick = { translation.regenerate(media) },
                disabled = item.loading,
            ) { Text(imageTranslationText("Translate again")) }
        }
    }
    if (showSettings) {
        ContentDialog(
            title = imageTranslationText("Image translation"),
            visible = true,
            content = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) { ImageTranslationSettingsContent() }
            },
            primaryButtonText = imageTranslationText("Save"),
            closeButtonText = imageTranslationText("Cancel"),
            onButtonClick = { showSettings = false },
        )
    }
    item.error?.let { error ->
        ContentDialog(
            title = imageTranslationText("Image translation"),
            visible = true,
            content = { Text(imageTranslationText(error.message), modifier = Modifier.padding(16.dp)) },
            primaryButtonText = imageTranslationText("Translation settings"),
            closeButtonText = imageTranslationText("Cancel"),
            onButtonClick = { button ->
                translation.clearError(media)
                if (button == ContentDialogButton.Primary) showSettings = true
            },
        )
    }
}
