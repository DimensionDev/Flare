package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.slapps.cupertino.theme.CupertinoTheme
import dev.dimension.flare.data.model.AppSettings
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.LocalComponentAppearance
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
    val appearanceSettings by settingsRepository.appearanceSettings.collectAsState(
        AppearanceSettings(),
    )
    val appSettings by settingsRepository.appSettings.collectAsState(AppSettings(""))
    CompositionLocalProvider(
        LocalAppearanceSettings provides appearanceSettings,
        LocalComponentAppearance provides
            remember(appearanceSettings, appSettings.aiConfig) {
                ComponentAppearance(
                    dynamicTheme = appearanceSettings.dynamicTheme,
                    avatarShape =
                        when (appearanceSettings.avatarShape) {
                            AvatarShape.CIRCLE -> ComponentAppearance.AvatarShape.CIRCLE
                            AvatarShape.SQUARE -> ComponentAppearance.AvatarShape.SQUARE
                        },
                    showActions = appearanceSettings.showActions,
                    showNumbers = appearanceSettings.showNumbers,
                    showLinkPreview = appearanceSettings.showLinkPreview,
                    showMedia = appearanceSettings.showMedia,
                    showSensitiveContent = appearanceSettings.showSensitiveContent,
                    videoAutoplay = ComponentAppearance.VideoAutoplay.NEVER,
                    expandMediaSize = appearanceSettings.expandMediaSize,
                    compatLinkPreview = appearanceSettings.compatLinkPreview,
                    aiConfig =
                        ComponentAppearance.AiConfig(
                            translation = appSettings.aiConfig.translation,
                            tldr = appSettings.aiConfig.tldr,
                        ),
                )
            },
        content = content,
    )
}
