package dev.dimension.flare.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.UiComposable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareRenderer
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareSlotId
import dev.dimension.flare.ui.FlareWidgetSystem
import dev.dimension.flare.ui.foundation.ColumnWidget
import dev.dimension.flare.ui.foundation.NativeButtonWidget
import dev.dimension.flare.ui.foundation.RowWidget
import dev.dimension.flare.ui.foundation.TextWidget
import androidx.compose.foundation.layout.Column as ComposeColumn
import androidx.compose.foundation.layout.Row as ComposeRow

/** Builds the Compose renderer set supplied by Foundation and optional plugins. */
public fun createAndroidComposeWidgetSystem(
    vararg plugins: FlareRendererPlugin<AndroidComposeBackend>,
): FlareWidgetSystem<AndroidComposeBackend> =
    FlareWidgetSystem(
        AndroidComposeFoundationRendererPlugin,
        *plugins,
    )

@FlareRenderer
internal class AndroidComposeColumnWidget :
    AbstractAndroidComposeWidget(),
    ColumnWidget {
    private val content = AndroidComposeChildren()

    override fun children(slot: FlareSlotId): FlareChildren {
        require(slot == ColumnWidget.Content) { "Column does not expose slot '$slot'." }
        return content
    }

    @Composable
    @UiComposable
    override fun Render() {
        ComposeColumn(modifier = composeModifier) {
            content.Render()
        }
    }
}

@FlareRenderer
internal class AndroidComposeRowWidget :
    AbstractAndroidComposeWidget(),
    RowWidget {
    private val content = AndroidComposeChildren()

    override fun children(slot: FlareSlotId): FlareChildren {
        require(slot == RowWidget.Content) { "Row does not expose slot '$slot'." }
        return content
    }

    @Composable
    @UiComposable
    override fun Render() {
        ComposeRow(modifier = composeModifier) {
            content.Render()
        }
    }
}

@FlareRenderer
internal class AndroidComposeTextWidget :
    AbstractAndroidComposeWidget(),
    TextWidget {
    private var currentText: String by mutableStateOf("")

    override fun setText(value: String) {
        currentText = value
    }

    @Composable
    @UiComposable
    override fun Render() {
        BasicText(
            text = currentText,
            modifier = composeModifier,
        )
    }
}

@FlareRenderer
internal class AndroidComposeNativeButtonWidget :
    AbstractAndroidComposeWidget(),
    NativeButtonWidget {
    private var currentLabel: String by mutableStateOf("")
    private var enabledState: Boolean by mutableStateOf(true)
    private var clickAction: () -> Unit = {}
    private val performClick: () -> Unit = { clickAction() }

    override fun setLabel(value: String) {
        currentLabel = value
    }

    override fun setEnabled(value: Boolean) {
        enabledState = value
    }

    override fun setOnClick(value: () -> Unit) {
        clickAction = value
    }

    @Composable
    @UiComposable
    override fun Render() {
        BasicText(
            text = currentLabel,
            modifier =
                composeModifier
                    .alpha(if (enabledState) 1f else DISABLED_ALPHA)
                    .background(
                        color = Color(0xFFE0E0E0),
                        shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
                    ).clickable(
                        enabled = enabledState,
                        role = Role.Button,
                        onClick = performClick,
                    ).padding(
                        horizontal = BUTTON_HORIZONTAL_PADDING,
                        vertical = BUTTON_VERTICAL_PADDING,
                    ),
        )
    }

    override fun dispose() {
        clickAction = {}
    }
}

private const val DISABLED_ALPHA: Float = 0.38f
private val BUTTON_CORNER_RADIUS = 4.dp
private val BUTTON_HORIZONTAL_PADDING = 16.dp
private val BUTTON_VERTICAL_PADDING = 8.dp
