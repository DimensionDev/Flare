package dev.dimension.flare.shared.image

import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.providers.base.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.AppKit.NSBitmapImageRep
import platform.AppKit.NSColorSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class, CryptographyProviderApi::class)
class MacOSImageCompressorTest {
    @Test
    fun veryWideImageFitsBothDimensions() =
        runTest {
            val result = MacOSImageCompressor().compress(wideTestPng, 100_000, 1000 to 1000)
            assertEquals(ImageMetadata(1000, 1), imageMetadata(result))
        }

    @Test
    fun transparentImagesUseWhiteBackground() =
        runTest {
            val result = MacOSImageCompressor().compress(transparentTestPng, 100_000, 20 to 20)
            val bitmap = NSBitmapImageRep.imageRepWithData(result.toNSData()) as NSBitmapImageRep
            val color = assertNotNull(bitmap.colorAtX(0, 0)?.colorUsingColorSpace(NSColorSpace.genericRGBColorSpace))
            assertTrue(color.redComponent > 0.96 && color.greenComponent > 0.96 && color.blueComponent > 0.96)
        }

    @Test
    fun impossibleSizeFailsInsteadOfReturningOversizedData() =
        runTest {
            assertFailsWith<IllegalStateException> { MacOSImageCompressor().compress(transparentTestPng, 1, 20 to 20) }
        }
}
