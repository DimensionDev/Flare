@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.lazy

import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareSubcomposition
import dev.dimension.flare.ui.FlareSubcompositionFactory
import dev.dimension.flare.ui.FlareWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeLazyCollectionControllerTest {
    @Test
    fun nativeReuseEndsTheOldCompositionAndBindsTheNewKey() {
        val fixture = Fixture()
        fixture.controller.setModel(fixture.model())
        fixture.flush()
        val item = ControllerTestItem()
        fixture.controller.bind(item, 0)
        fixture.controller.layout()
        assertEquals(
            listOf(0),
            fixture.state.layoutInfo.visibleItems
                .map { it.key },
        )
        assertEquals(1, fixture.activeCompositions)

        fixture.controller.release(item)
        assertEquals(0, fixture.activeCompositions)
        fixture.controller.bind(item, 1)
        fixture.controller.layout()
        assertEquals(
            listOf(1),
            fixture.state.layoutInfo.visibleItems
                .map { it.key },
        )
        assertEquals(1, fixture.activeCompositions)

        fixture.controller.dispose()
        fixture.flush()
        assertEquals(0, fixture.activeCompositions)
        assertEquals(2, fixture.disposals)
    }

    @Test
    fun consecutiveModelsApplyTheLatestGenerationOutsideTheParentComposition() {
        val fixture = Fixture()
        fixture.controller.setModel(fixture.model())
        fixture.flush()
        fixture.controller.bind(ControllerTestItem(), 0)
        val updates = fixture.contentUpdates

        fixture.controller.setModel(fixture.model(count = 100))
        fixture.controller.setModel(fixture.model(count = 101))
        assertEquals(updates, fixture.contentUpdates)
        assertEquals(20, fixture.controller.itemCount)

        fixture.flush()
        assertEquals(101, fixture.controller.itemCount)
        assertEquals(101, fixture.state.layoutInfo.totalItemsCount)
        assertEquals(0, fixture.activeCompositions)
        fixture.controller.dispose()
    }

    @Test
    fun shrinkingTheModelCancelsAnimationAndIgnoresItsLateCompletion() =
        runBlocking {
            val fixture = Fixture()
            fixture.controller.setModel(fixture.model())
            fixture.flush()
            val job = launch(Dispatchers.Unconfined) { fixture.state.animateScrollToItem(19) }
            val completion = checkNotNull(fixture.animationCompletion)
            assertTrue(fixture.state.isScrollInProgress)

            fixture.controller.setModel(fixture.model(count = 1))
            fixture.flush()
            completion()
            job.join()
            assertTrue(job.isCancelled)
            assertFalse(fixture.state.isScrollInProgress)
            assertEquals(1, fixture.state.layoutInfo.totalItemsCount)
            assertEquals(1, fixture.animationStops)
            fixture.controller.dispose()
        }
}

private class Fixture :
    NativeLazyCollection,
    FlareSubcompositionFactory {
    val state = LazyListState()
    val controller = NativeLazyCollectionController(this, Dispatchers.Unconfined)
    private val scheduled = mutableListOf<() -> Unit>()
    private var offset = 0.0
    var activeCompositions = 0
    var disposals = 0
    var contentUpdates = 0
    var animationCompletion: (() -> Unit)? = null
    var animationStops = 0

    fun model(count: Int = 20) =
        LazyCollectionModel(
            LazyListOrientation.Vertical,
            0f,
            LazyCrossAxisAlignment.Stretch,
            IntervalLazyListScope().apply { items(count, key = { it }) {} }.build(),
            this,
            state,
        )

    fun flush() {
        while (scheduled.isNotEmpty()) scheduled.removeAt(0).invoke()
    }

    override fun create(root: FlareChildren): FlareSubcomposition {
        activeCompositions += 1
        return object : FlareSubcomposition {
            override fun setContent(content: FlareContent) {
                contentUpdates += 1
                root.onEndChanges()
            }

            override fun deactivate() = Unit

            override fun dispose() {
                activeCompositions -= 1
                disposals += 1
            }
        }
    }

    override fun viewport(orientation: LazyListOrientation) = NativeLazyViewport(offset, 200.0, 320.0)

    override val isPhysicalScrollInProgress: Boolean get() = false

    override fun reloadData() = Unit

    override fun invalidateLayout() = Unit

    override fun layoutIfNeeded() = Unit

    override fun scrollTo(
        offset: Double,
        animated: Boolean,
        completion: () -> Unit,
    ) {
        if (animated) {
            animationCompletion = completion
        } else {
            this.offset = offset
            completion()
        }
    }

    override fun stopAnimatedScroll() {
        animationStops += 1
    }

    override fun schedule(block: () -> Unit) {
        scheduled += block
    }
}

private class ControllerTestItem : NativeLazyItem {
    override val children =
        object : FlareChildren {
            override fun insert(
                index: Int,
                widget: FlareWidget,
            ) = Unit

            override fun move(
                fromIndex: Int,
                toIndex: Int,
                count: Int,
            ) = Unit

            override fun remove(
                index: Int,
                count: Int,
            ) = Unit
        }

    override fun configure(model: LazyCollectionModel) = Unit

    override fun measure(
        orientation: LazyListOrientation,
        crossExtent: Double,
    ): Double = 36.0
}
