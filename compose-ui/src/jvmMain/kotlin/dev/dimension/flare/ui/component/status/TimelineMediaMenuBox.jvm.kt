package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun TimelineMediaMenuBox(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier,
    menu: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.buttons.isSecondaryPressed) {
                        onExpandedChange(true)
                    }
                }.clickable(onClick = onClick),
    ) {
        content()
        menu()
    }
}
