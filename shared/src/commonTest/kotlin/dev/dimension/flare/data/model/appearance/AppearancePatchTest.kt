package dev.dimension.flare.data.model.appearance

import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.BottomBarBehavior
import dev.dimension.flare.data.model.BottomBarStyle
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.data.model.VideoAutoplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.ExperimentalSerializationApi

class AppearancePatchTest {
    @Test
    fun emptyPatchSynthesizesDefaultAppearanceSettings() {
        assertEquals(AppearanceSettings.Default, AppearancePatch.EMPTY.toAppearanceSettings())
    }

    @Test
    fun getSetAndClearUseDefaults() {
        val patch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.Theme, Theme.DARK)

        assertEquals(Theme.DARK, patch[AppearanceKeys.Theme])
        assertEquals(AppearanceKeys.ShowMedia.default, patch[AppearanceKeys.ShowMedia])

        val cleared = patch.clear(AppearanceKeys.Theme)
        assertEquals(AppearanceKeys.Theme.default, cleared[AppearanceKeys.Theme])
        assertFalse(cleared.contains(AppearanceKeys.Theme))
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun activeAppearanceSettingsFieldsAreCoveredByCatalog() {
        val deprecatedFields = setOf("showActions")
        val activeFields =
            AppearanceSettings
                .serializer()
                .descriptor
                .let { descriptor ->
                    (0 until descriptor.elementsCount)
                        .map { descriptor.getElementName(it) }
                        .filterNot { it in deprecatedFields }
                }

        assertEquals(activeFields.size, AppearanceKeys.all.size)
    }

    @Test
    fun appearanceSettingsRoundTripPreservesActiveFields() {
        val settings =
            AppearanceSettings(
                theme = Theme.DARK,
                dynamicTheme = false,
                colorSeed = 123456u,
                avatarShape = AvatarShape.SQUARE,
                pureColorMode = false,
                showNumbers = false,
                showLinkPreview = false,
                showMedia = false,
                showSensitiveContent = true,
                videoAutoplay = VideoAutoplay.NEVER,
                expandMediaSize = false,
                compatLinkPreview = true,
                fontSizeDiff = 2f,
                lineHeightDiff = 4f,
                showComposeInHomeTimeline = false,
                bottomBarStyle = BottomBarStyle.Classic,
                bottomBarBehavior = BottomBarBehavior.AlwaysShow,
                inAppBrowser = false,
                fullWidthPost = true,
                postActionStyle = PostActionStyle.Hidden,
                absoluteTimestamp = true,
                showPlatformLogo = false,
                timelineDisplayMode = TimelineDisplayMode.Gallery,
            )

        assertEquals(settings, settings.toPatch().toAppearanceSettings())
    }

    @Test
    fun bagRoundTripPreservesExplicitValues() {
        val patch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.Theme, Theme.LIGHT)
                .set(AppearanceKeys.ShowMedia, false)
                .set(AppearanceKeys.VideoAutoplay, VideoAutoplay.ALWAYS)
                .set(AppearanceKeys.PostActionStyle, PostActionStyle.Stretch)

        assertEquals(patch, patch.toBag().toPatch())
    }

    @Test
    fun unknownEntriesAreIgnored() {
        val bag =
            AppearanceBag(
                entries = mapOf("future.key" to byteArrayOf(1, 2, 3).toHexString()),
            )

        assertEquals(AppearancePatch.EMPTY, bag.toPatch())
    }
}
