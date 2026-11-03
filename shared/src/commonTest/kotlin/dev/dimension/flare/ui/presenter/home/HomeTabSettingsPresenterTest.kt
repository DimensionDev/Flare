package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.data.model.appearance.AppearanceKeys
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeTabSettingsPresenterTest {
    @Test
    fun webPresentationEditsPreserveNativeMediaPreferenceAndInheritance() {
        for (value in listOf(null, false, true)) {
            val existing = value?.let { AppearancePatch.EMPTY.set(AppearanceKeys.LimitSingleMediaHeight, it) }
            for (mediaEnabled in listOf(false, true)) {
                val defaults = TimelineAppearance.Default
                val updated =
                    buildAppearancePatch(
                        existingPatch = existing,
                        layoutEnabled = true,
                        timelineDisplayMode = defaults.timelineDisplayMode,
                        fullWidthPost = true,
                        postActionStyle = defaults.postActionStyle,
                        showNumbers = defaults.showNumbers,
                        displayEnabled = false,
                        absoluteTimestamp = defaults.absoluteTimestamp,
                        showPlatformLogo = defaults.showPlatformLogo,
                        showLinkPreview = defaults.showLinkPreview,
                        compatLinkPreview = defaults.compatLinkPreview,
                        mediaEnabled = mediaEnabled,
                        showMedia = defaults.showMedia,
                        showSensitiveContent = defaults.showSensitiveContent,
                        expandContentWarning = defaults.expandContentWarning,
                        expandMediaSize = false,
                        videoAutoplay = defaults.videoAutoplay,
                        themeEnabled = false,
                        avatarShape = defaults.avatarShape,
                    )!!
                assertEquals(value != null, updated.contains(AppearanceKeys.LimitSingleMediaHeight))
                assertEquals(value ?: true, updated[AppearanceKeys.LimitSingleMediaHeight])
                assertEquals(true, updated[AppearanceKeys.FullWidthPost])
            }
        }
    }
}
