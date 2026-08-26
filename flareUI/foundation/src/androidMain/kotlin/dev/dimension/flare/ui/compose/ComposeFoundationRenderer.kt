package dev.dimension.flare.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.UiComposable
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.FlareWidgetSystem
import dev.dimension.flare.ui.foundation.ColumnWidget
import dev.dimension.flare.ui.foundation.HorizontalAlignment
import dev.dimension.flare.ui.foundation.NativeButtonWidget
import dev.dimension.flare.ui.foundation.RowWidget
import dev.dimension.flare.ui.foundation.TextWidget
import dev.dimension.flare.ui.foundation.VerticalAlignment
import androidx.compose.foundation.layout.Column as ComposeColumn
import androidx.compose.foundation.layout.Row as ComposeRow

/** Builds the Compose renderer set supplied by Foundation and optional plugins. */
public fun createAndroidComposeWidgetSystem(
    vararg plugins: FlareRendererPlugin<AndroidComposeBackend>,
): FlareWidgetSystem<AndroidComposeBackend> =
    FlareWidgetSystem(
        AndroidComposeRuntimeRendererPlugin,
        AndroidComposeFoundationRendererPlugin,
        *plugins,
    )

public object AndroidComposeFoundationRendererPlugin : FlareRendererPlugin<AndroidComposeBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AndroidComposeBackend>) {
        registrar.register(ColumnWidget::class) { _ -> AndroidComposeColumnWidget() }
        registrar.register(RowWidget::class) { _ -> AndroidComposeRowWidget() }
        registrar.register(TextWidget::class) { _ -> AndroidComposeTextWidget() }
        registrar.register(NativeButtonWidget::class) { _ -> AndroidComposeNativeButtonWidget() }
    }
}

internal class AndroidComposeColumnWidget :
    AbstractAndroidComposeWidget(),
    ColumnWidget {
    override val children: AndroidComposeChildren = AndroidComposeChildren()
    private var itemSpacing: Float by mutableFloatStateOf(0f)
    private var itemAlignment: HorizontalAlignment by mutableStateOf(HorizontalAlignment.Start)

    override fun setSpacing(value: Float) {
        itemSpacing = value
    }

    override fun setHorizontalAlignment(value: HorizontalAlignment) {
        itemAlignment = value
    }

    @Composable
    @UiComposable
    override fun Render() {
        ComposeColumn(
            modifier = composeModifier,
            verticalArrangement = Arrangement.spacedBy(itemSpacing.dp),
            horizontalAlignment = itemAlignment.toComposeAlignment(),
        ) {
            children.Render()
        }
    }
}

internal class AndroidComposeRowWidget :
    AbstractAndroidComposeWidget(),
    RowWidget {
    override val children: AndroidComposeChildren = AndroidComposeChildren()
    private var itemSpacing: Float by mutableFloatStateOf(0f)
    private var itemAlignment: VerticalAlignment by mutableStateOf(VerticalAlignment.Center)

    override fun setSpacing(value: Float) {
        itemSpacing = value
    }

    override fun setVerticalAlignment(value: VerticalAlignment) {
        itemAlignment = value
    }

    @Composable
    @UiComposable
    override fun Render() {
        ComposeRow(
            modifier = composeModifier,
            horizontalArrangement = Arrangement.spacedBy(itemSpacing.dp),
            verticalAlignment = itemAlignment.toComposeAlignment(),
        ) {
            children.Render()
        }
    }
}

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
        Text(
            text = currentText,
            modifier = composeModifier,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

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
        Button(
            onClick = performClick,
            modifier = composeModifier,
            enabled = enabledState,
        ) {
            Text(currentLabel)
        }
    }

    override fun dispose() {
        clickAction = {}
    }
}

private fun HorizontalAlignment.toComposeAlignment(): Alignment.Horizontal =
    when (this) {
        HorizontalAlignment.Start -> Alignment.Start
        HorizontalAlignment.Center -> Alignment.CenterHorizontally
        HorizontalAlignment.End -> Alignment.End
    }

private fun VerticalAlignment.toComposeAlignment(): Alignment.Vertical =
    when (this) {
        VerticalAlignment.Top -> Alignment.Top
        VerticalAlignment.Center -> Alignment.CenterVertically
        VerticalAlignment.Bottom -> Alignment.Bottom
    }
