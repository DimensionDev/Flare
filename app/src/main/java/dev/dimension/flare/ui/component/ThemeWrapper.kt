package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import com.ramcosta.composedestinations.scope.DestinationScope
import com.ramcosta.composedestinations.wrapper.DestinationWrapper
import dev.dimension.flare.ui.theme.FlareTheme

internal object ThemeWrapper : DestinationWrapper {
    @Composable
    override fun <T> DestinationScope<T>.Wrap(screenContent: @Composable () -> Unit) {
        FlareTheme(content = screenContent)
    }
}
