package dev.dimension.flare

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/** Opt-in acceptance against the normal app and real, already signed-in accounts.
 * Run capture, restart-ranked and restart-mixed in separate instrumentation processes.
 * The saved JSON contains test expectations; app bookmarks and feeds are never seeded.
 */
class LiveHomeTimelineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val args get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext
    private val directory get() = File(context.filesDir, "home-acceptance").apply { mkdirs() }
    private val ranked get() = args.getString("rankedTab") ?: context.getString(R.string.home_tab_featured_title)
    private val mixed get() = args.getString("mixedTab") ?: context.getString(R.string.home_tab_mixed_timeline_title)
    private val itemSelector = By.res(Pattern.compile("timeline-item:.*"))
    private var launchBatch: String? = null

    private data class Bookmark(
        val tag: String,
        val top: Int,
    ) {
        fun json() = JSONObject().put("tag", tag).put("top", top)
    }

    @Test fun liveHomeAcceptance() {
        val phase = args.getString("phase")
        assumeTrue("Live account acceptance requires an explicit -e phase argument", phase != null)
        if (phase == "pull-ranked" || phase == "pull-mixed") {
            launchBatch = newestBatch(phase.removePrefix("pull-"))
        }
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        // Live avatars and media can animate continuously. Synchronize on the
        // explicit item/geometry conditions below instead of global animation idle.
        Configurator.getInstance().waitForIdleTimeout = 500
        Log.i("HOME-ACCEPTANCE", "begin $phase")
        readCache { db ->
            db.prepare("PRAGMA user_version").use {
                it.step()
                println("HOME-CACHE version=${it.getLong(0)}")
            }
            db.prepare("SELECT count(*) FROM DbStatus").use {
                it.step()
                println("HOME-CACHE cachedPosts=${it.getLong(0)}")
            }
        }
        SystemClock.sleep(3000)
        when (phase) {
            "configure" -> {
                configure()
            }

            "toggle-memory" -> {
                select(ranked)
                setRememberPosition(false)
                select(ranked)
                waitUntil { items().isNotEmpty() && items().all { !it.resourceName.contains("home-history/") } }
                setRememberPosition(true)
                select(ranked)
                waitUntil { items().any { it.resourceName.contains("home-history/") } }
            }

            "capture" -> {
                select(ranked)
                val a = scrollAndRemember("ranked-before-switch")
                select(mixed)
                val b = scrollAndRemember("mixed-before-switch")
                select(ranked)
                assertRestored(a, "ranked-after-tab-switch")
                select(mixed)
                assertRestored(b, "mixed-after-tab-switch")
                select(ranked)
                File(directory, "bookmarks.json").writeText(JSONObject().put("ranked", a.json()).put("mixed", b.json()).toString())
            }

            "restart-ranked", "restart-mixed" -> {
                val expected = JSONObject(File(directory, "bookmarks.json").readText())
                val first = if (phase == "restart-ranked") "ranked" else "mixed"
                val second = if (first == "ranked") "mixed" else "ranked"
                waitForTitle(if (first == "ranked") ranked else mixed)
                for (kind in listOf(first, second)) {
                    if (kind != first) select(if (kind == "ranked") ranked else mixed)
                    val item = expected.getJSONObject(kind)
                    assertRestored(Bookmark(item.getString("tag"), item.getInt("top")), "$kind-after-$phase")
                }
            }

            "auto-on" -> {
                setAutomaticRefresh(true)
            }

            "auto-off" -> {
                setAutomaticRefresh(false)
            }

            "auto-ranked" -> {
                verifyAutomaticRefresh(ranked, "ranked")
            }

            "auto-mixed" -> {
                verifyAutomaticRefresh(mixed, "mixed")
            }

            "pull-ranked" -> {
                verifyPullRefresh(ranked, "ranked")
            }

            "pull-mixed" -> {
                verifyPullRefresh(mixed, "mixed")
            }

            "gap-ranked" -> {
                verifyGap(ranked, "ranked")
            }

            "gap-mixed" -> {
                verifyGap(mixed, "mixed")
            }

            else -> {
                error("Unknown acceptance phase: $phase")
            }
        }
        device.pressHome()
        SystemClock.sleep(1500)
    }

    private fun find(selector: BySelector): UiObject2 =
        device.wait(Until.findObject(selector), 30_000)
            ?: error(
                "Required UI element missing: $selector; " +
                    "homeList=${device.hasObject(By.res("home-timeline-list"))}, " +
                    "navigation=${device.hasObject(By.desc(context.getString(R.string.open_navigation_menu)))}, " +
                    "loginPrompt=${device.hasObject(By.text(context.getString(R.string.login_expired_relogin)))}",
            )

    private fun click(selector: BySelector) {
        repeat(5) {
            try {
                instrumentation.uiAutomation.clearCache()
                find(selector).click()
                return
            } catch (_: StaleObjectException) {
                SystemClock.sleep(500)
            }
        }
        error("UI control kept being replaced: $selector")
    }

    private fun clickText(text: String) {
        repeat(7) {
            device.findObject(By.text(text))?.let {
                click(By.text(text))
                return
            }
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, .7f)
                ?: device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 3 / 4,
                    device.displayWidth / 2,
                    device.displayHeight / 3,
                    45,
                )
        }
        click(By.text(text))
    }

    private fun configure() {
        revealToolbar()
        click(By.desc(context.getString(R.string.open_navigation_menu)))
        clickText(context.getString(R.string.settings_title))
        clickText(context.getString(R.string.settings_behavior_title))
        find(By.text(context.getString(R.string.settings_remember_home_timeline_position)))
        device.dumpWindowHierarchy(File(directory, "settings-hierarchy.xml"))
        waitUntil { device.findObjects(By.checkable(true)).size >= 2 }
        for (index in 0..1) {
            val toggle = device.findObjects(By.checkable(true))[index]
            if (!toggle.isChecked) toggle.click()
        }
        waitUntil { device.findObjects(By.checkable(true)).take(2).let { it.size == 2 && it.all { toggle -> toggle.isChecked } } }
        screenshot("reading-settings-enabled")
        device.pressBack()
        device.pressBack()
    }

    private fun setRememberPosition(enabled: Boolean) {
        revealToolbar()
        click(By.desc(context.getString(R.string.open_navigation_menu)))
        clickText(context.getString(R.string.settings_title))
        clickText(context.getString(R.string.settings_behavior_title))
        find(By.text(context.getString(R.string.settings_remember_home_timeline_position)))
        waitUntil { device.findObjects(By.checkable(true)).size >= 2 }
        val toggle = device.findObjects(By.checkable(true))[1]
        if (toggle.isChecked != enabled) toggle.click()
        waitUntil { device.findObjects(By.checkable(true))[1].isChecked == enabled }
        device.pressBack()
        device.pressBack()
    }

    private fun selected(): String {
        instrumentation.uiAutomation.clearCache()
        val title = device.findObject(By.res("home-timeline-title")) ?: return ""
        return (listOfNotNull(title.text) + title.findObjects(By.text(Pattern.compile(".+"))).map { it.text }).joinToString()
    }

    private fun waitForTitle(name: String) {
        try {
            waitUntil { selected().contains(name) }
        } finally {
            println("HOME-NAV expected=$name selected=${selected()} list=${device.hasObject(By.res("home-timeline-list"))}")
        }
        assertTrue("Selected Home tab must be $name", selected().contains(name))
    }

    private fun select(name: String) {
        val loginPrompt = By.text(context.getString(R.string.login_expired_relogin))
        if (device.hasObject(loginPrompt)) {
            println("HOME-NAV waiting for login notification")
            assertTrue("Login notification must finish before using the toolbar", device.wait(Until.gone(loginPrompt), 20_000))
            SystemClock.sleep(1500)
        }
        revealToolbar()
        if (!selected().contains(name)) {
            click(By.res("home-timeline-title"))
            clickText(name)
        }
        waitForTitle(name)
        waitUntil { items().isNotEmpty() }
        SystemClock.sleep(1500)
    }

    private fun revealToolbar() {
        repeat(8) {
            instrumentation.uiAutomation.clearCache()
            val bounds = device.findObject(By.res("home-timeline-title"))?.visibleBounds
            if (bounds != null && bounds.height() > 0) {
                SystemClock.sleep(500)
                instrumentation.uiAutomation.clearCache()
                if (device.findObject(By.res("home-timeline-title"))?.visibleBounds == bounds) return
            } else {
                dragList(.32, .6)
            }
        }
        find(By.res("home-timeline-title"))
    }

    private data class VisibleItem(
        val resourceName: String,
        val visibleBounds: Rect,
    )

    private fun UiObject2.snapshot(): VisibleItem? =
        try {
            VisibleItem(resourceName, visibleBounds)
        } catch (_: StaleObjectException) {
            null
        }

    private fun items() =
        device
            .findObject(By.res("home-timeline-list"))
            ?.findObjects(itemSelector)
            .orEmpty()
            .mapNotNull { it.snapshot() }
            .filter {
                !it.resourceName.startsWith("timeline-item:timeline-gap:") &&
                    it.visibleBounds.height() > 0
            }.sortedBy { it.visibleBounds.top }

    private fun scrollAndRemember(name: String): Bookmark {
        repeat(2) {
            device.swipe(60, device.displayHeight * 3 / 4, 60, device.displayHeight / 3, 75)
            SystemClock.sleep(1000)
        }
        // Use the outer list margin so a post's media gestures do not consume the drag.
        repeat(5) {
            instrumentation.uiAutomation.clearCache()
            if (!device.hasObject(By.res("home-timeline-title"))) {
                dragList(.32, .6)
            }
        }
        find(By.res("home-timeline-title"))
        SystemClock.sleep(2500)
        val item = items().first { it.visibleBounds.top > device.displayHeight * .16 && it.visibleBounds.top < device.displayHeight * .75 }
        assertTrue("Reading-position history must be enabled", item.resourceName.contains("home-history/"))
        return Bookmark(item.resourceName, item.visibleBounds.top).also { assertRestored(it, name) }
    }

    private fun assertRestored(
        bookmark: Bookmark,
        name: String,
    ) {
        var samples = 0
        var actual = Int.MIN_VALUE
        try {
            waitUntil {
                instrumentation.uiAutomation.clearCache()
                actual = device.findObject(By.res(bookmark.tag))?.visibleBounds?.top ?: Int.MIN_VALUE
                samples = if (kotlin.math.abs(actual.toLong() - bookmark.top) <= 1) samples + 1 else 0
                samples >= 3
            }
        } finally {
            screenshot(name)
            Log.i("HOME-ACCEPTANCE", "$name item=${bookmark.tag.hashCode()} expected=${bookmark.top} actual=$actual")
        }
        assertEquals(name, bookmark.top, actual)
    }

    private fun setAutomaticRefresh(enabled: Boolean) {
        revealToolbar()
        click(By.desc(context.getString(R.string.open_navigation_menu)))
        clickText(context.getString(R.string.settings_title))
        clickText(context.getString(R.string.settings_behavior_title))
        val choices =
            listOf(
                R.string.settings_auto_refresh_disabled,
                R.string.settings_auto_refresh_one_minute,
                R.string.settings_auto_refresh_five_minutes,
                R.string.settings_auto_refresh_fifteen_minutes,
                R.string.settings_auto_refresh_thirty_minutes,
                R.string.settings_auto_refresh_one_hour,
            ).map(context::getString)
        val choiceSelector = By.text(Pattern.compile(choices.joinToString("|") { Pattern.quote(it) }))
        val headline = find(By.text(context.getString(R.string.settings_home_timeline_auto_refresh_interval)))
        var row = headline.parent
        while (row.findObjects(choiceSelector).isEmpty()) row = row.parent
        val current = row.findObjects(choiceSelector).single()
        val target = context.getString(if (enabled) R.string.settings_auto_refresh_one_minute else R.string.settings_auto_refresh_disabled)
        if (current.text != target) {
            current.click()
            click(By.text(target))
        }
        waitUntil {
            instrumentation.uiAutomation.clearCache()
            device.hasObject(By.text(target)) && !device.hasObject(By.text(context.getString(R.string.settings_auto_refresh_five_minutes)))
        }
        screenshot(if (enabled) "auto-refresh-enabled" else "auto-refresh-restored-off")
        device.pressBack()
        device.pressBack()
    }

    private fun newestBatch(kind: String): String =
        readCache { db ->
            val key = historyKey(db, kind)
            db
                .rawQuery(
                    "SELECT id FROM DbTimelineBatch WHERE historyKey = ? ORDER BY ordinal LIMIT 1",
                    arrayOf(key),
                ).use {
                    check(it.step())
                    it.getText(0)
                }
        }

    private fun verifyAutomaticRefresh(
        tab: String,
        kind: String,
    ) {
        select(tab)
        val bookmark = scrollAndRemember("$kind-before-timed-refresh")
        val batch = newestBatch(kind)
        val existing = historyItems(kind)
        waitUntil(95_000) { newestBatch(kind) != batch }
        assertRestored(bookmark, "$kind-after-timed-refresh")
        val after = historyItems(kind)
        val retained = existing.toSet()
        assertEquals("Timed refresh must preserve existing order", existing, after.filter { it in retained })
        Log.i("HOME-ACCEPTANCE", "$kind-auto-refresh retained=${existing.size} added=${after.size - existing.size}")
    }

    private fun verifyPullRefresh(
        tab: String,
        kind: String,
    ) {
        select(tab)
        // Finish the launch refresh before positioning for a separate pull.
        waitUntil(60_000) { newestBatch(kind) != launchBatch }
        SystemClock.sleep(2500)
        val latest = By.text(context.getString(R.string.home_timeline_view_latest))
        device.wait(Until.findObject(latest), 10_000)?.let { click(latest) }
        SystemClock.sleep(2500)
        waitUntil {
            instrumentation.uiAutomation.clearCache()
            val head = "timeline-item:${historyItems(kind).first()}"
            val atHead =
                device
                    .findObject(By.res(head))
                    ?.visibleBounds
                    ?.top
                    ?.let { it > 0 } == true
            if (!atHead && device.hasObject(latest)) {
                click(latest)
                SystemClock.sleep(1500)
            }
            atHead
        }
        instrumentation.uiAutomation.clearCache()
        val item = items().first { it.resourceName == "timeline-item:${historyItems(kind).first()}" }
        val bookmark = Bookmark(item.resourceName, item.visibleBounds.top)
        assertRestored(bookmark, "$kind-before-pull-refresh")
        val batch = newestBatch(kind)
        val existing = historyItems(kind)
        // Start below the toolbar and in the list margin, outside media gestures.
        instrumentation.uiAutomation.clearCache()
        dragList(.28, .78)
        screenshot("$kind-pull-gesture-released")
        waitUntil(60_000) { newestBatch(kind) != batch }
        assertRestored(bookmark, "$kind-after-pull-refresh")
        val after = historyItems(kind)
        assertEquals("Pull refresh must preserve existing order", existing, after.filter { it in existing.toSet() })
        Log.i("HOME-ACCEPTANCE", "$kind-pull-refresh retained=${existing.size} added=${after.size - existing.size}")
        assertTrue("The live feed must prepend content to exercise its new-content notice", after.first() != existing.first())
        find(latest)
        screenshot("$kind-new-content-notice-visible")
        click(latest)
        waitUntil {
            instrumentation.uiAutomation.clearCache()
            !device.hasObject(latest) && device.hasObject(By.res("timeline-item:${after.first()}"))
        }
        screenshot("$kind-new-content-notice-dismissed")
        dragList(.75, .3)
        SystemClock.sleep(1000)
        instrumentation.uiAutomation.clearCache()
        assertTrue("Scrolling away from the latest post must not announce it again", !device.hasObject(latest))
        screenshot("$kind-no-notice-after-scrolling-away")
        Log.i("HOME-ACCEPTANCE", "$kind-new-content-notice shown, clicked, dismissed, and stayed dismissed after scrolling")
    }

    private data class Batch(
        val cursor: String?,
        val count: Int,
    )

    private fun <T> readCache(block: (SQLiteConnection) -> T): T =
        // Use the same SQLite library as Room; another engine in this process can
        // interfere with the live database's POSIX locks and WAL shared memory.
        BundledSQLiteDriver().open(File(context.cacheDir, "cache.db").path, SQLITE_OPEN_READONLY).use(block)

    private fun SQLiteConnection.rawQuery(
        sql: String,
        args: Array<String>,
    ): SQLiteStatement = prepare(sql).also { statement -> args.forEachIndexed { index, value -> statement.bindText(index + 1, value) } }

    private fun batch(id: String): Batch =
        readCache { db ->
            db
                .rawQuery(
                    "SELECT nextKey, (SELECT count(*) FROM DbTimelineEntry WHERE batchId = ?) FROM DbTimelineBatch WHERE id = ?",
                    arrayOf(id, id),
                ).use {
                    check(it.step())
                    Batch(if (it.isNull(0)) null else it.getText(0), it.getLong(1).toInt())
                }
        }

    private fun historyItems(kind: String): List<String> =
        readCache { db ->
            val key = historyKey(db, kind)
            db
                .rawQuery(
                    "SELECT e.itemKey FROM DbTimelineEntry e JOIN DbTimelineBatch b ON b.id=e.batchId WHERE e.historyKey = ? ORDER BY b.ordinal,e.position",
                    arrayOf(key),
                ).use {
                    buildList { while (it.step()) add(it.getText(0)) }
                }
        }

    private fun historyKey(
        db: SQLiteConnection,
        kind: String,
    ): String {
        val tabId = args.getString("${kind}TabId") ?: "mixed_timeline_system_home".takeIf { kind == "mixed" }
        val keys =
            if (tabId != null) {
                db.rawQuery("SELECT historyKey FROM DbTimelineBinding WHERE tabId = ?", arrayOf(tabId))
            } else {
                // A mixed namespace also contains its featured child key. Match a
                // bound leaf rather than accidentally combining both histories.
                db.rawQuery(
                    "SELECT DISTINCT historyKey FROM DbTimelineBinding WHERE historyKey LIKE ? AND historyKey NOT LIKE ?",
                    arrayOf("home-history/%featured_%", "%mixed:%"),
                )
            }.use { statement -> buildList { while (statement.step()) add(statement.getText(0)) } }
        check(keys.size == 1) { "Expected one $kind binding; provide -e ${kind}TabId for a custom fixture" }
        return keys.single()
    }

    private fun dragList(
        start: Double,
        end: Double,
    ) {
        val from = (device.displayHeight * start).toInt()
        val to = (device.displayHeight * end).toInt()
        repeat(3) {
            if (device.swipe(60, from, 60, to, 180)) {
                SystemClock.sleep(1500)
                instrumentation.uiAutomation.clearCache()
                return
            }
            SystemClock.sleep(500)
        }
        error("Could not inject a list gesture")
    }

    private fun scrollList(direction: Direction) {
        if (direction == Direction.DOWN) dragList(.78, .28) else dragList(.28, .78)
    }

    private fun verifyGap(
        tab: String,
        kind: String,
    ) {
        select(tab)
        val latest = By.text(context.getString(R.string.home_timeline_view_latest))
        if (args.getString("fromCurrent") != "true") {
            device.wait(Until.findObject(latest), 10_000)?.let { click(latest) }
        }
        SystemClock.sleep(2000)
        var gap: VisibleItem? = null
        var predecessor: VisibleItem? = null
        val skipped = mutableSetOf<String>()
        repeat(150) { attempt ->
            if (gap != null) return@repeat
            val visible = device.findObjects(By.res(Pattern.compile("timeline-item:timeline-gap:.*"))).mapNotNull { it.snapshot() }
            if (attempt % 10 ==
                0
            ) {
                Log.i(
                    "HOME-ACCEPTANCE",
                    "$kind-gap search=$attempt gaps=${visible.map {
                        it.visibleBounds
                    }} first=${items().firstOrNull()?.let { "${it.resourceName.hashCode()}:${it.visibleBounds}" }}",
                )
            }
            gap = visible.firstOrNull { it.resourceName !in skipped && it.visibleBounds.height() > 0 }
            if (gap != null) {
                // Keep the click above the floating bottom navigation overlay.
                if (gap!!.visibleBounds.bottom > device.displayHeight * .83) {
                    find(By.res("home-timeline-list")).scroll(Direction.DOWN, .2f, 500)
                    SystemClock.sleep(1500)
                    instrumentation.uiAutomation.clearCache()
                    gap = null
                    return@repeat
                }
                predecessor =
                    items().filter { it.visibleBounds.bottom <= gap!!.visibleBounds.top + 2 }.maxByOrNull { it.visibleBounds.bottom }
                // Accessibility clips partially visible items to the screen. Only a
                // visible top edge can prove the predecessor's position is preserved.
                if (predecessor == null || predecessor.visibleBounds.top <= 0) {
                    skipped += gap!!.resourceName
                    gap = null
                }
            }
            if (gap == null) scrollList(Direction.DOWN)
        }
        val target = checkNotNull(gap) { "No real manual gap found" }
        val anchor = checkNotNull(predecessor)
        val id = target.resourceName.removePrefix("timeline-item:timeline-gap:")
        val tag = target.resourceName
        val anchorTag = anchor.resourceName
        val before = batch(id)
        val existing = historyItems(kind)
        SystemClock.sleep(5000)
        assertEquals("Reaching a gap must not load it", before, batch(id))
        instrumentation.uiAutomation.clearCache()
        val anchorBounds = device.findObject(UiSelector().resourceId(anchorTag)).bounds
        screenshot("$kind-gap-before-click")
        Log.i("HOME-ACCEPTANCE", "$kind-gap clicking batch=${id.hashCode()} count=${before.count} anchor=$anchorBounds")
        click(By.res(tag))
        waitUntil(60_000) { batch(id) != before }
        SystemClock.sleep(2000)
        val after = batch(id)
        instrumentation.uiAutomation.clearCache()
        val actualAnchor = device.findObject(UiSelector().resourceId(anchorTag)).bounds
        screenshot("$kind-gap-after-click")
        Log.i("HOME-ACCEPTANCE", "$kind-gap anchor before=$anchorBounds after=$actualAnchor")
        assertEquals("Gap predecessor top", anchorBounds.top, actualAnchor.top)
        val following = historyItems(kind)
        assertTrue("A clicked page must add real content", following.size > existing.size)
        val retained = existing.toSet()
        assertEquals("Existing items must retain their order", existing, following.filter { it in retained })
        if (after.cursor == null) {
            assertTrue("A completed gap must disappear", !device.hasObject(By.res(tag)))
            Log.i("HOME-ACCEPTANCE", "$kind-gap joined old content; retained=${existing.size}")
            return
        }
        screenshot("$kind-gap-after-click")
        Log.i(
            "HOME-ACCEPTANCE",
            "$kind-gap before=${before.count} after=${after.count} retained=${existing.size} anchorTop=${actualAnchor.top} anchorBottom=${actualAnchor.bottom}",
        )
        val targetOrdinal =
            readCache { db ->
                db.rawQuery("SELECT ordinal FROM DbTimelineBatch WHERE id = ?", arrayOf(id)).use {
                    check(it.step())
                    it.getLong(0)
                }
            }
        var found = false
        repeat(250) {
            if (found) return@repeat
            try {
                val remainingBounds = device.findObject(By.res(tag))?.visibleBounds
                found =
                    remainingBounds != null && remainingBounds.top > device.displayHeight * .2 &&
                    remainingBounds.bottom < device.displayHeight * .8
                if (!found) {
                    val first = items().firstOrNull()?.resourceName?.removePrefix("timeline-item:")
                    val passed =
                        first?.let { key ->
                            readCache { db ->
                                db
                                    .rawQuery(
                                        "SELECT b.ordinal FROM DbTimelineEntry e JOIN DbTimelineBatch b ON b.id=e.batchId WHERE e.itemKey = ?",
                                        arrayOf(key),
                                    ).use {
                                        it.step() &&
                                            it.getLong(0) > targetOrdinal
                                    }
                            }
                        } ?: false
                    val direction =
                        if (passed ||
                            (remainingBounds != null && remainingBounds.height() > 0 && remainingBounds.top <= device.displayHeight * .2)
                        ) {
                            Direction.UP
                        } else {
                            Direction.DOWN
                        }
                    scrollList(direction)
                }
            } catch (_: StaleObjectException) {
                // A loaded page replaces accessibility nodes. Reacquire on the next sample.
                SystemClock.sleep(300)
            }
        }
        screenshot("$kind-remaining-gap")
        assertTrue("The remaining gap must appear below the inserted items", found)
        SystemClock.sleep(5000)
        assertEquals("The remaining gap must also wait for a click", after, batch(id))
    }

    private fun waitUntil(
        timeout: Long = 30_000,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (try {
                    condition()
                } catch (_: StaleObjectException) {
                    false
                }
            ) {
                return
            }
            SystemClock.sleep(300)
        }
        error("UI condition did not become true within ${timeout}ms")
    }

    private fun screenshot(name: String) {
        if (args.getString("screenshots") == "true") {
            device.takeScreenshot(File(directory, "$name.png"))
        }
    }
}
