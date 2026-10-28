package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.ok
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.presenter.media.ImageTranslationPresenter
import dev.dimension.flare.ui.screen.settings.imageTranslationText
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ImageTranslationActions(
    translation: ImageTranslationPresenter.State,
    media: UiMedia,
) {
    if (media !is UiMedia.Image) return
    val item = translation.item(media)
    Row {
        SubtleButton(
            onClick = { translation.toggle(media) },
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
    item.error?.let { error ->
        ContentDialog(
            title = imageTranslationText("Image translation"),
            visible = true,
            content = { Text(imageTranslationText(error.message), modifier = Modifier.padding(16.dp)) },
            primaryButtonText = stringResource(Res.string.ok),
            onButtonClick = { translation.clearError(media) },
        )
    }
}
