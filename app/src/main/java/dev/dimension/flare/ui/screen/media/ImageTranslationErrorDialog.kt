package dev.dimension.flare.ui.screen.media

import android.content.ClipData
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.presenter.media.ImageTranslationPresenter
import dev.dimension.flare.ui.screen.settings.imageTranslationText
import kotlinx.coroutines.launch

@Composable
internal fun ImageTranslationErrorDialog(
    translation: ImageTranslationPresenter.State,
    media: UiMedia,
) {
    val item = translation.item(media)
    if (item.error == null && !item.errorDetailsVisible) return
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val title = imageTranslationText(if (item.errorDetailsVisible) "Error details" else "Image translation")
    val copyLabel = imageTranslationText("Copy error details")

    fun dismiss() {
        translation.clearError(media)
        translation.setErrorDetailsVisible(media, false)
    }
    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text(title) },
        text = {
            if (item.errorDetailsVisible) {
                SelectionContainer {
                    Text(item.errorDetails.orEmpty(), Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
                }
            } else {
                item.error?.let { Text(imageTranslationText(it.message)) }
            }
        },
        confirmButton = { TextButton(onClick = ::dismiss) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = {
            item.errorDetails?.let { details ->
                TextButton(onClick = {
                    if (item.errorDetailsVisible) {
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyLabel, details))) }
                    } else {
                        translation.setErrorDetailsVisible(media, true)
                    }
                }) {
                    Text(if (item.errorDetailsVisible) copyLabel else imageTranslationText("View error details"))
                }
            }
        },
    )
}
