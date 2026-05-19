package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.slapps.cupertino.theme.CupertinoTheme
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.datastore.SettingsRepository
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import org.koin.compose.koinInject

@Composable
internal fun FlareTheme(content: @Composable () -> Unit) {
    CupertinoTheme {
        ProvideThemeSettings {
            content()
        }
    }
}

@Composable
internal fun ProvideThemeSettings(content: @Composable () -> Unit) {
    val settingsRepository = koinInject<SettingsRepository>()
    val globalAppearance by settingsRepository.globalAppearance.collectAsState(
        GlobalAppearance(),
    )
    val baseTimelineAppearance by settingsRepository.timelineAppearance.collectAsState(
        TimelineAppearance(),
    )
    val timelineAppearance =
        remember(baseTimelineAppearance) {
            baseTimelineAppearance.copy(videoAutoplay = VideoAutoplay.NEVER)
        }
    val appSettings by settingsRepository.appSettings.collectAsState(AppSettings(""))
    CompositionLocalProvider(
        LocalGlobalAppearance provides globalAppearance,
        LocalTimelineAppearance provides
            remember(globalAppearance, timelineAppearance, appSettings.translateConfig, appSettings.aiConfig.tldr) {
                timelineAppearance.copy(
                    aiConfig =
                        TimelineAppearance.AiConfig(
                            translation = true,
                            tldr = appSettings.aiConfig.tldr,
                        ),
                )
            },
        content = content,
    )
}
