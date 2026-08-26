package dev.dimension.flare.ui.demo

import dev.dimension.flare.ui.demo.resources.DemoRes
import dev.dimension.flare.ui.resources.moko.AppleMokoResourceResolver
import dev.icerock.moko.resources.desc.StringDesc
import dev.icerock.moko.resources.desc.desc
import dev.icerock.moko.resources.format
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class DemoResourcesIosTest {
    @Test
    public fun resolvesGeneratedStringsPluralsAndImage() {
        StringDesc.localeType = StringDesc.LocaleType.Custom("en")
        try {
            assertEquals(
                "Flare UI renderer runtime",
                AppleMokoResourceResolver.resolve(DemoRes.strings.demo_title.desc()),
            )
            assertEquals(
                "Count: 7",
                AppleMokoResourceResolver.resolve(DemoRes.strings.count_format.format(7)),
            )
            assertEquals(
                "1 update",
                AppleMokoResourceResolver.resolve(DemoRes.plurals.update_count.format(1, 1)),
            )
            assertTrue(
                AppleMokoResourceResolver
                    .resolve(DemoRes.images.flare_mark)
                    .uiImage
                    .toString()
                    .isNotBlank(),
            )
            StringDesc.localeType = StringDesc.LocaleType.Custom("zh")
            assertEquals(
                "Flare UI 渲染运行时",
                AppleMokoResourceResolver.resolve(DemoRes.strings.demo_title.desc()),
            )
            assertEquals(
                "已更新 3 次",
                AppleMokoResourceResolver.resolve(DemoRes.plurals.update_count.format(3, 3)),
            )
        } finally {
            StringDesc.localeType = StringDesc.LocaleType.System
        }
    }
}
