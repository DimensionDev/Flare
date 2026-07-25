package dev.dimension.flare.flareui.view

import android.content.Context
import android.os.Looper
import android.util.AttributeSet
import android.view.Choreographer
import android.widget.FrameLayout
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import dev.dimension.flare.flareui.FlareUiApplier
import dev.dimension.flare.flareui.FlareUiContent
import dev.dimension.flare.flareui.ProvideWidgetRegistry
import dev.dimension.flare.flareui.WidgetRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * A standalone Android View host for Flare UI content.
 */
public class FlareViewHost
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {
        private var composition: Composition? = null
        private var recomposer: Recomposer? = null
        private var recomposerScope: CoroutineScope? = null
        private var content: FlareUiContent? = null
        private val widgetRegistry: WidgetRegistry by lazy {
            androidViewWidgetRegistry(context)
        }

        public fun setContent(content: FlareUiContent) {
            checkMainThread()
            this.content = content

            val currentComposition = composition
            if (currentComposition == null) {
                createComposition()
            } else {
                currentComposition.setContent {
                    ProvideWidgetRegistry(
                        registry = widgetRegistry,
                        content = { content() },
                    )
                }
            }
        }

        public fun disposeComposition() {
            checkMainThread()
            composition?.dispose()
            composition = null
            recomposer?.cancel()
            recomposer = null
            recomposerScope?.cancel()
            recomposerScope = null
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (composition == null) {
                createComposition()
            }
        }

        override fun onDetachedFromWindow() {
            disposeComposition()
            super.onDetachedFromWindow()
        }

        private fun createComposition() {
            val currentContent = content ?: return
            AndroidSnapshotManager.ensureStarted()
            val coroutineContext = androidRecomposerContext()
            val scope = CoroutineScope(coroutineContext)
            val newRecomposer = Recomposer(coroutineContext)
            val newComposition =
                Composition(
                    applier =
                        FlareUiApplier(
                            AndroidViewNode(
                                type = null,
                                view = this,
                            ),
                        ),
                    parent = newRecomposer,
                )

            recomposerScope = scope
            recomposer = newRecomposer
            composition = newComposition

            scope.launch {
                newRecomposer.runRecomposeAndApplyChanges()
            }
            newComposition.setContent {
                ProvideWidgetRegistry(
                    registry = widgetRegistry,
                    content = { currentContent() },
                )
            }
        }

        private fun androidRecomposerContext(): CoroutineContext {
            val choreographer = Choreographer.getInstance()
            lateinit var frameClock: BroadcastFrameClock
            frameClock =
                BroadcastFrameClock {
                    choreographer.postFrameCallback(frameClock::sendFrame)
                }
            return Dispatchers.Main.immediate + frameClock + SupervisorJob()
        }

        private fun checkMainThread() {
            check(Looper.myLooper() === Looper.getMainLooper()) {
                "FlareViewHost must be used from the Android main thread"
            }
        }
    }
