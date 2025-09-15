package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import dev.dimension.flare.ui.common.OnNewIntent
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.screen.login.ServiceSelectionScreenContent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ServiceSelectScreen(
    onXQT: () -> Unit,
    onVVO: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        modifier =
            modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (onBack != null) {
                FlareTopAppBar(
                    title = {
                    },
                    navigationIcon = {
                        BackButton(onBack = onBack)
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { contentPadding ->
        ServiceSelectionScreenContent(
            onXQT = onXQT,
            onVVO = onVVO,
            contentPadding = contentPadding,
            openUri = uriHandler::openUri,
            registerDeeplinkCallback = { callback ->
                OnNewIntent {
                    callback.invoke(it.dataString.orEmpty())
                }
            },
            onBack = onBack,
        )
    }
}
