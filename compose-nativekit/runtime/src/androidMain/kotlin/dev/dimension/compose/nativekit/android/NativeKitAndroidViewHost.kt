@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.android

import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AndroidUiDispatcher
import dev.dimension.compose.nativekit.LowLevelNativeKitApi
import dev.dimension.compose.nativekit.NativeKitComposition
import dev.dimension.compose.nativekit.NativeKitContent
import dev.dimension.compose.nativekit.NativeKitNativeControllerOwner
import dev.dimension.compose.nativekit.NativeKitWidgetSystem
import dev.dimension.compose.nativekit.ProvideNativeKitNativeControllerOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Standalone Android View host supplied by NativeKit Runtime.
 * Compose applications can embed this class with `AndroidView`.
 */
public class NativeKitAndroidViewHost private constructor(
    context: Context,
    private val widgetSystem: NativeKitWidgetSystem<AndroidViewBackend>,
    nativeControllerOwner: NativeControllerOwnerBox,
) : FrameLayout(context) {
    public constructor(
        context: Context,
        widgetSystem: NativeKitWidgetSystem<AndroidViewBackend>,
    ) : this(context, widgetSystem, NativeControllerOwnerBox(null))

    @LowLevelNativeKitApi
    public constructor(
        context: Context,
        widgetSystem: NativeKitWidgetSystem<AndroidViewBackend>,
        nativeControllerOwner: NativeKitNativeControllerOwner,
    ) : this(context, widgetSystem, NativeControllerOwnerBox(nativeControllerOwner))

    private val nativeControllerOwner = nativeControllerOwner.value
    private var content: NativeKitContent? = null
    private var composition: NativeKitComposition<AndroidViewBackend>? = null
    private var recomposer: Recomposer? = null
    private var recomposerScope: CoroutineScope? = null
    private var snapshotManagerAcquired: Boolean = false

    public fun setContent(content: NativeKitContent) {
        checkMainThread()
        this.content = content
        val current = composition
        if (current == null && isAttachedToWindow) {
            createComposition()
        } else if (current != null) {
            current.setContent(hostedContent(content))
        }
    }

    public fun disposeComposition() {
        checkMainThread()
        val currentComposition = composition
        composition = null
        try {
            currentComposition?.dispose()
        } finally {
            releaseRuntime()
        }
    }

    private fun releaseRuntime() {
        val currentRecomposer = recomposer
        recomposer = null
        val currentScope = recomposerScope
        recomposerScope = null
        try {
            currentRecomposer?.cancel()
            currentScope?.cancel()
        } finally {
            if (snapshotManagerAcquired) {
                snapshotManagerAcquired = false
                AndroidNativeKitSnapshotManager.release()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (composition == null && content != null) {
            createComposition()
        }
    }

    override fun onDetachedFromWindow() {
        disposeComposition()
        super.onDetachedFromWindow()
    }

    private fun createComposition() {
        check(isAttachedToWindow) {
            "NativeKitAndroidViewHost can create a composition only while attached to a window."
        }
        val currentContent = content ?: return
        AndroidNativeKitSnapshotManager.acquire()
        snapshotManagerAcquired = true
        try {
            val coroutineContext = AndroidUiDispatcher.Main + SupervisorJob()
            checkNotNull(coroutineContext[MonotonicFrameClock]) {
                "AndroidUiDispatcher.Main must provide its Choreographer frame clock."
            }
            val scope = CoroutineScope(coroutineContext)
            val newRecomposer = Recomposer(coroutineContext)
            val newComposition =
                NativeKitComposition(
                    root = AndroidViewChildren(this),
                    widgetSystem = widgetSystem,
                    backend = AndroidViewBackend(context),
                    parent = newRecomposer,
                )

            recomposerScope = scope
            recomposer = newRecomposer
            composition = newComposition
            scope.launch {
                newRecomposer.runRecomposeAndApplyChanges()
            }
            newComposition.setContent(hostedContent(currentContent))
        } catch (throwable: Throwable) {
            disposeComposition()
            throw throwable
        }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() === Looper.getMainLooper()) {
            "NativeKitAndroidViewHost must be used from the Android main thread."
        }
    }

    private fun hostedContent(value: NativeKitContent): NativeKitContent =
        {
            ProvideNativeKitNativeControllerOwner(
                owner = nativeControllerOwner,
                content = value,
            )
        }
}

private class NativeControllerOwnerBox(
    val value: NativeKitNativeControllerOwner?,
)
