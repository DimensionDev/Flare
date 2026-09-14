@file:OptIn(
    dev.dimension.compose.nativekit.LowLevelNativeKitApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.compose.nativekit.uikit

import dev.dimension.compose.nativekit.AppleHostController
import dev.dimension.compose.nativekit.LowLevelNativeKitApi
import dev.dimension.compose.nativekit.NativeKitContent
import dev.dimension.compose.nativekit.NativeKitNativeControllerOwner
import dev.dimension.compose.nativekit.NativeKitWidgetSystem
import dev.dimension.compose.nativekit.ProvideNativeKitNativeControllerOwner
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentLeading
import kotlin.native.HiddenFromObjC

/**
 * Direct UIKit host supplied by NativeKit Runtime.
 * Its [view] can be embedded directly in any UIKit view hierarchy.
 */
public class NativeKitUIKitHost private constructor(
    widgetSystem: NativeKitWidgetSystem<UIKitBackend>,
    nativeControllerOwner: NativeControllerOwnerBox,
) {
    public constructor(widgetSystem: NativeKitWidgetSystem<UIKitBackend>) :
        this(widgetSystem, NativeControllerOwnerBox(null))

    @LowLevelNativeKitApi
    public constructor(
        widgetSystem: NativeKitWidgetSystem<UIKitBackend>,
        nativeControllerOwner: NativeKitNativeControllerOwner,
    ) : this(widgetSystem, NativeControllerOwnerBox(nativeControllerOwner))

    private val nativeControllerOwner = nativeControllerOwner.value
    private val hostView =
        UIKitHostView().apply {
            axis = UILayoutConstraintAxisVertical
            alignment = UIStackViewAlignmentLeading
        }
    private val controller =
        AppleHostController(
            root = UIKitChildren(hostView),
            widgetSystem = widgetSystem,
            backend = UIKitBackend,
            hostName = HOST_NAME,
        )

    public val view: UIStackView
        get() = hostView

    init {
        hostView.onAttachmentChanged = controller::attachmentChanged
    }

    @HiddenFromObjC
    public fun setContent(content: NativeKitContent) {
        controller.setContent(hostedContent(content))
    }

    public fun dispose() {
        controller.dispose()
        hostView.onAttachmentChanged = null
    }

    private fun hostedContent(value: NativeKitContent): NativeKitContent =
        {
            ProvideNativeKitNativeControllerOwner(
                owner = nativeControllerOwner,
                content = value,
            )
        }
}

private class NativeControllerOwnerBox(
    val value: NativeKitNativeControllerOwner?,
)

private class UIKitHostView : UIStackView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var onAttachmentChanged: ((Boolean) -> Unit)? = null

    override fun didMoveToWindow() {
        super.didMoveToWindow()
        onAttachmentChanged?.invoke(window != null)
    }
}

private const val HOST_NAME: String = "NativeKitUIKitHost"
