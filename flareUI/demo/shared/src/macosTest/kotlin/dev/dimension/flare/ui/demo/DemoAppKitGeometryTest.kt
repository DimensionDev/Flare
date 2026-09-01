@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.demo

import kotlinx.cinterop.useContents
import platform.AppKit.NSApplication
import platform.AppKit.NSBackingStoreBuffered
import platform.AppKit.NSView
import platform.AppKit.NSWindow
import platform.AppKit.NSWindowStyleMaskBorderless
import platform.AppKit.alignmentRectForFrame
import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSThread
import platform.Foundation.valueForKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

public class DemoAppKitGeometryTest {
    @Test
    public fun catalogImageAndTitleShareTheirLeadingEdge() {
        assertTrue(NSThread.isMainThread)
        NSApplication.sharedApplication
        val window =
            NSWindow(
                contentRect = CGRectMake(0.0, 0.0, HOST_WIDTH, HOST_HEIGHT),
                styleMask = NSWindowStyleMaskBorderless,
                backing = NSBackingStoreBuffered,
                defer = false,
            )
        val root = NSView(frame = CGRectMake(0.0, 0.0, HOST_WIDTH, HOST_HEIGHT))
        window.contentView = root
        val host = FlareDemoHost()

        try {
            host.view.frame = root.bounds
            root.addSubview(host.view)

            var image: NSView? = null
            var title: NSView? = null
            awaitDemoLayout("AppKit demo did not create the catalog hierarchy.") {
                root.layoutSubtreeIfNeeded()
                image = root.findTaggedView(CATALOG_IMAGE_TAG)
                title = root.findTaggedView(CATALOG_TITLE_TAG)
                image != null && title != null
            }
            root.layoutSubtreeIfNeeded()

            val catalogImage = checkNotNull(image)
            val catalogTitle = checkNotNull(title)
            assertEquals(catalogImage.superview, catalogTitle.superview)

            val imageFrame = catalogImage.frame
            imageFrame.useContents {
                assertEquals(IMAGE_SIZE, size.width, absoluteTolerance = FRAME_TOLERANCE)
                assertEquals(IMAGE_SIZE, size.height, absoluteTolerance = FRAME_TOLERANCE)
            }
            val imageLeading = catalogImage.alignmentRectForFrame(imageFrame).useContents { origin.x }
            val titleFrame = catalogTitle.frame
            val titleLeading = catalogTitle.alignmentRectForFrame(titleFrame).useContents { origin.x }
            assertEquals(imageLeading, titleLeading, absoluteTolerance = FRAME_TOLERANCE)
        } finally {
            host.dispose()
            window.close()
        }
    }
}

private fun NSView.findTaggedView(tag: String): NSView? {
    if (valueForKey(ACCESSIBILITY_IDENTIFIER_KEY) == tag) return this
    return subviews
        .filterIsInstance<NSView>()
        .firstNotNullOfOrNull { child -> child.findTaggedView(tag) }
}

private fun awaitDemoLayout(
    message: String,
    condition: () -> Boolean,
) {
    val startedAt = TimeSource.Monotonic.markNow()
    while (!condition() && startedAt.elapsedNow() < UI_TIMEOUT) {
        CFRunLoopRunInMode(kCFRunLoopDefaultMode, RUN_LOOP_STEP_SECONDS, true)
    }
    check(condition()) { message }
}

private const val ACCESSIBILITY_IDENTIFIER_KEY: String = "accessibilityIdentifier"
private const val CATALOG_IMAGE_TAG: String = "demo-catalog-image"
private const val CATALOG_TITLE_TAG: String = "demo-catalog-title"
private const val HOST_WIDTH: Double = 640.0
private const val HOST_HEIGHT: Double = 480.0
private const val IMAGE_SIZE: Double = 64.0
private const val FRAME_TOLERANCE: Double = 0.5
private const val RUN_LOOP_STEP_SECONDS: Double = 0.01
private val UI_TIMEOUT = 5.seconds
