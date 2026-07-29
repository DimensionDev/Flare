@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.plugin.badge

import dev.dimension.flare.ui.FlareRenderer
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.uikit.AbstractUIKitWidget
import dev.dimension.flare.ui.uikit.UIKitBackend
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.systemGray5Color
import platform.UIKit.systemGreenColor
import platform.UIKit.systemOrangeColor

public expect val UIKitBadgeRendererPlugin: FlareRendererPlugin<UIKitBackend>

@FlareRenderer
internal class UIKitBadgeWidget :
    AbstractUIKitWidget<UIButton>(
        view =
            UIButton.buttonWithType(UIButtonTypeSystem).apply {
                layer.cornerRadius = 12.0
            },
    ),
    BadgeWidget {
    private var clickAction: () -> Unit = {}
    private var action: UIAction? = UIAction.actionWithHandler { clickAction() }

    init {
        view.addAction(checkNotNull(action), forControlEvents = UIControlEventTouchUpInside)
    }

    override fun setText(value: String) {
        view.setTitle(value, forState = UIControlStateNormal)
    }

    override fun setTone(value: BadgeTone) {
        view.backgroundColor =
            when (value) {
                BadgeTone.Neutral -> UIColor.systemGray5Color
                BadgeTone.Positive -> UIColor.systemGreenColor
                BadgeTone.Warning -> UIColor.systemOrangeColor
            }
    }

    override fun setOnClick(value: () -> Unit) {
        clickAction = value
    }

    override fun dispose() {
        clickAction = {}
        action?.let { current ->
            view.removeAction(current, forControlEvents = UIControlEventTouchUpInside)
        }
        action = null
    }
}
