package dev.dimension.flare.common

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.awtClipboard
import java.awt.HeadlessException
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ImageClipboardManager(
    private val onImagePasted: (File) -> Unit,
) : Clipboard {
    private val systemClipboard by lazy {
        try {
            Toolkit.getDefaultToolkit().systemClipboard
        } catch (_: HeadlessException) {
            null
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun getClipEntry(): ClipEntry? {
        if (systemClipboard?.isDataFlavorAvailable(DataFlavor.imageFlavor) == true) {
            systemClipboard?.getData(DataFlavor.imageFlavor)?.let { data ->
                when (data) {
                    is BufferedImage -> {
                        val file = File.createTempFile(Uuid.random().toString(), ".png")
                        javax.imageio.ImageIO.write(data, "png", file)
                        onImagePasted(file)
                    }

                    is Image -> {
                        val bufferedImage = toMaxResolutionBufferedImage(data)
                        val file = File.createTempFile(Uuid.random().toString(), ".png")
                        javax.imageio.ImageIO.write(bufferedImage, "png", file)
                        onImagePasted(file)
                    }
                }
            }
            return null
        } else {
            val transferable = systemClipboard?.getContents(null) ?: return null
            val flavors = transferable.transferDataFlavors
            if (flavors?.size == 0) return null
            return ClipEntry(transferable)
        }
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val transferable = clipEntry?.nativeClipEntry as? Transferable
        if (transferable != null) {
            systemClipboard?.setContents(
                // contents =
                transferable,
                // owner =
                transferable as? ClipboardOwner,
            )
        }
    }

    /**
     * Provides an instance of a platform clipboard.
     * The actual implementation may vary depending on the underlying GUI toolkit.
     * See [awtClipboard] to access [java.awt.datatransfer.Clipboard].
     */
    override val nativeClipboard: NativeClipboard
        get() = systemClipboard ?: error("systemClipboard is not available in headless mode")

    fun toMaxResolutionBufferedImage(image: Image): BufferedImage {
        if (image is MultiResolutionImage) {
            val resolutionVariants = image.resolutionVariants

            val maxImage =
                resolutionVariants
                    .stream()
                    .max(
                        Comparator { i1: Image?, i2: Image? ->
                            val area1 = i1!!.getWidth(null) * i1.getHeight(null)
                            val area2 = i2!!.getWidth(null) * i2.getHeight(null)
                            area1.compareTo(area2)
                        },
                    ).orElse(image)

            return toBufferedImage(maxImage)
        } else {
            return toBufferedImage(image)
        }
    }

    private fun toBufferedImage(img: Image): BufferedImage {
        if (img is BufferedImage) {
            return img
        }
        val bimage =
            BufferedImage(
                img.getWidth(null),
                img.getHeight(null),
                BufferedImage.TYPE_INT_ARGB,
            )
        val g2d = bimage.createGraphics()
        g2d.drawImage(img, 0, 0, null)
        g2d.dispose()
        return bimage
    }
}
