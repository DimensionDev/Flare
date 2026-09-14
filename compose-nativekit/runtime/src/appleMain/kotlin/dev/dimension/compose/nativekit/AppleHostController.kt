@file:OptIn(
    dev.dimension.compose.nativekit.LowLevelNativeKitApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.compose.nativekit

import platform.Foundation.NSThread

/** Shared lifecycle controller behind the UIKit and AppKit host adapters. */
internal class AppleHostController<B : NativeKitBackend>(
    private val root: NativeKitChildren,
    private val widgetSystem: NativeKitWidgetSystem<B>,
    private val backend: B,
    private val hostName: String,
) {
    private var content: NativeKitContent? = null
    private var composition: NativeKitAppleComposition<B>? = null
    private var attached: Boolean = false
    private var disposed: Boolean = false

    init {
        checkAppleMainThread(hostName)
    }

    fun setContent(value: NativeKitContent) {
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
            NativeKitAppleComposition(
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
