package dev.dimension.flare.ui.resources.moko

import androidx.compose.runtime.Recomposer
import dev.dimension.flare.ui.FlareBackend
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareComposition
import dev.dimension.flare.ui.FlareWidget
import dev.dimension.flare.ui.FlareWidgetSystem
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.desc.StringDesc
import dev.icerock.moko.resources.desc.desc
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

public class MokoResourcesTest {
    @Test
    public fun stringDescIsResolvedThroughProvidedResolver() {
        var rendered = ""
        val resolver = RecordingResolver("localized value")
        val composition = testComposition()

        try {
            composition.setContent {
                ProvideMokoResources(resolver) {
                    rendered = stringResource("raw value".desc())
                }
            }

            assertEquals("localized value", rendered)
            assertEquals(1, resolver.stringResolveCount)
        } finally {
            composition.dispose()
        }
    }

    private fun testComposition(): FlareComposition<TestBackend> =
        FlareComposition(
            root = EmptyChildren,
            widgetSystem = FlareWidgetSystem(),
            backend = TestBackend,
            parent = Recomposer(EmptyCoroutineContext),
        )

    private class RecordingResolver(
        private val result: String,
    ) : MokoResourceResolver {
        var stringResolveCount: Int = 0
            private set

        override fun resolve(value: StringDesc): String {
            stringResolveCount += 1
            return result
        }

        override fun resolve(value: ImageResource): FlareImage = error("This test resolves only strings.")
    }

    private data object TestBackend : FlareBackend

    private data object EmptyChildren : FlareChildren {
        override fun insert(
            index: Int,
            widget: FlareWidget,
        ): Unit = error("The test content must not emit widgets.")

        override fun move(
            fromIndex: Int,
            toIndex: Int,
            count: Int,
        ): Unit = error("The test content must not emit widgets.")

        override fun remove(
            index: Int,
            count: Int,
        ): Unit = error("The test content must not emit widgets.")
    }
}
