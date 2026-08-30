@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.appkit

import dev.dimension.flare.ui.foundation.HorizontalAlignment
import dev.dimension.flare.ui.foundation.VerticalAlignment
import platform.AppKit.NSLayoutAttributeBottom
import platform.AppKit.NSLayoutAttributeTrailing
import kotlin.test.Test
import kotlin.test.assertEquals

public class AppKitFoundationLayoutTest {
    @Test
    public fun mapsSharedSpacingAlignmentAndMultilineText() {
        val column = AppKitColumnWidget()
        val row = AppKitRowWidget()
        val text = AppKitTextWidget()

        column.setSpacing(12f)
        column.setHorizontalAlignment(HorizontalAlignment.End)
        row.setSpacing(8f)
        row.setVerticalAlignment(VerticalAlignment.Bottom)

        assertEquals(12.0, column.view.spacing)
        assertEquals(NSLayoutAttributeTrailing, column.view.alignment)
        assertEquals(8.0, row.view.spacing)
        assertEquals(NSLayoutAttributeBottom, row.view.alignment)
        assertEquals(0L, text.view.maximumNumberOfLines)
    }
}
