package dev.dimension.flare.common

import androidx.compose.ui.awt.ComposeWindow
import java.awt.FileDialog
import java.io.File
import javax.swing.JFileChooser

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

    fun chooseDirectory(window: ComposeWindow?): File? {
        val chooser =
            JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
            }
        return if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile
        } else {
            null
        }
    }
}
