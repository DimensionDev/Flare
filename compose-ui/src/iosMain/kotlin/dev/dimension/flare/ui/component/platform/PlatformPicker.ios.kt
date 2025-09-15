package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.collections.immutable.ImmutableList
import platform.UIKit.UIAction.Companion.actionWithHandler
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
    UIKitView(
        factory = {
            UISegmentedControl(options).also { control ->
                options.forEachIndexed { index, title ->
                    control.setTitle(title, index.convert<NSUInteger>())
                    control.setAction(
                        action =
                            actionWithHandler {
                                onSelected(control.selectedSegmentIndex.toInt())
                            },
                        forSegmentAtIndex = index.convert<NSUInteger>(),
                    )
                }
                control.selectedSegmentIndex = 0.convert<NSInteger>()
            }
        },
    )
}
