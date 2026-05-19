package dev.dimension.flare.data.model.appearance

import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.SourceTimelineTabItemV2
import dev.dimension.flare.data.model.tab.resolveTimelineAppearance
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiText
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineTabAppearanceTest {
    @Test
    fun timelineTabItemV2ResolvesTimelineAppearanceFromItemPatch() {
        val item =
            SourceTimelineTabItemV2.runtime(
                id = "test",
                title = UiText.Raw("Test"),
                icon = IconType.Material(UiIcon.List),
                appearancePatch =
                    AppearancePatch.EMPTY
                        .set(AppearanceKeys.ShowNumbers, false)
                        .set(AppearanceKeys.AbsoluteTimestamp, true),
                runtimePresenterFactory = { error("unused in test") },
            )
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
                absoluteTimestamp = true,
                aiConfig = TimelineAppearance.AiConfig(translation = true),
                lineLimit = 7,
            ),
            item.resolveTimelineAppearance(base),
        )
    }
}
