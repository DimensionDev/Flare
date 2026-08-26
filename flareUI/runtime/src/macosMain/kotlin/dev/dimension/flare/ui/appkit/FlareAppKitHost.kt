@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.appkit

import dev.dimension.flare.ui.FlareAppleComposition
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareWidgetSystem
import platform.AppKit.NSLayoutAttributeLeading
import platform.AppKit.NSStackView
import platform.AppKit.NSUserInterfaceLayoutOrientationVertical
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSThread
import kotlin.native.HiddenFromObjC

/** Direct AppKit host supplied by Flare Runtime. */
public class FlareAppKitHost(
    private val widgetSystem: FlareWidgetSystem<AppKitBackend>,
) {
    private val controller = AppKitHostController(widgetSystem)

    public val view: NSStackView =
        AppKitHostView(controller).apply {
            orientation = NSUserInterfaceLayoutOrientationVertical
            alignment = NSLayoutAttributeLeading
        }

    init {
        checkMainThread()
    }

    @HiddenFromObjC
    public fun setContent(content: FlareContent) {
        checkMainThread()
        controller.setContent(content, view)
    }

    public fun dispose() {
        checkMainThread()
        controller.dispose()
    }
}

private class AppKitHostView(
    private val controller: AppKitHostController,
) : NSStackView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    override fun viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        controller.windowDidChange(this)
    }
}

private class AppKitHostController(
    private val widgetSystem: FlareWidgetSystem<AppKitBackend>,
) {
    private val backend = AppKitBackend
    private var content: FlareContent? = null
    private var composition: FlareAppleComposition<AppKitBackend>? = null
    private var disposed: Boolean = false

    fun setContent(
        value: FlareContent,
        view: NSStackView,
    ) {
        check(!disposed) { "FlareAppKitHost is already disposed." }
        content = value
        val current = composition
        if (current != null) {
            current.setContent(value)
        } else if (view.window != null) {
            createComposition(view)
        }
    }

    fun windowDidChange(view: NSStackView) {
        if (disposed) return
        if (view.window == null) {
            disposeComposition()
        } else {
            createComposition(view)
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        content = null
        disposeComposition()
    }

    private fun createComposition(view: NSStackView) {
        if (composition != null) return
        val currentContent = content ?: return
        val newComposition =
            FlareAppleComposition(
                root = AppKitChildren(view),
                widgetSystem = widgetSystem,
                backend = backend,
                hostName = "FlareAppKitHost",
            )
        composition = newComposition
        try {
            newComposition.setContent(currentContent)
        } catch (throwable: Throwable) {
            disposeComposition()
            throw throwable
        }
    }

    private fun disposeComposition() {
        composition?.dispose()
        composition = null
    }
}

private fun checkMainThread() {
    check(NSThread.isMainThread) {
        "FlareAppKitHost must be used from the Apple main thread."
    }
}
