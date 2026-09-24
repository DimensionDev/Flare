package dev.dimension.flare.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import dev.dimension.flare.R
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.ui.common.AppUriHandler
import dev.dimension.flare.ui.common.BindAmberSignerLauncher
import dev.dimension.flare.ui.component.LocalAppSettings
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.takeSuccessOr
import dev.dimension.flare.ui.presenter.EnvironmentSettingsPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.home.HomeScreen
import dev.dimension.flare.ui.theme.FlareTheme
import moe.tlaster.precompose.molecule.producePresenter

@Composable
fun AppContainer(afterInit: () -> Unit) {
    FlareApp {
        FlareTheme {
            HomeScreen(
                afterInit = afterInit,
            )
        }
    }
}

@Composable
fun FlareApp(content: @Composable () -> Unit) {
    BindAmberSignerLauncher()
    val state by producePresenter("env") {
        remember { EnvironmentSettingsPresenter() }.invoke()
    }
    val globalAppearance = state.globalAppearance.takeSuccessOr(GlobalAppearance.Default)
    val timelineAppearance = state.timelineAppearance.takeSuccessOr(TimelineAppearance.Default)
    val originalUriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val uriHandler =
        remember(context, originalUriHandler, globalAppearance.inAppBrowser) {
            AppUriHandler(context, originalUriHandler, globalAppearance.inAppBrowser) {
                Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
            }
        }

    val appSettings = state.appSettings.takeSuccessOr(AppSettings(""))
    val openAIConfig = appSettings.aiConfig.type as? AppSettings.AiConfig.Type.OpenAI
    CompositionLocalProvider(
        LocalAppSettings provides appSettings,
        LocalUriHandler provides uriHandler,
        LocalGlobalAppearance provides globalAppearance,
        LocalTimelineAppearance provides
            remember(globalAppearance, timelineAppearance, appSettings.translateConfig, appSettings.aiConfig) {
                timelineAppearance.copy(
                    aiConfig =
                        TimelineAppearance.AiConfig(
                            translation = true,
                            tldr = appSettings.aiConfig.tldr,
                            agent = appSettings.aiConfig.agent && !openAIConfig?.model.isNullOrBlank(),
                            showOriginalWithTranslation =
                                appSettings.translateConfig.showOriginalWithTranslation,
                        ),
                )
            },
        content = content,
    )
}
