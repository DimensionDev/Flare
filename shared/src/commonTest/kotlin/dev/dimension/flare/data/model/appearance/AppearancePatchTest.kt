package dev.dimension.flare.data.model.appearance

import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostActionLayoutConfig
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.BottomBarBehavior
import dev.dimension.flare.data.model.BottomBarStyle
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.resolveTimelineAppearance
import dev.dimension.flare.data.model.tab.toUiTimelineTabItem
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiText
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppearancePatchTest {
    @Test
    fun emptyPatchSynthesizesDefaultAppearanceSettings() {
        assertEquals(AppearanceSettings.Default, AppearancePatch.EMPTY.toAppearanceSettings())
        assertFalse(TimelineAppearance.Default.expandContentWarning)
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

    @Test
    fun patchSynthesizesGlobalAndTimelineAppearanceModels() {
        val patch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.Theme, Theme.DARK)
                .set(AppearanceKeys.ShowBottomBarLabels, false)
                .set(AppearanceKeys.DeckMode, true)
                .set(AppearanceKeys.AvatarShape, AvatarShape.SQUARE)
                .set(AppearanceKeys.ShowMedia, false)
                .set(AppearanceKeys.ExpandContentWarning, true)
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
                expandContentWarning = true,
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
                .set(AppearanceKeys.ExpandContentWarning, false)
        val timelinePatch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.ShowMedia, true)
                .set(AppearanceKeys.ExpandContentWarning, true)

        assertEquals(
            TimelineAppearance(
                showMedia = true,
                expandContentWarning = true,
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
                .set(AppearanceKeys.ExpandContentWarning, true)
                .set(AppearanceKeys.TimelineDisplayMode, TimelineDisplayMode.Gallery)

        assertEquals(
            TimelineAppearance(
                showMedia = true,
                expandContentWarning = true,
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
    fun timelineTabItemV2ResolvesTimelineAppearanceFromItemPatch() {
        val item =
            CommonTimelineSpecs.home
                .candidate(
                    data = TimelineSpec.AccountBasedData(MicroBlogKey("test", "example.test")),
                    title = UiText.Raw("Test"),
                    icon = IconType.Material(UiIcon.List),
                ).copy(
                    appearancePatch =
                        AppearancePatch.EMPTY
                            .set(AppearanceKeys.ShowNumbers, false)
                            .set(AppearanceKeys.ExpandContentWarning, true)
                            .set(AppearanceKeys.AbsoluteTimestamp, true),
                ).toUiTimelineTabItem()
        val base =
            TimelineAppearance(
                showNumbers = true,
                absoluteTimestamp = false,
                aiConfig = TimelineAppearance.AiConfig(translation = true),
                lineLimit = 7,
            )

        assertEquals(
            TimelineAppearance(
                showNumbers = false,
                expandContentWarning = true,
                absoluteTimestamp = true,
                aiConfig = TimelineAppearance.AiConfig(translation = true),
                lineLimit = 7,
            ),
            item.resolveTimelineAppearance(base),
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun activeAppearanceSettingsFieldsAreCoveredByCatalog() {
        val deprecatedFields = setOf("showActions")
        val bagOnlyFields =
            setOf(
                AppearanceKeys.ShowBottomBarLabels,
                AppearanceKeys.DeckMode,
                AppearanceKeys.ExpandContentWarning,
                AppearanceKeys.PostActionLayout,
            )
        val activeFields =
            AppearanceSettings
                .serializer()
                .descriptor
                .let { descriptor ->
                    (0 until descriptor.elementsCount)
                        .map { descriptor.getElementName(it) }
                        .filterNot { it in deprecatedFields }
                }

        assertEquals(activeFields.size + bagOnlyFields.size, AppearanceKeys.all.size)
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
                .set(AppearanceKeys.ShowBottomBarLabels, false)
                .set(AppearanceKeys.DeckMode, true)
                .set(AppearanceKeys.ShowMedia, false)
                .set(AppearanceKeys.ExpandContentWarning, true)
                .set(AppearanceKeys.VideoAutoplay, VideoAutoplay.ALWAYS)
                .set(AppearanceKeys.PostActionStyle, PostActionStyle.Stretch)
                .set(
                    AppearanceKeys.PostActionLayout,
                    PostActionLayoutConfig(
                        enabled = true,
                        primary = kotlinx.collections.immutable.persistentListOf(PostActionFamily.Like),
                        overflow = kotlinx.collections.immutable.persistentListOf(PostActionFamily.Share),
                        hidden = kotlinx.collections.immutable.persistentListOf(PostActionFamily.Report),
                    ),
                )

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
