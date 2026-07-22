package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.data.datasource.microblog.PostActionLayoutConfig
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.TimelineAutoRefreshInterval
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.model.appearance.AppearanceKey
import dev.dimension.flare.data.model.appearance.AppearanceKeys
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.web.shared.WebIgnore
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@WebPresenter("settings")
public class SettingsPresenter : PresenterBase<SettingsPresenter.State>() {
    private val repository: SettingsRepository by koinInject()

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val appearancePatch by repository.appearancePatch.collectAsUiState()
        val appSettings by repository.appSettings.collectAsUiState()
        return object : State {
            override val appearance: UiState<AppearancePatch> = appearancePatch
            override val appSettings: UiState<AppSettings> = appSettings

            override fun <T : Any> update(
                key: AppearanceKey<T>,
                value: T,
            ) {
                scope.launch {
                    withContext(Dispatchers.Main) {
                        repository.updateAppearance(key, value)
                    }
                }
            }

            override fun clear(key: AppearanceKey<*>) {
                scope.launch {
                    withContext(Dispatchers.Main) {
                        repository.clearAppearance(key)
                    }
                }
            }

            override fun updateFontScale(fontSizeDiff: Float) {
                scope.launch {
                    withContext(Dispatchers.Main) {
                        repository.updateAppearance {
                            set(AppearanceKeys.FontSizeDiff, fontSizeDiff)
                                .set(AppearanceKeys.LineHeightDiff, fontSizeDiff * 2)
                        }
                    }
                }
            }

            override fun updateTheme(value: Theme) = update(AppearanceKeys.Theme, value)

            override fun updateAvatarShape(value: AvatarShape) = update(AppearanceKeys.AvatarShape, value)

            override fun updateAbsoluteTimestamp(value: Boolean) = update(AppearanceKeys.AbsoluteTimestamp, value)

            override fun updateShowPlatformLogo(value: Boolean) = update(AppearanceKeys.ShowPlatformLogo, value)

            override fun updateShowLinkPreview(value: Boolean) = update(AppearanceKeys.ShowLinkPreview, value)

            override fun updateCompatLinkPreview(value: Boolean) = update(AppearanceKeys.CompatLinkPreview, value)

            override fun updateInAppBrowser(value: Boolean) = update(AppearanceKeys.InAppBrowser, value)

            override fun updateShowMedia(value: Boolean) = update(AppearanceKeys.ShowMedia, value)

            override fun updateExpandMediaSize(value: Boolean) = update(AppearanceKeys.ExpandMediaSize, value)

            override fun updateLimitMediaGridToNine(value: Boolean) = update(AppearanceKeys.LimitMediaGridToNine, value)

            override fun updateShowSensitiveContent(value: Boolean) = update(AppearanceKeys.ShowSensitiveContent, value)

            override fun updateExpandContentWarning(value: Boolean) = update(AppearanceKeys.ExpandContentWarning, value)

            override fun updateShowBottomBarLabels(value: Boolean) = update(AppearanceKeys.ShowBottomBarLabels, value)

            override fun updateDeckMode(value: Boolean) = update(AppearanceKeys.DeckMode, value)

            override fun updateVideoAutoplay(value: VideoAutoplay) = update(AppearanceKeys.VideoAutoplay, value)

            override fun updateTimelineDisplayMode(value: TimelineDisplayMode) = update(AppearanceKeys.TimelineDisplayMode, value)

            override fun updateFullWidthPost(value: Boolean) = update(AppearanceKeys.FullWidthPost, value)

            override fun updatePostActionStyle(value: PostActionStyle) = update(AppearanceKeys.PostActionStyle, value)

            override fun updatePostActionLayout(value: PostActionLayoutConfig) = update(AppearanceKeys.PostActionLayout, value)

            override fun updatePostActionFixedWidth(value: Boolean) = update(AppearanceKeys.PostActionFixedWidth, value)

            override fun updateShowNumbers(value: Boolean) = update(AppearanceKeys.ShowNumbers, value)

            override fun updateRefreshHomeTimelineOnLaunch(value: Boolean) {
                updateAppSettings {
                    copy(refreshHomeTimelineOnLaunch = value)
                }
            }

            override fun updateHomeTimelineAutoRefreshInterval(value: TimelineAutoRefreshInterval) {
                updateAppSettings {
                    copy(homeTimelineAutoRefreshInterval = value)
                }
            }

            override fun updateAppSettings(block: AppSettings.() -> AppSettings) {
                scope.launch {
                    withContext(Dispatchers.Main) {
                        repository.updateAppSettings(block)
                    }
                }
            }
        }
    }

    public interface State {
        @WebIgnore
        public val appearance: UiState<AppearancePatch>

        @WebIgnore
        public val appSettings: UiState<AppSettings>

        @WebIgnore
        public fun <T : Any> update(
            key: AppearanceKey<T>,
            value: T,
        )

        @WebIgnore
        public fun clear(key: AppearanceKey<*>)

        public fun updateFontScale(fontSizeDiff: Float)

        public fun updateTheme(value: Theme)

        public fun updateAvatarShape(value: AvatarShape)

        public fun updateAbsoluteTimestamp(value: Boolean)

        public fun updateShowPlatformLogo(value: Boolean)

        public fun updateShowLinkPreview(value: Boolean)

        public fun updateCompatLinkPreview(value: Boolean)

        public fun updateInAppBrowser(value: Boolean)

        public fun updateShowMedia(value: Boolean)

        public fun updateExpandMediaSize(value: Boolean)

        public fun updateLimitMediaGridToNine(value: Boolean)

        public fun updateShowSensitiveContent(value: Boolean)

        public fun updateExpandContentWarning(value: Boolean)

        public fun updateShowBottomBarLabels(value: Boolean)

        public fun updateDeckMode(value: Boolean)

        public fun updateVideoAutoplay(value: VideoAutoplay)

        public fun updateTimelineDisplayMode(value: TimelineDisplayMode)

        public fun updateFullWidthPost(value: Boolean)

        public fun updatePostActionStyle(value: PostActionStyle)

        public fun updatePostActionLayout(value: PostActionLayoutConfig)

        public fun updatePostActionFixedWidth(value: Boolean)

        public fun updateShowNumbers(value: Boolean)

        public fun updateRefreshHomeTimelineOnLaunch(value: Boolean)

        public fun updateHomeTimelineAutoRefreshInterval(value: TimelineAutoRefreshInterval)

        @WebIgnore
        public fun updateAppSettings(block: AppSettings.() -> AppSettings)
    }
}
