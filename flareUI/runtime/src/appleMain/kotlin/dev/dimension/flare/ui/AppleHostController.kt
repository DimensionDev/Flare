@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui

import platform.Foundation.NSThread

/** Shared lifecycle controller behind the UIKit and AppKit host adapters. */
internal class AppleHostController<B : FlareBackend>(
    private val root: FlareChildren,
    private val widgetSystem: FlareWidgetSystem<B>,
    private val backend: B,
    private val hostName: String,
) {
    private var content: FlareContent? = null
    private var composition: FlareAppleComposition<B>? = null
    private var attached: Boolean = false
    private var disposed: Boolean = false

    init {
        checkAppleMainThread(hostName)
    }

    fun setContent(value: FlareContent) {
        checkAppleMainThread(hostName)
        check(!disposed) { "$hostName is already disposed." }
        content = value
        val current = composition
        if (current != null) {
            current.setContent(value)
        } else if (attached) {
            createComposition()
        }
    }

    fun attachmentChanged(isAttached: Boolean) {
        checkAppleMainThread(hostName)
        if (disposed || attached == isAttached) return
        attached = isAttached
        if (isAttached) {
            createComposition()
        } else {
            disposeComposition()
        }
    }

    fun dispose() {
        checkAppleMainThread(hostName)
        if (disposed) return
        disposed = true
        attached = false
        content = null
        disposeComposition()
    }

    private fun createComposition() {
        if (composition != null) return
        val currentContent = content ?: return
        val newComposition =
            FlareAppleComposition(
                root = root,
                widgetSystem = widgetSystem,
                backend = backend,
                hostName = hostName,
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

internal fun checkAppleMainThread(hostName: String) {
    check(NSThread.isMainThread) {
        "$hostName must be used from the Apple main thread."
    }
}
