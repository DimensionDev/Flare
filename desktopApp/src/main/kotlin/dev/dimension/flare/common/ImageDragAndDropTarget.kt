package dev.dimension.flare.common

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ImageDragAndDropTarget(
    private val onImageDropped: (List<File>) -> Unit,
) : DragAndDropTarget {
    @OptIn(ExperimentalComposeUiApi::class, ExperimentalUuidApi::class)
    override fun onDrop(event: DragAndDropEvent): Boolean {
        event.awtTransferable.let {
            if (it.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                it
                    .getTransferData(DataFlavor.imageFlavor)
                    .let { image ->
                        if (image is BufferedImage) {
                            val imageFiles = mutableListOf<File>()
                            val tempFile = File.createTempFile(Uuid.random().toString(), ".png")
                            javax.imageio.ImageIO.write(image, "png", tempFile)
                            imageFiles.add(tempFile)
                            onImageDropped(imageFiles)
                        }
                    }
                return true
            } else if (it.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                it
                    .getTransferData(DataFlavor.javaFileListFlavor)
                    .let { files ->
                        if (files is List<*>) {
                            val imageFiles =
                                files
                                    .filterIsInstance<File>()
                                    .filter {
                                        it.extension in
                                            listOf(
                                                "jpg",
                                                "jpeg",
                                                "png",
                                                "gif",
                                                "webp",
                                            )
                                    }
                            if (imageFiles.isNotEmpty()) {
                                onImageDropped(imageFiles)
                            }
                        }
                    }
                return true
            }
        }
        return false
    }
}
