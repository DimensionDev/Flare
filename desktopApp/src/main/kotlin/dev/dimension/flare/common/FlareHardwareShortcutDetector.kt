package dev.dimension.flare.common

import androidx.compose.animation.core.SnapSpec
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFold
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.launch
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.HardwareShortcutDetector
import me.saket.telephoto.zoomable.HardwareShortcutDetector.ShortcutEvent
import me.saket.telephoto.zoomable.HardwareShortcutDetector.ShortcutEvent.PanDirection
import me.saket.telephoto.zoomable.HardwareShortcutDetector.ShortcutEvent.ZoomDirection
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import org.apache.commons.lang3.SystemUtils
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

internal object FlareHardwareShortcutDetector : HardwareShortcutDetector {
    override fun detectKey(event: KeyEvent): ShortcutEvent? {
        // Note for self: Some devices/peripherals have dedicated zoom buttons that map to Key.ZoomIn
        // and Key.ZoomOut. Examples include: Samsung Galaxy Camera, a motorcycle handlebar controller.
        if (event.key == Key.ZoomIn || event.isZoomInEvent()) {
            return ShortcutEvent.Zoom(ZoomDirection.In)
        } else if (event.key == Key.ZoomOut || (event.isZoomOutEvent())) {
            return ShortcutEvent.Zoom(ZoomDirection.Out)
        }

        val panDirection =
            when (event.key) {
                Key.DirectionUp -> PanDirection.Up
                Key.DirectionDown -> PanDirection.Down
                Key.DirectionLeft -> PanDirection.Left
                Key.DirectionRight -> PanDirection.Right
                else -> null
            }
        return when (panDirection) {
            null -> null
            else ->
                ShortcutEvent.Pan(
                    direction = panDirection,
                    panOffset = ShortcutEvent.DefaultPanOffset * if (event.isAltPressed) 10f else 1f,
                )
        }
    }

    private fun KeyEvent.isZoomInEvent(): Boolean = this.key == Key.Equals && isCtrlPressed()

    private fun KeyEvent.isZoomOutEvent(): Boolean = key == Key.Minus && isCtrlPressed()

    private fun KeyEvent.isCtrlPressed(): Boolean =
        if (SystemUtils.IS_OS_MAC_OSX) {
            isMetaPressed
        } else {
            isCtrlPressed
        }

    override fun detectScroll(event: PointerEvent): ShortcutEvent? {
        if (!event.isZoomEvent()) {
            val scroll = event.calculateScroll()
            return ShortcutEvent.Pan(
                direction =
                    when {
                        event.keyboardModifiers.isShiftPressed && scroll.y != 0f -> {
                            if (scroll.y < 0f) PanDirection.Left else PanDirection.Right
                        }
                        else -> {
                            if (scroll.x == 0f) {
                                if (scroll.y < 0f) PanDirection.Up else PanDirection.Down
                            } else {
                                if (scroll.x < 0f) PanDirection.Left else PanDirection.Right
                            }
                        }
                    },
                panOffset = ShortcutEvent.DefaultPanOffset * if (event.keyboardModifiers.isAltPressed) 10f else 1f,
            )
        } else {
            return when (val scrollY = event.calculateScroll().y) {
                0f -> null
                else ->
                    ShortcutEvent.Zoom(
                        direction = if (scrollY < 0f) ZoomDirection.In else ZoomDirection.Out,
                        centroid = event.calculateScrollCentroid(),
                        // Deltas observed on various platforms and mice:
                        // Android:
                        //   Logitech MX: -1.0 / +1.0
                        // macOS:
                        //   Logitech MX: -1.2 / +1.3
                        //   MacBook trackpad: -0.1 / 0.1
                        zoomFactor = (ShortcutEvent.DefaultZoomFactor / 2f) * scrollY.absoluteValue,
                    )
            }
        }
    }

    private fun PointerEvent.isZoomEvent(): Boolean =
        if (SystemUtils.IS_OS_MAC_OSX) {
            keyboardModifiers.isMetaPressed
        } else {
            keyboardModifiers.isCtrlPressed
        }

    private fun PointerEvent.calculateScroll(): Offset =
        changes.fastFold(Offset.Zero) { acc, c ->
            acc + c.scrollDelta
        }

    private fun PointerEvent.calculateScrollCentroid(): Offset {
        check(type == PointerEventType.Scroll)
        var centroid = Offset.Zero
        var centroidWeight = 0f
        changes.fastForEach { change ->
            val position = change.position
            centroid += position
            centroidWeight++
        }
        return when (centroidWeight) {
            0f -> Offset.Unspecified
            else -> centroid / centroidWeight
        }
    }
}

/** Responds to keyboard and mouse events to zoom and pan. */
internal data class FlareHardwareShortcutsElement(
    private val state: ZoomableState,
) : ModifierNodeElement<FlareHardwareShortcutsNode>() {
    override fun create(): FlareHardwareShortcutsNode = FlareHardwareShortcutsNode(state)

    override fun update(node: FlareHardwareShortcutsNode) {
        node.state = state
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "hardwareShortcuts"
    }
}

internal class FlareHardwareShortcutsNode(
    var state: ZoomableState,
    val shortcutDetector: HardwareShortcutDetector = FlareHardwareShortcutDetector,
) : Modifier.Node(),
    KeyInputModifierNode,
    PointerInputModifierNode {
    val canPan: () -> Boolean = {
        state.contentAlignment == Alignment.TopCenter ||
            state.contentTransformation.scaleMetadata.userZoom > 1f
    }
    val onZoom: (factor: Float, centroid: Offset) -> Unit = { factor, centroid ->
        coroutineScope.launch {
            state.zoomBy(
                zoomFactor = factor,
                centroid = centroid,
                animationSpec = SnapSpec(),
            )
        }
    }
    val onPan: (delta: DpOffset) -> Unit = { delta ->
        coroutineScope.launch {
            state.panBy(
                offset =
                    with(requireDensity()) {
                        Offset(x = delta.x.toPx(), y = delta.y.toPx())
                    },
                animationSpec = SnapSpec(),
            )
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown) {
            val shortcut = shortcutDetector.detectKey(event)
            shortcut?.let(::handleShortcut)
            return shortcut != null
        } else {
            return false
        }
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (
            pointerEvent.type == PointerEventType.Scroll &&
            pass == PointerEventPass.Main &&
            pointerEvent.changes.fastAny { !it.isConsumed }
        ) {
            val shortcut = shortcutDetector.detectScroll(pointerEvent)
            if (shortcut != null) {
                handleShortcut(shortcut, pointerEvent)
            }
        }
    }

    @OptIn(ExperimentalTelephotoApi::class)
    private fun handleShortcut(
        shortcut: ShortcutEvent,
        pointerEvent: PointerEvent? = null,
    ) {
        when (shortcut) {
            is ShortcutEvent.Zoom -> {
                when (shortcut.direction) {
                    ShortcutEvent.ZoomDirection.In -> onZoom(1f + shortcut.zoomFactor, shortcut.centroid)
                    ShortcutEvent.ZoomDirection.Out -> onZoom(1f - shortcut.zoomFactor, shortcut.centroid)
                }
            }
            is ShortcutEvent.Pan -> {
                if (canPan()) {
                    val canContinuePan =
                        with(state.coordinateSystem) {
                            val contentBounds = contentBounds(false).rectIn(CoordinateSpace.Viewport)
                            val viewportSize = viewportSize
                            when (shortcut.direction) {
                                PanDirection.Up -> contentBounds.top < 0f
                                PanDirection.Down -> contentBounds.bottom.roundToInt() > viewportSize.height.roundToInt()
                                PanDirection.Left -> contentBounds.left < 0f
                                PanDirection.Right -> contentBounds.right.roundToInt() > viewportSize.width.roundToInt()
                            }
                        }
                    if (!canContinuePan) {
                        return
                    }

                    val offset =
                        when (shortcut.direction) {
                            ShortcutEvent.PanDirection.Up -> DpOffset(x = 0.dp, y = shortcut.panOffset)
                            ShortcutEvent.PanDirection.Down -> DpOffset(x = 0.dp, y = -shortcut.panOffset)
                            ShortcutEvent.PanDirection.Left -> DpOffset(x = shortcut.panOffset, y = 0.dp)
                            ShortcutEvent.PanDirection.Right -> DpOffset(x = -shortcut.panOffset, y = 0.dp)
                        }
                    onPan(offset)
                    pointerEvent?.changes?.fastForEach { it.consume() }
                }
            }
        }
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false

    override fun onCancelPointerInput() = Unit
}
