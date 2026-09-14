@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.compose.nativekit.appkit

import dev.dimension.compose.nativekit.NativeKitRendererPlugin
import dev.dimension.compose.nativekit.NativeKitWidgetRegistrar
import dev.dimension.compose.nativekit.NativeKitWidgetSystem
import dev.dimension.compose.nativekit.foundation.ColumnWidget
import dev.dimension.compose.nativekit.foundation.HorizontalAlignment
import dev.dimension.compose.nativekit.foundation.NativeButtonWidget
import dev.dimension.compose.nativekit.foundation.RowWidget
import dev.dimension.compose.nativekit.foundation.TextWidget
import dev.dimension.compose.nativekit.foundation.VerticalAlignment
import kotlinx.cinterop.ObjCAction
import platform.AppKit.NSBezelStyleRounded
import platform.AppKit.NSButton
import platform.AppKit.NSButtonTypeMomentaryPushIn
import platform.AppKit.NSLayoutAttributeBottom
import platform.AppKit.NSLayoutAttributeCenterX
import platform.AppKit.NSLayoutAttributeCenterY
import platform.AppKit.NSLayoutAttributeLeading
import platform.AppKit.NSLayoutAttributeNotAnAttribute
import platform.AppKit.NSLayoutAttributeTop
import platform.AppKit.NSLayoutAttributeTrailing
import platform.AppKit.NSLineBreakByWordWrapping
import platform.AppKit.NSStackView
import platform.AppKit.NSTextField
import platform.AppKit.NSUserInterfaceLayoutOrientationHorizontal
import platform.AppKit.NSUserInterfaceLayoutOrientationVertical
import platform.AppKit.labelWithString
import platform.darwin.NSObject
import platform.darwin.sel_registerName

public object AppKitFoundationRendererPlugin : NativeKitRendererPlugin<AppKitBackend> {
    override fun register(registrar: NativeKitWidgetRegistrar<AppKitBackend>) {
        registrar.register(ColumnWidget::class) { _ -> AppKitColumnWidget() }
        registrar.register(RowWidget::class) { _ -> AppKitRowWidget() }
        registrar.register(TextWidget::class) { _ -> AppKitTextWidget() }
        registrar.register(NativeButtonWidget::class) { _ -> AppKitNativeButtonWidget() }
    }
}

/** Builds the AppKit renderer set supplied by Foundation and optional plugins. */
public fun createAppKitWidgetSystem(vararg plugins: NativeKitRendererPlugin<AppKitBackend>): NativeKitWidgetSystem<AppKitBackend> =
    NativeKitWidgetSystem(
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

    override fun setSpacing(value: Float) {
        view.spacing = value.toDouble()
    }

    override fun setHorizontalAlignment(value: HorizontalAlignment) {
        view.alignment =
            when (value) {
                HorizontalAlignment.Start -> NSLayoutAttributeLeading
                HorizontalAlignment.Center -> NSLayoutAttributeCenterX
                HorizontalAlignment.End -> NSLayoutAttributeTrailing
                HorizontalAlignment.Stretch -> NSLayoutAttributeNotAnAttribute
            }
    }
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

    override fun setSpacing(value: Float) {
        view.spacing = value.toDouble()
    }

    override fun setVerticalAlignment(value: VerticalAlignment) {
        view.alignment =
            when (value) {
                VerticalAlignment.Top -> NSLayoutAttributeTop
                VerticalAlignment.Center -> NSLayoutAttributeCenterY
                VerticalAlignment.Bottom -> NSLayoutAttributeBottom
                VerticalAlignment.Stretch -> NSLayoutAttributeNotAnAttribute
            }
    }
}

internal class AppKitTextWidget :
    AbstractAppKitWidget<NSTextField>(
        view =
            NSTextField.labelWithString("").apply {
                maximumNumberOfLines = 0
                lineBreakMode = NSLineBreakByWordWrapping
                usesSingleLineMode = false
            },
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
