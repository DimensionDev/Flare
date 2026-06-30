package dev.dimension.flare.common

import androidx.compose.ui.awt.ComposeWindow
import java.awt.FileDialog
import java.io.File

internal object DesktopSaveDialog {
    fun chooseFile(
        window: ComposeWindow?,
        defaultName: String,
    ): File? {
        val dialog =
            FileDialog(window).apply {
                mode = FileDialog.SAVE
                file = defaultName
                isVisible = true
            }
        val directory = dialog.directory
        val file = dialog.file
        if (directory.isNullOrBlank() || file.isNullOrBlank()) {
            return null
        }
        return File(directory, file)
    }
}
