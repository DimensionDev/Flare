@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
)

package dev.dimension.flare.ui.lazy

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.Row
import dev.dimension.flare.ui.foundation.Text
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.getenv
import kotlin.math.abs
import kotlin.native.Platform
import kotlin.test.Test
import kotlin.time.TimeSource

/** Opt-in, native-view benchmark. See docs/benchmarks/apple-lazy.md for the measured boundary. */
public class AppleLazyBenchmark {
    @Test
    public fun nativeCollectionWorkloads() {
        if (getenv("FLARE_LAZY_BENCHMARK")?.toKString() != "1") return
        check(!Platform.isDebugBinary) { "Build the benchmarkReleaseTest binary before measuring." }
        val selected = getenv("FLARE_LAZY_BENCHMARK_SCENARIO")?.toKString()
        val fixtures =
            listOf(
                BenchmarkFixture("column_1000", 1_000),
                BenchmarkFixture("column_10000", 10_000),
                BenchmarkFixture("column_100000", 100_000),
                BenchmarkFixture("cards_10000", 10_000, cards = true),
                BenchmarkFixture("row_10000", 10_000, horizontal = true),
            )
        val failures = mutableListOf<String>()
        fixtures.filter { selected == null || it.name == selected }.forEach { fixture ->
            try {
                benchmark(fixture)
                println("FLARE_BENCH_COMPLETED,${fixture.name}")
            } catch (error: Exception) {
                val message = error.message.orEmpty().replace('\n', ' ')
                failures += "${fixture.name}: $message"
                println("FLARE_BENCH_FAILED,${fixture.name},$message")
            }
        }
        check(failures.isEmpty()) { failures.joinToString("; ") }
    }
}

internal interface AppleLazyBenchmarkHost {
    val platform: String

    fun setContent(content: FlareContent)

    fun layout()

    fun viewportSize(): Pair<Double, Double>

    fun offset(horizontal: Boolean): Double

    fun scrollTo(
        offset: Double,
        horizontal: Boolean,
    )

    fun physicalScroll(active: Boolean)

    /** Checks published geometry against the actual native visible cells/items. */
    fun matches(
        state: LazyListState,
        horizontal: Boolean,
    ): Boolean

    /** Numeric identities avoid retaining native items and changing their lifetime. */
    fun nativeItemIds(): List<Int>

    fun dispose()
}

internal expect fun createAppleLazyBenchmarkHost(): AppleLazyBenchmarkHost

private data class BenchmarkFixture(
    val name: String,
    val count: Int,
    val cards: Boolean = false,
    val horizontal: Boolean = false,
) {
    fun extent(key: Int): Float = if (cards) 156f + (key.mod(3) * 24f) else 44f + (key.mod(5) * 12f)
}

private class Counters {
    var created = 0
    var disposed = 0
    var active = 0
    var peak = 0
    var keyLookups = 0
}

private class Workload(
    val fixture: BenchmarkFixture,
) {
    val state = LazyListState()
    val counters = Counters()
    var prepended = 0
    var expandedKey: Int? = null
    var expanded = false

    fun content(): FlareContent {
        // Capture each provider generation by value, as required by the lazy DSL.
        val prefix = prepended
        val resizedKey = expandedKey
        val isExpanded = expanded
        val items: LazyListScope.() -> Unit = {
            items(
                count = fixture.count + prefix,
                key = {
                    counters.keyLookups++
                    it - prefix
                },
                contentType = { (it - prefix).mod(3) },
                layoutVersion = { isExpanded && it - prefix == resizedKey },
            ) { index ->
                val key = index - prefix
                val savedKey = rememberSaveable { key }
                DisposableEffect(key) {
                    counters.created++
                    counters.active++
                    counters.peak = maxOf(counters.peak, counters.active)
                    onDispose {
                        counters.disposed++
                        counters.active--
                    }
                }
                val extent = fixture.extent(key) + if (isExpanded && key == resizedKey) 24f else 0f
                if (fixture.cards) {
                    Column(modifier = FlareModifier.None.fillMaxWidth().height(extent), spacing = 6f) {
                        Row(spacing = 8f) {
                            Text("Author $savedKey")
                            Text("12:34")
                        }
                        Text(
                            "Post $key: variable-size native lazy content with a longer body, " +
                                "nested stacks, stable identity, and saveable item state.",
                            modifier = FlareModifier.None.fillMaxWidth(),
                        )
                        Row(spacing = 12f) {
                            Text("Reply 12")
                            Text("Repost 34")
                            Text("Like 56")
                        }
                    }
                } else {
                    Text(
                        "Item $savedKey",
                        modifier = if (fixture.horizontal) FlareModifier.None.width(extent) else FlareModifier.None.height(extent),
                    )
                }
            }
        }
        return {
            if (fixture.horizontal) {
                LazyRow(modifier = FlareModifier.None.fillMaxSize(), state = state, spacing = 4f, content = items)
            } else {
                LazyColumn(modifier = FlareModifier.None.fillMaxSize(), state = state, spacing = 4f, content = items)
            }
        }
    }
}

private fun benchmark(fixture: BenchmarkFixture) {
    println("FLARE_BENCH_BEGIN,${fixture.name}")
    // Warm process: each mount creates a fresh window, host, composition, and list.
    repeat(25) { iteration ->
        autoreleasepool {
            val workload = Workload(fixture)
            val start = TimeSource.Monotonic.markNow()
            val host = createAppleLazyBenchmarkHost()
            try {
                host.setContent(workload.content())
                settle(host, workload) {
                    workload.state.layoutInfo.visibleItems
                        .firstOrNull()
                        ?.index == 0
                }
                val nanos = start.elapsedNow().inWholeNanoseconds
                if (iteration >= 5) emit(host, workload, "mount", iteration - 5, nanos, 0, 0, 0)
            } finally {
                host.dispose()
                check(workload.counters.active == 0) { "Disposing the host leaked compositions." }
            }
        }
    }

    val workload = Workload(fixture)
    val host = createAppleLazyBenchmarkHost()
    try {
        host.setContent(workload.content())
        settle(host, workload)
        check(host.viewportSize() == (390.0 to 780.0)) { "Unexpected viewport: ${host.viewportSize()}" }
        println("FLARE_BENCH_VIEWPORT,${host.platform},${fixture.name},390,780")
        measure(host, workload, "idle", 5, 20) { }
        host.physicalScroll(true)
        var requestedOffset = 0.0
        measure(host, workload, "scroll_48pt", 30, 180, verify = {
            val actual = host.offset(fixture.horizontal)
            check(abs(actual - requestedOffset) < 1.0) {
                "Native scroll did not advance: requested=$requestedOffset actual=$actual, ${workload.state.layoutInfo}"
            }
        }) {
            requestedOffset = host.offset(fixture.horizontal) + 48.0
            host.scrollTo(requestedOffset, fixture.horizontal)
        }
        host.physicalScroll(false)
        settle(host, workload)
        measure(host, workload, "jump", 5, 25) { iteration ->
            val target = fixture.count / 10 + (iteration * 7919).mod(fixture.count * 8 / 10)
            runBlocking { workload.state.scrollToItem(target, 13f) }
            settle(host, workload) {
                workload.state.layoutInfo.visibleItems
                    .any { it.index == target && abs(it.offset + 13f) < 1f }
            }
        }
        runBlocking { workload.state.scrollToItem(fixture.count * 3 / 4, 13f) }
        settle(host, workload)
        measure(host, workload, "prepend_20", 3, 20) {
            val anchor =
                workload.state.layoutInfo.visibleItems
                    .first()
            workload.prepended += 20
            host.setContent(workload.content())
            settle(host, workload) {
                val first =
                    workload.state.layoutInfo.visibleItems
                        .firstOrNull()
                workload.state.layoutInfo.totalItemsCount == fixture.count + workload.prepended &&
                    first?.key == anchor.key && abs(first.offset - anchor.offset) < 1f
            }
        }
        workload.expandedKey =
            workload.state.layoutInfo.visibleItems
                .first()
                .key as Int
        measure(host, workload, "resize_visible", 3, 20) {
            workload.expanded = !workload.expanded
            host.setContent(workload.content())
            val expected = fixture.extent(checkNotNull(workload.expandedKey)) + if (workload.expanded) 24f else 0f
            settle(host, workload) {
                workload.state.layoutInfo.visibleItems
                    .any { it.key == workload.expandedKey && abs(it.size - expected) < 1f }
            }
        }
    } finally {
        host.dispose()
        check(workload.counters.active == 0) { "Disposing the host leaked compositions." }
    }
}

private fun measure(
    host: AppleLazyBenchmarkHost,
    workload: Workload,
    operation: String,
    warmup: Int,
    count: Int,
    verify: () -> Unit = {},
    action: (Int) -> Unit,
) {
    val nativeIds = mutableSetOf<Int>()
    repeat(warmup + count) { iteration ->
        autoreleasepool {
            val counters = workload.counters
            val created = counters.created
            val disposed = counters.disposed
            val lookups = counters.keyLookups
            counters.peak = counters.active
            val start = TimeSource.Monotonic.markNow()
            action(iteration)
            settle(host, workload)
            val nanos = start.elapsedNow().inWholeNanoseconds
            verify()
            nativeIds += host.nativeItemIds()
            if (iteration >= warmup) {
                emit(host, workload, operation, iteration - warmup, nanos, created, disposed, lookups)
            }
        }
    }
    println("FLARE_BENCH_REUSE,${host.platform},${workload.fixture.name},$operation,${nativeIds.size}")
}

private fun emit(
    host: AppleLazyBenchmarkHost,
    workload: Workload,
    operation: String,
    iteration: Int,
    nanos: Long,
    createdBefore: Int,
    disposedBefore: Int,
    lookupsBefore: Int,
) {
    val c = workload.counters
    println(
        "FLARE_BENCH,${host.platform},${workload.fixture.name},$operation,$iteration,$nanos," +
            "${c.created - createdBefore},${c.disposed - disposedBefore},${c.active},${c.peak},${c.keyLookups - lookupsBefore}",
    )
}

private fun settle(
    host: AppleLazyBenchmarkHost,
    workload: Workload,
    condition: () -> Boolean = { true },
) {
    val started = TimeSource.Monotonic.markNow()
    var stablePasses = 0
    var previous: LazyListLayoutInfo? = null
    while (stablePasses < 2) {
        Snapshot.sendApplyNotifications()
        host.layout()
        // A main-queue barrier processes scheduled controller work, without a fixed frame sleep.
        var barrier = false
        dispatch_async(dispatch_get_main_queue()) { barrier = true }
        while (!barrier) {
            CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.0001, true)
            check(started.elapsedNow().inWholeSeconds < 5) { "Main queue did not drain." }
        }
        host.layout()
        val info = workload.state.layoutInfo
        val ready = info.visibleItems.isNotEmpty() && host.matches(workload.state, workload.fixture.horizontal) && condition()
        stablePasses = if (ready && previous == info) stablePasses + 1 else 0
        previous = info
        check(started.elapsedNow().inWholeSeconds < 5) { "Layout did not settle: ${workload.fixture.name}, $info" }
    }
}
