@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.appkit

import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.FlareWidgetSystem
import dev.dimension.flare.ui.foundation.ColumnWidget
import dev.dimension.flare.ui.foundation.NativeButtonWidget
import dev.dimension.flare.ui.foundation.RowWidget
import dev.dimension.flare.ui.foundation.TextWidget
import kotlinx.cinterop.ObjCAction
import platform.AppKit.NSBezelStyleRounded
import platform.AppKit.NSButton
import platform.AppKit.NSButtonTypeMomentaryPushIn
import platform.AppKit.NSStackView
import platform.AppKit.NSTextField
import platform.AppKit.NSUserInterfaceLayoutOrientationHorizontal
import platform.AppKit.NSUserInterfaceLayoutOrientationVertical
import platform.AppKit.labelWithString
import platform.darwin.NSObject
import platform.darwin.sel_registerName

public object AppKitFoundationRendererPlugin : FlareRendererPlugin<AppKitBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AppKitBackend>) {
        registrar.register(ColumnWidget::class) { _ -> AppKitColumnWidget() }
        registrar.register(RowWidget::class) { _ -> AppKitRowWidget() }
        registrar.register(TextWidget::class) { _ -> AppKitTextWidget() }
        registrar.register(NativeButtonWidget::class) { _ -> AppKitNativeButtonWidget() }
    }
}

/** Builds the AppKit renderer set supplied by Foundation and optional plugins. */
public fun createAppKitWidgetSystem(vararg plugins: FlareRendererPlugin<AppKitBackend>): FlareWidgetSystem<AppKitBackend> =
    FlareWidgetSystem(
        AppKitFoundationRendererPlugin,
        *plugins,
    )

internal class AppKitColumnWidget :
    AbstractAppKitWidget<NSStackView>(
        view =
            NSStackView().apply {
                orientation = NSUserInterfaceLayoutOrientationVertical
            },
    ),
    ColumnWidget {
    override val children: AppKitChildren = AppKitChildren(view)
}

internal class AppKitRowWidget :
    AbstractAppKitWidget<NSStackView>(
        view =
            NSStackView().apply {
                orientation = NSUserInterfaceLayoutOrientationHorizontal
            },
    ),
    RowWidget {
    override val children: AppKitChildren = AppKitChildren(view)
}

internal class AppKitTextWidget :
    AbstractAppKitWidget<NSTextField>(
        view = NSTextField.labelWithString(""),
    ),
    TextWidget {
    override fun setText(value: String) {
        view.stringValue = value
    }
}

internal class AppKitNativeButtonWidget :
    AbstractAppKitWidget<NSButton>(
        view =
            NSButton().apply {
                bezelStyle = NSBezelStyleRounded
                setButtonType(NSButtonTypeMomentaryPushIn)
            },
    ),
    NativeButtonWidget {
    private val actionTarget = AppKitButtonActionTarget()

    init {
        view.target = actionTarget
        view.action = sel_registerName("performClick")
    }

    override fun setLabel(value: String) {
        view.title = value
    }

    override fun setEnabled(value: Boolean) {
        view.enabled = value
    }

    override fun setOnClick(value: () -> Unit) {
        actionTarget.onClick = value
    }

    override fun dispose() {
        view.target = null
        view.action = null
        actionTarget.onClick = {}
    }
}

private class AppKitButtonActionTarget : NSObject() {
    var onClick: () -> Unit = {}

    @ObjCAction
    fun performClick() {
        onClick()
    }
}
