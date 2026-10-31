package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.ok
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.presenter.media.ImageTranslationPresenter
import dev.dimension.flare.ui.screen.settings.imageTranslationText
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.awt.datatransfer.StringSelection

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun ImageTranslationActions(
    translation: ImageTranslationPresenter.State,
    media: UiMedia,
) {
    if (media !is UiMedia.Image) return
    val item = translation.item(media)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Row {
        if (translation.canToggle(media)) {
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
        }
        if (translation.canRegenerate(media)) {
            SubtleButton(
                onClick = { translation.regenerate(media) },
                disabled = item.loading,
            ) { Text(imageTranslationText("Translate again")) }
        }
    }
    if (item.error != null || item.errorDetailsVisible) {
        ContentDialog(
            title = imageTranslationText(if (item.errorDetailsVisible) "Error details" else "Image translation"),
            visible = true,
            content = {
                if (item.errorDetailsVisible) {
                    SelectionContainer {
                        Text(
                            item.errorDetails.orEmpty(),
                            modifier = Modifier.padding(16.dp).heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                } else {
                    item.error?.let { Text(imageTranslationText(it.message), modifier = Modifier.padding(16.dp)) }
                }
            },
            primaryButtonText = stringResource(Res.string.ok),
            secondaryButtonText =
                if (item.errorDetails != null) {
                    imageTranslationText(if (item.errorDetailsVisible) "Copy error details" else "View error details")
                } else {
                    ""
                },
            onButtonClick = { button ->
                if (button == ContentDialogButton.Secondary && item.errorDetails != null) {
                    if (item.errorDetailsVisible) {
                        scope.launch { clipboard.setClipEntry(ClipEntry(StringSelection(item.errorDetails))) }
                    } else {
                        translation.setErrorDetailsVisible(media, true)
                    }
                } else {
                    translation.clearError(media)
                    translation.setErrorDetailsVisible(media, false)
                }
            },
        )
    }
}
