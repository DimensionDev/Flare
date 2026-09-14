@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.lazy

import dev.dimension.compose.nativekit.NativeKitChildren
import dev.dimension.compose.nativekit.NativeKitContent
import dev.dimension.compose.nativekit.NativeKitSubcomposition
import dev.dimension.compose.nativekit.NativeKitSubcompositionFactory
import dev.dimension.compose.nativekit.NativeKitWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeLazyCollectionControllerTest {
    @Test
    fun animationWaitsForDeferredAnchorCorrectionsWithoutLosingCompletion() =
        runBlocking {
            val fixture = Fixture()
            fixture.controller.setModel(fixture.model(count = 100))
            fixture.flush()
            val prefix = ControllerTestItem(36.0)
            fixture.controller.bind(prefix, 0)
            fixture.controller.bind(ControllerTestItem(48.0), 10)
            fixture.controller.layout()
            fixture.state.scrollToItem(10)
            val job = launch(Dispatchers.Unconfined) { fixture.state.animateScrollToItem(80, 7f) }
            try {
                var corrections = 30
                fixture.onLayout = {
                    if (corrections-- > 0) {
                        prefix.extent += 1
                        fixture.controller.bind(prefix, 0)
                    } else {
                        fixture.onLayout = null
                    }
                }
                fixture.controller.setModel(fixture.model(count = 100).copy(spacing = 4f))
                fixture.flush()
                checkNotNull(fixture.animationCompletion) {
                    "Deferred anchor restoration discarded the native completion."
                }.invoke()
                assertTrue(job.isCompleted)
                assertFalse(job.isCancelled)
                assertFalse(fixture.state.isScrollInProgress)
            } finally {
                job.cancel()
                fixture.controller.dispose()
            }
        }

    @Test
    fun nativeSpliceAtTheViewportPreservesMeasurementsBeforeTheViewport() =
        runBlocking {
            val fixture = Fixture()
            fixture.controller.setModel(fixture.model(count = 10_000))
            fixture.flush()
            repeat(4_200) { index ->
                val item = ControllerTestItem(if (index == 0) 32.0 else 60.0)
                fixture.controller.bind(item, index)
                fixture.controller.layout()
                fixture.controller.release(item)
            }
            fixture.controller.bind(ControllerTestItem(60.0), 4_200)
            fixture.controller.layout()
            fixture.state.scrollToItem(4_200)
            val start = fixture.controller.itemStart(4_200)
            fixture.controller.setModel(fixture.model(count = 10_001, prefix = 1))
            fixture.flush()
            assertEquals(32.0, fixture.controller.itemExtent(1), "A prepend lost a measurement older than the exact-size LRU.")
            assertEquals(start + 48.0, fixture.controller.itemStart(4_201))
            fixture.controller.dispose()
        }

    @Test
    fun cachedSizesResolvedByNativeLayoutStillCorrectTheReorderedAnchor() =
        runBlocking {
            val fixture = Fixture()
            fixture.controller.setModel(fixture.model(count = 100))
            fixture.flush()
            repeat(100) { index ->
                val item =
                    ControllerTestItem(
                        when (index) {
                            20 -> 90.0
                            50 -> 30.0
                            else -> 48.0
                        },
                    )
                fixture.controller.bind(item, index)
                fixture.controller.layout()
                fixture.controller.release(item)
            }
            val anchor = ControllerTestItem(90.0)
            fixture.controller.bind(anchor, 20)
            fixture.controller.layout()
            fixture.state.scrollToItem(20, 13f)

            fixture.onReload = { fixture.controller.bind(anchor, 50) }
            fixture.onLayout = {
                fixture.onLayout = null
                // A native layout attribute lookup consumes this cached prefix change before measure().
                fixture.controller.itemExtent(20)
            }
            val provider =
                IntervalLazyListScope()
                    .apply {
                        items(100, key = {
                            when (it) {
                                20 -> 50
                                50 -> 20
                                else -> it
                            }
                        }) {}
                    }.build()
            fixture.controller.setModel(fixture.model(count = 100).copy(itemProvider = provider))
            fixture.flush()
            assertEquals(
                -13f,
                fixture.state.layoutInfo.visibleItems
                    .single { it.key == 20 }
                    .offset,
            )
            fixture.controller.dispose()
        }

    @Test
    fun updatingAnAnimationRetargetsItAndIgnoresTheOldCompletion() =
        runBlocking {
            val fixture = Fixture()
            fixture.controller.setModel(fixture.model())
            fixture.flush()
            val job = launch(Dispatchers.Unconfined) { fixture.state.animateScrollToItem(10, 7f) }
            val oldCompletion = checkNotNull(fixture.animationCompletion)
            fixture.controller.setModel(fixture.model().copy(spacing = 4f))
            fixture.flush()
            assertEquals(1, fixture.animationStops)
            oldCompletion()
            assertTrue(job.isActive, "A superseded animation completed the current request.")
            assertTrue(fixture.state.isScrollInProgress)
            checkNotNull(fixture.animationCompletion).invoke()
            assertTrue(job.isCompleted)
            assertFalse(job.isCancelled)
            assertFalse(fixture.state.isScrollInProgress)
            assertEquals(527.0, fixture.viewport(LazyListOrientation.Vertical).offset)
            fixture.controller.dispose()
        }

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
        assertEquals(1, fixture.activeCompositions)
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
    NativeKitSubcompositionFactory {
    val state = LazyListState()
    val controller = NativeLazyCollectionController(this, Dispatchers.Unconfined)
    private val scheduled = mutableListOf<() -> Unit>()
    private var offset = 0.0
    var activeCompositions = 0
    var disposals = 0
    var contentUpdates = 0
    var animationCompletion: (() -> Unit)? = null
    var animationStops = 0
    var onLayout: (() -> Unit)? = null
    var onReload: (() -> Unit)? = null

    fun model(
        count: Int = 20,
        prefix: Int = 0,
    ) = LazyCollectionModel(
        LazyListOrientation.Vertical,
        0f,
        LazyCrossAxisAlignment.Stretch,
        IntervalLazyListScope().apply { items(count, key = { it - prefix }) {} }.build(),
        this,
        state,
    )

    fun flush() {
        while (scheduled.isNotEmpty()) scheduled.removeAt(0).invoke()
    }

    override fun create(root: NativeKitChildren): NativeKitSubcomposition {
        activeCompositions += 1
        return object : NativeKitSubcomposition {
            override fun setContent(content: NativeKitContent) {
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

    override fun reloadData() {
        onReload?.invoke()
    }

    override fun updateItems(
        index: Int,
        removedCount: Int,
        insertedCount: Int,
        apply: () -> Double?,
    ) {
        apply()?.let { offset = it }
    }

    override fun invalidateLayout() = Unit

    override fun layoutIfNeeded() {
        onLayout?.invoke()
    }

    override fun scrollTo(
        offset: Double,
        animated: Boolean,
        completion: () -> Unit,
    ) {
        if (animated) {
            animationCompletion = completion
        } else {
            animationCompletion = null
            this.offset = offset
            completion()
        }
    }

    override fun stopAnimatedScroll() {
        animationStops += 1
        animationCompletion = null
    }

    override fun schedule(block: () -> Unit) {
        scheduled += block
    }
}

private class ControllerTestItem(
    var extent: Double = 36.0,
) : NativeLazyItem {
    override val children =
        object : NativeKitChildren {
            override fun insert(
                index: Int,
                widget: NativeKitWidget,
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
    ): Double = extent
}
