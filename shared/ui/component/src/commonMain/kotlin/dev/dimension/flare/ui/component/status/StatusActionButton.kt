package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenu
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenuScope
import dev.dimension.flare.ui.component.platform.PlatformIcon
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.component.platform.rippleIndication
import dev.dimension.flare.ui.theme.PlatformContentColor

@Composable
public fun StatusActionButton(
    icon: ImageVector,
    text: String?,
    onClicked: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PlatformContentColor.current,
    contentDescription: String? = null,
    enabled: Boolean = true,
    withTextMinWidth: Boolean = false,
    content: @Composable RowScope.() -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val appearanceSettings = LocalComponentAppearance.current
    val textMinWidth =
        if (withTextMinWidth) {
            with(LocalDensity.current) { PlatformTextStyle.current.fontSize.toDp() * 3.5f }
        } else {
            0.dp
        }
    Row(
        modifier =
            modifier
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .height(with(LocalDensity.current) { PlatformTextStyle.current.fontSize.toDp() + 4.dp }),
        verticalAlignment = Alignment.Bottom,
    ) {
        PlatformIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(
                        onClick = onClicked,
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication =
                            rippleIndication(
                                bounded = false,
                                radius = 20.dp,
                                color = color,
                            ),
                    ),
            tint = color,
        )
        if (!text.isNullOrEmpty() && appearanceSettings.showNumbers) {
            Spacer(modifier = Modifier.width(4.dp))
            PlatformText(
                text = text,
//                style = MaterialTheme.typography.bodySmall,
                color = color,
                modifier =
                    Modifier
                        .width(textMinWidth)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            onClick = onClicked,
                            enabled = enabled,
                            interactionSource = interactionSource,
                            indication = null,
                        ),
            )
        } else {
            if (withTextMinWidth) {
                Spacer(modifier = Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier.width(textMinWidth),
            )
        }
        content.invoke(this)
    }
}

@Composable
internal fun StatusActionGroup(
    icon: ImageVector,
    text: String?,
    modifier: Modifier = Modifier,
    color: Color = PlatformContentColor.current,
    contentDescription: String? = null,
    enabled: Boolean = true,
    withTextMinWidth: Boolean = false,
    subMenus: @Composable PlatformDropdownMenuScope.(closeMenu: () -> Unit, isMenuShown: Boolean) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    StatusActionButton(
        icon = icon,
        text = text,
        modifier = modifier,
        contentDescription = contentDescription,
        onClicked = {
            showMenu = true
        },
        color = color,
        enabled = enabled,
        withTextMinWidth = withTextMinWidth,
        content = {
            PlatformDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                subMenus.invoke(
                    this,
                    { showMenu = false },
                    showMenu,
                )
            }
        },
    )
}
