package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role

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
                }.onKeyEvent { event ->
                    if (
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.F10 &&
                        event.isShiftPressed
                    ) {
                        onExpandedChange(true)
                        true
                    } else {
                        false
                    }
                }.clickable(
                    role = Role.Button,
                    onClick = onClick,
                ),
    ) {
        content()
        menu()
    }
}
