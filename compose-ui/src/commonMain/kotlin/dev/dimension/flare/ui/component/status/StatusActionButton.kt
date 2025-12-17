package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.AnimatedNumber
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenu
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenuScope
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.component.platform.rippleIndication
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.theme.PlatformContentColor
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
public fun StatusActionButton(
    icon: ImageVector,
    number: UiNumber?,
    onClicked: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PlatformContentColor.current,
    contentDescription: String? = null,
    enabled: Boolean = true,
    withTextMinWidth: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val appearanceSettings = LocalComponentAppearance.current
    Row(
        modifier =
            modifier
                .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (!LocalIsScrollingInProgress.current) {
            val contentColor = PlatformContentColor.current
            AnimatedContent(
                color,
                transitionSpec = {
                    if (targetState == contentColor) {
                        fadeIn() togetherWith fadeOut()
                    } else {
                        fadeIn() +
                            scaleIn(
                                animationSpec =
                                    spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                    ),
                            ) togetherWith scaleOut() + fadeOut()
                    }.using(SizeTransform(clip = false))
                },
            ) { color ->
                FAIcon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier =
                        Modifier
                            .height(PlatformTextStyle.current.fontSize.value.dp + 2.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(
                                onClick = onClicked,
                                enabled = enabled,
                                interactionSource = interactionSource,
                                indication =
                                    rippleIndication(
                                        bounded = false,
                                        radius = 20.dp,
                                        color = Color.Unspecified,
                                    ),
                            ),
                    tint = color,
                )
            }
        } else {
            FAIcon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier =
                    Modifier
                        .height(PlatformTextStyle.current.fontSize.value.dp + 2.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            onClick = onClicked,
                            enabled = enabled,
                            interactionSource = interactionSource,
                            indication =
                                rippleIndication(
                                    bounded = false,
                                    radius = 20.dp,
                                    color = Color.Unspecified,
                                ),
                        ),
                tint = color,
            )
        }
        if (withTextMinWidth || number != null && appearanceSettings.showNumbers) {
            Box(
                modifier = Modifier.align(Alignment.CenterVertically),
            ) {
                if (withTextMinWidth) {
                    PlatformText(
                        "0000",
                        color = Color.Transparent,
                    )
                }
                if (number != null && appearanceSettings.showNumbers) {
                    AnimatedNumber(
                        number = number,
                        color = color,
                        modifier =
                            Modifier
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
                }
            }
        }
    }
}

@Composable
internal fun StatusActionGroup(
    icon: ImageVector,
    number: UiNumber?,
    modifier: Modifier = Modifier,
    color: Color = PlatformContentColor.current,
    contentDescription: String? = null,
    enabled: Boolean = true,
    withTextMinWidth: Boolean = false,
    subMenus: @Composable PlatformDropdownMenuScope.(closeMenu: () -> Unit, isMenuShown: Boolean) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(
        modifier = modifier,
    ) {
        StatusActionButton(
            icon = icon,
            number = number,
            contentDescription = contentDescription,
            onClicked = {
                showMenu = true
            },
            color = color,
            enabled = enabled,
            withTextMinWidth = withTextMinWidth,
        )
        PlatformDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            CompositionLocalProvider(
                PlatformContentColor provides PlatformTheme.colorScheme.text,
                PlatformTextStyle provides PlatformTheme.typography.body,
            ) {
                subMenus.invoke(
                    this,
                    { showMenu = false },
                    showMenu,
                )
            }
        }
    }
}
