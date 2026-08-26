@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.FlareAppleComposition
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareWidgetSystem
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSThread
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentLeading
import kotlin.native.HiddenFromObjC

/**
 * Direct UIKit host supplied by Flare Runtime.
 * Its [view] can be embedded directly in any UIKit view hierarchy.
 */
public class FlareUIKitHost(
    private val widgetSystem: FlareWidgetSystem<UIKitBackend>,
) {
    private val controller = UIKitHostController(widgetSystem)

    public val view: UIStackView =
        UIKitHostView(controller).apply {
            axis = UILayoutConstraintAxisVertical
            alignment = UIStackViewAlignmentLeading
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

private class UIKitHostView(
    private val controller: UIKitHostController,
) : UIStackView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    override fun didMoveToWindow() {
        super.didMoveToWindow()
        controller.windowDidChange(this)
    }
}

private class UIKitHostController(
    private val widgetSystem: FlareWidgetSystem<UIKitBackend>,
) {
    private val backend = UIKitBackend
    private var content: FlareContent? = null
    private var composition: FlareAppleComposition<UIKitBackend>? = null
    private var disposed: Boolean = false

    fun setContent(
        value: FlareContent,
        view: UIStackView,
    ) {
        check(!disposed) { "FlareUIKitHost is already disposed." }
        content = value
        val current = composition
        if (current != null) {
            current.setContent(value)
        } else if (view.window != null) {
            createComposition(view)
        }
    }

    fun windowDidChange(view: UIStackView) {
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

    private fun createComposition(view: UIStackView) {
        if (composition != null) return
        val currentContent = content ?: return
        val newComposition =
            FlareAppleComposition(
                root = UIKitChildren(view),
                widgetSystem = widgetSystem,
                backend = backend,
                hostName = "FlareUIKitHost",
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
        "FlareUIKitHost must be used from the Apple main thread."
    }
}
