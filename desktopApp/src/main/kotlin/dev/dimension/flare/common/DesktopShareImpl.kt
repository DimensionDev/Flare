package dev.dimension.flare.common

import dev.dimension.flare.Res
import dev.dimension.flare.common.windows.WindowsBridge
import dev.dimension.flare.copied_to_clipboard
import dev.dimension.flare.ui.common.DesktopShare
import dev.dimension.flare.ui.component.ComposeInAppNotification
import java.awt.Toolkit
import org.apache.commons.lang3.SystemUtils

internal class DesktopShareImpl(
    private val windowsBridge: WindowsBridge,
    private val inAppNotification: ComposeInAppNotification,
) : DesktopShare {
    override fun shareText(text: String) {
        if (SystemUtils.IS_OS_WINDOWS) {
            windowsBridge.shareText(text)
            inAppNotification.message(Res.string.copied_to_clipboard)
        } else {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(
                java.awt.datatransfer.StringSelection(text),
                null,
            )
            inAppNotification.message(Res.string.copied_to_clipboard)
        }
    }
}
