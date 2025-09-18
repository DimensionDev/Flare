package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.useContents
import kotlinx.collections.immutable.ImmutableList
import platform.UIKit.UIAction.Companion.actionWithHandler
import platform.UIKit.UIImageView
import platform.UIKit.UISegmentedControl
import platform.darwin.NSInteger
import platform.darwin.NSUInteger

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformPicker(
    options: ImmutableList<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    val controlManager =
        remember(options) {
            SegmentedControlManager(
                options = options,
                titleForItem = { it },
                onSelectionChanged = { onSelected(options.indexOf(it)) },
            )
        }

//    LaunchedEffect(selected) {
//        if (controlManager.selected != selected) {
//            controlManager.setSelected(selected)
//        }
//    }

    UIKitView(
        modifier =
            modifier
                .let {
                    if (controlManager.segmentedControlWidth > 0) {
                        it.width(controlManager.segmentedControlWidth.dp)
                    } else {
                        it
                    }
                }.let {
                    if (controlManager.segmentedControlHeight > 0) {
                        it.height(controlManager.segmentedControlHeight.dp)
                    } else {
                        it
                    }
                },
        factory = {
            return@UIKitView controlManager.controller
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private class SegmentedControlManager<T> internal constructor(
    private val options: List<T>,
    private val titleForItem: (T) -> String,
    private val onSelectionChanged: (option: T) -> Unit,
) {
    var segmentedControlWidth by mutableStateOf(0f)
        private set
    var segmentedControlHeight by mutableStateOf(0f)
        private set

    val controller: UISegmentedControl = UISegmentedControl(options.map { it!!::class.simpleName })

    init {
        options.forEachIndexed { index, item ->
            controller.setAction(action = actionWithHandler { onSelectionChanged(item) }, forSegmentAtIndex = index.convert<NSUInteger>())
            controller.setTitle(titleForItem(item), forSegmentAtIndex = index.convert<NSUInteger>())
        }

        controller.selectedSegmentIndex = 0.convert<NSInteger>()

        controller.frame.useContents {
            segmentedControlWidth = this.size.width.toFloat()
            segmentedControlHeight = this.size.height.toFloat()
            controller.layer.cornerRadius = size.width / 2
            controller.layer.masksToBounds = true

            (controller.subviews[0] as? UIImageView)?.layer?.cornerRadius = controller.layer.cornerRadius
        }
    }

    fun setSelected(option: T) {
        options.indexOf(option).takeIf { it >= 0 }?.let {
            controller.selectedSegmentIndex = it.convert<NSInteger>()
        }
    }

    val selected: T?
        get() =
            controller.selectedSegmentIndex.let {
                runCatching { options[it.toInt()] }.getOrNull()
            }
}
