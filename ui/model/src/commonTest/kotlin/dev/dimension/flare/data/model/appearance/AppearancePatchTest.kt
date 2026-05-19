package dev.dimension.flare.data.model.appearance

import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.data.model.VideoAutoplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppearancePatchTest {
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

    @Test
    fun patchSynthesizesGlobalAndTimelineAppearanceModels() {
        val patch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.Theme, Theme.DARK)
                .set(AppearanceKeys.ShowBottomBarLabels, false)
                .set(AppearanceKeys.DeckMode, true)
                .set(AppearanceKeys.AvatarShape, AvatarShape.SQUARE)
                .set(AppearanceKeys.ShowMedia, false)
                .set(AppearanceKeys.TimelineDisplayMode, TimelineDisplayMode.Gallery)

        assertEquals(
            GlobalAppearance(
                theme = Theme.DARK,
                showBottomBarLabels = false,
                deckMode = true,
            ),
            patch.toGlobalAppearance(),
        )
        assertEquals(
            TimelineAppearance(
                avatarShape = AvatarShape.SQUARE,
                showMedia = false,
                timelineDisplayMode = TimelineDisplayMode.Gallery,
            ),
            patch.toTimelineAppearance(),
        )
    }

    @Test
    fun timelineOverrideFallsBackToGlobalTimelineDefaults() {
        val globalPatch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.ShowMedia, false)
                .set(AppearanceKeys.ShowNumbers, false)
        val timelinePatch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.ShowMedia, true)

        assertEquals(
            TimelineAppearance(
                showMedia = true,
                showNumbers = false,
            ),
            globalPatch.toTimelineAppearance(timelinePatch),
        )
    }

    @Test
    fun timelineAppearanceWithPatchPreservesNonPatchFields() {
        val base =
            TimelineAppearance(
                showMedia = false,
                showNumbers = false,
                aiConfig = TimelineAppearance.AiConfig(translation = true, tldr = true),
                lineLimit = 9,
                showTranslateButton = false,
            )
        val patch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.ShowMedia, true)
                .set(AppearanceKeys.TimelineDisplayMode, TimelineDisplayMode.Gallery)

        assertEquals(
            TimelineAppearance(
                showMedia = true,
                showNumbers = false,
                timelineDisplayMode = TimelineDisplayMode.Gallery,
                aiConfig = TimelineAppearance.AiConfig(translation = true, tldr = true),
                lineLimit = 9,
                showTranslateButton = false,
            ),
            base.withPatch(patch),
        )
    }

    @Test
    fun bagRoundTripPreservesExplicitValues() {
        val patch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.Theme, Theme.LIGHT)
                .set(AppearanceKeys.ShowBottomBarLabels, false)
                .set(AppearanceKeys.DeckMode, true)
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
