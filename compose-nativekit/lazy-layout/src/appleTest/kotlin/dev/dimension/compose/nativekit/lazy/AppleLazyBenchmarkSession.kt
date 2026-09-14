@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.compose.nativekit.lazy

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.runBlocking
import platform.darwin.NSObject

/** Benchmark-only bridge for the Swift app's common UIKit/AppKit measurement loop. */
public class AppleLazyBenchmarkSession(
    count: Int,
    cards: Boolean,
    horizontal: Boolean,
) {
    private val workload = Workload(BenchmarkFixture("embedded", count, cards, horizontal))
    private val host = createAppleLazyBenchmarkHost(embedded = true)

    public val view: NSObject get() = host.nativeView
    public val created: Int get() = workload.counters.created
    public val disposed: Int get() = workload.counters.disposed
    public val active: Int get() = workload.counters.active

    public fun mount() {
        host.setContent(workload.content())
    }

    public fun layout() {
        Snapshot.sendApplyNotifications()
        host.layout()
    }

    public fun ready(): Boolean =
        workload.state.layoutInfo.visibleItems
            .isNotEmpty() && host.matches(workload.state, workload.fixture.horizontal)

    public fun items(): List<AppleLazyBenchmarkItem> =
        workload.state.layoutInfo.visibleItems.map {
            AppleLazyBenchmarkItem(it.index, it.key as Int, it.offset.toDouble(), it.size.toDouble())
        }

    public fun offset(): Double = host.offset(workload.fixture.horizontal)

    public fun scroll(offset: Double) {
        host.scrollTo(offset, workload.fixture.horizontal)
    }

    public fun physicalScroll(active: Boolean) {
        host.physicalScroll(active)
    }

    public fun jump(index: Int) {
        runBlocking { workload.state.scrollToItem(index, 13f) }
    }

    public fun prepend() {
        workload.prepended += 20
        host.setContent(workload.content())
    }

    public fun resize() {
        if (workload.expandedKey == null) {
            workload.expandedKey =
                workload.state.layoutInfo.visibleItems
                    .first()
                    .key as Int
        }
        workload.expanded = !workload.expanded
        host.setContent(workload.content())
    }

    public fun dispose() {
        host.dispose()
    }
}

public class AppleLazyBenchmarkItem(
    public val index: Int,
    public val key: Int,
    public val offset: Double,
    public val extent: Double,
)
