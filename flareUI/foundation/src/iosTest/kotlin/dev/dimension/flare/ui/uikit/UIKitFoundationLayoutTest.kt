@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.foundation.HorizontalAlignment
import dev.dimension.flare.ui.foundation.VerticalAlignment
import platform.UIKit.UIStackViewAlignmentBottom
import platform.UIKit.UIStackViewAlignmentTrailing
import kotlin.test.Test
import kotlin.test.assertEquals

public class UIKitFoundationLayoutTest {
    @Test
    public fun mapsSharedSpacingAlignmentAndMultilineText() {
        val column = UIKitColumnWidget()
        val row = UIKitRowWidget()
        val text = UIKitTextWidget()

        column.setSpacing(12f)
        column.setHorizontalAlignment(HorizontalAlignment.End)
        row.setSpacing(8f)
        row.setVerticalAlignment(VerticalAlignment.Bottom)

        assertEquals(12.0, column.view.spacing)
        assertEquals(UIStackViewAlignmentTrailing, column.view.alignment)
        assertEquals(8.0, row.view.spacing)
        assertEquals(UIStackViewAlignmentBottom, row.view.alignment)
        assertEquals(0L, text.view.numberOfLines)
    }
}
