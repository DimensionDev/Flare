@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.flareui.apple

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import dev.dimension.flare.flareui.FlareUiApplier
import dev.dimension.flare.flareui.FlareUiContent
import dev.dimension.flare.flareui.ProvideWidgetRegistry
import dev.dimension.flare.flareui.WidgetNode
import dev.dimension.flare.flareui.WidgetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.Foundation.NSThread
import platform.QuartzCore.CACurrentMediaTime
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.CoroutineContext
import kotlin.native.HiddenFromObjC

/**
 * An immutable snapshot passed across the Kotlin/Swift boundary.
 */
public class FlareUiNodeSnapshot internal constructor(
    public val id: Long,
    public val kind: FlareUiNodeKind,
    public val payload: FlareUiNodePayload,
    public val children: List<FlareUiNodeSnapshot>,
)

/**
 * Owns a standalone Compose Runtime composition and publishes immutable snapshots to Swift.
 *
 * All methods must be called from the Apple main thread.
 */
public class FlareUiTreeHost internal constructor(
    private val content: FlareUiContent,
) {
    private var nextNodeId = 1L
    private var onTreeChanged: ((List<FlareUiNodeSnapshot>) -> Unit)? = null
    private var publishPending = false
    private var disposed = false
    private val root =
        AppleTreeNode(
            id = 0L,
            type = null,
            onChanged = ::schedulePublish,
            onAction = ::performAction,
        )
    private val registry = generatedAppleWidgetRegistry(::createNode)
    private val coroutineContext = appleRecomposerContext()
    private val recomposerScope = CoroutineScope(coroutineContext)
    private val recomposer = Recomposer(coroutineContext)
    private val composition =
        Composition(
            applier = FlareUiApplier(root),
            parent = recomposer,
        )

    init {
        checkMainThread()
        AppleSnapshotManager.ensureStarted()
        recomposerScope.launch {
            recomposer.runRecomposeAndApplyChanges()
        }
        renderContent()
    }

    private fun renderContent() {
        composition.setContent {
            ProvideWidgetRegistry(
                registry = registry,
                content = { content() },
            )
        }
    }

    /**
     * Installs one listener and immediately sends the latest complete tree.
     */
    public fun setOnTreeChanged(listener: ((List<FlareUiNodeSnapshot>) -> Unit)?) {
        checkMainThread()
        check(!disposed) { "FlareUiTreeHost is already disposed" }
        onTreeChanged = listener
        listener?.invoke(snapshot())
    }

    public fun snapshot(): List<FlareUiNodeSnapshot> {
        checkMainThread()
        return root.snapshotChildren()
    }

    public fun dispose() {
        checkMainThread()
        if (disposed) return

        disposed = true
        onTreeChanged = null
        composition.dispose()
        recomposer.cancel()
        recomposerScope.cancel()
    }

    private fun createNode(type: WidgetType<*>): AppleTreeNode =
        AppleTreeNode(
            id = nextNodeId++,
            type = type,
            onChanged = ::schedulePublish,
            onAction = ::performAction,
        )

    private fun performAction(action: () -> Unit) {
        checkMainThread()
        check(!disposed) { "FlareUiTreeHost is already disposed" }
        action()
    }

    private fun schedulePublish() {
        if (disposed || publishPending || onTreeChanged == null) return

        publishPending = true
        dispatch_async(dispatch_get_main_queue()) {
            publishPending = false
            if (!disposed) {
                onTreeChanged?.invoke(snapshot())
            }
        }
    }
}

/**
 * Creates an Apple tree host without exposing Compose function types to Objective-C.
 *
 * Public Kotlin modules can use this to provide a concrete, Swift-visible factory.
 */
@HiddenFromObjC
public fun createFlareUiTreeHost(content: FlareUiContent): FlareUiTreeHost = FlareUiTreeHost(content)

internal class AppleTreeNode(
    internal val id: Long,
    type: WidgetType<*>?,
    private val onChanged: () -> Unit,
    private val onAction: (() -> Unit) -> Unit,
) : WidgetNode(type) {
    private var props: Any? = null
        private set
    private val children = mutableListOf<AppleTreeNode>()

    internal fun setProps(value: Any) {
        props = value
        onChanged()
    }

    override fun insert(
        index: Int,
        child: WidgetNode,
    ) {
        children.add(index, child.requireAppleTreeNode())
        onChanged()
    }

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        if (from == to || count == 0) return

        val moved = children.subList(from, from + count).toList()
        children.subList(from, from + count).clear()
        val destination = if (from > to) to else to - count
        children.addAll(destination, moved)
        onChanged()
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        children.subList(index, index + count).clear()
        onChanged()
    }

    override fun clear() {
        children.clear()
        onChanged()
    }

    internal fun snapshotChildren(): List<FlareUiNodeSnapshot> = children.map(AppleTreeNode::generatedSnapshot)

    internal fun dispatchAction(action: () -> Unit) {
        onAction(action)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <P : Any> requireProps(): P =
        props as? P
            ?: error("Invalid props for ${type?.debugName}")

    private fun WidgetNode.requireAppleTreeNode(): AppleTreeNode =
        this as? AppleTreeNode
            ?: error("Cannot mix Apple tree nodes with another backend")
}

private object AppleSnapshotManager {
    private var started = false

    fun ensureStarted() {
        if (started) return
        started = true

        Snapshot.registerGlobalWriteObserver {
            dispatch_async(dispatch_get_main_queue()) {
                Snapshot.sendApplyNotifications()
            }
        }
    }
}

private fun appleRecomposerContext(): CoroutineContext {
    lateinit var frameClock: BroadcastFrameClock
    frameClock =
        BroadcastFrameClock {
            dispatch_async(dispatch_get_main_queue()) {
                frameClock.sendFrame((CACurrentMediaTime() * NANOS_PER_SECOND).toLong())
            }
        }
    return Dispatchers.Main.immediate + frameClock + SupervisorJob()
}

private fun checkMainThread() {
    check(NSThread.isMainThread) {
        "FlareUiTreeHost must be used from the Apple main thread"
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000.0
