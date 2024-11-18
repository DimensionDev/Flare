package dev.dimension.flare.ui.component

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.window.ComposeUIViewController
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.status.TimelineComponent
import dev.dimension.flare.ui.component.status.TimelineList
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

@Suppress("FunctionName")
fun TimelineViewController(
    presenter: TimelinePresenter,
    accountType: AccountType,
    darkMode: Boolean,
    onOpenLink: (String) -> Unit,
): UIViewController =
    ComposeUIViewController {
        val colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()
        MaterialTheme(
            colorScheme = colorScheme,
        ) {
            CompositionLocalProvider(
                LocalUriHandler provides
                    remember {
                        object : UriHandler {
                            override fun openUri(uri: String) {
                                onOpenLink(uri)
                            }
                        }
                    },
                LocalIndication provides remember { CustomIndication(color = colorScheme.onBackground) },
            ) {
                Scaffold {
                    TimelineComponent(
                        presenter = presenter,
                        accountType = accountType,
                    )
                }
            }
        }
    }

@OptIn(ExperimentalForeignApi::class)
@Suppress("FunctionName")
fun TimelineListViewController(
    onRefresh: () -> Unit,
    pagingState: PagingState<UiTimeline>,
    darkMode: Boolean,
    onOpenLink: (String) -> Unit,
//    viewController: UIViewController,
    detailStatusKey: MicroBlogKey? = null,
): UIViewController =
    ComposeUIViewController(
//        configure = {
//            this.delegate = object : ComposeUIViewControllerDelegate {
//                override fun viewDidAppear(animated: Boolean) {
//                    super.viewDidAppear(animated)
//                    viewController.navigationController?.interactivePopGestureRecognizer?.let { popRecognizer ->
//                        viewController.view.gestureRecognizers?.filterIsInstance<CMPGestureRecognizer>()?.firstOrNull()
//                            ?.requireGestureRecognizerToFail(popRecognizer)
//                    }
//                }
//            }
//        }
    ) {
        val colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()
        MaterialTheme(
            colorScheme = colorScheme,
        ) {
            CompositionLocalProvider(
                LocalUriHandler provides
                    remember {
                        object : UriHandler {
                            override fun openUri(uri: String) {
                                onOpenLink(uri)
                            }
                        }
                    },
                LocalIndication provides remember { CustomIndication(color = colorScheme.onBackground) },
            ) {
                Scaffold {
                    TimelineList(
                        onRefresh = onRefresh,
                        pagingState = pagingState,
                        detailStatusKey = detailStatusKey,
                    )
                }
            }
        }
    }

private class CustomIndication(
    private val color: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        DefaultDebugIndicationInstance(interactionSource, color = color)

    override fun hashCode(): Int = -1

    override fun equals(other: Any?) = other === this

    private class DefaultDebugIndicationInstance(
        private val interactionSource: InteractionSource,
        private val color: Color,
    ) : Modifier.Node(),
        DrawModifierNode {
        private var isPressed = false
        private var isHovered = false
        private var isFocused = false

        override fun onAttach() {
            coroutineScope.launch {
                var pressCount = 0
                var hoverCount = 0
                var focusCount = 0
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> pressCount++
                        is PressInteraction.Release -> pressCount--
                        is PressInteraction.Cancel -> pressCount--
                        is HoverInteraction.Enter -> hoverCount++
                        is HoverInteraction.Exit -> hoverCount--
                        is FocusInteraction.Focus -> focusCount++
                        is FocusInteraction.Unfocus -> focusCount--
                    }
                    val pressed = pressCount > 0
                    val hovered = hoverCount > 0
                    val focused = focusCount > 0
                    var invalidateNeeded = false
                    if (isPressed != pressed) {
                        isPressed = pressed
                        invalidateNeeded = true
                    }
                    if (isHovered != hovered) {
                        isHovered = hovered
                        invalidateNeeded = true
                    }
                    if (isFocused != focused) {
                        isFocused = focused
                        invalidateNeeded = true
                    }
                    if (invalidateNeeded) invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (isPressed) {
                drawRect(color = color.copy(alpha = 0.15f), size = size)
            } else if (isHovered || isFocused) {
                drawRect(color = color.copy(alpha = 0.05f), size = size)
            }
        }
    }
}
