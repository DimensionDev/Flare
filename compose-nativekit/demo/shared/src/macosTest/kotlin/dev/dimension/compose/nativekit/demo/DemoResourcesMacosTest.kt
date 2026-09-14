package dev.dimension.compose.nativekit.demo

import dev.dimension.compose.nativekit.demo.resources.DemoRes
import dev.dimension.compose.nativekit.resources.moko.AppleMokoResourceResolver
import dev.icerock.moko.resources.desc.StringDesc
import dev.icerock.moko.resources.desc.desc
import dev.icerock.moko.resources.format
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class DemoResourcesMacosTest {
    @Test
    public fun resolvesGeneratedStringsPluralsAndImage() {
        StringDesc.localeType = StringDesc.LocaleType.Custom("en")
        try {
            assertEquals(
                "Compose NativeKit renderer runtime",
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
                    .resolve(DemoRes.images.nativekit_mark)
                    .nsImage
                    .toString()
                    .isNotBlank(),
            )
            StringDesc.localeType = StringDesc.LocaleType.Custom("zh")
            assertEquals(
                "Compose NativeKit 渲染运行时",
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
