@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareRenderer
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareSlotId
import dev.dimension.flare.ui.FlareWidgetSystem
import dev.dimension.flare.ui.foundation.ColumnWidget
import dev.dimension.flare.ui.foundation.NativeButtonWidget
import dev.dimension.flare.ui.foundation.RowWidget
import dev.dimension.flare.ui.foundation.TextWidget
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UIStackView

/** Target-specific Foundation plugin generated for each iOS binary. */
public expect val UIKitFoundationRendererPlugin: FlareRendererPlugin<UIKitBackend>

/** Builds the UIKit renderer set supplied by Foundation and optional plugins. */
public fun createUIKitWidgetSystem(vararg plugins: FlareRendererPlugin<UIKitBackend>): FlareWidgetSystem<UIKitBackend> =
    FlareWidgetSystem(
        UIKitFoundationRendererPlugin,
        *plugins,
    )

@FlareRenderer
internal class UIKitColumnWidget(
    backend: UIKitBackend,
) : AbstractUIKitWidget<UIStackView>(
        view =
            UIStackView().apply {
                axis = UILayoutConstraintAxisVertical
            },
    ),
    ColumnWidget {
    private val content = UIKitChildren(view, backend)

    override fun children(slot: FlareSlotId): FlareChildren {
        require(slot == ColumnWidget.Content) { "Column does not expose slot '$slot'." }
        return content
    }
}

@FlareRenderer
internal class UIKitRowWidget(
    backend: UIKitBackend,
) : AbstractUIKitWidget<UIStackView>(
        view =
            UIStackView().apply {
                axis = UILayoutConstraintAxisHorizontal
            },
    ),
    RowWidget {
    private val content = UIKitChildren(view, backend)

    override fun children(slot: FlareSlotId): FlareChildren {
        require(slot == RowWidget.Content) { "Row does not expose slot '$slot'." }
        return content
    }
}

@FlareRenderer
internal class UIKitTextWidget :
    AbstractUIKitWidget<UILabel>(
        view = UILabel(),
    ),
    TextWidget {
    override fun setText(value: String) {
        view.text = value
    }
}

@FlareRenderer
internal class UIKitNativeButtonWidget :
    AbstractUIKitWidget<UIButton>(
        view = UIButton.buttonWithType(UIButtonTypeSystem),
    ),
    NativeButtonWidget {
    private var clickAction: () -> Unit = {}
    private var action: UIAction? = UIAction.actionWithHandler { clickAction() }

    init {
        view.addAction(checkNotNull(action), forControlEvents = UIControlEventTouchUpInside)
    }

    override fun setLabel(value: String) {
        view.setTitle(value, forState = UIControlStateNormal)
    }

    override fun setEnabled(value: Boolean) {
        view.enabled = value
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
