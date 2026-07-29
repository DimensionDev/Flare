package dev.dimension.flare.ui.android

import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AndroidUiDispatcher
import dev.dimension.flare.ui.FlareComposition
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareWidgetSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Standalone Android View host supplied by Flare Runtime.
 * Compose applications can embed this class with `AndroidView`.
 */
public class FlareAndroidViewHost(
    context: Context,
    private val widgetSystem: FlareWidgetSystem<AndroidViewBackend>,
) : FrameLayout(context) {
    private var content: FlareContent? = null
    private var composition: FlareComposition<AndroidViewBackend>? = null
    private var recomposer: Recomposer? = null
    private var recomposerScope: CoroutineScope? = null
    private var snapshotManagerAcquired: Boolean = false

    public fun setContent(content: FlareContent) {
        checkMainThread()
        this.content = content
        val current = composition
        if (current == null && isAttachedToWindow) {
            createComposition()
        } else if (current != null) {
            current.setContent(content)
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
                AndroidFlareSnapshotManager.release()
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
            "FlareAndroidViewHost can create a composition only while attached to a window."
        }
        val currentContent = content ?: return
        AndroidFlareSnapshotManager.acquire()
        snapshotManagerAcquired = true
        try {
            val coroutineContext = AndroidUiDispatcher.Main + SupervisorJob()
            val scope = CoroutineScope(coroutineContext)
            val newRecomposer = Recomposer(coroutineContext)
            val newComposition =
                FlareComposition(
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
            newComposition.setContent(currentContent)
        } catch (throwable: Throwable) {
            disposeComposition()
            throw throwable
        }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() === Looper.getMainLooper()) {
            "FlareAndroidViewHost must be used from the Android main thread."
        }
    }
}
